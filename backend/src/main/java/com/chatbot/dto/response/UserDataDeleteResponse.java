package com.chatbot.dto.response;

public class UserDataDeleteResponse {

    private boolean success;
    private String message;
    private String requestId;

    public UserDataDeleteResponse() {
    }

    public UserDataDeleteResponse(boolean success, String message, String requestId) {
        this.success = success;
        this.message = message;
        this.requestId = requestId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
