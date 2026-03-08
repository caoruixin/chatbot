package com.chatbot.eval.report;

import com.chatbot.eval.evaluator.EvalResult;
import com.chatbot.eval.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Generates a static report.html dashboard with layered evaluation display.
 * Phase 1: 分层总览 + Suite 切片（含层级 pass rate）+ 失败归因 + Episode 详情。
 */
public class HtmlReportGenerator {

    public void generate(EvalSummary summary, Map<String, RunResult> runResults,
                         Map<String, EvalScore> scores, String outputDir) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Eval Dashboard — Layered Assessment</title>\n");
        appendStyles(html);
        html.append("</head>\n<body>\n");

        // Header bar
        html.append("<div class=\"header\">\n");
        html.append("<div class=\"header-inner\">\n");
        html.append("<div class=\"header-title\">AI Agent Eval Dashboard — Layered Assessment</div>\n");
        html.append("<div class=\"header-meta\">");
        html.append(formatTimestamp(summary.getTimestamp()));
        if (summary.getFingerprint() != null) {
            html.append(" &middot; ").append(esc(summary.getFingerprint().getModelId()));
            html.append(" &middot; <code>").append(esc(summary.getFingerprint().getCompositeHash())).append("</code>");
        }
        html.append("</div>\n");
        html.append("</div></div>\n");

        html.append("<div class=\"container\">\n");

        // Row 1: KPI cards
        appendKpiCards(html, summary);

        // Row 2: Layer pass rate bars (L1 → L2 → L3 → L4)
        appendLayerBars(html, summary);

        // Row 3: Failure attribution
        appendFailureAttribution(html, summary);

        // Row 4: Suite breakdown with layer pass rates
        appendSuiteBreakdown(html, summary);

        // Row 5: Tag breakdown
        appendTagBreakdown(html, summary);

        // Row 6: Episode list with filter
        appendEpisodeList(html, runResults, scores);

        html.append("</div>\n");
        appendScript(html);
        html.append("</body>\n</html>");

