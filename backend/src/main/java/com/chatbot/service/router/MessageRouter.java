package com.chatbot.service.router;

import com.chatbot.enums.SenderType;
import com.chatbot.enums.SessionStatus;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.SessionService;
import com.chatbot.service.agent.AgentCore;
import com.chatbot.service.human.HumanAgentService;
import com.chatbot.service.stream.GetStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    private final SessionService sessionService;
    private final HumanAgentService humanAgentService;
    private final AgentCore agentCore;
    private final GetStreamService getStreamService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final List<String> transferKeywords;

    public MessageRouter(SessionService sessionService,
                         HumanAgentService humanAgentService,
                         AgentCore agentCore,
                         GetStreamService getStreamService,
                         ConversationService conversationService,
                         MessageService messageService,
                         @Value("${chatbot.router.transfer-keywords:转人工}") String transferKeywordsStr) {
        this.sessionService = sessionService;
        this.humanAgentService = humanAgentService;
        this.agentCore = agentCore;
        this.getStreamService = getStreamService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.transferKeywords = Arrays.asList(transferKeywordsStr.split(","));
    }

    public void route(Session session, Message message) {
        String channelId = conversationService.getChannelId(session.getConversationId().toString());

        // 1. Check for transfer-to-human keywords
        if (containsTransferKeyword(message.getContent())) {
            log.info("Transfer to human requested: sessionId={}", session.getSessionId());

            sessionService.updateStatus(session.getSessionId(), SessionStatus.HUMAN_HANDLING);
            humanAgentService.assignAgent(session);

            // Send system message
            String systemMessage = "正在为您转接人工客服，请稍候...";
            getStreamService.sendMessage(channelId, "system", systemMessage);
            messageService.save(session.getConversationId(), session.getSessionId(),
                    SenderType.SYSTEM, "system", systemMessage);
            return;
        }

        // 2. Route based on session status
        SessionStatus status = session.getStatus();
        log.info("Routing message: sessionId={}, status={}", session.getSessionId(), status);

        switch (status) {
            case HUMAN_HANDLING -> {
                humanAgentService.forwardMessage(session, message);
            }
            case AI_HANDLING -> {
                agentCore.handleMessage(session, message);
            }
            default -> {
                log.warn("Unknown session status for routing: sessionId={}, status={}",
                        session.getSessionId(), status);
            }
        }
    }

    private boolean containsTransferKeyword(String content) {
        for (String keyword : transferKeywords) {
            if (content.contains(keyword.trim())) {
                return true;
            }
        }
        return false;
    }
}
