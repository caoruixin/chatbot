package com.chatbot.service.agent;

import com.chatbot.enums.SenderType;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.llm.KimiMessage;
import com.chatbot.service.stream.GetStreamService;
import com.chatbot.service.tool.ToolCall;
import com.chatbot.service.tool.ToolDispatcher;
import com.chatbot.service.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Agent Core - the main AI agent processing loop.
 * Full implementation with IntentRouter, ReAct loop (bounded), and ResponseComposer.
 * Runs asynchronously on the aiTaskExecutor thread pool so it doesn't block the API thread.
 */
@Service
public class AgentCore {

    private static final Logger log = LoggerFactory.getLogger(AgentCore.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Keywords that count as user confirmation for pending operations */
    private static final Set<String> CONFIRMATION_KEYWORDS = Set.of(
            "确认删除", "确认", "确定", "是", "是的", "好的", "好", "同意"
    );

    private final MessageService messageService;
    private final GetStreamService getStreamService;
    private final ConversationService conversationService;
    private final IntentRouter intentRouter;
    private final ReactPlanner reactPlanner;
    private final ResponseComposer responseComposer;
    private final ToolDispatcher toolDispatcher;
    private final String aiBotId;
    private final int maxReactRounds;
    private final double confidenceThreshold;

    public AgentCore(MessageService messageService,
                     GetStreamService getStreamService,
                     ConversationService conversationService,
                     IntentRouter intentRouter,
                     ReactPlanner reactPlanner,
                     ResponseComposer responseComposer,
                     ToolDispatcher toolDispatcher,
                     @Value("${chatbot.ai.bot-id}") String aiBotId,
                     @Value("${chatbot.ai.max-react-rounds:3}") int maxReactRounds,
                     @Value("${chatbot.ai.confidence-threshold:0.7}") double confidenceThreshold) {
        this.messageService = messageService;
        this.getStreamService = getStreamService;
        this.conversationService = conversationService;
        this.intentRouter = intentRouter;
        this.reactPlanner = reactPlanner;
        this.responseComposer = responseComposer;
        this.toolDispatcher = toolDispatcher;
        this.aiBotId = aiBotId;
        this.maxReactRounds = maxReactRounds;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Handle a user message routed to the AI agent.
     * Full AI processing: intent recognition -> ReAct loop -> response composition -> send reply.
     * Runs on the aiTaskExecutor thread pool.
     */
    @Async("aiTaskExecutor")
    public void handleMessage(Session session, Message message) {
        log.info("AI Agent handling message: sessionId={}, messageId={}",
                session.getSessionId(), message.getMessageId());

        try {
            String userMessage = message.getContent();

            // 1. Get conversation history from current session (excluding the current message to avoid duplication)
            List<KimiMessage> history = buildSessionHistory(session, message.getMessageId());

            // 2. Check if there's a pending confirmation from a previous AI message
            ToolCall confirmedToolCall = checkPendingConfirmation(session, userMessage);
            if (confirmedToolCall != null) {
                log.info("User confirmed pending operation: tool={}", confirmedToolCall.getToolName());
                ToolResult toolResult = toolDispatcher.dispatch(confirmedToolCall);
                String reply;
                if (toolResult.isSuccess()) {
                    reply = "您的数据删除请求已提交，预计 24 小时内处理完毕。如有疑问请联系人工客服。";
                } else {
                    reply = "数据删除请求处理失败，请稍后重试或联系人工客服。";
                }
                sendReply(session, reply, null);
                return;
            }

            // 3. Intent recognition
            IntentResult intent = intentRouter.recognize(userMessage, history);
            log.info("Intent result: sessionId={}, intent={}, confidence={}, risk={}",
                    session.getSessionId(), intent.getIntent(), intent.getConfidence(), intent.getRisk());

            // 4. Check confidence threshold
            if (intent.getConfidence() < confidenceThreshold) {
                String clarification = "抱歉，我不太确定您的意思。能否请您再详细描述一下您的问题？";
                sendReply(session, clarification, null);
                return;
            }

            // 5. ReAct loop (max rounds from config)
            ToolResult toolResult = null;
            for (int round = 0; round < maxReactRounds; round++) {
                ToolCall toolCall = reactPlanner.plan(intent, userMessage, history, toolResult);
                if (toolCall == null) {
                    log.info("No tool call planned at round {}, exiting ReAct loop", round);
                    break;
                }

                log.info("ReAct round {}: tool={}", round, toolCall.getToolName());
                toolResult = toolDispatcher.dispatch(toolCall);

                if (toolResult.isSuccess()) {
                    log.info("Tool call succeeded at round {}", round);
                    break;
                }

                if (toolResult.needsConfirmation()) {
                    log.info("Tool needs user confirmation at round {}", round);
                    // Store pending confirmation metadata for the confirmation reply
                    String confirmationMeta = buildConfirmationMetadata(
                            toolCall.getToolName(), toolCall.getParams());
                    String reply = composeReply(intent, userMessage, toolResult, history);
                    sendReply(session, reply, confirmationMeta);
                    return;
                }

                if (toolResult.isRetryable()) {
                    log.warn("Tool call returned retryable error at round {}: {}",
                            round, toolResult.getError());
                } else {
                    log.warn("Tool call returned non-retryable error at round {}: {}",
                            round, toolResult.getError());
                    break;
                }
            }

            // 6. Compose final reply
            String reply = composeReply(intent, userMessage, toolResult, history);

            // 7. Send reply
            sendReply(session, reply, null);
        } catch (Exception e) {
            log.error("AI Agent processing failed: sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());

            // Fallback: send error message to user
            String fallbackReply = "抱歉，AI 助手暂时遇到问题。您可以发送\"转人工\"联系人工客服。";
            sendReply(session, fallbackReply, null);
        }
    }

    /**
     * Build conversation history from current session messages for LLM context.
     * Excludes the current message (identified by excludeMessageId) to avoid duplication,
     * since IntentRouter.recognize() adds the current user message separately.
     */
    private List<KimiMessage> buildSessionHistory(Session session, UUID excludeMessageId) {
        List<KimiMessage> history = new ArrayList<>();
        try {
            List<Message> sessionMessages = messageService.findBySessionId(
                    session.getSessionId().toString());

            for (Message msg : sessionMessages) {
                // Skip the current message to avoid duplication in LLM context
                if (msg.getMessageId().equals(excludeMessageId)) {
                    continue;
                }
                String role;
                switch (msg.getSenderType()) {
                    case USER -> role = "user";
                    case AI_CHATBOT -> role = "assistant";
                    case HUMAN_AGENT -> role = "assistant";
                    default -> role = "user";
                }
                history.add(new KimiMessage(role, msg.getContent()));
            }
        } catch (Exception e) {
            log.warn("Failed to load session history: sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());
        }
        return history;
    }

    /**
     * Check if there's a pending confirmation from the last AI message.
     * If the user's message matches confirmation keywords, return a confirmed ToolCall.
     * Returns null if no pending confirmation or user did not confirm.
     */
    @SuppressWarnings("unchecked")
    private ToolCall checkPendingConfirmation(Session session, String userMessage) {
        try {
            String trimmedMsg = userMessage.strip();
            if (!CONFIRMATION_KEYWORDS.contains(trimmedMsg)) {
                return null;
            }

            // Find the last AI message in this session
            List<Message> sessionMessages = messageService.findBySessionId(
                    session.getSessionId().toString());
            Message lastAiMessage = null;
            for (int i = sessionMessages.size() - 1; i >= 0; i--) {
                if (sessionMessages.get(i).getSenderType() == SenderType.AI_CHATBOT) {
                    lastAiMessage = sessionMessages.get(i);
                    break;
                }
            }

            if (lastAiMessage == null || lastAiMessage.getMetadataJson() == null) {
                return null;
            }

            Map<String, Object> metadata = objectMapper.readValue(
                    lastAiMessage.getMetadataJson(), new TypeReference<>() {});
            if (!Boolean.TRUE.equals(metadata.get("pendingConfirmation"))) {
                return null;
            }

            String toolName = (String) metadata.get("toolName");
            Map<String, Object> toolParams = (Map<String, Object>) metadata.get("toolParams");
            if (toolName == null || toolParams == null) {
                return null;
            }

            log.info("Pending confirmation found: tool={}, user confirmed with '{}'", toolName, trimmedMsg);
            return new ToolCall(toolName, toolParams, true);
        } catch (Exception e) {
            log.warn("Failed to check pending confirmation: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build JSON metadata for a pending confirmation AI message.
     */
    private String buildConfirmationMetadata(String toolName, Map<String, Object> toolParams) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("pendingConfirmation", true);
            metadata.put("toolName", toolName);
            metadata.put("toolParams", toolParams);
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to build confirmation metadata: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compose the final reply based on intent risk level and tool result.
     * High-risk (critical) intents use template-based responses.
     * Low-risk intents use LLM-generated responses with evidence.
     */
    private String composeReply(IntentResult intent, String userMessage,
                                ToolResult toolResult, List<KimiMessage> history) {
        String intentType = intent.getIntent();

        // GENERAL_CHAT with no tool call: generate a direct LLM response
        if ("GENERAL_CHAT".equals(intentType)) {
            return responseComposer.composeWithEvidence(userMessage, intent, toolResult, history);
        }

        // Critical risk: use template-based responses only
        if ("critical".equals(intent.getRisk())) {
            return responseComposer.composeFromTemplate(intent, toolResult);
        }

        // Low-risk intents that need a follow-up question (no tool call was made because params are missing)
        if (toolResult == null) {
            if ("POST_QUERY".equals(intentType)) {
                return "请问您要查询哪位用户的帖子？请提供用户名，例如：\"帮我查一下user_alice的帖子\"。";
            }
            return responseComposer.composeWithEvidence(userMessage, intent, toolResult, history);
        }

        // Low-risk with tool result: use LLM to format evidence into reply
        return responseComposer.composeWithEvidence(userMessage, intent, toolResult, history);
    }

    /**
     * Save the AI reply to the database and send it via GetStream.
     *
     * @param session      the current session
     * @param reply        the reply text
     * @param metadataJson optional metadata JSON (e.g. pending confirmation state), can be null
     */
    private void sendReply(Session session, String reply, String metadataJson) {
        // Save AI reply to database
        messageService.save(
                session.getConversationId(),
                session.getSessionId(),
                SenderType.AI_CHATBOT,
                aiBotId,
                reply,
                metadataJson
        );

        // Send via GetStream
        String channelId = conversationService.getChannelId(session.getConversationId().toString());
        try {
            getStreamService.sendMessage(channelId, aiBotId, reply);
        } catch (Exception e) {
            log.error("Failed to send AI reply via GetStream: channelId={}, error={}",
                    channelId, e.getMessage());
        }
    }
}
