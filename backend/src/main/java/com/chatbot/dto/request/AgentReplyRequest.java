package com.chatbot.dto.request;

public class AgentReplyRequest {

    private String sessionId;
    private String agentId;
    private String content;

    public AgentReplyRequest() {
    }

    public AgentReplyRequest(String sessionId, String agentId, String content) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.content = content;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
