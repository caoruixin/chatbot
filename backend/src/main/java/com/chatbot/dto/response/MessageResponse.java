package com.chatbot.dto.response;

public class MessageResponse {

    private String messageId;
    private String conversationId;
    private String sessionId;
    private String senderType;
    private String senderId;
    private String content;
    private String createdAt;

    public MessageResponse() {
    }

    public MessageResponse(String messageId, String conversationId, String sessionId,
                           String senderType, String senderId, String content, String createdAt) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
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

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
