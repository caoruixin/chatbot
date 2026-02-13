package com.chatbot.dto.response;

public class SessionResponse {

    private String sessionId;
    private String conversationId;
    private String status;
    private String assignedAgentId;
    private String createdAt;
    private String lastActivityAt;

    public SessionResponse() {
    }

    public SessionResponse(String sessionId, String conversationId, String status,
                           String assignedAgentId, String createdAt, String lastActivityAt) {
        this.sessionId = sessionId;
        this.conversationId = conversationId;
        this.status = status;
        this.assignedAgentId = assignedAgentId;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedAgentId() {
        return assignedAgentId;
    }

    public void setAssignedAgentId(String assignedAgentId) {
        this.assignedAgentId = assignedAgentId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(String lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
