package com.chatbot.eval.adapter;

import com.chatbot.enums.RiskLevel;
import com.chatbot.service.tool.ToolCall;
import com.chatbot.service.tool.ToolDefinition;
import com.chatbot.service.tool.ToolExecutor;
import com.chatbot.service.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Side-effect-aware tool dispatcher for eval.
 * Wraps real ToolExecutor instances but auto-confirms irreversible operations
 * when configured to do so.
 */
public class EvalToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EvalToolDispatcher.class);

    private final Map<String, ToolExecutor> executors;
    private final boolean autoConfirmIrreversible;

    public EvalToolDispatcher(Map<String, ToolExecutor> executors, boolean autoConfirmIrreversible) {
        this.executors = executors;
        this.autoConfirmIrreversible = autoConfirmIrreversible;
    }

    public ToolResult dispatch(ToolCall toolCall) {
        // 1. Find tool definition
        ToolDefinition def = ToolDefinition.fromName(toolCall.getToolName());
        if (def == null) {
            return ToolResult.error("未知工具: " + toolCall.getToolName());
        }

        // 2. Schema validation
        String validationError = validateParams(def, toolCall.getParams());
        if (validationError != null) {
            return ToolResult.schemaError(validationError);
        }

        // 3. Risk check: auto-confirm if configured
        if (def.getRisk() == RiskLevel.IRREVERSIBLE) {
            if (!toolCall.isUserConfirmed() && !autoConfirmIrreversible) {
                return ToolResult.needsConfirmation("此操作不可逆，需要用户二次确认");
            }
            // If autoConfirmIrreversible is true, proceed without confirmation
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
            log.debug("Eval tool dispatched: tool={}, success={}, duration={}ms",
                    toolCall.getToolName(), result.isSuccess(), duration);
            return result;
        } catch (Exception e) {
            log.error("Eval tool execution failed: tool={}, error={}",
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
