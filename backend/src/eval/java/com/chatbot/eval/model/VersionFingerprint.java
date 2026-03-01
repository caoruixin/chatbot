package com.chatbot.eval.model;

import java.util.Map;

public class VersionFingerprint {

    private String modelId;
    private Map<String, String> promptHashes;
    private String workflowVersion;
    private String toolSchemaVersion;
    private String kbSnapshot;
    private String gitCommit;
    private String compositeHash;

    public VersionFingerprint() {
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Map<String, String> getPromptHashes() {
        return promptHashes;
    }

    public void setPromptHashes(Map<String, String> promptHashes) {
        this.promptHashes = promptHashes;
    }

    public String getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(String workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public String getToolSchemaVersion() {
        return toolSchemaVersion;
    }

    public void setToolSchemaVersion(String toolSchemaVersion) {
        this.toolSchemaVersion = toolSchemaVersion;
    }

    public String getKbSnapshot() {
        return kbSnapshot;
    }

    public void setKbSnapshot(String kbSnapshot) {
        this.kbSnapshot = kbSnapshot;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public void setGitCommit(String gitCommit) {
        this.gitCommit = gitCommit;
    }

    public String getCompositeHash() {
        return compositeHash;
    }

    public void setCompositeHash(String compositeHash) {
        this.compositeHash = compositeHash;
    }
}
