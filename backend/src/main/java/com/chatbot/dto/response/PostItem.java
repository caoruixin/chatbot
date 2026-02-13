package com.chatbot.dto.response;

public class PostItem {

    private int postId;
    private String username;
    private String title;
    private String status;
    private String createdAt;

    public PostItem() {
    }

    public PostItem(int postId, String username, String title, String status, String createdAt) {
        this.postId = postId;
        this.username = username;
        this.title = title;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
