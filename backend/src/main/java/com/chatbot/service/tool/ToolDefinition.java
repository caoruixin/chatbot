package com.chatbot.service.tool;

import com.chatbot.enums.RiskLevel;

public enum ToolDefinition {

    FAQ_SEARCH("faq_search", "搜索 FAQ 知识库", RiskLevel.READ),
    POST_QUERY("post_query", "查询用户帖子状态", RiskLevel.READ),
    USER_DATA_DELETE("user_data_delete", "删除用户数据", RiskLevel.IRREVERSIBLE);

    private final String name;
    private final String description;
    private final RiskLevel risk;

    ToolDefinition(String name, String description, RiskLevel risk) {
        this.name = name;
        this.description = description;
        this.risk = risk;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RiskLevel getRisk() {
        return risk;
    }

    public static ToolDefinition fromName(String name) {
        for (ToolDefinition def : values()) {
            if (def.name.equals(name)) {
                return def;
            }
        }
        return null;
    }
}
