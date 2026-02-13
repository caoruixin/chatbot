package com.chatbot.controller;

import com.chatbot.dto.request.InboundMessageRequest;
import com.chatbot.dto.response.InboundMessageResponse;
import com.chatbot.enums.SenderType;
import com.chatbot.exception.SessionNotFoundException;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.SessionService;
import com.chatbot.service.orchestrator.GlobalOrchestrator;
import com.chatbot.service.stream.GetStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GlobalOrchestrator orchestrator;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private GetStreamService getStreamService;

    @Test
    void inbound_validRequest_returns200() throws Exception {
        InboundMessageResponse response = new InboundMessageResponse("conv-1", "sess-1", "msg-1");
        when(orchestrator.handleInboundMessage(any(InboundMessageRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(new InboundMessageRequest("user1", "Hello"));

        mockMvc.perform(post("/api/messages/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.data.messageId").value("msg-1"));
    }

    @Test
    void agentReply_validRequest_returns200() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Session session = new Session();
        session.setSessionId(sessionId);
        session.setConversationId(convId);
        when(sessionService.findById(anyString())).thenReturn(session);

        Conversation conv = new Conversation();
        conv.setConversationId(convId);
        conv.setGetstreamChannelId("conv-channel");
        when(conversationService.findById(anyString())).thenReturn(conv);

        Message msg = new Message();
        msg.setMessageId(UUID.randomUUID());
        msg.setConversationId(convId);
        msg.setSessionId(sessionId);
        when(messageService.save(any(), any(), any(), any(), any())).thenReturn(msg);

        String body = "{\"sessionId\":\"" + sessionId + "\",\"agentId\":\"agent1\",\"content\":\"Hi\"}";

        mockMvc.perform(post("/api/messages/agent-reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void agentReply_sessionNotFound_returns400() throws Exception {
        when(sessionService.findById(anyString()))
                .thenThrow(new SessionNotFoundException("nonexistent"));

        String body = "{\"sessionId\":\"nonexistent\",\"agentId\":\"agent1\",\"content\":\"Hi\"}";

        mockMvc.perform(post("/api/messages/agent-reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getMessages_withConversationId_returns200() throws Exception {
        Message msg = new Message();
        msg.setMessageId(UUID.randomUUID());
        msg.setConversationId(UUID.randomUUID());
        msg.setSessionId(UUID.randomUUID());
        msg.setSenderType(SenderType.USER);
        msg.setSenderId("user1");
        msg.setContent("Hello");
        msg.setCreatedAt(LocalDateTime.now());

        when(messageService.findByConversationId("conv1")).thenReturn(List.of(msg));

        mockMvc.perform(get("/api/messages")
                        .param("conversationId", "conv1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].content").value("Hello"));
    }

    @Test
    void getMessages_noParams_returnsError() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getMessages_withSessionId_returns200() throws Exception {
        when(messageService.findBySessionId("sess1")).thenReturn(List.of());

        mockMvc.perform(get("/api/messages")
                        .param("sessionId", "sess1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMessages_withBothIds_usesCombinedQuery() throws Exception {
        when(messageService.findByConversationAndSession("conv1", "sess1"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/messages")
                        .param("conversationId", "conv1")
                        .param("sessionId", "sess1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
