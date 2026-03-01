package com.chatbot.eval.model;

import java.util.Map;

public class ToolArgConstraint {

    private String name;
    private Map<String, Object> args;
    private String matchMode; // "exact" (default) | "contains"

    public ToolArgConstraint() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public String getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(String matchMode) {
        this.matchMode = matchMode;
    }

    public boolean isContainsMode() {
        return "contains".equalsIgnoreCase(matchMode);
    }
}
