package com.chatbot.eval.model;

import java.util.List;

/**
 * 单轮执行结果。记录一个 turn 内 agent 的完整处理过程。
 */
public class TurnResult {

    private int turnIndex;
    private String userMessage;
    private String agentReply;
    private List<ToolAction> actions;
    private IntentSummary intentSummary;
    private long latencyMs;

    // 中间检查点结果（可选）
    private TurnExpectation expectation;
    private List<String> expectationViolations;

    public TurnResult() {
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAgentReply() {
        return agentReply;
    }

    public void setAgentReply(String agentReply) {
        this.agentReply = agentReply;
    }

    public List<ToolAction> getActions() {
        return actions;
    }

    public void setActions(List<ToolAction> actions) {
        this.actions = actions;
    }

    public IntentSummary getIntentSummary() {
        return intentSummary;
    }

    public void setIntentSummary(IntentSummary intentSummary) {
        this.intentSummary = intentSummary;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public TurnExpectation getExpectation() {
        return expectation;
    }

    public void setExpectation(TurnExpectation expectation) {
        this.expectation = expectation;
    }

    public List<String> getExpectationViolations() {
        return expectationViolations;
    }

    public void setExpectationViolations(List<String> expectationViolations) {
        this.expectationViolations = expectationViolations;
    }

    /**
     * 意图识别摘要，不暴露完整 IntentResult。
     */
    public static class IntentSummary {
        private String intent;
        private double confidence;
        private String risk;

        public IntentSummary() {
        }

        public IntentSummary(String intent, double confidence, String risk) {
            this.intent = intent;
            this.confidence = confidence;
            this.risk = risk;
        }

        public String getIntent() {
            return intent;
        }

        public void setIntent(String intent) {
            this.intent = intent;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getRisk() {
            return risk;
        }

        public void setRisk(String risk) {
            this.risk = risk;
        }
    }
}
