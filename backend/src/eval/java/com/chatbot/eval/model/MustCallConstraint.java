package com.chatbot.eval.model;

public class MustCallConstraint {

    private String name;
    private int min;

    public MustCallConstraint() {
    }

    public MustCallConstraint(String name, int min) {
        this.name = name;
        this.min = min;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }
}
