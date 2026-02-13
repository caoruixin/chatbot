package com.chatbot.dto.response;

import java.util.List;

public class PostQueryResponse {

    private List<PostItem> posts;

    public PostQueryResponse() {
    }

    public PostQueryResponse(List<PostItem> posts) {
        this.posts = posts;
    }

    public List<PostItem> getPosts() {
        return posts;
    }

    public void setPosts(List<PostItem> posts) {
        this.posts = posts;
    }
}
