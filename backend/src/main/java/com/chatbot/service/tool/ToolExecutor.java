package com.chatbot.service.tool;

import java.util.Map;

public interface ToolExecutor {

    ToolResult execute(Map<String, Object> params);
}
