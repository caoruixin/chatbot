package com.chatbot.service.tool;

import java.util.Map;

public class ToolCall {

    private String toolName;
    private Map<String, Object> params;
    private boolean userConfirmed;

    public ToolCall() {
    }

    public ToolCall(String toolName, Map<String, Object> params, boolean userConfirmed) {
        this.toolName = toolName;
        this.params = params;
        this.userConfirmed = userConfirmed;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public boolean isUserConfirmed() {
        return userConfirmed;
    }

    public void setUserConfirmed(boolean userConfirmed) {
        this.userConfirmed = userConfirmed;
    }
}
