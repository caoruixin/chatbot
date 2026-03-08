package com.chatbot.eval.model;

import java.util.List;

/**
 * L4 ReplyQuality: 回复质量约束（语义相似度 + LLM Judge + RAG 质量）。
 */
public class ReplyQualityExpected {

    private ReplyConstraints replyConstraints;   // 语言、mustMention
    private String goldenReply;                  // 参考答案
    private List<String> expectedContexts;       // 预期 FAQ 检索
    private Boolean faithfulnessCheck;           // 忠实度检查

    public ReplyQualityExpected() {
    }

    public ReplyConstraints getReplyConstraints() {
        return replyConstraints;
    }

    public void setReplyConstraints(ReplyConstraints replyConstraints) {
        this.replyConstraints = replyConstraints;
    }

    public String getGoldenReply() {
        return goldenReply;
    }

    public void setGoldenReply(String goldenReply) {
        this.goldenReply = goldenReply;
    }

    public List<String> getExpectedContexts() {
        return expectedContexts;
    }

    public void setExpectedContexts(List<String> expectedContexts) {
        this.expectedContexts = expectedContexts;
    }

    public Boolean getFaithfulnessCheck() {
        return faithfulnessCheck;
    }

    public void setFaithfulnessCheck(Boolean faithfulnessCheck) {
        this.faithfulnessCheck = faithfulnessCheck;
    }
}
