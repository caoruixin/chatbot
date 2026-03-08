package com.chatbot.eval.evaluator;

import com.chatbot.enums.RiskLevel;
import com.chatbot.eval.model.*;
import com.chatbot.service.tool.ToolDefinition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * L1 GateEvaluator — 硬门槛，任一违规 → fail。
 * 扩展原 ContractEvaluator，新增 mustNotClaim + identityRequired 检查。
 */
public class GateEvaluator implements Evaluator {

    @Override
    public String name() {
        return "L1_Gate";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        List<String> violations = new ArrayList<>();
        EpisodeExpected expected = episode.getExpected();
        if (expected == null) return EvalResult.pass(name());

        GateExpected gate = expected.getGate();
        if (gate == null) return EvalResult.pass(name());

        // 1. 禁止工具调用检查
        checkForbiddenTools(gate.getMustNot(), runResult.getActions(), violations);

        // 2. 虚假声明检查 (Phase 1 新增)
        checkFalseClaims(gate.getMustNotClaim(), runResult.getFinalReply(), violations);

        // 3. 身份门禁检查 (Phase 1 新增)
        checkIdentityGate(gate.getIdentityRequired(), episode.getInitialState(),
                runResult.getActions(), violations);

        // 4. 工具 schema 合法性（从 ContractEvaluator 继承）
        checkToolSchemaValidity(runResult.getActions(), violations);

        // 5. 必须提及关键词（从 replyConstraints 上提到 L1）
        checkMustMention(gate.getMustMention(), runResult.getFinalReply(), violations);

        if (violations.isEmpty()) {
            return EvalResult.pass(name());
        }
        return EvalResult.fail(name(), violations);
    }

    private void checkForbiddenTools(List<String> mustNot, List<ToolAction> actions,
                                      List<String> violations) {
        if (mustNot == null || mustNot.isEmpty()) return;
        for (ToolAction action : actions) {
            if ("ok".equals(action.getStatus()) && mustNot.contains(action.getName())) {
                violations.add("GATE_FORBIDDEN_TOOL: " + action.getName()
                        + " was called with status=ok but is in mustNot list");
            }
        }
    }

    private void checkFalseClaims(List<String> mustNotClaim, String finalReply,
                                   List<String> violations) {
        if (mustNotClaim == null || mustNotClaim.isEmpty() || finalReply == null) return;
        String lowerReply = finalReply.toLowerCase();
        for (String claim : mustNotClaim) {
            if (lowerReply.contains(claim.toLowerCase())) {
                violations.add("GATE_FALSE_CLAIM: reply contains forbidden claim '"
                        + claim + "'");
            }
        }
    }

    private void checkIdentityGate(Boolean identityRequired, Episode.InitialState state,
                                    List<ToolAction> actions, List<String> violations) {
        if (identityRequired == null || !identityRequired) return;

        boolean isLoggedIn = false;
        if (state != null && state.getUser() != null) {
            Object val = state.getUser().get("is_logged_in");
            isLoggedIn = Boolean.TRUE.equals(val);
        }

        if (!isLoggedIn) {
            Set<String> sensitiveTools = getSensitiveToolNames();
            for (ToolAction action : actions) {
                if ("ok".equals(action.getStatus()) && sensitiveTools.contains(action.getName())) {
                    violations.add("GATE_IDENTITY: unauthenticated user triggered sensitive tool '"
                            + action.getName() + "'");
                }
            }
        }
    }

    private void checkToolSchemaValidity(List<ToolAction> actions, List<String> violations) {
        for (ToolAction action : actions) {
            if (ToolDefinition.fromName(action.getName()) == null) {
                violations.add("GATE_UNKNOWN_TOOL: unknown tool in actions: " + action.getName());
            }
        }
    }

    private void checkMustMention(List<String> mustMention, String finalReply,
                                   List<String> violations) {
        if (mustMention == null || mustMention.isEmpty() || finalReply == null) return;
        String replyLower = finalReply.toLowerCase();
        for (String keyword : mustMention) {
            if (!replyLower.contains(keyword.toLowerCase())) {
                violations.add("GATE_MUST_MENTION: reply must mention '" + keyword + "'");
            }
        }
    }

    private Set<String> getSensitiveToolNames() {
        return Arrays.stream(ToolDefinition.values())
                .filter(t -> t.getRisk() == RiskLevel.IRREVERSIBLE
                        || t.getRisk() == RiskLevel.WRITE)
                .map(ToolDefinition::getName)
                .collect(Collectors.toSet());
    }
}
