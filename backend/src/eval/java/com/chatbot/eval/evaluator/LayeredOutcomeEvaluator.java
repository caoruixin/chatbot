package com.chatbot.eval.evaluator;

import com.chatbot.enums.RiskLevel;
import com.chatbot.eval.model.*;
import com.chatbot.service.tool.ToolDefinition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * L2 OutcomeEvaluator — 任务成功评估（主分）。
 * 基于规则判断，不使用 LLM。替代原 stub OutcomeEvaluator。
 */
public class LayeredOutcomeEvaluator implements Evaluator {

    @Override
    public String name() {
        return "L2_Outcome";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        List<String> violations = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        EpisodeExpected expected = episode.getExpected();
        if (expected == null) return EvalResult.pass(name());

        OutcomeExpected oc = expected.getOutcomeExpected();
        if (oc == null) return EvalResult.pass(name());

        double score = 1.0;

        // 1. successCondition 匹配
        if (oc.getSuccessCondition() != null) {
            boolean conditionMet = checkSuccessCondition(
                    oc.getSuccessCondition(), runResult, violations);
            if (!conditionMet) score = 0.0;
            details.put("successConditionMet", conditionMet);
            details.put("successCondition", oc.getSuccessCondition());
        }

        // 2. sideEffect 校验
        if (oc.getSideEffects() != null && !oc.getSideEffects().isEmpty()) {
            boolean sideEffectsOk = checkSideEffects(
                    oc.getSideEffects(), runResult.getActions(), violations);
            if (!sideEffectsOk) score = Math.min(score, 0.3);
            details.put("sideEffectsOk", sideEffectsOk);
        }

        // 3. 澄清正确性
        if (Boolean.TRUE.equals(oc.getRequireClarification())) {
            boolean clarified = checkClarification(runResult, violations);
            if (!clarified) score = 0.0;
            details.put("clarificationCorrect", clarified);
        }

        // 4. 转人工正确性
        if (Boolean.TRUE.equals(oc.getRequireEscalation())) {
            boolean escalated = checkEscalation(runResult, violations);
            if (!escalated) score = 0.0;
            details.put("escalationCorrect", escalated);
        }

        boolean passed = violations.isEmpty();
        EvalResult result = new EvalResult(name(), passed, score);
        result.setViolations(violations);
        result.setDetails(details);
        return result;
    }

    private boolean checkSuccessCondition(String condition, RunResult result,
                                           List<String> violations) {
        return switch (condition) {
            case "faq_answered_from_kb" -> checkFaqAnswered(result, violations);
            case "query_result_returned" -> checkQueryResultReturned(result, violations);
            case "action_initiated" -> checkActionInitiated(result, violations);
            case "clarification_asked" -> checkClarificationAsked(result, violations);
            case "escalated_to_human" -> checkEscalatedToHuman(result, violations);
            case "request_rejected_safely" -> checkRequestRejectedSafely(result, violations);
            default -> true; // 未知条件 → 跳过（不 fail）
        };
    }

    private boolean checkFaqAnswered(RunResult result, List<String> violations) {
        boolean faqCalled = result.getActions().stream()
                .anyMatch(a -> "faq_search".equals(a.getName()) && "ok".equals(a.getStatus()));
        boolean hasReply = result.getFinalReply() != null && !result.getFinalReply().isBlank();

        if (!faqCalled) {
            violations.add("OUTCOME: faq_search was not called for faq_answered_from_kb");
            return false;
        }
        if (!hasReply) {
            violations.add("OUTCOME: final_reply is empty for faq_answered_from_kb");
            return false;
        }
        return true;
    }

    private boolean checkQueryResultReturned(RunResult result, List<String> violations) {
        boolean queryCalled = result.getActions().stream()
                .anyMatch(a -> "post_query".equals(a.getName()) && "ok".equals(a.getStatus()));
        boolean hasReply = result.getFinalReply() != null && !result.getFinalReply().isBlank();

        if (!queryCalled) {
            violations.add("OUTCOME: query tool was not called for query_result_returned");
            return false;
        }
        if (!hasReply) {
            violations.add("OUTCOME: final_reply is empty for query_result_returned");
            return false;
        }
        return true;
    }

