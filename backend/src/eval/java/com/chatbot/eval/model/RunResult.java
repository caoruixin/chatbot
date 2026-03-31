package com.chatbot.eval.model;

import java.util.List;

public class RunResult {

    private String episodeId;
    private String finalReply;
    private List<ToolAction> actions;
    private EvalArtifacts artifacts;
    private EvalMetrics metrics;
    private Trace trace;
    private VersionFingerprint version;
    private List<RetrievedContext> retrievedContexts;

    // Phase 2: 多轮对话支持
    private List<TurnResult> turnResults;

    public RunResult() {
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(String episodeId) {
        this.episodeId = episodeId;
    }

    public String getFinalReply() {
        return finalReply;
    }

    public void setFinalReply(String finalReply) {
        this.finalReply = finalReply;
    }

    public List<ToolAction> getActions() {
        return actions;
    }

    public void setActions(List<ToolAction> actions) {
        this.actions = actions;
    }

    public EvalArtifacts getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(EvalArtifacts artifacts) {
        this.artifacts = artifacts;
    }

    public EvalMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(EvalMetrics metrics) {
        this.metrics = metrics;
    }

    public Trace getTrace() {
        return trace;
    }

    public void setTrace(Trace trace) {
        this.trace = trace;
    }

    public VersionFingerprint getVersion() {
        return version;
    }

    public void setVersion(VersionFingerprint version) {
        this.version = version;
    }

    public List<RetrievedContext> getRetrievedContexts() {
        return retrievedContexts;
    }

    public void setRetrievedContexts(List<RetrievedContext> retrievedContexts) {
        this.retrievedContexts = retrievedContexts;
    }

    public List<TurnResult> getTurnResults() {
        return turnResults;
    }

    public void setTurnResults(List<TurnResult> turnResults) {
        this.turnResults = turnResults;
    }
}
