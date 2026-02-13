package com.chatbot.service.human;

import com.chatbot.mapper.ConversationMapper;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.stream.GetStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HumanAgentService {

    private static final Logger log = LoggerFactory.getLogger(HumanAgentService.class);

    private final GetStreamService getStreamService;
    private final ConversationMapper conversationMapper;

    @Value("${chatbot.agent.default-id}")
    private String defaultAgentId;

    @Value("${chatbot.agent.default-name}")
    private String defaultAgentName;

    public HumanAgentService(GetStreamService getStreamService,
                             ConversationMapper conversationMapper) {
        this.getStreamService = getStreamService;
        this.conversationMapper = conversationMapper;
    }

    /**
     * Assign a human agent to the session.
     * Adds the agent to the GetStream channel.
     * Note: session status is already updated to HUMAN_HANDLING by MessageRouter.
     */
    public void assignAgent(Session session) {
        log.info("Assigning agent to session: sessionId={}, agentId={}",
                session.getSessionId(), defaultAgentId);

        // Upsert agent user and add to channel
        Conversation conv = conversationMapper.findById(session.getConversationId().toString());
        if (conv != null) {
            try {
                getStreamService.upsertUser(defaultAgentId, defaultAgentName);
                getStreamService.addMember(conv.getGetstreamChannelId(), defaultAgentId);
                log.info("Agent added to GetStream channel: channelId={}, agentId={}",
                        conv.getGetstreamChannelId(), defaultAgentId);
            } catch (Exception e) {
                log.error("Failed to add agent to GetStream channel: {}", e.getMessage());
            }
        }
    }

    /**
     * Forward a message to the human agent.
     * In Phase 1, messages are already in the GetStream channel,
     * so no additional forwarding is needed.
     */
    public void forwardMessage(Session session, Message message) {
        // No-op for Phase 1: message is already visible in the GetStream channel
        // The human agent sees new messages via GetStream WebSocket
        log.info("Message forwarded to human agent (no-op): sessionId={}, messageId={}",
                session.getSessionId(), message.getMessageId());
    }
}
