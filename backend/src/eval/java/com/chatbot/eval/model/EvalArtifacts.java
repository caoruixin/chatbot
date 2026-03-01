package com.chatbot.eval.model;

import java.util.Map;

public class EvalArtifacts {

    private Map<String, Object> identityArtifacts;
    private Map<String, Object> executionArtifacts;
    private Map<String, Object> composerArtifacts;

    public EvalArtifacts() {
    }

    public Map<String, Object> getIdentityArtifacts() {
        return identityArtifacts;
    }

    public void setIdentityArtifacts(Map<String, Object> identityArtifacts) {
        this.identityArtifacts = identityArtifacts;
    }

    public Map<String, Object> getExecutionArtifacts() {
        return executionArtifacts;
    }

    public void setExecutionArtifacts(Map<String, Object> executionArtifacts) {
        this.executionArtifacts = executionArtifacts;
    }

    public Map<String, Object> getComposerArtifacts() {
        return composerArtifacts;
    }

    public void setComposerArtifacts(Map<String, Object> composerArtifacts) {
        this.composerArtifacts = composerArtifacts;
    }
}
