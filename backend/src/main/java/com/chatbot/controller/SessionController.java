package com.chatbot.controller;

import com.chatbot.dto.ApiResponse;
import com.chatbot.dto.response.SessionResponse;
import com.chatbot.model.Session;
import com.chatbot.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionResponse> getSession(@PathVariable String sessionId) {
        Session session = sessionService.findById(sessionId);
        return ApiResponse.success(toSessionResponse(session));
    }

    @GetMapping("/active")
    public ApiResponse<List<SessionResponse>> getActiveSessions(@RequestParam String agentId) {
        List<Session> sessions = sessionService.findActiveByAgentId(agentId);
        List<SessionResponse> responseList = sessions.stream()
                .map(this::toSessionResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responseList);
    }

    private SessionResponse toSessionResponse(Session session) {
        return new SessionResponse(
                session.getSessionId().toString(),
                session.getConversationId().toString(),
                session.getStatus().name(),
                session.getAssignedAgentId(),
                session.getCreatedAt() != null ? session.getCreatedAt().toString() : null,
                session.getLastActivityAt() != null ? session.getLastActivityAt().toString() : null
        );
    }
}
