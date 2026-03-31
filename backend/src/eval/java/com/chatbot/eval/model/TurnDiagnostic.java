package com.chatbot.eval.model;

import java.util.List;

/**
 * 单轮诊断结果。不影响 overall pass/fail，仅用于报告展示。
 */
public class TurnDiagnostic {

    private int turnIndex;
    private boolean expectationMet;
    private List<String> violations;

    public TurnDiagnostic() {
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    public boolean isExpectationMet() {
        return expectationMet;
    }

    public void setExpectationMet(boolean expectationMet) {
        this.expectationMet = expectationMet;
    }

    public List<String> getViolations() {
        return violations;
    }

    public void setViolations(List<String> violations) {
        this.violations = violations;
    }
}
