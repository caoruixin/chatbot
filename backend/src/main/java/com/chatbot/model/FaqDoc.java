package com.chatbot.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class FaqDoc {

    private UUID faqId;
    private String question;
    private String answer;
    private String embedding;
    private LocalDateTime createdAt;

    // score is computed at query time, not persisted
    private Double score;

    public FaqDoc() {
    }

    public UUID getFaqId() {
        return faqId;
    }

    public void setFaqId(UUID faqId) {
        this.faqId = faqId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
