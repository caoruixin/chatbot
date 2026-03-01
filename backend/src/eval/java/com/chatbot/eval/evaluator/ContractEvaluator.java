package com.chatbot.eval.evaluator;

import com.chatbot.eval.model.*;
import com.chatbot.service.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * E1: Contract evaluator - hard gate checks.
 * Checks must_not violations, reply constraints, and tool schema validity.
 */
public class ContractEvaluator implements Evaluator {

    @Override
    public String name() {
        return "contract";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        List<String> violations = new ArrayList<>();
        EpisodeExpected expected = episode.getExpected();

        // 1. must_not check: forbidden tool calls
        if (expected.getMustNot() != null) {
            for (String forbidden : expected.getMustNot()) {
                for (ToolAction action : runResult.getActions()) {
                    if (forbidden.equals(action.getName()) && "ok".equals(action.getStatus())) {
                        violations.add("Forbidden tool called: " + forbidden);
                    }
                }
            }
        }

        // 2. reply_constraints.must_mention: keywords that must appear in reply
        ReplyConstraints replyConstraints = expected.getReplyConstraints();
        if (replyConstraints != null && replyConstraints.getMustMention() != null) {
            String reply = runResult.getFinalReply();
            if (reply != null) {
                String replyLower = reply.toLowerCase();
                for (String keyword : replyConstraints.getMustMention()) {
                    if (!replyLower.contains(keyword.toLowerCase())) {
                        violations.add("Reply must mention: " + keyword);
                    }
                }
            }
        }

        // 3. Tool call schema validation: every action.name must exist in ToolDefinition
        for (ToolAction action : runResult.getActions()) {
            if (ToolDefinition.fromName(action.getName()) == null) {
                violations.add("Unknown tool in actions: " + action.getName());
            }
        }

        if (violations.isEmpty()) {
            return EvalResult.pass(name());
        }
        return EvalResult.fail(name(), violations);
    }
}
