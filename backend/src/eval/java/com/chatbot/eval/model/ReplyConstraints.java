package com.chatbot.eval.model;

import java.util.List;

public class ReplyConstraints {

    private String language;
    private List<String> mustMention;

    public ReplyConstraints() {
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<String> getMustMention() {
        return mustMention;
    }

    public void setMustMention(List<String> mustMention) {
        this.mustMention = mustMention;
    }
}
