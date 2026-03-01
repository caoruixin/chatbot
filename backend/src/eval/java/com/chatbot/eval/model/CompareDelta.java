package com.chatbot.eval.model;

import java.util.List;
import java.util.Map;

public class CompareDelta {

    private VersionFingerprint baselineFingerprint;
    private VersionFingerprint candidateFingerprint;
    private double baselinePassRate;
    private double candidatePassRate;
    private double deltaPassRate;
    private Map<String, SuiteDelta> suiteDeltas;
    private List<String> regressions;
    private List<String> newPasses;

    public CompareDelta() {
    }

    public VersionFingerprint getBaselineFingerprint() {
        return baselineFingerprint;
    }

    public void setBaselineFingerprint(VersionFingerprint baselineFingerprint) {
        this.baselineFingerprint = baselineFingerprint;
    }

    public VersionFingerprint getCandidateFingerprint() {
        return candidateFingerprint;
    }

    public void setCandidateFingerprint(VersionFingerprint candidateFingerprint) {
        this.candidateFingerprint = candidateFingerprint;
    }

    public double getBaselinePassRate() {
        return baselinePassRate;
    }

    public void setBaselinePassRate(double baselinePassRate) {
        this.baselinePassRate = baselinePassRate;
    }

    public double getCandidatePassRate() {
        return candidatePassRate;
    }

    public void setCandidatePassRate(double candidatePassRate) {
        this.candidatePassRate = candidatePassRate;
    }

    public double getDeltaPassRate() {
        return deltaPassRate;
    }

    public void setDeltaPassRate(double deltaPassRate) {
        this.deltaPassRate = deltaPassRate;
    }

    public Map<String, SuiteDelta> getSuiteDeltas() {
        return suiteDeltas;
    }

    public void setSuiteDeltas(Map<String, SuiteDelta> suiteDeltas) {
        this.suiteDeltas = suiteDeltas;
    }

    public List<String> getRegressions() {
        return regressions;
    }

    public void setRegressions(List<String> regressions) {
        this.regressions = regressions;
    }

    public List<String> getNewPasses() {
        return newPasses;
    }

    public void setNewPasses(List<String> newPasses) {
        this.newPasses = newPasses;
    }

    public static class SuiteDelta {
        private double baselinePassRate;
        private double candidatePassRate;
        private double delta;

        public SuiteDelta() {
        }

        public SuiteDelta(double baselinePassRate, double candidatePassRate, double delta) {
            this.baselinePassRate = baselinePassRate;
            this.candidatePassRate = candidatePassRate;
            this.delta = delta;
        }

        public double getBaselinePassRate() { return baselinePassRate; }
        public void setBaselinePassRate(double baselinePassRate) { this.baselinePassRate = baselinePassRate; }
        public double getCandidatePassRate() { return candidatePassRate; }
        public void setCandidatePassRate(double candidatePassRate) { this.candidatePassRate = candidatePassRate; }
        public double getDelta() { return delta; }
        public void setDelta(double delta) { this.delta = delta; }
    }
}
