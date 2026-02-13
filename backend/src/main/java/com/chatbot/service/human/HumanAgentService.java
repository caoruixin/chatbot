package com.chatbot.service.human;

import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.SessionService;
import com.chatbot.service.stream.GetStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HumanAgentService {

    private static final Logger log = LoggerFactory.getLogger(HumanAgentService.class);

    private final GetStreamService getStreamService;
    private final ConversationService conversationService;
    private final SessionService sessionService;

    @Value("${chatbot.agent.default-id}")
    private String defaultAgentId;

    @Value("${chatbot.agent.default-name}")
    private String defaultAgentName;

    public HumanAgentService(GetStreamService getStreamService,
                             ConversationService conversationService,
                             SessionService sessionService) {
        this.getStreamService = getStreamService;
        this.conversationService = conversationService;
        this.sessionService = sessionService;
    }

    /**
     * Assign a human agent to the session.
     * Updates assigned_agent_id in database and adds agent to GetStream channel.
     * Note: session status is already updated to HUMAN_HANDLING by MessageRouter.
     */
    public void assignAgent(Session session) {
        log.info("Assigning agent to session: sessionId={}, agentId={}",
                session.getSessionId(), defaultAgentId);

        // Update assigned_agent_id in database
        sessionService.assignAgent(session.getSessionId(), defaultAgentId);

        // Upsert agent user and add to channel
        String channelId = conversationService.getChannelId(session.getConversationId().toString());
        try {
            getStreamService.upsertUser(defaultAgentId, defaultAgentName);
            getStreamService.addMember(channelId, defaultAgentId);
            log.info("Agent added to GetStream channel: channelId={}, agentId={}",
                    channelId, defaultAgentId);
        } catch (Exception e) {
            log.error("Failed to add agent to GetStream channel: {}", e.getMessage());
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