        Files.writeString(Path.of(outputDir).resolve("report.html"), html.toString());
    }

    private void appendStyles(StringBuilder html) {
        html.append("<style>\n");
        html.append("""
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   background: #f0f2f5; color: #1a1a2e; line-height: 1.6; }
            .header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
                      color: #fff; padding: 20px 0; margin-bottom: 24px; }
            .header-inner { max-width: 1200px; margin: 0 auto; padding: 0 24px; }
            .header-title { font-size: 1.5em; font-weight: 700; }
            .header-meta { font-size: 0.85em; color: #a0aec0; margin-top: 4px; }
            .header-meta code { background: rgba(255,255,255,0.1); padding: 1px 6px; border-radius: 3px; }
            .container { max-width: 1200px; margin: 0 auto; padding: 0 24px 40px; }

            /* KPI cards */
            .kpi-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
                       gap: 16px; margin-bottom: 24px; }
            .kpi-card { background: #fff; border-radius: 12px; padding: 20px; text-align: center;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
            .kpi-value { font-size: 2.2em; font-weight: 800; line-height: 1.2; }
            .kpi-label { font-size: 0.8em; color: #718096; margin-top: 4px; text-transform: uppercase;
                         letter-spacing: 0.5px; }
            .kpi-pass { color: #38a169; }
            .kpi-fail { color: #e53e3e; }
            .kpi-warn { color: #d69e2e; }
            .kpi-neutral { color: #4a5568; }

            /* Section */
            .section { margin-bottom: 24px; }
            .section-title { font-size: 1.1em; font-weight: 700; color: #2d3748; margin-bottom: 12px; }
            .card { background: #fff; border-radius: 12px; padding: 20px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.08); }

            /* Layer bars */
            .layer-bars { display: flex; flex-direction: column; gap: 10px; }
            .layer-bar-row { display: flex; align-items: center; gap: 12px; }
            .layer-bar-name { width: 140px; font-size: 0.9em; font-weight: 600; color: #4a5568; text-align: right; }
            .layer-bar-track { flex: 1; height: 28px; background: #edf2f7; border-radius: 14px; overflow: hidden; }
            .layer-bar-fill { height: 100%%; border-radius: 14px; transition: width 0.5s; display: flex;
                             align-items: center; padding-left: 12px; font-size: 0.8em; font-weight: 700; color: #fff; }
            .bar-green { background: linear-gradient(90deg, #38a169, #48bb78); }
            .bar-yellow { background: linear-gradient(90deg, #d69e2e, #ecc94b); }
            .bar-red { background: linear-gradient(90deg, #e53e3e, #fc8181); }

            /* Failure attribution */
            .attr-row { display: flex; gap: 8px; align-items: center; padding: 6px 0; }
            .attr-label { width: 160px; font-size: 0.9em; color: #4a5568; }
            .attr-count { font-weight: 700; font-size: 0.9em; }

            /* Tables */
            .breakdown-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
            @media (max-width: 768px) { .breakdown-row { grid-template-columns: 1fr; } }
            table { width: 100%%; border-collapse: collapse; }
            th { font-size: 0.8em; text-transform: uppercase; letter-spacing: 0.5px; color: #718096;
                 padding: 8px 12px; text-align: left; border-bottom: 2px solid #edf2f7; }
            td { padding: 10px 12px; border-bottom: 1px solid #f7fafc; font-size: 0.9em; }
            tr:hover td { background: #f7fafc; }
            .td-pass { color: #38a169; font-weight: 600; }
            .td-fail { color: #e53e3e; font-weight: 600; }
            .mini-bar { display: inline-block; height: 6px; border-radius: 3px; vertical-align: middle; }

            /* Episode list */
            .filter-bar { display: flex; gap: 8px; margin-bottom: 12px; }
            .filter-btn { padding: 6px 16px; border-radius: 20px; border: 1px solid #e2e8f0;
                          background: #fff; cursor: pointer; font-size: 0.85em; color: #4a5568;
                          transition: all 0.2s; }
            .filter-btn:hover { border-color: #a0aec0; }
            .filter-btn.active { background: #1a1a2e; color: #fff; border-color: #1a1a2e; }
            .ep-card { background: #fff; border-radius: 10px; margin-bottom: 8px;
                       box-shadow: 0 1px 2px rgba(0,0,0,0.05); overflow: hidden; }
            .ep-header { padding: 12px 16px; cursor: pointer; display: flex;
                         justify-content: space-between; align-items: center; transition: background 0.15s; }
            .ep-header:hover { background: #f7fafc; }
            .ep-id { font-weight: 600; font-size: 0.95em; }
            .ep-tags { display: flex; gap: 6px; align-items: center; }
            .tag { font-size: 0.75em; padding: 2px 8px; border-radius: 10px; font-weight: 500; }
            .tag-pass { background: #c6f6d5; color: #276749; }
            .tag-fail { background: #fed7d7; color: #9b2c2c; }
            .tag-mock { background: #fefcbf; color: #975a16; }
            .tag-suite { background: #e9d8fd; color: #553c9a; }
            .ep-body { display: none; padding: 0 16px 16px; border-top: 1px solid #f0f0f0; }
            .ep-body.open { display: block; padding-top: 12px; }
            .reply-box { background: #f7fafc; padding: 12px; border-radius: 8px; border-left: 3px solid #4299e1;
                         margin: 8px 0; white-space: pre-wrap; font-size: 0.9em; line-height: 1.7; }

            /* Layer detail tree */
            .layer-tree { margin: 8px 0; font-size: 0.88em; }
            .layer-item { padding: 4px 0 4px 16px; border-left: 2px solid #e2e8f0; margin-left: 8px; }
            .layer-item-header { font-weight: 600; }
            .layer-item-detail { color: #718096; font-size: 0.92em; padding-left: 4px; }
            .layer-pass { color: #38a169; }
            .layer-fail { color: #e53e3e; }
            .violation { color: #e53e3e; font-size: 0.85em; margin: 2px 0 2px 20px; }
            .meta-line { font-size: 0.8em; color: #a0aec0; margin-top: 6px; }

            /* Detail boxes */
            .detail-box { margin: 8px 0; padding: 10px; border-radius: 6px; font-size: 0.85em; }
            .detail-semantic { background: #ebf8ff; border-left: 3px solid #4299e1; }
            .detail-rag { background: #f0fff4; border-left: 3px solid #38a169; }
            details { margin-top: 8px; }
            details summary { cursor: pointer; font-size: 0.85em; color: #718096; }
            """);
        html.append("</style>\n");
    }

    private void appendKpiCards(StringBuilder html, EvalSummary summary) {
        html.append("<div class=\"kpi-row\">\n");
        appendKpi(html, String.valueOf(summary.getTotalEpisodes()), "Episodes", "kpi-neutral");
        String prClass = summary.getOverallPassRate() >= 0.8 ? "kpi-pass" :
                summary.getOverallPassRate() >= 0.5 ? "kpi-warn" : "kpi-fail";
        appendKpi(html, String.format("%.0f%%", summary.getOverallPassRate() * 100),
                "Overall Pass (L1∧L2)", prClass);
        appendKpi(html, String.valueOf(summary.getTotalPass()), "Passed", "kpi-pass");
        appendKpi(html, String.valueOf(summary.getTotalFail()), "Failed", "kpi-fail");
        if (summary.getLatencyStats() != null) {
            appendKpi(html, String.format("%.0fms", summary.getLatencyStats().getP50()), "p50 Latency", "kpi-neutral");
            appendKpi(html, String.format("%.0fms", summary.getLatencyStats().getP95()), "p95 Latency", "kpi-neutral");
        }
        html.append("</div>\n");
    }

    private void appendKpi(StringBuilder html, String value, String label, String cssClass) {
        html.append("<div class=\"kpi-card\">");
        html.append("<div class=\"kpi-value ").append(cssClass).append("\">").append(esc(value)).append("</div>");
        html.append("<div class=\"kpi-label\">").append(esc(label)).append("</div>");
        html.append("</div>\n");
    }

    private void appendLayerBars(StringBuilder html, EvalSummary summary) {
        if (summary.getPassRateByEvaluator() == null || summary.getPassRateByEvaluator().isEmpty()) return;

        html.append("<div class=\"section\"><div class=\"section-title\">Layer Pass Rates</div>\n");
        html.append("<div class=\"card\"><div class=\"layer-bars\">\n");
        String[] layerOrder = {"L1_Gate", "L2_Outcome", "L3_Trajectory", "L4_ReplyQuality"};
        String[] layerLabels = {"L1 Gate (P0)", "L2 Outcome (P1)", "L3 Trajectory (P2)", "L4 Reply Quality (P3)"};

        for (int i = 0; i < layerOrder.length; i++) {
            Double rate = summary.getPassRateByEvaluator().get(layerOrder[i]);
            if (rate == null) continue;
            int pct = (int) Math.round(rate * 100);
            String barClass = pct >= 80 ? "bar-green" : pct >= 50 ? "bar-yellow" : "bar-red";
            html.append("<div class=\"layer-bar-row\">");
            html.append("<div class=\"layer-bar-name\">").append(esc(layerLabels[i])).append("</div>");
            html.append("<div class=\"layer-bar-track\">");
            html.append("<div class=\"layer-bar-fill ").append(barClass).append("\" style=\"width:")
                    .append(Math.max(pct, 8)).append("%\">").append(pct).append("%</div>");
            html.append("</div></div>\n");
        }
        html.append("</div></div></div>\n");
    }

    private void appendFailureAttribution(StringBuilder html, EvalSummary summary) {
        if (summary.getFailureAttribution() == null || summary.getFailureAttribution().isEmpty()) return;

        html.append("<div class=\"section\"><div class=\"section-title\">Failure Attribution</div>\n");
        html.append("<div class=\"card\">\n");
        int total = summary.getTotalEpisodes();
        for (Map.Entry<String, Integer> entry : summary.getFailureAttribution().entrySet()) {
            int count = entry.getValue();
            double pct = total > 0 ? (double) count / total * 100 : 0;
            html.append("<div class=\"attr-row\">");
            html.append("<div class=\"attr-label\">").append(esc(entry.getKey())).append("</div>");
            html.append("<div class=\"attr-count td-fail\">").append(count);
            html.append(" (").append(String.format("%.0f%%", pct)).append(")</div>");
            html.append("</div>\n");
        }
        html.append("</div></div>\n");
    }

    private void appendSuiteBreakdown(StringBuilder html, EvalSummary summary) {
        if (summary.getSuiteBreakdown() == null || summary.getSuiteBreakdown().isEmpty()) return;

        html.append("<div class=\"section\"><div class=\"section-title\">Suite Breakdown</div>\n");
        html.append("<div class=\"card\">\n");
        html.append("<table>\n");
        html.append("<tr><th>Suite</th><th>N</th><th>Pass</th>");
        html.append("<th>L1</th><th>L2</th><th>L3</th><th>L4</th><th>Avg Latency</th></tr>\n");

        for (Map.Entry<String, EvalSummary.SuiteStats> entry : summary.getSuiteBreakdown().entrySet()) {
            EvalSummary.SuiteStats s = entry.getValue();
            int pct = (int) Math.round(s.getPassRate() * 100);
            html.append("<tr><td><strong>").append(esc(entry.getKey())).append("</strong></td>");
            html.append("<td>").append(s.getTotal()).append("</td>");
            html.append("<td class=\"").append(pct >= 80 ? "td-pass" : "td-fail").append("\">")
                    .append(pct).append("%</td>");

            // Layer pass rates
            Map<String, Map<String, Double>> slpr = summary.getSuiteLayerPassRates();
            if (slpr != null && slpr.containsKey(entry.getKey())) {
                Map<String, Double> lr = slpr.get(entry.getKey());
                appendLayerCell(html, lr.get("L1_Gate"));
                appendLayerCell(html, lr.get("L2_Outcome"));
                appendLayerCell(html, lr.get("L3_Trajectory"));
                appendLayerCell(html, lr.get("L4_ReplyQuality"));
            } else {
                html.append("<td>-</td><td>-</td><td>-</td><td>-</td>");
            }

            html.append("<td>").append(String.format("%.0fms", s.getAvgLatencyMs())).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</table>\n");
        html.append("</div></div>\n");
    }

    private void appendLayerCell(StringBuilder html, Double rate) {
        if (rate == null) {
            html.append("<td>-</td>");
            return;
        }
        int pct = (int) Math.round(rate * 100);
        String css = pct >= 80 ? "td-pass" : "td-fail";
        html.append("<td class=\"").append(css).append("\">").append(pct).append("%</td>");
    }

    private void appendTagBreakdown(StringBuilder html, EvalSummary summary) {
        if (summary.getTagBreakdown() == null || summary.getTagBreakdown().isEmpty()) return;

        html.append("<div class=\"section\"><div class=\"section-title\">Tag Breakdown</div>\n");
        html.append("<div class=\"card\">\n");
        html.append("<table>\n");
        html.append("<tr><th>Tag</th><th>Pass Rate</th><th>Pass</th><th>Fail</th></tr>\n");
        for (Map.Entry<String, EvalSummary.SuiteStats> entry : summary.getTagBreakdown().entrySet()) {
            EvalSummary.SuiteStats s = entry.getValue();
            int pct = (int) Math.round(s.getPassRate() * 100);
            html.append("<tr><td><strong>").append(esc(entry.getKey())).append("</strong></td>");
            html.append("<td>").append(pct).append("%</td>");
            html.append("<td class=\"td-pass\">").append(s.getPass()).append("</td>");
            html.append("<td class=\"td-fail\">").append(s.getFail()).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</table></div></div>\n");
    }

    private void appendEpisodeList(StringBuilder html, Map<String, RunResult> runResults,
                                    Map<String, EvalScore> scores) {
        html.append("<div class=\"section\">\n");
        html.append("<div class=\"section-title\">Episode Details</div>\n");
        html.append("<div class=\"filter-bar\">");
        html.append("<button class=\"filter-btn active\" onclick=\"filterEp('all')\">All</button>");
        html.append("<button class=\"filter-btn\" onclick=\"filterEp('pass')\">Passed</button>");
        html.append("<button class=\"filter-btn\" onclick=\"filterEp('fail')\">Failed</button>");
        html.append("</div>\n");

        for (Map.Entry<String, RunResult> entry : runResults.entrySet()) {
            String episodeId = entry.getKey();
            RunResult rr = entry.getValue();
            EvalScore score = scores.get(episodeId);
            boolean passed = score != null && score.isOverallPass();
            double overallScore = score != null ? score.getOverallScore() : 0;

            html.append("<div class=\"ep-card\" data-status=\"").append(passed ? "pass" : "fail").append("\">\n");
            html.append("<div class=\"ep-header\" onclick=\"toggleEp('").append(esc(episodeId)).append("')\">\n");
            html.append("<div><span class=\"ep-id\">").append(esc(episodeId)).append("</span>");
            html.append(" <span style=\"font-size:0.85em;color:#718096\">(")
                    .append(String.format("%.2f", overallScore)).append(")</span>");
            html.append("</div>\n");
            html.append("<div class=\"ep-tags\">");

            // Check for mock mode
            if (score != null) {
                for (EvalResult er : score.getEvaluatorResults()) {
                    if (er.getDetails() != null && "mock".equals(er.getDetails().get("mode"))) {
                        html.append("<span class=\"tag tag-mock\">mock</span>");
                        break;
                    }
                }
            }

            html.append("<span class=\"tag ").append(passed ? "tag-pass" : "tag-fail").append("\">");
            html.append(passed ? "PASS" : "FAIL").append("</span>");
            if (rr.getMetrics() != null) {
                html.append("<span style=\"font-size:0.8em;color:#a0aec0\">")
                        .append(rr.getMetrics().getLatencyMs()).append("ms</span>");
            }
            html.append("</div></div>\n");

            // Body
            html.append("<div class=\"ep-body\" id=\"ep-").append(esc(episodeId)).append("\">\n");

            // Reply
            html.append("<div style=\"font-weight:600;font-size:0.85em;color:#718096;margin-bottom:4px\">AI Reply</div>");
            html.append("<div class=\"reply-box\">").append(esc(rr.getFinalReply())).append("</div>\n");

            // Layer tree (Phase 1 style)
            if (score != null) {
                html.append("<div style=\"font-weight:600;font-size:0.85em;color:#718096;margin:8px 0 4px\">Layer Results</div>");
                html.append("<div class=\"layer-tree\">\n");
                for (EvalResult er : score.getEvaluatorResults()) {
                    String lClass = er.isPassed() ? "layer-pass" : "layer-fail";
                    String icon = er.isPassed() ? "&#10003;" : "&#10007;";
                    html.append("<div class=\"layer-item\">");
                    html.append("<div class=\"layer-item-header ").append(lClass).append("\">");
                    html.append(icon).append(" ").append(esc(er.getEvaluatorName()));
                    html.append(" (").append(String.format("%.2f", er.getScore())).append(")</div>\n");

                    // Violations
                    if (!er.getViolations().isEmpty()) {
                        for (String v : er.getViolations()) {
                            html.append("<div class=\"violation\">").append(esc(v)).append("</div>\n");
                        }
                    }

                    // Key details
                    appendLayerDetails(html, er);

                    html.append("</div>\n");
                }
                html.append("</div>\n");
            }

            // Tool actions
            if (!rr.getActions().isEmpty()) {
                html.append("<details><summary>Tool Actions (").append(rr.getActions().size()).append(")</summary>");
                html.append("<table style=\"font-size:0.85em\"><tr><th>Tool</th><th>Status</th><th>Result</th></tr>\n");
                for (ToolAction action : rr.getActions()) {
                    html.append("<tr><td>").append(esc(action.getName())).append("</td>");
                    html.append("<td>").append(esc(action.getStatus())).append("</td>");
                    String sum = action.getResultSummary();
                    if (sum != null && sum.length() > 150) sum = sum.substring(0, 150) + "...";
                    html.append("<td style=\"font-size:0.85em\">").append(esc(sum)).append("</td></tr>\n");
                }
                html.append("</table></details>\n");
            }

            // Trace
            if (rr.getTrace() != null && !rr.getTrace().getSpans().isEmpty()) {
                html.append("<details><summary>Trace Spans (").append(rr.getTrace().getSpans().size()).append(")</summary>");
                html.append("<table style=\"font-size:0.85em\"><tr><th>Span</th><th>Duration</th><th>Attributes</th></tr>\n");
                for (TraceSpan span : rr.getTrace().getSpans()) {
                    html.append("<tr><td>").append(esc(span.getSpanName())).append("</td>");
                    html.append("<td>").append(span.getEndMs() - span.getStartMs()).append("ms</td>");
                    html.append("<td>").append(esc(String.valueOf(span.getAttributes()))).append("</td></tr>\n");
                }
                html.append("</table></details>\n");
            }

            html.append("</div></div>\n");
        }
        html.append("</div>\n");
    }

    @SuppressWarnings("unchecked")
    private void appendLayerDetails(StringBuilder html, EvalResult er) {
        Map<String, Object> details = er.getDetails();
        if (details == null || details.isEmpty()) return;

        // L2 Outcome details
        if ("L2_Outcome".equals(er.getEvaluatorName())) {
            if (details.containsKey("successCondition")) {
                boolean met = Boolean.TRUE.equals(details.get("successConditionMet"));
                html.append("<div class=\"layer-item-detail\">successCondition: ")
                        .append(esc(String.valueOf(details.get("successCondition"))))
                        .append(" ").append(met ? "&#10003;" : "&#10007;").append("</div>\n");
            }
        }

        // L3 Trajectory details
        if ("L3_Trajectory".equals(er.getEvaluatorName())) {
            if (details.containsKey("latencyMs")) {
                html.append("<div class=\"layer-item-detail\">latency: ")
                        .append(details.get("latencyMs")).append("ms</div>\n");
            }
            if (details.containsKey("totalToolCalls")) {
                html.append("<div class=\"layer-item-detail\">tool calls: ")
                        .append(details.get("totalToolCalls")).append("</div>\n");
            }
        }

        // L4 ReplyQuality details
        if ("L4_ReplyQuality".equals(er.getEvaluatorName()) && !details.containsKey("note")) {
            html.append("<div class=\"detail-box detail-semantic\">");
            if (details.containsKey("mode")) {
                html.append("<span class=\"tag tag-mock\">mock</span> ");
            }
            if (details.containsKey("similarityScore")) {
                html.append("Similarity: <strong>").append(details.get("similarityScore")).append("</strong> ");
            }
            if (details.containsKey("judgeCompositeScore")) {
                html.append("Judge: <strong>").append(details.get("judgeCompositeScore")).append("/5</strong> ");
            }
            if (details.containsKey("judgeScores")) {
                Map<String, Object> js = (Map<String, Object>) details.get("judgeScores");
                html.append("(C:").append(js.getOrDefault("correctness", "-"));
                html.append(" Cm:").append(js.getOrDefault("completeness", "-"));
                html.append(" T:").append(js.getOrDefault("tone", "-")).append(") ");
            }
            if (details.containsKey("ragScores")) {
                Map<String, Object> rs = (Map<String, Object>) details.get("ragScores");
                html.append("<br>RAG P:").append(rs.getOrDefault("precision", "-"));
                html.append(" R:").append(rs.getOrDefault("recall", "-"));
                if (rs.containsKey("faithfulness")) {
                    html.append(" F:").append(rs.get("faithfulness"));
                }
            }
            html.append("</div>\n");
        }
    }

    private void appendScript(StringBuilder html) {
        html.append("<script>\n");
        html.append("""
            function toggleEp(id) {
                var el = document.getElementById('ep-' + id);
                el.classList.toggle('open');
            }
            function filterEp(status) {
                document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
                event.target.classList.add('active');
                document.querySelectorAll('.ep-card').forEach(card => {
                    if (status === 'all') { card.style.display = ''; }
                    else { card.style.display = card.dataset.status === status ? '' : 'none'; }
                });
            }
            """);
        html.append("</script>\n");
    }

    private String formatTimestamp(Instant ts) {
        if (ts == null) return "N/A";
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
