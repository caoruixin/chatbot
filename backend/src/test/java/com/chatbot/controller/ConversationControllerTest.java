package com.chatbot.controller;

import com.chatbot.enums.ConversationStatus;
import com.chatbot.enums.SessionStatus;
import com.chatbot.exception.ConversationNotFoundException;
import com.chatbot.model.Conversation;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
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

@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private SessionService sessionService;

    @Test
    void getConversation_exists_returns200() throws Exception {
        Conversation conv = new Conversation();
        conv.setConversationId(UUID.randomUUID());
        conv.setUserId("user1");
        conv.setStatus(ConversationStatus.ACTIVE);
        conv.setGetstreamChannelId("conv-channel");
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());

        when(conversationService.findByUserId("user1")).thenReturn(conv);

        mockMvc.perform(get("/api/conversations")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getConversation_notFound_returnsError() throws Exception {
        when(conversationService.findByUserId("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/conversations")
                        .param("userId", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getSessions_validConversation_returns200() throws Exception {
        String convId = UUID.randomUUID().toString();
        Conversation conv = new Conversation();
        conv.setConversationId(UUID.fromString(convId));
        when(conversationService.findById(convId)).thenReturn(conv);

        Session session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setConversationId(UUID.fromString(convId));
        session.setStatus(SessionStatus.AI_HANDLING);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActivityAt(LocalDateTime.now());
        when(sessionService.findByConversationId(convId)).thenReturn(List.of(session));

        mockMvc.perform(get("/api/conversations/" + convId + "/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getSessions_conversationNotFound_returns400() throws Exception {
        String convId = UUID.randomUUID().toString();
        when(conversationService.findById(convId))
                .thenThrow(new ConversationNotFoundException(convId));

        mockMvc.perform(get("/api/conversations/" + convId + "/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
