package com.chatbot.eval.evaluator.deprecated;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.evaluator.Evaluator;
import com.chatbot.eval.model.*;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @deprecated Phase 0 flat evaluator. Merged into ReplyQualityEvaluator (L4) in Phase 1.
 * Kept for reference only — do not use in new code.
 */
@Deprecated
public class RagQualityEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(RagQualityEvaluator.class);

    private static final String FAITHFULNESS_SYSTEM_PROMPT =
            "你是一个事实核查专家。请判断\"AI回复\"中的每个事实声明是否可以在\"检索到的上下文\"中找到依据。\n" +
            "评分标准：\n" +
            "- 1.0：所有事实声明都有上下文依据\n" +
            "- 0.5：部分事实声明有依据，部分是合理推断但无直接依据\n" +
            "- 0.0：包含明显编造的信息（上下文中完全没有依据的事实声明）\n\n" +
            "请严格以JSON格式输出：{\"score\": N, \"reasoning\": \"...\"}";

    private final KimiClient kimiClient;
    private final boolean mockMode;

    public RagQualityEvaluator(KimiClient kimiClient, boolean mockMode) {
        this.kimiClient = kimiClient;
        this.mockMode = mockMode;
    }

    @Override
    public String name() {
        return "rag_quality";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        EpisodeExpected expected = episode.getExpected();
        if (expected == null) {
            return EvalResult.pass(name());
        }

        boolean hasExpectedContexts = expected.getExpectedContexts() != null
                && !expected.getExpectedContexts().isEmpty();
        boolean hasFaithfulnessCheck = Boolean.TRUE.equals(expected.getFaithfulnessCheck());

        // Skip if no RAG quality checks configured
        if (!hasExpectedContexts && !hasFaithfulnessCheck) {
            EvalResult result = EvalResult.pass(name());
            result.getDetails().put("note", "No RAG quality checks configured for this episode");
            return result;
        }

        List<RetrievedContext> retrieved = runResult.getRetrievedContexts();
        if (retrieved == null) {
            retrieved = List.of();
        }

        List<String> violations = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        double totalWeight = 0;
        double weightedScore = 0;

        // Sub-check 1 & 2: Context Precision and Recall
        if (hasExpectedContexts) {
            Set<String> expectedSet = new HashSet<>(expected.getExpectedContexts());
            Set<String> retrievedQuestions = new HashSet<>();
            for (RetrievedContext rc : retrieved) {
                retrievedQuestions.add(rc.getQuestion());
            }

            // Precision: of retrieved docs, how many are in expected
            long hits = retrieved.stream()
                    .filter(rc -> expectedSet.contains(rc.getQuestion()))
                    .count();
            double precision = retrieved.isEmpty() ? 0.0 : (double) hits / retrieved.size();
            details.put("contextPrecision", Math.round(precision * 1000.0) / 1000.0);

            // Recall: of expected docs, how many were retrieved
            long recalled = expectedSet.stream()
                    .filter(retrievedQuestions::contains)
                    .count();
            double recall = expectedSet.isEmpty() ? 1.0 : (double) recalled / expectedSet.size();
            details.put("contextRecall", Math.round(recall * 1000.0) / 1000.0);
            details.put("expectedContexts", expected.getExpectedContexts());
            details.put("retrievedContexts", retrieved.stream()
                    .map(RetrievedContext::getQuestion).toList());

            if (recall < 0.5) {
                violations.add(String.format("Context recall %.3f is below 0.5 (retrieved %d of %d expected)",
                        recall, recalled, expectedSet.size()));
            }

            // Weight precision and recall equally
            double prScore = (precision + recall) / 2.0;
            weightedScore += 0.5 * prScore;
            totalWeight += 0.5;
        }

        // Sub-check 3: Faithfulness
        if (hasFaithfulnessCheck && !retrieved.isEmpty()) {
            try {
                double faithfulness;
                if (mockMode) {
                    // Mock: assume reasonable faithfulness
                    faithfulness = 0.85;
                    details.put("faithfulnessMode", "mock");
                } else {
                    faithfulness = checkFaithfulness(runResult.getFinalReply(), retrieved);
                }
                details.put("faithfulnessScore", Math.round(faithfulness * 1000.0) / 1000.0);

                if (faithfulness < 0.5) {
                    violations.add(String.format("Faithfulness score %.3f is below 0.5", faithfulness));
                }

                weightedScore += 0.5 * faithfulness;
                totalWeight += 0.5;
            } catch (Exception e) {
                log.warn("Faithfulness check failed for episode {}: {}", episode.getId(), e.getMessage());
                details.put("faithfulnessError", e.getMessage());
            }
        } else if (hasFaithfulnessCheck && retrieved.isEmpty()) {
            details.put("faithfulnessNote", "Skipped: no retrieved contexts available");
        }

        double finalScore = totalWeight > 0 ? weightedScore / totalWeight : 1.0;
        boolean passed = violations.isEmpty();

        EvalResult result = new EvalResult(name(), passed, finalScore);
        result.setViolations(violations);
        result.setDetails(details);
        return result;
    }

    @SuppressWarnings("unchecked")
    private double checkFaithfulness(String reply, List<RetrievedContext> contexts) {
        StringBuilder contextStr = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            RetrievedContext rc = contexts.get(i);
            contextStr.append(String.format("%d. 问题: %s (相似度: %.2f)\n", i + 1, rc.getQuestion(), rc.getScore()));
        }

        String userPrompt = String.format(
                "检索到的上下文：\n%s\nAI回复：\n%s",
                contextStr, reply);

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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> result = mapper.readValue(json, Map.class);
            Object score = result.get("score");
            if (score instanceof Number) {
                return ((Number) score).doubleValue();
            }
            return 0.5;
        } catch (Exception e) {
            log.warn("Failed to parse faithfulness response: {}", content);
            throw new RuntimeException("Failed to parse faithfulness response: " + e.getMessage());
        }
    }
}
