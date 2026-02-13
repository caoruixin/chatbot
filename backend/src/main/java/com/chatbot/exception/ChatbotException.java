package com.chatbot.exception;

public class ChatbotException extends RuntimeException {

    private final String errorCode;

    public ChatbotException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ChatbotException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
