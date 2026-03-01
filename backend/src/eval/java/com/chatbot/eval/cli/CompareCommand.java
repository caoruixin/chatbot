package com.chatbot.eval.cli;

import com.chatbot.eval.model.CompareDelta;
import com.chatbot.eval.model.EvalScore;
import com.chatbot.eval.model.EvalSummary;
import com.chatbot.eval.report.CompareReportGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class CompareCommand {

    private static final Logger log = LoggerFactory.getLogger(CompareCommand.class);

    public void execute(String[] args) {
        String baselineDir = getArg(args, "--baseline", null);
        String candidateDir = getArg(args, "--candidate", null);
        String outputDir = getArg(args, "--out", "results/compare_" + System.currentTimeMillis());

        if (baselineDir == null || candidateDir == null) {
            System.err.println("Usage: compare --baseline <dir> --candidate <dir> [--out <dir>]");
            return;
        }

        log.info("Eval compare: baseline={}, candidate={}, output={}", baselineDir, candidateDir, outputDir);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.registerModule(new JavaTimeModule());

            EvalSummary baseline = mapper.readValue(
                    Files.readString(Path.of(baselineDir, "summary.json")), EvalSummary.class);
            EvalSummary candidate = mapper.readValue(
                    Files.readString(Path.of(candidateDir, "summary.json")), EvalSummary.class);

            // Build pass/fail maps
            Map<String, Boolean> baselinePassMap = buildPassMap(baseline);
            Map<String, Boolean> candidatePassMap = buildPassMap(candidate);

            // Compute delta
            CompareDelta delta = new CompareDelta();
            delta.setBaselineFingerprint(baseline.getFingerprint());
            delta.setCandidateFingerprint(candidate.getFingerprint());
            delta.setBaselinePassRate(baseline.getOverallPassRate());
            delta.setCandidatePassRate(candidate.getOverallPassRate());
            delta.setDeltaPassRate(candidate.getOverallPassRate() - baseline.getOverallPassRate());

            // Regressions and new passes
            List<String> regressions = new ArrayList<>();
            List<String> newPasses = new ArrayList<>();
            Set<String> allIds = new LinkedHashSet<>();
            allIds.addAll(baselinePassMap.keySet());
            allIds.addAll(candidatePassMap.keySet());

            for (String id : allIds) {
                Boolean basePassed = baselinePassMap.get(id);
                Boolean candPassed = candidatePassMap.get(id);
                if (Boolean.TRUE.equals(basePassed) && !Boolean.TRUE.equals(candPassed)) {
                    regressions.add(id);
                } else if (!Boolean.TRUE.equals(basePassed) && Boolean.TRUE.equals(candPassed)) {
                    newPasses.add(id);
                }
            }
            delta.setRegressions(regressions);
            delta.setNewPasses(newPasses);

            // Suite deltas
            Map<String, CompareDelta.SuiteDelta> suiteDeltas = new LinkedHashMap<>();
            Set<String> allSuites = new LinkedHashSet<>();
            if (baseline.getSuiteBreakdown() != null) allSuites.addAll(baseline.getSuiteBreakdown().keySet());
            if (candidate.getSuiteBreakdown() != null) allSuites.addAll(candidate.getSuiteBreakdown().keySet());

            for (String suite : allSuites) {
                double baseRate = baseline.getSuiteBreakdown() != null
                        && baseline.getSuiteBreakdown().containsKey(suite)
                        ? baseline.getSuiteBreakdown().get(suite).getPassRate() : 0;
                double candRate = candidate.getSuiteBreakdown() != null
                        && candidate.getSuiteBreakdown().containsKey(suite)
                        ? candidate.getSuiteBreakdown().get(suite).getPassRate() : 0;
                suiteDeltas.put(suite, new CompareDelta.SuiteDelta(baseRate, candRate, candRate - baseRate));
            }
            delta.setSuiteDeltas(suiteDeltas);

            // Write results
            Files.createDirectories(Path.of(outputDir));
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(Path.of(outputDir, "compare.json").toFile(), delta);

            CompareReportGenerator reportGen = new CompareReportGenerator();
            reportGen.generate(delta, baseline, candidate, outputDir);

            // Print summary
            System.out.println("\n=== Compare Complete ===");
            System.out.printf("Baseline: %.0f%% -> Candidate: %.0f%% (delta: %+.1f%%)%n",
                    delta.getBaselinePassRate() * 100,
                    delta.getCandidatePassRate() * 100,
                    delta.getDeltaPassRate() * 100);
            System.out.println("Regressions: " + regressions.size());
            System.out.println("New passes: " + newPasses.size());
            System.out.println("Report: " + outputDir + "/compare.html");

        } catch (Exception e) {
            log.error("Eval compare failed: {}", e.getMessage(), e);
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private Map<String, Boolean> buildPassMap(EvalSummary summary) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        if (summary.getScores() != null) {
            for (EvalScore score : summary.getScores()) {
                map.put(score.getEpisodeId(), score.isOverallPass());
            }
        }
        return map;
    }

    private String getArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
