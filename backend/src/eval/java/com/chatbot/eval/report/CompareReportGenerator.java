package com.chatbot.eval.report;

import com.chatbot.eval.model.CompareDelta;
import com.chatbot.eval.model.EvalSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates a static compare.html for baseline vs candidate comparison.
 * Phase 1: 按层对比 delta + regressions/improvements。
 */
public class CompareReportGenerator {

    public void generate(CompareDelta delta, EvalSummary baseline, EvalSummary candidate,
                         String outputDir) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Eval Compare Report — Layered</title>\n");
        appendStyles(html);
        html.append("</head>\n<body>\n");

        html.append("<div class=\"container\">\n");
        html.append("<h1>Eval Compare Report — Layered Assessment</h1>\n");
        html.append("<p class=\"timestamp\">Generated: ").append(formatTimestamp(Instant.now())).append("</p>\n");

        // Fingerprints
        if (delta.getBaselineFingerprint() != null && delta.getCandidateFingerprint() != null) {
            html.append("<p class=\"timestamp\">Baseline: <code>").append(esc(delta.getBaselineFingerprint().getCompositeHash()));
            html.append("</code> vs Candidate: <code>").append(esc(delta.getCandidateFingerprint().getCompositeHash()));
            html.append("</code></p>\n");
        }

        // Overall comparison
        html.append("<h2>Overall Comparison</h2>\n");
        html.append("<div class=\"card\">\n");
        html.append("<div class=\"compare-row\">\n");
        appendCompareMetric(html, "Baseline", String.format("%.0f%%", delta.getBaselinePassRate() * 100));
        appendCompareMetric(html, "Candidate", String.format("%.0f%%", delta.getCandidatePassRate() * 100));
        String deltaClass = delta.getDeltaPassRate() >= 0 ? "positive" : "negative";
        appendCompareMetric(html, "Delta",
                String.format("%+.1f%%", delta.getDeltaPassRate() * 100), deltaClass);
        html.append("</div>\n");
        html.append("</div>\n");

        // Per-Layer comparison
        if (baseline.getPassRateByEvaluator() != null && candidate.getPassRateByEvaluator() != null) {
            html.append("<h2>Per-Layer Comparison</h2>\n");
            html.append("<div class=\"card\">\n");
            html.append("<table>\n");
            html.append("<tr><th>Layer</th><th>Baseline</th><th>Candidate</th><th>Delta</th></tr>\n");

            String[] layerOrder = {"L1_Gate", "L2_Outcome", "L3_Trajectory", "L4_ReplyQuality"};
            for (String layer : layerOrder) {
                double bRate = baseline.getPassRateByEvaluator().getOrDefault(layer, 0.0);
                double cRate = candidate.getPassRateByEvaluator().getOrDefault(layer, 0.0);
                double d = cRate - bRate;
                String dc = d >= 0 ? "positive" : "negative";
                String arrow = d > 0 ? " &#9650;" : d < 0 ? " &#9660;" : "";
                html.append("<tr><td><strong>").append(esc(layer)).append("</strong></td>");
                html.append("<td>").append(String.format("%.0f%%", bRate * 100)).append("</td>");
                html.append("<td>").append(String.format("%.0f%%", cRate * 100)).append("</td>");
                html.append("<td class=\"").append(dc).append("\">");
                html.append(String.format("%+.1f%%", d * 100)).append(arrow).append("</td></tr>\n");
            }
            html.append("</table>\n");
            html.append("</div>\n");
        }

        // Regressions
        if (delta.getRegressions() != null && !delta.getRegressions().isEmpty()) {
            html.append("<h2>Regressions (").append(delta.getRegressions().size()).append(")</h2>\n");
            html.append("<div class=\"card\">\n");
            html.append("<p class=\"subtitle\">Episodes that passed in baseline but failed in candidate:</p>\n");
            html.append("<ul>\n");
            for (String r : delta.getRegressions()) {
                html.append("<li class=\"regression\">").append(esc(r)).append("</li>\n");
            }
            html.append("</ul>\n");
            html.append("</div>\n");
        }

        // New passes
        if (delta.getNewPasses() != null && !delta.getNewPasses().isEmpty()) {
            html.append("<h2>Improvements (").append(delta.getNewPasses().size()).append(")</h2>\n");
            html.append("<div class=\"card\">\n");
            html.append("<p class=\"subtitle\">Episodes that failed in baseline but passed in candidate:</p>\n");
            html.append("<ul>\n");
            for (String p : delta.getNewPasses()) {
                html.append("<li class=\"new-pass\">").append(esc(p)).append("</li>\n");
            }
            html.append("</ul>\n");
            html.append("</div>\n");
        }

