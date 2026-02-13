package com.chatbot.dto.request;

public class PostQueryRequest {

    private String username;

    public PostQueryRequest() {
    }

    public PostQueryRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
