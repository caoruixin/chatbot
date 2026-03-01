package com.chatbot.eval.evaluator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EvalResult {

    private String evaluatorName;
    private boolean passed;
    private double score;
    private List<String> violations;
    private Map<String, Object> details;

    public EvalResult() {
        this.violations = new ArrayList<>();
        this.details = new LinkedHashMap<>();
    }

    public EvalResult(String evaluatorName, boolean passed, double score) {
        this.evaluatorName = evaluatorName;
        this.passed = passed;
        this.score = score;
        this.violations = new ArrayList<>();
        this.details = new LinkedHashMap<>();
    }

    public static EvalResult pass(String evaluatorName) {
        return new EvalResult(evaluatorName, true, 1.0);
    }

    public static EvalResult fail(String evaluatorName, List<String> violations) {
        EvalResult result = new EvalResult(evaluatorName, false, 0.0);
        result.setViolations(violations);
        return result;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public void setEvaluatorName(String evaluatorName) {
        this.evaluatorName = evaluatorName;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<String> getViolations() {
        return violations;
    }

    public void setViolations(List<String> violations) {
        this.violations = violations;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
