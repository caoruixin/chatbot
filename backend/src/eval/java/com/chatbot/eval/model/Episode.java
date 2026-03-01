package com.chatbot.eval.model;

import java.util.List;
import java.util.Map;

public class Episode {

    private String id;
    private String suite;
    private List<String> tags;
    private InitialState initialState;
    private List<ConversationTurn> conversation;
    private EpisodeExpected expected;

    public Episode() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSuite() {
        return suite;
    }

    public void setSuite(String suite) {
        this.suite = suite;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public InitialState getInitialState() {
        return initialState;
    }

    public void setInitialState(InitialState initialState) {
        this.initialState = initialState;
    }

    public List<ConversationTurn> getConversation() {
        return conversation;
    }

    public void setConversation(List<ConversationTurn> conversation) {
        this.conversation = conversation;
    }

    public EpisodeExpected getExpected() {
        return expected;
    }

    public void setExpected(EpisodeExpected expected) {
        this.expected = expected;
    }

    public static class InitialState {
        private Map<String, Object> user;
        private Map<String, Object> env;

        public InitialState() {
        }

        public Map<String, Object> getUser() {
            return user;
        }

        public void setUser(Map<String, Object> user) {
            this.user = user;
        }

        public Map<String, Object> getEnv() {
            return env;
        }

        public void setEnv(Map<String, Object> env) {
            this.env = env;
        }
    }

    public static class ConversationTurn {
        private String role;
        private String content;

        public ConversationTurn() {
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
