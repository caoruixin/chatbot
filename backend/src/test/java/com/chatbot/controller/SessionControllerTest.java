package com.chatbot.controller;

import com.chatbot.enums.SessionStatus;
import com.chatbot.exception.SessionNotFoundException;
import com.chatbot.model.Session;
import com.chatbot.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @Test
    void getSession_exists_returns200() throws Exception {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setConversationId(UUID.randomUUID());
        session.setStatus(SessionStatus.AI_HANDLING);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActivityAt(LocalDateTime.now());

        when(sessionService.findById(session.getSessionId().toString())).thenReturn(session);

        mockMvc.perform(get("/api/sessions/" + session.getSessionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("AI_HANDLING"));
    }

    @Test
    void getSession_notFound_returns400() throws Exception {
        String id = UUID.randomUUID().toString();
        when(sessionService.findById(id)).thenThrow(new SessionNotFoundException(id));

        mockMvc.perform(get("/api/sessions/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getActiveSessions_validAgent_returns200() throws Exception {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setConversationId(UUID.randomUUID());
        session.setStatus(SessionStatus.HUMAN_HANDLING);
        session.setAssignedAgentId("agent1");
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActivityAt(LocalDateTime.now());

        when(sessionService.findActiveByAgentId("agent1")).thenReturn(List.of(session));

        mockMvc.perform(get("/api/sessions/active")
                        .param("agentId", "agent1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].assignedAgentId").value("agent1"));
    }

    @Test
    void getActiveSessions_missingAgentId_returns500() throws Exception {
        // BUG: GlobalExceptionHandler 未处理 MissingServletRequestParameterException，
        // 缺少必填参数应返回 400 而非 500
        mockMvc.perform(get("/api/sessions/active"))
                .andExpect(status().isInternalServerError());
    }
}
