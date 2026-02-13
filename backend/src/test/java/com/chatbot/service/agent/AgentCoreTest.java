package com.chatbot.service.agent;

import com.chatbot.enums.SenderType;
import com.chatbot.enums.SessionStatus;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.stream.GetStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentCoreTest {

    @Mock
    private MessageService messageService;

    @Mock
    private GetStreamService getStreamService;

    @Mock
    private ConversationService conversationService;

    private AgentCore agentCore;

    private Session session;
    private Message userMessage;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        agentCore = new AgentCore(messageService, getStreamService, conversationService);
        ReflectionTestUtils.setField(agentCore, "aiBotId", "ai_bot");

        UUID convId = UUID.randomUUID();

        conversation = new Conversation();
        conversation.setConversationId(convId);
        conversation.setGetstreamChannelId("conv-" + convId);

        session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setConversationId(convId);
        session.setStatus(SessionStatus.AI_HANDLING);

        userMessage = new Message();
        userMessage.setMessageId(UUID.randomUUID());
        userMessage.setConversationId(convId);
        userMessage.setSessionId(session.getSessionId());
        userMessage.setContent("Hello");
    }

    @Test
    void handleMessage_normalFlow_savesAndSendsReply() {
        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), eq(SenderType.AI_CHATBOT), eq("ai_bot"), anyString()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        agentCore.handleMessage(session, userMessage);

        verify(messageService).save(
                eq(session.getConversationId()),
                eq(session.getSessionId()),
                eq(SenderType.AI_CHATBOT),
                eq("ai_bot"),
                anyString()
        );
        verify(getStreamService).sendMessage(
                eq(conversation.getGetstreamChannelId()),
                eq("ai_bot"),
                anyString()
        );
    }

    @Test
    void handleMessage_getStreamFails_doesNotThrow() {
        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), any(), any(), anyString()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());
        doThrow(new RuntimeException("GetStream error"))
                .when(getStreamService).sendMessage(anyString(), anyString(), anyString());

        // Should not throw
        agentCore.handleMessage(session, userMessage);

        verify(messageService).save(any(), any(), any(), any(), anyString());
    }

    @Test
    void handleMessage_conversationNotFoundInDb_usesFallbackChannelId() {
        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), any(), any(), anyString()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString()))
                .thenReturn("conv-" + session.getConversationId().toString());

        agentCore.handleMessage(session, userMessage);

        verify(getStreamService).sendMessage(
                eq("conv-" + session.getConversationId().toString()),
                eq("ai_bot"),
                anyString()
        );
    }
}
