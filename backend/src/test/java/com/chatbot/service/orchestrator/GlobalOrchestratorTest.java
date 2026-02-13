package com.chatbot.service.orchestrator;

import com.chatbot.dto.request.InboundMessageRequest;
import com.chatbot.dto.response.InboundMessageResponse;
import com.chatbot.enums.ConversationStatus;
import com.chatbot.enums.SenderType;
import com.chatbot.enums.SessionStatus;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.SessionService;
import com.chatbot.service.router.MessageRouter;
import com.chatbot.service.stream.GetStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalOrchestratorTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private SessionService sessionService;

    @Mock
    private MessageService messageService;

    @Mock
    private GetStreamService getStreamService;

    @Mock
    private MessageRouter messageRouter;

    @InjectMocks
    private GlobalOrchestrator orchestrator;

    private InboundMessageRequest request;
    private Conversation conversation;
    private Session session;
    private Message message;

    @BeforeEach
    void setUp() {
        request = new InboundMessageRequest("user1", "Hello");

        conversation = new Conversation();
        conversation.setConversationId(UUID.randomUUID());
        conversation.setUserId("user1");
        conversation.setGetstreamChannelId("conv-" + conversation.getConversationId());
        conversation.setStatus(ConversationStatus.ACTIVE);

        session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setConversationId(conversation.getConversationId());
        session.setStatus(SessionStatus.AI_HANDLING);

        message = new Message();
        message.setMessageId(UUID.randomUUID());
        message.setConversationId(conversation.getConversationId());
        message.setSessionId(session.getSessionId());
        message.setSenderType(SenderType.USER);
        message.setSenderId("user1");
        message.setContent("Hello");
    }

    @Test
    void handleInboundMessage_normalFlow_returnsResponse() {
        when(conversationService.findOrCreate("user1")).thenReturn(conversation);
        when(sessionService.findActiveOrCreate(conversation.getConversationId())).thenReturn(session);
        when(messageService.save(
                eq(conversation.getConversationId()),
                eq(session.getSessionId()),
                eq(SenderType.USER),
                eq("user1"),
                eq("Hello")
        )).thenReturn(message);

        InboundMessageResponse response = orchestrator.handleInboundMessage(request);

        assertNotNull(response);
        assertEquals(conversation.getConversationId().toString(), response.getConversationId());
        assertEquals(session.getSessionId().toString(), response.getSessionId());
        assertEquals(message.getMessageId().toString(), response.getMessageId());

        verify(conversationService).findOrCreate("user1");
        verify(sessionService).findActiveOrCreate(conversation.getConversationId());
        verify(messageService).save(any(), any(), eq(SenderType.USER), eq("user1"), eq("Hello"));
        verify(getStreamService).sendMessage(
                eq(conversation.getGetstreamChannelId()),
                eq("user1"),
                eq("Hello")
        );
        verify(messageRouter).route(session, message);
        verify(sessionService).updateLastActivity(session.getSessionId());
    }

    @Test
    void handleInboundMessage_getStreamFails_stillReturnsResponse() {
        when(conversationService.findOrCreate("user1")).thenReturn(conversation);
        when(sessionService.findActiveOrCreate(conversation.getConversationId())).thenReturn(session);
        when(messageService.save(any(), any(), any(), any(), any())).thenReturn(message);
        doThrow(new RuntimeException("GetStream error"))
                .when(getStreamService).sendMessage(anyString(), anyString(), anyString());

        InboundMessageResponse response = orchestrator.handleInboundMessage(request);

        assertNotNull(response);
        assertEquals(conversation.getConversationId().toString(), response.getConversationId());
        verify(messageRouter).route(session, message);
        verify(sessionService).updateLastActivity(session.getSessionId());
    }

    @Test
    void handleInboundMessage_existingConversation_reusesConversation() {
        when(conversationService.findOrCreate("user1")).thenReturn(conversation);
        when(sessionService.findActiveOrCreate(conversation.getConversationId())).thenReturn(session);
        when(messageService.save(any(), any(), any(), any(), any())).thenReturn(message);

        orchestrator.handleInboundMessage(request);

        verify(conversationService, times(1)).findOrCreate("user1");
    }

    @Test
    void handleInboundMessage_routerIsCalled_withCorrectSessionAndMessage() {
        when(conversationService.findOrCreate("user1")).thenReturn(conversation);
        when(sessionService.findActiveOrCreate(conversation.getConversationId())).thenReturn(session);
        when(messageService.save(any(), any(), any(), any(), any())).thenReturn(message);

        orchestrator.handleInboundMessage(request);

        verify(messageRouter).route(eq(session), eq(message));
    }
}
