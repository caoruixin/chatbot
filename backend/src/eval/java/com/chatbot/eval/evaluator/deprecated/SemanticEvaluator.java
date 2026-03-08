package com.chatbot.eval.evaluator.deprecated;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.evaluator.Evaluator;
import com.chatbot.eval.model.*;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @deprecated Phase 0 flat evaluator. Replaced by ReplyQualityEvaluator (L4) in Phase 1.
 * Semantic similarity + LLM Judge + RAG → L4 ReplyQualityEvaluator.
 * Tool arg validation → L3 LayeredTrajectoryEvaluator.
 * Kept for reference only — do not use in new code.
 */
@Deprecated
public class SemanticEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(SemanticEvaluator.class);

    private static final String JUDGE_SYSTEM_PROMPT =
            "你是一个客服回复质量评估专家。请对比\"参考回复\"和\"实际回复\"，从以下三个维度打分（1-5分）：\n" +
            "1. correctness（正确性）：实际回复是否准确，是否与参考回复语义一致。5分=完全准确，3分=基本正确但有遗漏，1分=有事实错误。\n" +
            "2. completeness（完整性）：是否包含关键信息点。5分=包含所有关键信息，3分=包含主要信息，1分=严重遗漏。\n" +
            "3. tone（语气）：是否符合客服场景（礼貌、专业）。5分=非常专业礼貌，3分=基本得体，1分=不当语气。\n\n" +
            "请严格以JSON格式输出，不要包含其他内容：{\"correctness\": N, \"completeness\": N, \"tone\": N, \"reasoning\": \"...\"}";

    private final KimiClient kimiClient;
    private final String judgeModel;
    private final double similarityThreshold;
    private final double judgeScoreThreshold;
    private final Map<String, Double> judgeWeights;
    private final boolean mockMode;

    public SemanticEvaluator(KimiClient kimiClient, String judgeModel,
                             double similarityThreshold, double judgeScoreThreshold,
                             Map<String, Double> judgeWeights, boolean mockMode) {
        this.kimiClient = kimiClient;
        this.judgeModel = judgeModel;
        this.similarityThreshold = similarityThreshold;
        this.judgeScoreThreshold = judgeScoreThreshold;
        this.judgeWeights = judgeWeights;
        this.mockMode = mockMode;
    }

    @Override
    public String name() {
        return "semantic";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        EpisodeExpected expected = episode.getExpected();
        if (expected == null) {
            return EvalResult.pass(name());
        }

        boolean hasGoldenReply = expected.getGoldenReply() != null && !expected.getGoldenReply().isBlank();
        boolean hasToolArgConstraints = expected.getToolArgConstraints() != null && !expected.getToolArgConstraints().isEmpty();

        // Skip if no semantic checks are configured for this episode
        if (!hasGoldenReply && !hasToolArgConstraints) {
            EvalResult result = EvalResult.pass(name());
            result.getDetails().put("note", "No semantic checks configured for this episode");
            return result;
        }

        // Mock mode: return plausible mock scores without API calls
        if (mockMode) {
            return buildMockResult(episode, runResult, hasGoldenReply, hasToolArgConstraints);
        }

        List<String> violations = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        double totalWeight = 0;
        double weightedScore = 0;

        // Sub-check 1: Golden reply similarity
        if (hasGoldenReply) {
            try {
                double similarity = computeSimilarity(runResult.getFinalReply(), expected.getGoldenReply());
                details.put("similarityScore", Math.round(similarity * 1000.0) / 1000.0);
                details.put("similarityThreshold", similarityThreshold);

                if (similarity < similarityThreshold) {
                    violations.add(String.format("Similarity %.3f below threshold %.3f", similarity, similarityThreshold));
                }
                weightedScore += 0.4 * similarity;
                totalWeight += 0.4;
            } catch (Exception e) {
                log.warn("Similarity computation failed for episode {}: {}", episode.getId(), e.getMessage());
                details.put("similarityError", e.getMessage());
            }
        }

        // Sub-check 2: LLM-as-Judge scoring
        if (hasGoldenReply) {
            try {
                String userMsg = (episode.getConversation() != null && !episode.getConversation().isEmpty())
                        ? episode.getConversation().get(0).getContent() : "";
                Map<String, Object> judgeScores = runLlmJudge(
                        userMsg,
                        expected.getGoldenReply(),
                        runResult.getFinalReply());
                details.put("judgeScores", judgeScores);

                double compositeJudge = computeCompositeJudgeScore(judgeScores);
                details.put("judgeCompositeScore", Math.round(compositeJudge * 100.0) / 100.0);
                details.put("judgeScoreThreshold", judgeScoreThreshold);

                if (compositeJudge < judgeScoreThreshold) {
                    violations.add(String.format("Judge composite score %.2f below threshold %.2f",
                            compositeJudge, judgeScoreThreshold));
                }
                weightedScore += 0.4 * (compositeJudge / 5.0);
                totalWeight += 0.4;
            } catch (Exception e) {
                log.warn("LLM judge failed for episode {}: {}", episode.getId(), e.getMessage());
                details.put("judgeError", e.getMessage());
            }
        }

        // Sub-check 3: Tool argument validation
        if (hasToolArgConstraints) {
            List<Map<String, Object>> toolArgResults = validateToolArgs(
                    expected.getToolArgConstraints(), runResult.getActions());
            details.put("toolArgResults", toolArgResults);

            long toolArgFails = toolArgResults.stream()
                    .filter(r -> !(Boolean) r.get("passed"))
                    .count();
            if (toolArgFails > 0) {
                violations.add(String.format("%d tool argument constraint(s) failed", toolArgFails));
            }
            double toolArgScore = toolArgFails == 0 ? 1.0 : 1.0 - ((double) toolArgFails / toolArgResults.size());
            weightedScore += 0.2 * toolArgScore;
            totalWeight += 0.2;
        }

        // Compute final score
        double finalScore = totalWeight > 0 ? weightedScore / totalWeight : 1.0;
        boolean passed = violations.isEmpty();

        EvalResult result = new EvalResult(name(), passed, finalScore);
        result.setViolations(violations);
        result.setDetails(details);
        return result;
    }

    private EvalResult buildMockResult(Episode episode, RunResult runResult,
                                        boolean hasGoldenReply, boolean hasToolArgConstraints) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mode", "mock");
        double score = 0.85;

        if (hasGoldenReply) {
            // Generate deterministic mock scores based on reply length ratio
            int replyLen = runResult.getFinalReply() != null ? runResult.getFinalReply().length() : 0;
            int goldenLen = episode.getExpected().getGoldenReply().length();
            double lenRatio = goldenLen > 0 ? Math.min(1.0, (double) replyLen / goldenLen) : 0.5;
            double mockSimilarity = 0.6 + 0.3 * lenRatio;
            details.put("similarityScore", Math.round(mockSimilarity * 1000.0) / 1000.0);
            details.put("similarityThreshold", similarityThreshold);
            details.put("judgeScores", Map.of(
                    "correctness", 4, "completeness", 4, "tone", 5,
                    "reasoning", "[mock] 待人工评估补充"));
            details.put("judgeCompositeScore", 4.3);
            details.put("judgeScoreThreshold", judgeScoreThreshold);
            score = 0.88;
        }

        if (hasToolArgConstraints) {
            List<Map<String, Object>> toolArgResults = validateToolArgs(
                    episode.getExpected().getToolArgConstraints(), runResult.getActions());
            details.put("toolArgResults", toolArgResults);
            long toolArgFails = toolArgResults.stream()
                    .filter(r -> !(Boolean) r.get("passed")).count();
            if (toolArgFails > 0) {
                score = Math.max(0.5, score - 0.2);
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

        // Extract JSON from response (handle possible markdown wrapping)
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

    private double computeCompositeJudgeScore(Map<String, Object> scores) {
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
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 3.0; // default mid-score if missing
    }

    private List<Map<String, Object>> validateToolArgs(
            List<ToolArgConstraint> constraints, List<ToolAction> actions) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (ToolArgConstraint constraint : constraints) {
            Map<String, Object> checkResult = new LinkedHashMap<>();
            checkResult.put("tool", constraint.getName());
            checkResult.put("expectedArgs", constraint.getArgs());

            List<ToolAction> matching = actions.stream()
                    .filter(a -> constraint.getName().equals(a.getName()) && "ok".equals(a.getStatus()))
                    .toList();

            if (matching.isEmpty()) {
                checkResult.put("passed", false);
                checkResult.put("reason", "No successful call found for tool: " + constraint.getName());
                results.add(checkResult);
                continue;
            }

            boolean anyMatch = matching.stream().anyMatch(action -> argsMatch(constraint, action.getArgs()));
            checkResult.put("passed", anyMatch);
            if (!anyMatch) {
                checkResult.put("reason", "No matching args found in " + matching.size() + " call(s)");
                checkResult.put("actualArgs", matching.get(0).getArgs());
            }
            results.add(checkResult);
        }

        return results;
    }

    private boolean argsMatch(ToolArgConstraint constraint, Map<String, Object> actualArgs) {
        if (constraint.getArgs() == null || actualArgs == null) return false;

        for (Map.Entry<String, Object> entry : constraint.getArgs().entrySet()) {
            Object actualVal = actualArgs.get(entry.getKey());
            if (actualVal == null) return false;

            if (constraint.isContainsMode()) {
                if (!String.valueOf(actualVal).contains(String.valueOf(entry.getValue()))) {
                    return false;
                }
            } else {
                if (!String.valueOf(entry.getValue()).equals(String.valueOf(actualVal))) {
                    return false;
                }
            }
        }
        return true;
    }
}
