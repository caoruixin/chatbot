package com.chatbot.dto.response;

public class FaqSearchResponse {

    private String question;
    private String answer;
    private double score;

    public FaqSearchResponse() {
    }

    public FaqSearchResponse(String question, String answer, double score) {
        this.question = question;
        this.answer = answer;
        this.score = score;
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

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
