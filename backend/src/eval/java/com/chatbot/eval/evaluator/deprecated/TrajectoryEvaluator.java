package com.chatbot.eval.evaluator.deprecated;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.evaluator.Evaluator;
import com.chatbot.eval.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Phase 0 flat evaluator. Replaced by LayeredTrajectoryEvaluator (L3) in Phase 1.
 * Kept for reference only — do not use in new code.
 */
@Deprecated
public class TrajectoryEvaluator implements Evaluator {

    private final int maxToolCallCount;

    public TrajectoryEvaluator(int maxToolCallCount) {
        this.maxToolCallCount = maxToolCallCount;
    }

    @Override
    public String name() {
        return "trajectory";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        List<String> violations = new ArrayList<>();
        EpisodeExpected expected = episode.getExpected();

        // 1. must_call check: each tool called at least min times (counting only status="ok")
        if (expected.getMustCall() != null) {
            for (MustCallConstraint constraint : expected.getMustCall()) {
                long okCount = runResult.getActions().stream()
                        .filter(a -> constraint.getName().equals(a.getName()))
                        .filter(a -> "ok".equals(a.getStatus()))
                        .count();
                if (okCount < constraint.getMin()) {
                    violations.add(String.format("Tool '%s' called %d times (required min: %d)",
                            constraint.getName(), okCount, constraint.getMin()));
                }
            }
        }

        // 2. Tool call count upper bound
        int totalCalls = runResult.getActions().size();
        if (totalCalls > maxToolCallCount) {
            violations.add(String.format("Tool call count %d exceeds limit %d",
                    totalCalls, maxToolCallCount));
        }

        if (violations.isEmpty()) {
            return EvalResult.pass(name());
        }
        return EvalResult.fail(name(), violations);
    }
}
