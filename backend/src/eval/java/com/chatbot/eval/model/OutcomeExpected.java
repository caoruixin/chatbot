package com.chatbot.eval.model;

import java.util.List;

/**
 * L2 Outcome: 任务成功约束。用户的问题到底有没有被解决。
 */
public class OutcomeExpected {

    private String successCondition;         // faq_answered_from_kb | query_result_returned | ...
    private List<SideEffect> sideEffects;    // 预期副作用
    private Boolean requireClarification;    // 是否应该先澄清
    private Boolean requireEscalation;       // 是否应该转人工

    public OutcomeExpected() {
    }

    public String getSuccessCondition() {
        return successCondition;
    }

    public void setSuccessCondition(String successCondition) {
        this.successCondition = successCondition;
    }

    public List<SideEffect> getSideEffects() {
        return sideEffects;
    }

    public void setSideEffects(List<SideEffect> sideEffects) {
        this.sideEffects = sideEffects;
    }

    public Boolean getRequireClarification() {
        return requireClarification;
    }

    public void setRequireClarification(Boolean requireClarification) {
        this.requireClarification = requireClarification;
    }

    public Boolean getRequireEscalation() {
        return requireEscalation;
    }

    public void setRequireEscalation(Boolean requireEscalation) {
        this.requireEscalation = requireEscalation;
    }
}
