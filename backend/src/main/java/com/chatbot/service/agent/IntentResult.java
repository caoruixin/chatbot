package com.chatbot.service.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the result of intent recognition from the LLM.
 * Contains the classified intent, confidence score, risk level, and any extracted parameters.
 */
public class IntentResult {

    private String intent;       // POST_QUERY, DATA_DELETION, KB_QUESTION, GENERAL_CHAT
    private double confidence;   // 0.0 - 1.0
    private String risk;         // "low" or "critical"
    private Map<String, String> extractedParams;

    public IntentResult() {
        this.extractedParams = new HashMap<>();
    }

    public IntentResult(String intent, double confidence, String risk, Map<String, String> extractedParams) {
        this.intent = intent;
        this.confidence = confidence;
        this.risk = risk;
        this.extractedParams = extractedParams != null ? extractedParams : new HashMap<>();
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

    public Map<String, String> getExtractedParams() {
        return extractedParams;
    }

    public void setExtractedParams(Map<String, String> extractedParams) {
        this.extractedParams = extractedParams;
    }
}
