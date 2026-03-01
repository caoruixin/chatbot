package com.chatbot.eval.runner;

import com.chatbot.eval.adapter.AgentAdapter;
import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.evaluator.Evaluator;
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

            // 2. Evaluate with all evaluators (isolated: one failure doesn't abort the run)
            List<EvalResult> evalResults = new ArrayList<>();
            for (Evaluator evaluator : evaluators) {
                try {
                    EvalResult result = evaluator.evaluate(episode, runResult);
                    evalResults.add(result);
                } catch (Exception e) {
                    log.error("Evaluator '{}' threw exception for episode {}: {}",
                            evaluator.name(), episode.getId(), e.getMessage());
                    EvalResult errorResult = new EvalResult(evaluator.name(), false, 0.0);
                    errorResult.setViolations(List.of("Evaluator threw exception: " + e.getMessage()));
                    evalResults.add(errorResult);
                }
            }

            // 3. Aggregate score
            boolean overallPass = evalResults.stream().allMatch(EvalResult::isPassed);
            double overallScore = evalResults.stream()
                    .mapToDouble(EvalResult::getScore)
                    .average()
                    .orElse(0.0);

            EvalScore score = new EvalScore(episode.getId(), overallPass, overallScore, evalResults);
            scores.put(episode.getId(), score);

            log.info("Episode {} result: pass={}, score={}", episode.getId(), overallPass, String.format("%.2f", overallScore));
        }

        // 4. Build summary
        EvalSummary summary = buildSummary(episodes, scores, fingerprint);

        // 5. Write results
        resultWriter.writeResults(outputDir, summary, runResults, scores);

        // 6. Generate HTML report
        htmlReportGenerator.generate(summary, runResults, scores, outputDir);

        return summary;
    }

    private EvalSummary buildSummary(List<Episode> episodes, Map<String, EvalScore> scores,
                                     VersionFingerprint fingerprint) {
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

        // Pass rate by evaluator
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

        // Suite breakdown
        Map<String, List<Episode>> suiteMap = episodes.stream()
                .collect(Collectors.groupingBy(Episode::getSuite));
        Map<String, EvalSummary.SuiteStats> suiteBreakdown = new LinkedHashMap<>();
        for (Map.Entry<String, List<Episode>> entry : suiteMap.entrySet()) {
            suiteBreakdown.put(entry.getKey(), buildSuiteStats(entry.getValue(), scores));
        }
        summary.setSuiteBreakdown(suiteBreakdown);

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

        // Latency stats
        List<Long> latencies = scores.values().stream()
                .map(s -> {
                    // Find latency from evaluator details
                    return s.getEvaluatorResults().stream()
                            .filter(r -> "efficiency".equals(r.getEvaluatorName()))
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
                    if ("efficiency".equals(r.getEvaluatorName())) {
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

    private double percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
