package com.chatbot.eval.model;

public class RetrievedContext {

    private String faqId;
    private String question;
    private double score;

    public RetrievedContext() {
    }

    public RetrievedContext(String faqId, String question, double score) {
        this.faqId = faqId;
        this.question = question;
        this.score = score;
    }

    public String getFaqId() {
        return faqId;
    }

    public void setFaqId(String faqId) {
        this.faqId = faqId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
