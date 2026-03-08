package com.chatbot.eval.model;

/**
 * 工具调用上限约束：指定工具最多调用 max 次。
 */
public class AllowedCallConstraint {

    private String name;
    private int max;

    public AllowedCallConstraint() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }
}
