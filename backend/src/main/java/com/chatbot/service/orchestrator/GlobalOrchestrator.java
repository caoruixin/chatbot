package com.chatbot.service.orchestrator;

import com.chatbot.dto.request.InboundMessageRequest;
import com.chatbot.dto.response.InboundMessageResponse;
import com.chatbot.enums.SenderType;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.SessionService;
import com.chatbot.service.router.MessageRouter;
import com.chatbot.service.stream.GetStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GlobalOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GlobalOrchestrator.class);

    private final ConversationService conversationService;
    private final SessionService sessionService;
    private final MessageService messageService;
    private final GetStreamService getStreamService;
    private final MessageRouter messageRouter;

    public GlobalOrchestrator(ConversationService conversationService,
                              SessionService sessionService,
                              MessageService messageService,
                              GetStreamService getStreamService,
                              MessageRouter messageRouter) {
        this.conversationService = conversationService;
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.getStreamService = getStreamService;
        this.messageRouter = messageRouter;
    }

    public InboundMessageResponse handleInboundMessage(InboundMessageRequest request) {
        log.info("Handling inbound message: userId={}", request.getUserId());

        // 1. Find or create Conversation
        Conversation conv = conversationService.findOrCreate(request.getUserId());

        // 2. Find active Session or create new Session
        Session session = sessionService.findActiveOrCreate(conv.getConversationId());

        // 3. Save user message
        Message msg = messageService.save(
                conv.getConversationId(),
                session.getSessionId(),
                SenderType.USER,
                request.getUserId(),
                request.getContent()
        );

        // 4. Send user message via GetStream
        try {
            getStreamService.sendMessage(
                    conv.getGetstreamChannelId(),
                    request.getUserId(),
                    request.getContent()
            );
        } catch (Exception e) {
            log.error("Failed to send message via GetStream: {}", e.getMessage());
            // Don't fail the request - message is saved in DB
        }

        // 5. Route message
        messageRouter.route(session, msg);

        // 6. Update session last activity
        sessionService.updateLastActivity(session.getSessionId());

        return new InboundMessageResponse(
                conv.getConversationId().toString(),
                session.getSessionId().toString(),
                msg.getMessageId().toString()
        );
    }
}