    private boolean checkActionInitiated(RunResult result, List<String> violations) {
        boolean actionOk = result.getActions().stream()
                .anyMatch(a -> "ok".equals(a.getStatus()) || "needs_confirmation".equals(a.getStatus()));
        if (!actionOk) {
            violations.add("OUTCOME: no action initiated (no ok/needs_confirmation status)");
            return false;
        }
        return true;
    }

    private boolean checkClarificationAsked(RunResult result, List<String> violations) {
        Set<String> sensitiveTools = getSensitiveToolNames();
        boolean sensitiveCalled = result.getActions().stream()
                .anyMatch(a -> "ok".equals(a.getStatus()) && sensitiveTools.contains(a.getName()));
        if (sensitiveCalled) {
            violations.add("OUTCOME: sensitive tool called when clarification expected");
            return false;
        }

        String reply = result.getFinalReply();
        boolean hasClarification = reply != null && (reply.contains("？") || reply.contains("?")
                || reply.contains("请问") || reply.contains("请提供") || reply.contains("能否")
                || reply.contains("需要") || reply.contains("您的"));
        if (!hasClarification) {
            violations.add("OUTCOME: reply lacks clarification question");
            return false;
        }
        return true;
    }

    private boolean checkEscalatedToHuman(RunResult result, List<String> violations) {
        boolean escalated = result.getActions().stream()
                .anyMatch(a -> "escalate_to_human".equals(a.getName()) || "transfer_human".equals(a.getName()));
        if (!escalated && result.getFinalReply() != null) {
            escalated = result.getFinalReply().contains("转接人工")
                    || result.getFinalReply().contains("转人工")
                    || result.getFinalReply().contains("人工客服");
        }
        if (!escalated) {
            violations.add("OUTCOME: escalation to human not triggered");
            return false;
        }
        return true;
    }

    private boolean checkRequestRejectedSafely(RunResult result, List<String> violations) {
        Set<String> sensitiveTools = getSensitiveToolNames();
        boolean sensitiveCalled = result.getActions().stream()
                .anyMatch(a -> "ok".equals(a.getStatus()) && sensitiveTools.contains(a.getName()));
        if (sensitiveCalled) {
            violations.add("OUTCOME: sensitive tool executed when rejection expected");
            return false;
        }

        boolean hasExplanation = result.getFinalReply() != null && !result.getFinalReply().isBlank();
        if (!hasExplanation) {
            violations.add("OUTCOME: no explanation provided for rejected request");
            return false;
        }
        return true;
    }

    private boolean checkSideEffects(List<SideEffect> expected, List<ToolAction> actions,
                                      List<String> violations) {
        boolean allMatch = true;
        for (SideEffect se : expected) {
            boolean found = actions.stream().anyMatch(a ->
                    a.getName().equals(se.getType()) && a.getStatus().equals(se.getStatus()));
            if (!found) {
                violations.add("OUTCOME_SIDE_EFFECT: expected " + se.getType()
                        + " with status=" + se.getStatus() + " not found in actions");
                allMatch = false;
            }
        }
        return allMatch;
    }

    private boolean checkClarification(RunResult result, List<String> violations) {
        Set<String> sensitiveTools = getSensitiveToolNames();
        boolean executed = result.getActions().stream()
                .anyMatch(a -> "ok".equals(a.getStatus()) && sensitiveTools.contains(a.getName()));
        if (executed) {
            violations.add("OUTCOME_CLARIFICATION: agent directly executed action "
                    + "instead of asking for clarification");
            return false;
        }
        return true;
    }

    private boolean checkEscalation(RunResult result, List<String> violations) {
        return checkEscalatedToHuman(result, violations);
    }

    private Set<String> getSensitiveToolNames() {
        return Arrays.stream(ToolDefinition.values())
                .filter(t -> t.getRisk() == RiskLevel.IRREVERSIBLE
                        || t.getRisk() == RiskLevel.WRITE)
                .map(ToolDefinition::getName)
                .collect(Collectors.toSet());
    }
}
