package com.chatbot.service.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void success_withData_createsSuccessResult() {
        ToolResult result = ToolResult.success("test data");

        assertTrue(result.isSuccess());
        assertEquals("test data", result.getData());
        assertNull(result.getError());
        assertNull(result.getRequestId());
    }

    @Test
    void success_withDataAndRequestId_createsSuccessResult() {
        ToolResult result = ToolResult.success("data", "req-123");

        assertTrue(result.isSuccess());
        assertEquals("data", result.getData());
        assertEquals("req-123", result.getRequestId());
    }

    @Test
    void error_createsFailedNonRetryableResult() {
        ToolResult result = ToolResult.error("something went wrong");

        assertFalse(result.isSuccess());
        assertEquals("something went wrong", result.getError());
        assertFalse(result.isRetryable());
        assertFalse(result.needsConfirmation());
    }

    @Test
    void schemaError_createsRetryableResult() {
        ToolResult result = ToolResult.schemaError("missing param");

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertEquals("missing param", result.getError());
    }

    @Test
    void needsConfirmation_createsConfirmationResult() {
        ToolResult result = ToolResult.needsConfirmation("please confirm");

        assertFalse(result.isSuccess());
        assertTrue(result.needsConfirmation());
        assertEquals("please confirm", result.getError());
    }

    @Test
    void toJson_successResult_containsFields() {
        ToolResult result = ToolResult.success("hello");

        String json = result.toJson();

        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("hello"));
    }

    @Test
    void toJson_errorResult_containsErrorField() {
        ToolResult result = ToolResult.error("fail");

        String json = result.toJson();

        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("fail"));
    }

    @Test
    void toJson_nullData_handlesGracefully() {
        ToolResult result = ToolResult.error("fail");

        String json = result.toJson();

        assertNotNull(json);
        assertFalse(json.contains("null"));
    }
}
