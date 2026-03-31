package com.chatbot.eval.runner;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.evaluator.Evaluator;
import com.chatbot.eval.adapter.AgentAdapter;
import com.chatbot.eval.model.*;
import com.chatbot.eval.report.HtmlReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates dataset -> adapter -> evaluators -> output.
 * Phase 1: 分层评估逻辑 + 优先级判定。
 */
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final AgentAdapter adapter;
    private final List<Evaluator> evaluators;
    private final ResultWriter resultWriter;
    private final HtmlReportGenerator htmlReportGenerator;

    public EvalRunner(AgentAdapter adapter, List<Evaluator> evaluators,
                      ResultWriter resultWriter, HtmlReportGenerator htmlReportGenerator) {
        this.adapter = adapter;
        this.evaluators = evaluators;
        this.resultWriter = resultWriter;
        this.htmlReportGenerator = htmlReportGenerator;
    }

    public EvalSummary run(List<Episode> episodes, Map<String, Object> runConfig,
                           VersionFingerprint fingerprint, String outputDir) throws IOException {
        Map<String, RunResult> runResults = new LinkedHashMap<>();
        Map<String, EvalScore> scores = new LinkedHashMap<>();

        for (Episode episode : episodes) {
            log.info("Running episode: {}", episode.getId());

            // 1. Run episode through adapter
            RunResult runResult = adapter.runEpisode(episode, runConfig);
            runResult.setVersion(fingerprint);
            runResults.put(episode.getId(), runResult);

            // 2. Evaluate with all evaluators (all layers run, even if L1 fails)
            List<EvalResult> evalResults = new ArrayList<>();
            for (Evaluator evaluator : evaluators) {
                try {
                    EvalResult result = evaluator.evaluate(episode, runResult);
                    evalResults.add(result);
                } catch (Exception e) {
                    log.error("Evaluator '{}' threw exception for episode {}: {}",
                            evaluator.name(), episode.getId(), e.getMessage());
                    EvalResult errorResult = new EvalResult(evaluator.name(), false, 0.0);
                    errorResult.setViolations(List.of("EVALUATOR_ERROR: " + e.getMessage()));
                    evalResults.add(errorResult);
                }
            }

            // 3. Layered pass/fail + score
            boolean overallPass = computeOverallPass(evalResults);
            double overallScore = computeOverallScore(evalResults);

            EvalScore score = new EvalScore(episode.getId(), overallPass, overallScore, evalResults);

            // Phase 2: 提取多轮诊断信息
            if (runResult.getTurnResults() != null && !runResult.getTurnResults().isEmpty()) {
                List<TurnDiagnostic> diagnostics = new ArrayList<>();
                for (TurnResult tr : runResult.getTurnResults()) {
                    if (tr.getExpectation() != null) {
                        TurnDiagnostic diag = new TurnDiagnostic();
                        diag.setTurnIndex(tr.getTurnIndex());
                        diag.setExpectationMet(
                                tr.getExpectationViolations() == null
                                        || tr.getExpectationViolations().isEmpty());
                        diag.setViolations(tr.getExpectationViolations() != null
                                ? tr.getExpectationViolations() : List.of());
                        diagnostics.add(diag);
                    }
                }
                if (!diagnostics.isEmpty()) {
                    score.setTurnDiagnostics(diagnostics);
                }
            }

            scores.put(episode.getId(), score);

            log.info("Episode {} result: pass={}, score={}", episode.getId(), overallPass,
                    String.format("%.2f", overallScore));
        }

        // 4. Build summary
        EvalSummary summary = buildSummary(episodes, runResults, scores, fingerprint);

        // 5. Write results
        resultWriter.writeResults(outputDir, summary, runResults, scores);

        // 6. Generate HTML report
        htmlReportGenerator.generate(summary, runResults, scores, outputDir);

        return summary;
    }

    /**
     * L1 fail → overall fail; L2 fail → overall fail.
     * L3/L4 fail does not cause overall fail.
     */
    private boolean computeOverallPass(List<EvalResult> results) {
        for (EvalResult r : results) {
            if ("L1_Gate".equals(r.getEvaluatorName()) && !r.isPassed()) {
                return false;
            }
            if ("L2_Outcome".equals(r.getEvaluatorName()) && !r.isPassed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Overall score: L1 gate fail → 0; otherwise 0.5*L2 + 0.3*L3 + 0.2*L4.
     */
    private double computeOverallScore(List<EvalResult> results) {
        EvalResult gate = findResult(results, "L1_Gate");
        if (gate != null && !gate.isPassed()) return 0.0;

        double score = 0.0;
        EvalResult outcome = findResult(results, "L2_Outcome");
        if (outcome != null) score += 0.5 * outcome.getScore();

        EvalResult trajectory = findResult(results, "L3_Trajectory");
        if (trajectory != null) score += 0.3 * trajectory.getScore();

        EvalResult replyQuality = findResult(results, "L4_ReplyQuality");
        if (replyQuality != null) score += 0.2 * replyQuality.getScore();

        return score;
    }

    private EvalResult findResult(List<EvalResult> results, String name) {
        return results.stream()
                .filter(r -> name.equals(r.getEvaluatorName()))
                .findFirst()
                .orElse(null);
    }

    private EvalSummary buildSummary(List<Episode> episodes, Map<String, RunResult> runResults,
                                     Map<String, EvalScore> scores, VersionFingerprint fingerprint) {
        EvalSummary summary = new EvalSummary();
        summary.setTotalEpisodes(episodes.size());
        summary.setFingerprint(fingerprint);
        summary.setTimestamp(Instant.now());
        summary.setScores(new ArrayList<>(scores.values()));

        int totalPass = (int) scores.values().stream().filter(EvalScore::isOverallPass).count();
        int totalFail = episodes.size() - totalPass;
        summary.setTotalPass(totalPass);
        summary.setTotalFail(totalFail);
        summary.setOverallPassRate(episodes.isEmpty() ? 0.0 : (double) totalPass / episodes.size());

        // Pass rate by evaluator (layer)
        Map<String, Double> passRateByEvaluator = new LinkedHashMap<>();
        if (!scores.isEmpty()) {
            EvalScore first = scores.values().iterator().next();
            for (EvalResult er : first.getEvaluatorResults()) {
                String evalName = er.getEvaluatorName();
                long passed = scores.values().stream()
                        .flatMap(s -> s.getEvaluatorResults().stream())
                        .filter(r -> evalName.equals(r.getEvaluatorName()) && r.isPassed())
                        .count();
                passRateByEvaluator.put(evalName, (double) passed / scores.size());
            }
        }
        summary.setPassRateByEvaluator(passRateByEvaluator);

        // Failure attribution
        Map<String, Integer> failureAttribution = new LinkedHashMap<>();
        for (EvalScore score : scores.values()) {
            for (EvalResult er : score.getEvaluatorResults()) {
                if (!er.isPassed()) {
                    String key = er.getEvaluatorName().toLowerCase().replace(" ", "_") + "_fail";
                    failureAttribution.merge(key, 1, Integer::sum);
                }
            }
        }
        summary.setFailureAttribution(failureAttribution);

        // Suite breakdown with layer pass rates
        Map<String, List<Episode>> suiteMap = episodes.stream()
                .collect(Collectors.groupingBy(Episode::getSuite));
        Map<String, EvalSummary.SuiteStats> suiteBreakdown = new LinkedHashMap<>();
        Map<String, Map<String, Double>> suiteLayerPassRates = new LinkedHashMap<>();
        for (Map.Entry<String, List<Episode>> entry : suiteMap.entrySet()) {
            suiteBreakdown.put(entry.getKey(), buildSuiteStats(entry.getValue(), scores));
            suiteLayerPassRates.put(entry.getKey(), buildLayerPassRates(entry.getValue(), scores));
        }
        summary.setSuiteBreakdown(suiteBreakdown);
        summary.setSuiteLayerPassRates(suiteLayerPassRates);

        // Tag breakdown
        Map<String, List<Episode>> tagMap = new LinkedHashMap<>();
        for (Episode ep : episodes) {
            if (ep.getTags() != null) {
                for (String tag : ep.getTags()) {
                    tagMap.computeIfAbsent(tag, k -> new ArrayList<>()).add(ep);
                }
            }
        }
        Map<String, EvalSummary.SuiteStats> tagBreakdown = new LinkedHashMap<>();
        for (Map.Entry<String, List<Episode>> entry : tagMap.entrySet()) {
            tagBreakdown.put(entry.getKey(), buildSuiteStats(entry.getValue(), scores));
        }
        summary.setTagBreakdown(tagBreakdown);

        // Phase 2: Multi-turn stats
        buildMultiTurnStats(summary, runResults, scores);

        // Latency stats (from L3_Trajectory details)
        List<Long> latencies = scores.values().stream()
                .map(s -> {
                    return s.getEvaluatorResults().stream()
                            .filter(r -> "L3_Trajectory".equals(r.getEvaluatorName()))
                            .findFirst()
                            .map(r -> {
                                Object val = r.getDetails().get("latencyMs");
                                if (val instanceof Number) return ((Number) val).longValue();
                                return 0L;
                            })
                            .orElse(0L);
                })
                .sorted()
                .collect(Collectors.toList());

        if (!latencies.isEmpty()) {
            double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            double p50 = percentile(latencies, 50);
            double p95 = percentile(latencies, 95);
            double max = latencies.get(latencies.size() - 1);
            summary.setLatencyStats(new EvalSummary.LatencyStats(p50, p95, avg, max));
        }

        return summary;
    }

    private Map<String, Double> buildLayerPassRates(List<Episode> episodes, Map<String, EvalScore> scores) {
        Map<String, Double> layerRates = new LinkedHashMap<>();
        String[] layers = {"L1_Gate", "L2_Outcome", "L3_Trajectory", "L4_ReplyQuality"};
        for (String layer : layers) {
            long passed = episodes.stream()
                    .map(ep -> scores.get(ep.getId()))
                    .filter(Objects::nonNull)
                    .flatMap(s -> s.getEvaluatorResults().stream())
                    .filter(r -> layer.equals(r.getEvaluatorName()) && r.isPassed())
                    .count();
            layerRates.put(layer, episodes.isEmpty() ? 0.0 : (double) passed / episodes.size());
        }
        return layerRates;
    }

    private EvalSummary.SuiteStats buildSuiteStats(List<Episode> episodes,
                                                    Map<String, EvalScore> scores) {
        int total = episodes.size();
        int pass = 0;
        double totalLatency = 0;
        for (Episode ep : episodes) {
            EvalScore score = scores.get(ep.getId());
            if (score != null && score.isOverallPass()) {
                pass++;
            }
            if (score != null) {
                for (EvalResult r : score.getEvaluatorResults()) {
                    if ("L3_Trajectory".equals(r.getEvaluatorName())) {
                        Object val = r.getDetails().get("latencyMs");
                        if (val instanceof Number) totalLatency += ((Number) val).longValue();
                    }
                }
            }
        }
        int fail = total - pass;
        double passRate = total == 0 ? 0.0 : (double) pass / total;
        double avgLatency = total == 0 ? 0.0 : totalLatency / total;
        return new EvalSummary.SuiteStats(total, pass, fail, passRate, avgLatency);
    }

    private void buildMultiTurnStats(EvalSummary summary,
                                       Map<String, RunResult> runResults,
                                       Map<String, EvalScore> scores) {
        int multiTurnCount = 0;
        int singleTurnCount = 0;
        int totalTurns = 0;
        Map<String, Integer> resolutionDist = new LinkedHashMap<>();
        int totalCheckpoints = 0;
        int passedCheckpoints = 0;

        for (RunResult rr : runResults.values()) {
            if (rr.getTurnResults() != null && !rr.getTurnResults().isEmpty()) {
                multiTurnCount++;
                totalTurns += rr.getTurnResults().size();
            } else {
                singleTurnCount++;
            }

            // Resolution type distribution
            if (rr.getMetrics() != null && rr.getMetrics().getResolutionType() != null) {
                resolutionDist.merge(rr.getMetrics().getResolutionType(), 1, Integer::sum);
            }
        }

        // Checkpoint pass rate from turn diagnostics
        for (EvalScore score : scores.values()) {
            if (score.getTurnDiagnostics() != null) {
                for (TurnDiagnostic diag : score.getTurnDiagnostics()) {
                    totalCheckpoints++;
                    if (diag.isExpectationMet()) {
                        passedCheckpoints++;
                    }
                }
            }
        }

        if (multiTurnCount > 0) {
            EvalSummary.MultiTurnStats mts = new EvalSummary.MultiTurnStats();
            mts.setMultiTurnCount(multiTurnCount);
            mts.setSingleTurnCount(singleTurnCount);
            mts.setAvgTurnsToResolve(multiTurnCount > 0 ? (double) totalTurns / multiTurnCount : 0);
            mts.setResolutionTypeDistribution(resolutionDist);
            mts.setCheckpointPassRate(totalCheckpoints > 0
                    ? (double) passedCheckpoints / totalCheckpoints : 1.0);
            summary.setMultiTurnStats(mts);
        }
    }

    private double percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
