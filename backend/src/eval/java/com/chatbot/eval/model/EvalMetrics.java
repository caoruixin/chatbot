package com.chatbot.eval.model;

public class EvalMetrics {

    private long latencyMs;
    private Integer tokenIn;
    private Integer tokenOut;
    private Double cost;
    private int toolCallCount;

    public EvalMetrics() {
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getTokenIn() {
        return tokenIn;
    }

    public void setTokenIn(Integer tokenIn) {
        this.tokenIn = tokenIn;
    }

    public Integer getTokenOut() {
        return tokenOut;
    }

    public void setTokenOut(Integer tokenOut) {
        this.tokenOut = tokenOut;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }
}
