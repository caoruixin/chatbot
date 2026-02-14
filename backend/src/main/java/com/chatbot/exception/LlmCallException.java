package com.chatbot.exception;

public class LlmCallException extends ChatbotException {

    public LlmCallException(String message) {
        super("LLM_CALL_FAILED", message);
    }

    public LlmCallException(String message, Throwable cause) {
        super("LLM_CALL_FAILED", message, cause);
    }
}
