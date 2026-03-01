package com.chatbot.eval.model;

import java.util.List;

public class EpisodeExpected {

    private List<MustCallConstraint> mustCall;
    private List<String> mustNot;
    private String outcome;
    private List<SideEffect> sideEffects;
    private ReplyConstraints replyConstraints;
    private String goldenReply;
    private List<ToolArgConstraint> toolArgConstraints;
    private List<String> expectedContexts;
    private Boolean faithfulnessCheck;

    public EpisodeExpected() {
    }

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
}
