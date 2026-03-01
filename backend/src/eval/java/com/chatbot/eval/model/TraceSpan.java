package com.chatbot.eval.model;

import java.util.Map;

public class TraceSpan {

    private String spanName;
    private long startMs;
    private long endMs;
    private Map<String, Object> attributes;

    public TraceSpan() {
    }

    public TraceSpan(String spanName, long startMs, long endMs, Map<String, Object> attributes) {
        this.spanName = spanName;
        this.startMs = startMs;
        this.endMs = endMs;
        this.attributes = attributes;
    }

    public String getSpanName() {
        return spanName;
    }

    public void setSpanName(String spanName) {
        this.spanName = spanName;
    }

    public long getStartMs() {
        return startMs;
    }

    public void setStartMs(long startMs) {
        this.startMs = startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public void setEndMs(long endMs) {
        this.endMs = endMs;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
