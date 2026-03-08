package com.chatbot.eval.evaluator;

import com.chatbot.eval.model.*;

import java.util.*;

/**
 * L3 TrajectoryEvaluator — 过程轨迹 + 效率。
 * 合并原 TrajectoryEvaluator + EfficiencyEvaluator，新增 allowedCall、orderConstraints、toolArgConstraints。
 */
public class LayeredTrajectoryEvaluator implements Evaluator {

    private final int maxToolCallCount;
    private final long latencyThresholdMs;

    public LayeredTrajectoryEvaluator(int maxToolCallCount, long latencyThresholdMs) {
        this.maxToolCallCount = maxToolCallCount;
        this.latencyThresholdMs = latencyThresholdMs;
    }

    @Override
    public String name() {
        return "L3_Trajectory";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        List<String> violations = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        EpisodeExpected expected = episode.getExpected();
        TrajectoryExpected traj = expected != null ? expected.getTrajectory() : null;

        double score = 1.0;

        // 1. mustCall 检查（只计 status=ok）
        if (traj != null && traj.getMustCall() != null) {
            score = Math.min(score, checkMustCall(traj.getMustCall(),
                    runResult.getActions(), violations));
        }

        // 2. allowedCall 上限检查 (Phase 1 新增)
        if (traj != null && traj.getAllowedCall() != null) {
            score = Math.min(score, checkAllowedCall(traj.getAllowedCall(),
                    runResult.getActions(), violations));
        }

        // 3. 总调用上限
        int totalCalls = runResult.getActions().size();
        if (totalCalls > maxToolCallCount) {
            violations.add("TRAJ_TOTAL_CALLS: " + totalCalls + " > max " + maxToolCallCount);
            score = Math.min(score, 0.5);
        }
        details.put("totalToolCalls", totalCalls);

        // 4. orderConstraints 顺序约束 (Phase 1 新增)
        if (traj != null && traj.getOrderConstraints() != null) {
            score = Math.min(score, checkOrderConstraints(traj.getOrderConstraints(),
                    runResult.getActions(), violations));
        }

        // 5. toolArgConstraints 参数校验 (从 SemanticEvaluator 移入)
        if (traj != null && traj.getToolArgConstraints() != null) {
            score = Math.min(score, checkToolArgConstraints(traj.getToolArgConstraints(),
                    runResult.getActions(), violations));
        }

        // 6. 效率指标（从 EfficiencyEvaluator 合并）
        if (runResult.getMetrics() != null) {
            long latency = runResult.getMetrics().getLatencyMs();
            details.put("latencyMs", latency);
            details.put("toolCallCount", runResult.getMetrics().getToolCallCount());
            if (runResult.getMetrics().getTokenIn() != null) {
                details.put("tokenIn", runResult.getMetrics().getTokenIn());
            }
            if (runResult.getMetrics().getTokenOut() != null) {
                details.put("tokenOut", runResult.getMetrics().getTokenOut());
            }
            if (runResult.getMetrics().getCost() != null) {
                details.put("cost", runResult.getMetrics().getCost());
            }

            if (latency > latencyThresholdMs) {
                violations.add("TRAJ_LATENCY: " + latency + "ms > threshold " + latencyThresholdMs + "ms");
                double latencyPenalty = Math.min(0.3,
                        (latency - latencyThresholdMs) / (double) latencyThresholdMs * 0.3);
                score = Math.max(0.0, score - latencyPenalty);
            }
        }

        // Pass if no hard violations (mustCall / allowedCall)
        boolean passed = violations.stream()
                .noneMatch(v -> v.startsWith("TRAJ_MUST_CALL") || v.startsWith("TRAJ_ALLOWED"));

        EvalResult result = new EvalResult(name(), passed, score);
        result.setViolations(violations);
        result.setDetails(details);
        return result;
    }

    private double checkMustCall(List<MustCallConstraint> mustCalls, List<ToolAction> actions,
                                  List<String> violations) {
        double score = 1.0;
        for (MustCallConstraint mc : mustCalls) {
            long count = actions.stream()
                    .filter(a -> mc.getName().equals(a.getName()) && "ok".equals(a.getStatus()))
                    .count();
            if (count < mc.getMin()) {
                violations.add("TRAJ_MUST_CALL: " + mc.getName()
                        + " called " + count + " times < min " + mc.getMin());
                score = 0.0;
            }
        }
        return score;
    }

    private double checkAllowedCall(List<AllowedCallConstraint> constraints,
                                     List<ToolAction> actions, List<String> violations) {
        double score = 1.0;
        for (AllowedCallConstraint c : constraints) {
            long count = actions.stream()
                    .filter(a -> a.getName().equals(c.getName()))
                    .count();
            if (count > c.getMax()) {
                violations.add("TRAJ_ALLOWED: " + c.getName()
                        + " called " + count + " times > max " + c.getMax());
                score = 0.5;
            }
        }
        return score;
    }

    private double checkOrderConstraints(List<OrderConstraint> constraints,
                                          List<ToolAction> actions, List<String> violations) {
        double score = 1.0;
        for (OrderConstraint oc : constraints) {
            int beforeIdx = -1, afterIdx = -1;
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).getName().equals(oc.getBefore()) && beforeIdx == -1) {
                    beforeIdx = i;
                }
                if (actions.get(i).getName().equals(oc.getAfter())) {
                    afterIdx = i;
                }
            }
            if (beforeIdx >= 0 && afterIdx >= 0 && beforeIdx >= afterIdx) {
                violations.add("TRAJ_ORDER: " + oc.getBefore()
                        + " must be called before " + oc.getAfter());
                score = 0.5;
            }
        }
        return score;
    }

    private double checkToolArgConstraints(List<ToolArgConstraint> constraints,
                                            List<ToolAction> actions, List<String> violations) {
        double score = 1.0;
        for (ToolArgConstraint constraint : constraints) {
            List<ToolAction> matching = actions.stream()
                    .filter(a -> a.getName().equals(constraint.getName()) && "ok".equals(a.getStatus()))
                    .toList();

            if (matching.isEmpty()) {
                violations.add("TRAJ_ARGS: no matching ok action for " + constraint.getName());
                score = 0.0;
                continue;
            }

            boolean argMatch = matching.stream().anyMatch(action -> {
                Map<String, Object> expectedArgs = constraint.getArgs();
                Map<String, Object> actualArgs = action.getArgs();
                if (actualArgs == null) return false;
                String mode = constraint.getMatchMode() != null ? constraint.getMatchMode() : "exact";
                return matchArgs(expectedArgs, actualArgs, mode);
            });

            if (!argMatch) {
                violations.add("TRAJ_ARGS: args mismatch for " + constraint.getName()
                        + " expected=" + constraint.getArgs());
                score = Math.min(score, 0.5);
            }
        }
        return score;
    }

    private boolean matchArgs(Map<String, Object> expected, Map<String, Object> actual, String mode) {
        if (expected == null) return true;
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object actualVal = actual.get(entry.getKey());
            if (actualVal == null) return false;

            if ("contains".equals(mode)) {
                if (!actualVal.toString().contains(entry.getValue().toString())) return false;
            } else {
                if (!actualVal.toString().equals(entry.getValue().toString())) return false;
            }
        }
        return true;
    }
}
