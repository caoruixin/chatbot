package com.chatbot.eval.model;

import java.util.List;

/**
 * L1 Gate: 硬门槛约束。任一违规 → episode fail。
 */
public class GateExpected {

    private List<String> mustNot;           // 禁止调用的工具
    private List<String> mustNotClaim;      // 回复中不得出现的虚假声明
    private Boolean identityRequired;       // 是否要求身份验证
    private List<String> mustMention;       // 回复必须包含的关键词（从 replyConstraints 上提）

    public GateExpected() {
    }

    public List<String> getMustNot() {
        return mustNot;
    }

    public void setMustNot(List<String> mustNot) {
        this.mustNot = mustNot;
    }

    public List<String> getMustNotClaim() {
        return mustNotClaim;
    }

    public void setMustNotClaim(List<String> mustNotClaim) {
        this.mustNotClaim = mustNotClaim;
    }

    public Boolean getIdentityRequired() {
        return identityRequired;
    }

    public void setIdentityRequired(Boolean identityRequired) {
        this.identityRequired = identityRequired;
    }

    public List<String> getMustMention() {
        return mustMention;
    }

    public void setMustMention(List<String> mustMention) {
        this.mustMention = mustMention;
    }
}
