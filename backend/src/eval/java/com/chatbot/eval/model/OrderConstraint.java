package com.chatbot.eval.model;

/**
 * 工具调用顺序约束：before 必须在 after 之前被调用。
 */
public class OrderConstraint {

    private String before;
    private String after;

    public OrderConstraint() {
    }

    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }
}
