package com.chatbot.exception;

public class SessionNotFoundException extends ChatbotException {

    public SessionNotFoundException(String sessionId) {
        super("SESSION_NOT_FOUND", "Session not found: " + sessionId);
    }
}
