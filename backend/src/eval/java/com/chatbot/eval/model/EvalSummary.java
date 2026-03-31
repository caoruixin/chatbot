package com.chatbot.eval.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class EvalSummary {

    private int totalEpisodes;
    private int totalPass;
    private int totalFail;
    private double overallPassRate;
    private Map<String, Double> passRateByEvaluator;
    private Map<String, SuiteStats> suiteBreakdown;
    private Map<String, SuiteStats> tagBreakdown;
    private LatencyStats latencyStats;

    // Phase 1: 分层统计
    private Map<String, Map<String, Double>> suiteLayerPassRates; // {suite: {L1: rate, L2: rate, ...}}
    private Map<String, Integer> failureAttribution;              // {l1_gate_fail: N, l2_outcome_fail: N, ...}
    private VersionFingerprint fingerprint;
    private Instant timestamp;
    private List<EvalScore> scores;

    // Phase 2: 多轮统计
    private MultiTurnStats multiTurnStats;

    public EvalSummary() {
    }

    public int getTotalEpisodes() {
        return totalEpisodes;
    }

    public void setTotalEpisodes(int totalEpisodes) {
        this.totalEpisodes = totalEpisodes;
    }

    public int getTotalPass() {
        return totalPass;
    }

    public void setTotalPass(int totalPass) {
        this.totalPass = totalPass;
    }

    public int getTotalFail() {
        return totalFail;
    }

    public void setTotalFail(int totalFail) {
        this.totalFail = totalFail;
    }

    public double getOverallPassRate() {
        return overallPassRate;
    }

    public void setOverallPassRate(double overallPassRate) {
        this.overallPassRate = overallPassRate;
    }

    public Map<String, Double> getPassRateByEvaluator() {
        return passRateByEvaluator;
    }

    public void setPassRateByEvaluator(Map<String, Double> passRateByEvaluator) {
        this.passRateByEvaluator = passRateByEvaluator;
    }

    public Map<String, SuiteStats> getSuiteBreakdown() {
        return suiteBreakdown;
    }

    public void setSuiteBreakdown(Map<String, SuiteStats> suiteBreakdown) {
        this.suiteBreakdown = suiteBreakdown;
    }

    public Map<String, SuiteStats> getTagBreakdown() {
        return tagBreakdown;
    }

    public void setTagBreakdown(Map<String, SuiteStats> tagBreakdown) {
        this.tagBreakdown = tagBreakdown;
    }

    public LatencyStats getLatencyStats() {
        return latencyStats;
    }

    public void setLatencyStats(LatencyStats latencyStats) {
        this.latencyStats = latencyStats;
    }

    public VersionFingerprint getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(VersionFingerprint fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<EvalScore> getScores() {
        return scores;
    }

    public void setScores(List<EvalScore> scores) {
        this.scores = scores;
    }

    public Map<String, Map<String, Double>> getSuiteLayerPassRates() {
        return suiteLayerPassRates;
    }

    public void setSuiteLayerPassRates(Map<String, Map<String, Double>> suiteLayerPassRates) {
        this.suiteLayerPassRates = suiteLayerPassRates;
    }

    public Map<String, Integer> getFailureAttribution() {
        return failureAttribution;
    }

    public void setFailureAttribution(Map<String, Integer> failureAttribution) {
        this.failureAttribution = failureAttribution;
    }

    public MultiTurnStats getMultiTurnStats() {
        return multiTurnStats;
    }

    public void setMultiTurnStats(MultiTurnStats multiTurnStats) {
        this.multiTurnStats = multiTurnStats;
    }

    public static class MultiTurnStats {
        private int multiTurnCount;
        private int singleTurnCount;
        private double avgTurnsToResolve;
        private Map<String, Integer> resolutionTypeDistribution; // AI_RESOLVED: N, ESCALATED: N, ABANDONED: N
        private double checkpointPassRate;

        public MultiTurnStats() {
        }

        public int getMultiTurnCount() { return multiTurnCount; }
        public void setMultiTurnCount(int multiTurnCount) { this.multiTurnCount = multiTurnCount; }
        public int getSingleTurnCount() { return singleTurnCount; }
        public void setSingleTurnCount(int singleTurnCount) { this.singleTurnCount = singleTurnCount; }
        public double getAvgTurnsToResolve() { return avgTurnsToResolve; }
        public void setAvgTurnsToResolve(double avgTurnsToResolve) { this.avgTurnsToResolve = avgTurnsToResolve; }
        public Map<String, Integer> getResolutionTypeDistribution() { return resolutionTypeDistribution; }
        public void setResolutionTypeDistribution(Map<String, Integer> resolutionTypeDistribution) { this.resolutionTypeDistribution = resolutionTypeDistribution; }
        public double getCheckpointPassRate() { return checkpointPassRate; }
        public void setCheckpointPassRate(double checkpointPassRate) { this.checkpointPassRate = checkpointPassRate; }
    }

    public static class SuiteStats {
        private int total;
        private int pass;
        private int fail;
        private double passRate;
        private double avgLatencyMs;

        public SuiteStats() {
        }

        public SuiteStats(int total, int pass, int fail, double passRate, double avgLatencyMs) {
            this.total = total;
            this.pass = pass;
            this.fail = fail;
            this.passRate = passRate;
            this.avgLatencyMs = avgLatencyMs;
        }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getPass() { return pass; }
        public void setPass(int pass) { this.pass = pass; }
        public int getFail() { return fail; }
        public void setFail(int fail) { this.fail = fail; }
        public double getPassRate() { return passRate; }
        public void setPassRate(double passRate) { this.passRate = passRate; }
        public double getAvgLatencyMs() { return avgLatencyMs; }
        public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    }

    public static class LatencyStats {
        private double p50;
        private double p95;
        private double avg;
        private double max;

        public LatencyStats() {
        }

        public LatencyStats(double p50, double p95, double avg, double max) {
            this.p50 = p50;
            this.p95 = p95;
            this.avg = avg;
            this.max = max;
        }

        public double getP50() { return p50; }
        public void setP50(double p50) { this.p50 = p50; }
        public double getP95() { return p95; }
        public void setP95(double p95) { this.p95 = p95; }
        public double getAvg() { return avg; }
        public void setAvg(double avg) { this.avg = avg; }
        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }
    }
}
