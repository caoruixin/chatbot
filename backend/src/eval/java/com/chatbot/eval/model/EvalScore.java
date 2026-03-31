package com.chatbot.eval.model;

import com.chatbot.eval.evaluator.EvalResult;

import java.util.List;

public class EvalScore {

    private String episodeId;
    private boolean overallPass;
    private double overallScore;
    private List<EvalResult> evaluatorResults;

    // Phase 2: 多轮诊断
    private List<TurnDiagnostic> turnDiagnostics;

    public EvalScore() {
    }

    public EvalScore(String episodeId, boolean overallPass, double overallScore,
                     List<EvalResult> evaluatorResults) {
        this.episodeId = episodeId;
        this.overallPass = overallPass;
        this.overallScore = overallScore;
        this.evaluatorResults = evaluatorResults;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(String episodeId) {
        this.episodeId = episodeId;
    }

    public boolean isOverallPass() {
        return overallPass;
    }

    public void setOverallPass(boolean overallPass) {
        this.overallPass = overallPass;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public List<EvalResult> getEvaluatorResults() {
        return evaluatorResults;
    }

    public void setEvaluatorResults(List<EvalResult> evaluatorResults) {
        this.evaluatorResults = evaluatorResults;
    }

    public List<TurnDiagnostic> getTurnDiagnostics() {
        return turnDiagnostics;
    }

    public void setTurnDiagnostics(List<TurnDiagnostic> turnDiagnostics) {
        this.turnDiagnostics = turnDiagnostics;
    }
}
