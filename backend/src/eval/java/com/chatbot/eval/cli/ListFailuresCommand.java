package com.chatbot.eval.cli;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.model.EvalScore;
import com.chatbot.eval.model.EvalSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ListFailuresCommand {

    private static final Logger log = LoggerFactory.getLogger(ListFailuresCommand.class);

    public void execute(String[] args) {
        String resultsDir = getArg(args, "--results", null);
        String filter = getArg(args, "--filter", null);

        if (resultsDir == null) {
            System.err.println("Usage: list-failures --results <dir> [--filter <evaluator_name>]");
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.registerModule(new JavaTimeModule());

            EvalSummary summary = mapper.readValue(
                    Files.readString(Path.of(resultsDir, "summary.json")), EvalSummary.class);

            System.out.println("\n=== Failures ===");
            if (filter != null) {
                System.out.println("Filter: " + filter);
            }
            System.out.println();

            int failCount = 0;
            if (summary.getScores() != null) {
                for (EvalScore score : summary.getScores()) {
                    if (score.isOverallPass()) continue;

                    boolean matchesFilter = true;
                    if (filter != null && score.getEvaluatorResults() != null) {
                        // Check if any failed evaluator matches the filter
                        matchesFilter = score.getEvaluatorResults().stream()
                                .anyMatch(r -> !r.isPassed() && matchesEvaluatorFilter(r, filter));
                    }

                    if (!matchesFilter) continue;

                    failCount++;
                    System.out.println("[FAIL] " + score.getEpisodeId());
                    if (score.getEvaluatorResults() != null) {
                        for (EvalResult er : score.getEvaluatorResults()) {
                            if (!er.isPassed()) {
                                System.out.println("  " + er.getEvaluatorName() + ":");
                                for (String v : er.getViolations()) {
                                    System.out.println("    - " + v);
                                }
                            }
                        }
                    }
                    System.out.println();
                }
            }

            System.out.printf("Total failures: %d%n", failCount);

        } catch (Exception e) {
            log.error("List failures failed: {}", e.getMessage(), e);
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private boolean matchesEvaluatorFilter(EvalResult result, String filter) {
        // Support both "contract" and "contract_fail" style filters
        String evalName = result.getEvaluatorName();
        return filter.equals(evalName)
                || filter.equals(evalName + "_fail")
                || filter.startsWith(evalName);
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
