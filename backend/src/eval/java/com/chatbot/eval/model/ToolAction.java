package com.chatbot.eval.model;

import com.chatbot.service.tool.ToolCall;
import com.chatbot.service.tool.ToolResult;

import java.util.Map;

public class ToolAction {

    private String name;
    private Map<String, Object> args;
    private String status;
    private String resultSummary;
    private long timestampMs;

    public ToolAction() {
    }

    public static ToolAction fromToolCallAndResult(ToolCall call, ToolResult result, long ts) {
        ToolAction action = new ToolAction();
        action.name = call.getToolName();
        action.args = call.getParams();
        action.timestampMs = ts;

        if (result.isSuccess()) {
            action.status = "ok";
            action.resultSummary = result.toJson();
        } else if (result.needsConfirmation()) {
            action.status = "needs_confirmation";
            action.resultSummary = result.getError();
        } else {
            action.status = "failed";
            action.resultSummary = result.getError();
        }

        return action;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public void setTimestampMs(long timestampMs) {
        this.timestampMs = timestampMs;
    }
}
