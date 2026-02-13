package com.chatbot.dto.request;

public class FaqSearchRequest {

    private String query;

    public FaqSearchRequest() {
    }

    public FaqSearchRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
