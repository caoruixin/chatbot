package com.chatbot.controller;

import com.chatbot.dto.ApiResponse;
import com.chatbot.dto.response.ConversationResponse;
import com.chatbot.dto.response.SessionResponse;
import com.chatbot.model.Conversation;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final SessionService sessionService;

    public ConversationController(ConversationService conversationService,
                                  SessionService sessionService) {
        this.conversationService = conversationService;
        this.sessionService = sessionService;
    }

    @GetMapping
    public ApiResponse<ConversationResponse> getConversation(@RequestParam String userId) {
        Conversation conv = conversationService.findByUserId(userId);
        if (conv == null) {
            return ApiResponse.error("No conversation found for user: " + userId);
        }
        return ApiResponse.success(toConversationResponse(conv));
    }

    @GetMapping("/{conversationId}/sessions")
    public ApiResponse<List<SessionResponse>> getSessions(@PathVariable String conversationId) {
        // Verify conversation exists
        conversationService.findById(conversationId);

        // Find all sessions for this conversation
        List<Session> sessions = sessionService.findByConversationId(conversationId);
        List<SessionResponse> responseList = sessions.stream()
                .map(this::toSessionResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responseList);
    }

    private ConversationResponse toConversationResponse(Conversation conv) {
        return new ConversationResponse(
                conv.getConversationId().toString(),
                conv.getUserId(),
                conv.getStatus(),
                conv.getGetstreamChannelId(),
                conv.getCreatedAt() != null ? conv.getCreatedAt().toString() : null,
                conv.getUpdatedAt() != null ? conv.getUpdatedAt().toString() : null
        );
    }

    private SessionResponse toSessionResponse(Session session) {
        return new SessionResponse(
                session.getSessionId().toString(),
                session.getConversationId().toString(),
                session.getStatus(),
                session.getAssignedAgentId(),
                session.getCreatedAt() != null ? session.getCreatedAt().toString() : null,
                session.getLastActivityAt() != null ? session.getLastActivityAt().toString() : null
        );
    }
}
