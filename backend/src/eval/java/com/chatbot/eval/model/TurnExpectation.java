package com.chatbot.eval.model;

import java.util.List;

/**
 * 中间检查点约束。附着在 assistant 类型的 ConversationTurn 上，
 * 用于诊断多轮对话中哪一轮出了问题。
 *
 * 所有字段可选。只检查有值的字段。
 */
public class TurnExpectation {

    /**
     * 该轮 agent 应该进入的处理路径。
     * 复用 L2 OutcomeEvaluator 的 successCondition 规则。
     * 例如: "clarification_asked", "action_initiated"
     */
    private String successCondition;

    /**
     * 该轮回复中禁止出现的声明。
     * 复用 L1 GateEvaluator 的 mustNotClaim 逻辑。
     */
    private List<String> mustNotClaim;

    /**
     * 该轮禁止调用的工具。
     */
    private List<String> mustNotCall;

    /**
     * 该轮必须调用的工具。
     */
    private List<String> mustCall;

    /**
     * 该轮回复必须包含的关键词。
     */
    private List<String> mustMention;

    public TurnExpectation() {
    }

    public String getSuccessCondition() {
        return successCondition;
    }

    public void setSuccessCondition(String successCondition) {
        this.successCondition = successCondition;
    }

    public List<String> getMustNotClaim() {
        return mustNotClaim;
    }

    public void setMustNotClaim(List<String> mustNotClaim) {
        this.mustNotClaim = mustNotClaim;
    }

    public List<String> getMustNotCall() {
        return mustNotCall;
    }

    public void setMustNotCall(List<String> mustNotCall) {
        this.mustNotCall = mustNotCall;
    }

    public List<String> getMustCall() {
        return mustCall;
    }

    public void setMustCall(List<String> mustCall) {
        this.mustCall = mustCall;
    }

    public List<String> getMustMention() {
        return mustMention;
    }

    public void setMustMention(List<String> mustMention) {
        this.mustMention = mustMention;
    }
}
