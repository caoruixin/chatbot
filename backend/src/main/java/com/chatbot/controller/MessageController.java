package com.chatbot.controller;

import com.chatbot.dto.ApiResponse;
import com.chatbot.dto.request.AgentReplyRequest;
import com.chatbot.dto.request.InboundMessageRequest;
import com.chatbot.dto.response.InboundMessageResponse;
import com.chatbot.dto.response.MessageResponse;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.SessionService;
import com.chatbot.service.orchestrator.GlobalOrchestrator;
import com.chatbot.service.stream.GetStreamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final GlobalOrchestrator orchestrator;
    private final MessageService messageService;
    private final SessionService sessionService;
    private final ConversationService conversationService;
    private final GetStreamService getStreamService;

    public MessageController(GlobalOrchestrator orchestrator,
                             MessageService messageService,
                             SessionService sessionService,
                             ConversationService conversationService,
                             GetStreamService getStreamService) {
        this.orchestrator = orchestrator;
        this.messageService = messageService;
        this.sessionService = sessionService;
        this.conversationService = conversationService;
        this.getStreamService = getStreamService;
    }

    @PostMapping("/inbound")
    public ApiResponse<InboundMessageResponse> inbound(@RequestBody InboundMessageRequest request) {
        InboundMessageResponse response = orchestrator.handleInboundMessage(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/agent-reply")
    public ApiResponse<InboundMessageResponse> agentReply(@RequestBody AgentReplyRequest request) {
        Session session = sessionService.findById(request.getSessionId());

        // Save agent message
        Message msg = messageService.save(
                session.getConversationId(),
                session.getSessionId(),
                "HUMAN_AGENT",
                request.getAgentId(),
                request.getContent()
        );

        // Send via GetStream
        var conv = conversationService.findById(session.getConversationId().toString());
        getStreamService.sendMessage(
                conv.getGetstreamChannelId(),
                request.getAgentId(),
                request.getContent()
        );

        // Update session activity
        sessionService.updateLastActivity(session.getSessionId());

        InboundMessageResponse response = new InboundMessageResponse(
                session.getConversationId().toString(),
                session.getSessionId().toString(),
                msg.getMessageId().toString()
        );
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<MessageResponse>> getMessages(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String sessionId) {

        List<Message> messages;
        if (conversationId != null && sessionId != null) {
            messages = messageService.findByConversationAndSession(conversationId, sessionId);
        } else if (conversationId != null) {
            messages = messageService.findByConversationId(conversationId);
        } else if (sessionId != null) {
            messages = messageService.findBySessionId(sessionId);
        } else {
            return ApiResponse.error("conversationId or sessionId is required");
        }

        List<MessageResponse> responseList = messages.stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(responseList);
    }

    private MessageResponse toMessageResponse(Message msg) {
        return new MessageResponse(
                msg.getMessageId().toString(),
                msg.getConversationId().toString(),
                msg.getSessionId().toString(),
                msg.getSenderType(),
                msg.getSenderId(),
                msg.getContent(),
                msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null
        );
    }
}
