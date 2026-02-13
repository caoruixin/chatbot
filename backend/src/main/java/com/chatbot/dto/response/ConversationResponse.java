package com.chatbot.dto.response;

public class ConversationResponse {

    private String conversationId;
    private String userId;
    private String status;
    private String getstreamChannelId;
    private String createdAt;
    private String updatedAt;

    public ConversationResponse() {
    }

    public ConversationResponse(String conversationId, String userId, String status,
                                String getstreamChannelId, String createdAt, String updatedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.status = status;
        this.getstreamChannelId = getstreamChannelId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGetstreamChannelId() {
        return getstreamChannelId;
    }

    public void setGetstreamChannelId(String getstreamChannelId) {
        this.getstreamChannelId = getstreamChannelId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
