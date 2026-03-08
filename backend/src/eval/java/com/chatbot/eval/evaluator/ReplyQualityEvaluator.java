package com.chatbot.eval.evaluator;

import com.chatbot.eval.model.*;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * L4 ReplyQualityEvaluator — 回复质量（辅助分）。
 * 合并原 SemanticEvaluator + RagQualityEvaluator。
 * 三个子维度：语义相似度(0.4) + LLM Judge(0.4) + RAG 质量(0.2)。
 */
public class ReplyQualityEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(ReplyQualityEvaluator.class);

    private static final String JUDGE_SYSTEM_PROMPT =
            "你是一个客服回复质量评估专家。请对比\"参考回复\"和\"实际回复\"，从以下三个维度打分（1-5分）。\n\n" +
            "评分标准校准：\n" +
            "- 5分：完美，与参考回复语义完全一致，无遗漏无多余\n" +
            "- 4分：良好，关键信息完整，表述略有差异但不影响理解\n" +
            "- 3分：及格，包含核心信息但有遗漏或不准确\n" +
            "- 2分：较差，关键信息缺失或有误导性表述\n" +
            "- 1分：不及格，完全偏题或含有虚假信息\n\n" +
            "评分维度：\n" +
            "1. correctness（正确性，权重0.5）：实际回复是否准确，是否与参考回复语义一致\n" +
            "2. completeness（完整性，权重0.3）：是否包含关键信息点和下一步指引\n" +
            "3. tone（语气，权重0.2）：是否符合客服场景（有同理心、不僵硬、不过度承诺）\n\n" +
            "请严格以JSON格式输出：{\"correctness\": N, \"completeness\": N, \"tone\": N, \"reasoning\": \"...\"}";

    private static final String FAITHFULNESS_SYSTEM_PROMPT =
            "你是一个事实核查专家。请判断\"AI回复\"中的每个事实声明是否可以在\"检索到的上下文\"中找到依据。\n" +
            "评分标准：\n" +
            "- 1.0：所有事实声明都有上下文依据\n" +
            "- 0.5：部分事实声明有依据，部分是合理推断但无直接依据\n" +
            "- 0.0：包含明显编造的信息（上下文中完全没有依据的事实声明）\n\n" +
            "请严格以JSON格式输出：{\"score\": N, \"reasoning\": \"...\"}";

    private final KimiClient kimiClient;
    private final double similarityThreshold;
    private final String judgeModelId;
    private final Map<String, Double> judgeWeights;
    private final boolean mockMode;

    public ReplyQualityEvaluator(KimiClient kimiClient, String judgeModelId,
                                  double similarityThreshold,
                                  Map<String, Double> judgeWeights, boolean mockMode) {
        this.kimiClient = kimiClient;
        this.judgeModelId = judgeModelId;
        this.similarityThreshold = similarityThreshold;
        this.judgeWeights = judgeWeights;
        this.mockMode = mockMode;
    }

    @Override
    public String name() {
        return "L4_ReplyQuality";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        EpisodeExpected expected = episode.getExpected();
        if (expected == null) return EvalResult.pass(name());

        ReplyQualityExpected rq = expected.getReplyQuality();
        if (rq == null) return EvalResult.pass(name());

        boolean hasGoldenReply = rq.getGoldenReply() != null && !rq.getGoldenReply().isBlank();
        boolean hasExpectedContexts = rq.getExpectedContexts() != null && !rq.getExpectedContexts().isEmpty();

        if (!hasGoldenReply && !hasExpectedContexts) {
            EvalResult result = EvalResult.pass(name());
            result.getDetails().put("note", "No reply quality checks configured");
            return result;
        }

        // Mock mode
        if (mockMode) {
            return buildMockResult(episode, runResult, rq, hasGoldenReply, hasExpectedContexts);
        }

        List<String> violations = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        double totalScore = 0.0;
        double totalWeight = 0.0;

        // 1. 语义相似度 (权重 0.4)
        if (hasGoldenReply) {
            try {
                double simScore = computeSimilarity(runResult.getFinalReply(), rq.getGoldenReply());
                details.put("similarityScore", Math.round(simScore * 1000.0) / 1000.0);
                details.put("similarityThreshold", similarityThreshold);
                totalScore += 0.4 * simScore;
                totalWeight += 0.4;

                if (simScore < similarityThreshold) {
                    violations.add("REPLY_SIMILARITY: " + String.format("%.3f", simScore)
                            + " < threshold " + similarityThreshold);
                }
            } catch (Exception e) {
                log.warn("Similarity computation failed for episode {}: {}", episode.getId(), e.getMessage());
                details.put("similarityError", e.getMessage());
            }
        }

        // 2. LLM Judge 评分 (权重 0.4)
        if (hasGoldenReply) {
            try {
                String userMsg = (episode.getConversation() != null && !episode.getConversation().isEmpty())
                        ? episode.getConversation().get(0).getContent() : "";
                Map<String, Object> judgeScores = runLlmJudge(userMsg, rq.getGoldenReply(), runResult.getFinalReply());
                details.put("judgeScores", judgeScores);

                double judgeComposite = computeJudgeComposite(judgeScores);
                details.put("judgeCompositeScore", Math.round(judgeComposite * 100.0) / 100.0);
                totalScore += 0.4 * (judgeComposite / 5.0);
                totalWeight += 0.4;
            } catch (Exception e) {
                log.warn("LLM judge failed for episode {}: {}", episode.getId(), e.getMessage());
                details.put("judgeError", e.getMessage());
            }
        }

        // 3. RAG 质量 (权重 0.2)
        if (hasExpectedContexts) {
            Map<String, Double> ragScores = evaluateRagQuality(
                    rq.getExpectedContexts(), runResult.getRetrievedContexts(),
                    rq.getFaithfulnessCheck(), runResult.getFinalReply());
            details.put("ragScores", ragScores);

            double ragComposite = (ragScores.getOrDefault("precision", 0.0) * 0.5
                    + ragScores.getOrDefault("recall", 0.0) * 0.5);
            totalScore += 0.2 * ragComposite;
            totalWeight += 0.2;
        }

        double finalScore = totalWeight > 0 ? totalScore / totalWeight : 1.0;
        details.put("finalScore", Math.round(finalScore * 1000.0) / 1000.0);

        boolean passed = finalScore >= 0.5;
        EvalResult result = new EvalResult(name(), passed, finalScore);
        result.setViolations(violations);
        result.setDetails(details);
        return result;
    }

    private EvalResult buildMockResult(Episode episode, RunResult runResult,
                                        ReplyQualityExpected rq,
                                        boolean hasGoldenReply, boolean hasExpectedContexts) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mode", "mock");
        double score = 0.85;

        if (hasGoldenReply) {
            int replyLen = runResult.getFinalReply() != null ? runResult.getFinalReply().length() : 0;
            int goldenLen = rq.getGoldenReply().length();
            double lenRatio = goldenLen > 0 ? Math.min(1.0, (double) replyLen / goldenLen) : 0.5;
            double mockSimilarity = 0.6 + 0.3 * lenRatio;
            details.put("similarityScore", Math.round(mockSimilarity * 1000.0) / 1000.0);
            details.put("similarityThreshold", similarityThreshold);
            details.put("judgeScores", Map.of(
                    "correctness", 4, "completeness", 4, "tone", 5,
                    "reasoning", "[mock] 待人工评估补充"));
            details.put("judgeCompositeScore", 4.3);
            score = 0.88;
        }

        if (hasExpectedContexts) {
            List<RetrievedContext> retrieved = runResult.getRetrievedContexts();
            if (retrieved != null && !retrieved.isEmpty()) {
                Map<String, Double> ragScores = evaluateRagQuality(
                        rq.getExpectedContexts(), retrieved, null, null);
                details.put("ragScores", ragScores);
            } else {
                details.put("ragScores", Map.of("precision", 0.0, "recall", 0.0));
            }
        }

        EvalResult result = new EvalResult(name(), true, score);
        result.setDetails(details);
        return result;
    }

    private double computeSimilarity(String actualReply, String goldenReply) {
        float[] actualEmbedding = kimiClient.embeddingDocument(actualReply);
        float[] goldenEmbedding = kimiClient.embeddingDocument(goldenReply);
        return cosineSimilarity(actualEmbedding, goldenEmbedding);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Embedding dimensions don't match: " + a.length + " vs " + b.length);
        }
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runLlmJudge(String userMessage, String goldenReply, String actualReply) {
        String userPrompt = String.format(
                "用户问题：%s\n参考回复：%s\n实际回复：%s",
                userMessage, goldenReply, actualReply);

        KimiChatResponse response = kimiClient.chatCompletion(
                JUDGE_SYSTEM_PROMPT,
                List.of(new KimiMessage("user", userPrompt)),
                0.1);

        String content = response.getContent();
        if (content == null || content.isBlank()) {
            throw new RuntimeException("LLM judge returned empty response");
        }

        String json = content.trim();
        if (json.contains("{")) {
            json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse judge response as JSON: {}", content);
            throw new RuntimeException("Failed to parse LLM judge response: " + e.getMessage());
        }
    }

    private double computeJudgeComposite(Map<String, Object> scores) {
        double correctness = getScoreValue(scores, "correctness");
        double completeness = getScoreValue(scores, "completeness");
        double tone = getScoreValue(scores, "tone");

        double wCorrectness = judgeWeights.getOrDefault("correctness", 0.5);
        double wCompleteness = judgeWeights.getOrDefault("completeness", 0.3);
        double wTone = judgeWeights.getOrDefault("tone", 0.2);

        return correctness * wCorrectness + completeness * wCompleteness + tone * wTone;
    }

    private double getScoreValue(Map<String, Object> scores, String key) {
        Object val = scores.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 3.0;
    }

    private Map<String, Double> evaluateRagQuality(List<String> expectedContexts,
                                                     List<RetrievedContext> retrievedContexts,
                                                     Boolean faithfulnessCheck, String finalReply) {
        Map<String, Double> scores = new LinkedHashMap<>();

        if (retrievedContexts == null || retrievedContexts.isEmpty()) {
            scores.put("precision", 0.0);
            scores.put("recall", 0.0);
            return scores;
        }

        Set<String> retrievedQuestions = retrievedContexts.stream()
                .map(RetrievedContext::getQuestion)
                .collect(Collectors.toSet());

        // Context Precision
        long relevantRetrieved = retrievedQuestions.stream()
                .filter(q -> expectedContexts.stream().anyMatch(
                        e -> q.contains(e) || e.contains(q)))
                .count();
        double precision = (double) relevantRetrieved / retrievedContexts.size();
        scores.put("precision", Math.round(precision * 1000.0) / 1000.0);

        // Context Recall
        long expectedFound = expectedContexts.stream()
                .filter(e -> retrievedQuestions.stream().anyMatch(
                        q -> q.contains(e) || e.contains(q)))
                .count();
        double recall = expectedContexts.isEmpty() ? 1.0 : (double) expectedFound / expectedContexts.size();
        scores.put("recall", Math.round(recall * 1000.0) / 1000.0);

        // Faithfulness (optional)
        if (Boolean.TRUE.equals(faithfulnessCheck) && finalReply != null) {
            try {
                double faithfulness = checkFaithfulness(finalReply, retrievedContexts);
                scores.put("faithfulness", Math.round(faithfulness * 1000.0) / 1000.0);
            } catch (Exception e) {
                log.warn("Faithfulness check failed: {}", e.getMessage());
            }
        }

        return scores;
    }

    @SuppressWarnings("unchecked")
    private double checkFaithfulness(String reply, List<RetrievedContext> contexts) {
        StringBuilder contextStr = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            RetrievedContext rc = contexts.get(i);
            contextStr.append(String.format("%d. 问题: %s (相似度: %.2f)\n", i + 1, rc.getQuestion(), rc.getScore()));
        }

        String userPrompt = String.format("检索到的上下文：\n%s\nAI回复：\n%s", contextStr, reply);

        KimiChatResponse response = kimiClient.chatCompletion(
                FAITHFULNESS_SYSTEM_PROMPT,
                List.of(new KimiMessage("user", userPrompt)),
                0.1);

        String content = response.getContent();
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Faithfulness check returned empty response");
        }

        String json = content.trim();
        if (json.contains("{")) {
            json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> result = mapper.readValue(json, Map.class);
            Object scoreVal = result.get("score");
            if (scoreVal instanceof Number) return ((Number) scoreVal).doubleValue();
            return 0.5;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse faithfulness response: " + e.getMessage());
        }
    }
}
