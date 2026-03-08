package com.chatbot.eval.evaluator.deprecated;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.evaluator.Evaluator;
import com.chatbot.eval.model.Episode;
import com.chatbot.eval.model.EvalMetrics;
import com.chatbot.eval.model.RunResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Phase 0 flat evaluator. Merged into LayeredTrajectoryEvaluator (L3) in Phase 1.
 * Kept for reference only — do not use in new code.
 */
@Deprecated
public class EfficiencyEvaluator implements Evaluator {

    private final long latencyThresholdMs;

    public EfficiencyEvaluator(long latencyThresholdMs) {
        this.latencyThresholdMs = latencyThresholdMs;
    }

    @Override
    public String name() {
        return "efficiency";
    }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        List<String> violations = new ArrayList<>();
        EvalMetrics metrics = runResult.getMetrics();

        if (metrics == null) {
            EvalResult result = EvalResult.pass(name());
            result.getDetails().put("warning", "No metrics available");
            return result;
        }

        // Latency threshold check
        if (metrics.getLatencyMs() > latencyThresholdMs) {
            violations.add(String.format("Latency %dms exceeds threshold %dms",
                    metrics.getLatencyMs(), latencyThresholdMs));
        }

        // Record details for reporting
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("latencyMs", metrics.getLatencyMs());
        details.put("toolCallCount", metrics.getToolCallCount());
        if (metrics.getTokenIn() != null) details.put("tokenIn", metrics.getTokenIn());
        if (metrics.getTokenOut() != null) details.put("tokenOut", metrics.getTokenOut());
        if (metrics.getCost() != null) details.put("cost", metrics.getCost());

        double score = violations.isEmpty() ? 1.0
                : Math.max(0.0, 1.0 - (double) (metrics.getLatencyMs() - latencyThresholdMs) / latencyThresholdMs);

        EvalResult result = new EvalResult(name(), violations.isEmpty(), score);
        result.setViolations(violations);
        result.setDetails(details);
        return result;
    }
}
