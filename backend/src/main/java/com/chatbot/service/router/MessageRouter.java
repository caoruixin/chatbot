package com.chatbot.service.router;

import com.chatbot.mapper.ConversationMapper;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.MessageService;
import com.chatbot.service.SessionService;
import com.chatbot.service.agent.AgentCore;
import com.chatbot.service.human.HumanAgentService;
import com.chatbot.service.stream.GetStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    private final SessionService sessionService;
    private final HumanAgentService humanAgentService;
    private final AgentCore agentCore;
    private final GetStreamService getStreamService;
    private final ConversationMapper conversationMapper;
    private final MessageService messageService;

    public MessageRouter(SessionService sessionService,
                         HumanAgentService humanAgentService,
                         AgentCore agentCore,
                         GetStreamService getStreamService,
                         ConversationMapper conversationMapper,
                         MessageService messageService) {
        this.sessionService = sessionService;
        this.humanAgentService = humanAgentService;
        this.agentCore = agentCore;
        this.getStreamService = getStreamService;
        this.conversationMapper = conversationMapper;
        this.messageService = messageService;
    }

    public void route(Session session, Message message) {
        String channelId = getChannelId(session.getConversationId().toString());

        // 1. Check for "转人工" keyword
        if (message.getContent().contains("转人工")) {
            log.info("Transfer to human requested: sessionId={}", session.getSessionId());

            sessionService.updateStatus(session.getSessionId(), "HUMAN_HANDLING");
            humanAgentService.assignAgent(session);

            // Send system message
            String systemMessage = "正在为您转接人工客服，请稍候...";
            getStreamService.sendMessage(channelId, "ai_bot", systemMessage);
            messageService.save(session.getConversationId(), session.getSessionId(),
                    "AI_CHATBOT", "ai_bot", systemMessage);
            return;
        }

        // 2. Route based on session status
        String status = session.getStatus();
        log.info("Routing message: sessionId={}, status={}", session.getSessionId(), status);

        switch (status) {
            case "HUMAN_HANDLING" -> {
                humanAgentService.forwardMessage(session, message);
            }
            case "AI_HANDLING" -> {
                agentCore.handleMessage(session, message);
            }
            default -> {
                log.warn("Unknown session status for routing: sessionId={}, status={}",
                        session.getSessionId(), status);
            }
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
