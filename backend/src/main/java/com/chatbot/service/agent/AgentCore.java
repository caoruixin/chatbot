package com.chatbot.service.agent;

import com.chatbot.mapper.ConversationMapper;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.MessageService;
import com.chatbot.service.stream.GetStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent Core - the main AI agent processing loop.
 * Phase 1: Sends a placeholder message indicating AI features are under development.
 * Phase 2: Full implementation with IntentRouter, ReAct loop, and ResponseComposer.
 */
@Service
public class AgentCore {

    private static final Logger log = LoggerFactory.getLogger(AgentCore.class);

    private final MessageService messageService;
    private final GetStreamService getStreamService;
    private final ConversationMapper conversationMapper;

    @Value("${chatbot.ai.bot-id}")
    private String aiBotId;

    public AgentCore(MessageService messageService,
                     GetStreamService getStreamService,
                     ConversationMapper conversationMapper) {
        this.messageService = messageService;
        this.getStreamService = getStreamService;
        this.conversationMapper = conversationMapper;
    }

    /**
     * Handle a user message routed to the AI agent.
     * Phase 1: Send placeholder message.
     * Phase 2: Full AI processing with intent recognition, tool calls, and response generation.
     */
    public void handleMessage(Session session, Message message) {
        log.info("AI Agent handling message: sessionId={}, messageId={}",
                session.getSessionId(), message.getMessageId());

        // Phase 1: Send placeholder response
        String reply = "AI 客服功能即将上线，请发送'转人工'联系人工客服";

        // Save AI reply to database
        messageService.save(
                session.getConversationId(),
                session.getSessionId(),
                "AI_CHATBOT",
                aiBotId,
                reply
        );

        // Send via GetStream
        String channelId = getChannelId(session.getConversationId().toString());
        try {
            getStreamService.sendMessage(channelId, aiBotId, reply);
        } catch (Exception e) {
            log.error("Failed to send AI reply via GetStream: {}", e.getMessage());
        }
    }

    private String getChannelId(String conversationId) {
        Conversation conv = conversationMapper.findById(conversationId);
        if (conv != null) {
            return conv.getGetstreamChannelId();
        }
        return "conv-" + conversationId;
    }
}
