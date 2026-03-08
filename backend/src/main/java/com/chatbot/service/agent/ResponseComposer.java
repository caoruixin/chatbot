package com.chatbot.service.agent;

import com.chatbot.config.PromptConfig;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiMessage;
import com.chatbot.service.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Response Composer - generates user-facing responses.
 * Uses template-based responses for high-risk (critical) operations like data deletion,
 * and LLM-generated responses for low-risk operations like post queries and FAQ searches.
 */
@Service
public class ResponseComposer {

    private static final Logger log = LoggerFactory.getLogger(ResponseComposer.class);

    private final KimiClient kimiClient;
    private final PromptConfig promptConfig;

    public ResponseComposer(KimiClient kimiClient, PromptConfig promptConfig) {
        this.kimiClient = kimiClient;
        this.promptConfig = promptConfig;
    }

    public String getSystemPrompt() {
        return promptConfig.getResponseComposerPrompt();
    }

    /**
     * Compose a template-based response for high-risk (critical) operations.
     * Used for DATA_DELETION and other irreversible operations.
     * Does NOT call the LLM - uses fixed templates only.
     */
    public String composeFromTemplate(IntentResult intent, ToolResult toolResult) {
        String intentType = intent.getIntent();
        log.info("Composing template response: intent={}, toolSuccess={}",
                intentType, toolResult != null && toolResult.isSuccess());

        if ("DATA_DELETION".equals(intentType)) {
            if (toolResult == null) {
                return "请提供您的用户名，以便我们处理您的数据删除请求。";
            }
            if (toolResult.needsConfirmation()) {
                String username = intent.getExtractedParams().get("username");
                return String.format("您确定要删除用户 %s 的所有数据吗？此操作不可逆。请回复\"确认删除\"以继续。", username);
            }
            if (toolResult.isSuccess()) {
                return "您的数据删除请求已提交，预计 24 小时内处理完毕。如有疑问请联系人工客服。";
            }
            return "数据删除请求处理失败，请稍后重试或联系人工客服。";
        }

        // Fallback for other critical intents
        if (toolResult != null && toolResult.isSuccess()) {
            return "操作已完成。如有其他问题，请随时咨询。";
        }
        return "操作处理失败，请稍后重试或发送\"转人工\"联系人工客服。";
    }

    /**
     * Compose a response using LLM with tool result evidence.
     * Used for POST_QUERY and KB_QUESTION intents (low-risk).
     * Calls Kimi LLM to format tool results into a friendly reply.
     */
    public String composeWithEvidence(String userMessage, IntentResult intent,
                                      ToolResult toolResult, List<KimiMessage> history) {
        log.info("Composing evidence-based response: intent={}, toolSuccess={}",
                intent.getIntent(), toolResult != null && toolResult.isSuccess());

        try {
            List<KimiMessage> messages = new ArrayList<>();

            // Include conversation history for context
            if (history != null) {
                messages.addAll(history);
            }

            // Add user message
            messages.add(new KimiMessage("user", userMessage));

            // Add tool result as assistant context
            String toolContext = buildToolContext(intent, toolResult);
            messages.add(new KimiMessage("assistant",
                    "我查询了系统，以下是查询结果：\n" + toolContext + "\n请根据以上结果回复用户。"));

            KimiChatResponse response = kimiClient.chatCompletion(
                    promptConfig.getResponseComposerPrompt(), messages, 0.7);

            String content = response.getContent();
            if (content != null && !content.isBlank()) {
                return content;
            }

            log.warn("Empty response from evidence composition LLM, using fallback");
            return composeFallback(intent, toolResult);
        } catch (Exception e) {
            log.error("Evidence-based response composition failed: {}", e.getMessage());
            return composeFallback(intent, toolResult);
        }
    }

    private String buildToolContext(IntentResult intent, ToolResult toolResult) {
        if (toolResult == null) {
            return "未执行任何工具查询。";
        }
        if (!toolResult.isSuccess()) {
            return "查询失败：" + (toolResult.getError() != null ? toolResult.getError() : "未知错误");
        }
        return toolResult.toJson();
    }

    private String composeFallback(IntentResult intent, ToolResult toolResult) {
        if (toolResult != null && toolResult.isSuccess()) {
            return "已查询到相关信息。如需更多帮助，请随时咨询或发送\"转人工\"联系人工客服。";
        }
        return "抱歉，暂时无法获取相关信息。您可以发送\"转人工\"联系人工客服获取帮助。";
    }
}
