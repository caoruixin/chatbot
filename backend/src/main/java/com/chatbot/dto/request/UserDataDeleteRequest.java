package com.chatbot.dto.request;

public class UserDataDeleteRequest {

    private String username;

    public UserDataDeleteRequest() {
    }

    public UserDataDeleteRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
