package com.chatbot.service.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class ToolResult {

    private boolean success;
    private Object data;
    private String error;
    private boolean retryable;
    private boolean needsConfirmation;
    private String requestId;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ToolResult() {
    }

    public static ToolResult success(Object data) {
        ToolResult result = new ToolResult();
        result.success = true;
        result.data = data;
        return result;
    }

    public static ToolResult success(Object data, String requestId) {
        ToolResult result = new ToolResult();
        result.success = true;
        result.data = data;
        result.requestId = requestId;
        return result;
    }

    public static ToolResult error(String error) {
        ToolResult result = new ToolResult();
        result.success = false;
        result.error = error;
        result.retryable = false;
        return result;
    }

    public static ToolResult schemaError(String error) {
        ToolResult result = new ToolResult();
        result.success = false;
        result.error = error;
        result.retryable = true;
        return result;
    }

    public static ToolResult needsConfirmation(String message) {
        ToolResult result = new ToolResult();
        result.success = false;
        result.needsConfirmation = true;
        result.error = message;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean needsConfirmation() {
        return needsConfirmation;
    }

    public String getRequestId() {
        return requestId;
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("success", success,
                            "data", data != null ? data : "",
                            "error", error != null ? error : ""));
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed\"}";
        }
    }
}
