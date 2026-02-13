package com.chatbot.dto.response;

public class InboundMessageResponse {

    private String conversationId;
    private String sessionId;
    private String messageId;

    public InboundMessageResponse() {
    }

    public InboundMessageResponse(String conversationId, String sessionId, String messageId) {
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
