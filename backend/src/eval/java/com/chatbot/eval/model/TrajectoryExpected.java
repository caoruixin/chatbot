package com.chatbot.eval.model;

import java.util.List;

/**
 * L3 Trajectory: 过程约束（工具调用合规性 + 效率指标）。
 */
public class TrajectoryExpected {

    private List<MustCallConstraint> mustCall;           // 必须调用的工具及最少次数
    private List<AllowedCallConstraint> allowedCall;     // 允许的上限
    private List<OrderConstraint> orderConstraints;      // 顺序约束
    private List<ToolArgConstraint> toolArgConstraints;  // 工具参数校验

    public TrajectoryExpected() {
    }

    public List<MustCallConstraint> getMustCall() {
        return mustCall;
    }

    public void setMustCall(List<MustCallConstraint> mustCall) {
        this.mustCall = mustCall;
    }

    public List<AllowedCallConstraint> getAllowedCall() {
        return allowedCall;
    }

    public void setAllowedCall(List<AllowedCallConstraint> allowedCall) {
        this.allowedCall = allowedCall;
    }

    public List<OrderConstraint> getOrderConstraints() {
        return orderConstraints;
    }

    public void setOrderConstraints(List<OrderConstraint> orderConstraints) {
        this.orderConstraints = orderConstraints;
    }

    public List<ToolArgConstraint> getToolArgConstraints() {
        return toolArgConstraints;
    }

    public void setToolArgConstraints(List<ToolArgConstraint> toolArgConstraints) {
        this.toolArgConstraints = toolArgConstraints;
    }
}