        // Suite deltas
        if (delta.getSuiteDeltas() != null && !delta.getSuiteDeltas().isEmpty()) {
            html.append("<h2>Per-Suite Delta</h2>\n");
            html.append("<div class=\"card\">\n");
            html.append("<table>\n");
            html.append("<tr><th>Suite</th><th>Baseline</th><th>Candidate</th><th>Delta</th></tr>\n");
            for (Map.Entry<String, CompareDelta.SuiteDelta> entry : delta.getSuiteDeltas().entrySet()) {
                CompareDelta.SuiteDelta sd = entry.getValue();
                String dc = sd.getDelta() >= 0 ? "positive" : "negative";
                html.append("<tr><td>").append(esc(entry.getKey())).append("</td>");
                html.append("<td>").append(String.format("%.0f%%", sd.getBaselinePassRate() * 100)).append("</td>");
                html.append("<td>").append(String.format("%.0f%%", sd.getCandidatePassRate() * 100)).append("</td>");
                html.append("<td class=\"").append(dc).append("\">");
                html.append(String.format("%+.1f%%", sd.getDelta() * 100)).append("</td></tr>\n");
            }
            html.append("</table>\n");
            html.append("</div>\n");
        }

        html.append("</div>\n");
        html.append("</body>\n</html>");

        Files.writeString(Path.of(outputDir).resolve("compare.html"), html.toString());
    }

    private void appendStyles(StringBuilder html) {
        html.append("<style>\n");
        html.append("* { box-sizing: border-box; margin: 0; padding: 0; }\n");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        html.append("background: #f5f5f5; color: #333; line-height: 1.6; }\n");
        html.append(".container { max-width: 900px; margin: 0 auto; padding: 20px; }\n");
        html.append("h1 { margin-bottom: 5px; }\n");
        html.append("h2 { margin: 25px 0 10px; color: #444; }\n");
        html.append(".timestamp { color: #888; margin-bottom: 20px; font-size: 0.9em; }\n");
        html.append(".timestamp code { background: #e2e8f0; padding: 1px 6px; border-radius: 3px; }\n");
        html.append(".subtitle { color: #666; margin-bottom: 10px; }\n");
        html.append(".card { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 15px; ");
        html.append("box-shadow: 0 1px 3px rgba(0,0,0,0.1); }\n");
        html.append(".compare-row { display: flex; gap: 30px; justify-content: center; }\n");
        html.append(".compare-metric { text-align: center; min-width: 120px; }\n");
        html.append(".compare-metric .value { font-size: 2em; font-weight: bold; }\n");
        html.append(".compare-metric .label { font-size: 0.85em; color: #888; }\n");
        html.append(".positive { color: #2d8a4e; }\n");
        html.append(".negative { color: #d32f2f; }\n");
        html.append("table { width: 100%; border-collapse: collapse; margin: 10px 0; }\n");
        html.append("th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }\n");
        html.append("th { background: #fafafa; font-weight: 600; }\n");
        html.append(".regression { color: #d32f2f; }\n");
        html.append(".new-pass { color: #2d8a4e; }\n");
        html.append("ul { list-style: none; padding-left: 10px; }\n");
        html.append("li { padding: 4px 0; }\n");
        html.append("li::before { content: ''; display: inline-block; width: 8px; height: 8px; ");
        html.append("border-radius: 50%; margin-right: 8px; }\n");
        html.append(".regression::before { background: #d32f2f; }\n");
        html.append(".new-pass::before { background: #2d8a4e; }\n");
        html.append("</style>\n");
    }

    private void appendCompareMetric(StringBuilder html, String label, String value) {
        appendCompareMetric(html, label, value, "");
    }

    private void appendCompareMetric(StringBuilder html, String label, String value, String cssClass) {
        html.append("<div class=\"compare-metric\">");
        html.append("<div class=\"value ").append(cssClass).append("\">").append(esc(value)).append("</div>");
        html.append("<div class=\"label\">").append(esc(label)).append("</div>");
        html.append("</div>\n");
    }

    private String formatTimestamp(Instant ts) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(ts);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
