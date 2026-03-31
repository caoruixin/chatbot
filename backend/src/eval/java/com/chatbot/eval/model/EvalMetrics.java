package com.chatbot.eval.model;

public class EvalMetrics {

    private long latencyMs;
    private Integer tokenIn;
    private Integer tokenOut;
    private Double cost;
    private int toolCallCount;

    // Phase 2: 多轮对话指标
    private Integer turnsToResolve;
    private String resolutionType;  // AI_RESOLVED | ESCALATED | ABANDONED

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

    public Integer getTurnsToResolve() {
        return turnsToResolve;
    }

    public void setTurnsToResolve(Integer turnsToResolve) {
        this.turnsToResolve = turnsToResolve;
    }

    public String getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
    }
}
