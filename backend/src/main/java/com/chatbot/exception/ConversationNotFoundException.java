package com.chatbot.exception;

public class ConversationNotFoundException extends ChatbotException {

    public ConversationNotFoundException(String conversationId) {
        super("CONVERSATION_NOT_FOUND", "Conversation not found: " + conversationId);
    }
}
