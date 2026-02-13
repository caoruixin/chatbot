package com.chatbot.service.tool;

import com.chatbot.enums.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool dispatcher - validates, checks risk, and executes tool calls.
 * Full implementation for AI agent use in Phase 2.
 * Phase 1: Used by ToolController for manual tool invocation.
 */
@Service
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final Map<String, ToolExecutor> executors;

    public ToolDispatcher(FaqService faqService,
                          PostQueryService postQueryService,
                          UserDataDeletionService userDataDeletionService) {
        this.executors = new HashMap<>();
        this.executors.put(ToolDefinition.FAQ_SEARCH.getName(), faqService);
        this.executors.put(ToolDefinition.POST_QUERY.getName(), postQueryService);
        this.executors.put(ToolDefinition.USER_DATA_DELETE.getName(), userDataDeletionService);
    }

    public ToolResult dispatch(ToolCall toolCall) {
        // 1. Find tool definition
        ToolDefinition def = ToolDefinition.fromName(toolCall.getToolName());
        if (def == null) {
            return ToolResult.error("未知工具: " + toolCall.getToolName());
        }

        // 2. Schema validation (basic parameter check)
        String validationError = validateParams(def, toolCall.getParams());
        if (validationError != null) {
            return ToolResult.schemaError(validationError);
        }

        // 3. Risk check
        if (def.getRisk() == RiskLevel.IRREVERSIBLE) {
            if (!toolCall.isUserConfirmed()) {
                return ToolResult.needsConfirmation("此操作不可逆，需要用户二次确认");
            }
        }

        // 4. Execute
        ToolExecutor executor = executors.get(def.getName());
        if (executor == null) {
            return ToolResult.error("工具未注册: " + def.getName());
        }

        try {
            long start = System.currentTimeMillis();
            ToolResult result = executor.execute(toolCall.getParams());
            long duration = System.currentTimeMillis() - start;
            log.info("Tool dispatched: tool={}, success={}, duration={}ms",
                    toolCall.getToolName(), result.isSuccess(), duration);
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed: tool={}, error={}",
                    toolCall.getToolName(), e.getMessage());
            return ToolResult.error("工具执行失败: " + e.getMessage());
        }
    }

    private String validateParams(ToolDefinition def, Map<String, Object> params) {
        if (params == null) {
            return "参数不能为空";
        }

        switch (def) {
            case FAQ_SEARCH -> {
                if (!params.containsKey("query") || params.get("query") == null) {
                    return "缺少必需参数: query";
                }
            }
            case POST_QUERY -> {
                if (!params.containsKey("username") || params.get("username") == null) {
                    return "缺少必需参数: username";
                }
            }
            case USER_DATA_DELETE -> {
                if (!params.containsKey("username") || params.get("username") == null) {
                    return "缺少必需参数: username";
                }
            }
        }
        return null;
    }
}
