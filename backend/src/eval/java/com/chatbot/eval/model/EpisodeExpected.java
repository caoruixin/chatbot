package com.chatbot.eval.model;

import java.util.List;

public class EpisodeExpected {

    // ── Phase 0 扁平字段（保留，向后兼容）──
    private List<MustCallConstraint> mustCall;
    private List<String> mustNot;
    private String outcome;
    private List<SideEffect> sideEffects;
    private ReplyConstraints replyConstraints;
    private String goldenReply;
    private List<ToolArgConstraint> toolArgConstraints;
    private List<String> expectedContexts;
    private Boolean faithfulnessCheck;

    // ── Phase 1 分层字段（新增）──
    private GateExpected gate;
    private OutcomeExpected outcomeExpected;
    private TrajectoryExpected trajectory;
    private ReplyQualityExpected replyQuality;

    public EpisodeExpected() {
    }

    // Phase 0 getters/setters

    public List<MustCallConstraint> getMustCall() {
        return mustCall;
    }

    public void setMustCall(List<MustCallConstraint> mustCall) {
        this.mustCall = mustCall;
    }

    public List<String> getMustNot() {
        return mustNot;
    }

    public void setMustNot(List<String> mustNot) {
        this.mustNot = mustNot;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public List<SideEffect> getSideEffects() {
        return sideEffects;
    }

    public void setSideEffects(List<SideEffect> sideEffects) {
        this.sideEffects = sideEffects;
    }

    public ReplyConstraints getReplyConstraints() {
        return replyConstraints;
    }

    public void setReplyConstraints(ReplyConstraints replyConstraints) {
        this.replyConstraints = replyConstraints;
    }

    public String getGoldenReply() {
        return goldenReply;
    }

    public void setGoldenReply(String goldenReply) {
        this.goldenReply = goldenReply;
    }

    public List<ToolArgConstraint> getToolArgConstraints() {
        return toolArgConstraints;
    }

    public void setToolArgConstraints(List<ToolArgConstraint> toolArgConstraints) {
        this.toolArgConstraints = toolArgConstraints;
    }

    public List<String> getExpectedContexts() {
        return expectedContexts;
    }

    public void setExpectedContexts(List<String> expectedContexts) {
        this.expectedContexts = expectedContexts;
    }

    public Boolean getFaithfulnessCheck() {
        return faithfulnessCheck;
    }

    public void setFaithfulnessCheck(Boolean faithfulnessCheck) {
        this.faithfulnessCheck = faithfulnessCheck;
    }

    // Phase 1 getters/setters

    public GateExpected getGate() {
        return gate;
    }

    public void setGate(GateExpected gate) {
        this.gate = gate;
    }

    public OutcomeExpected getOutcomeExpected() {
        return outcomeExpected;
    }

    public void setOutcomeExpected(OutcomeExpected outcomeExpected) {
        this.outcomeExpected = outcomeExpected;
    }

    public TrajectoryExpected getTrajectory() {
        return trajectory;
    }

    public void setTrajectory(TrajectoryExpected trajectory) {
        this.trajectory = trajectory;
    }

    public ReplyQualityExpected getReplyQuality() {
        return replyQuality;
    }

    public void setReplyQuality(ReplyQualityExpected replyQuality) {
        this.replyQuality = replyQuality;
    }
}
