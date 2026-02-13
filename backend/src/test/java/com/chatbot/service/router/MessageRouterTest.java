package com.chatbot.service.router;

import com.chatbot.enums.SenderType;
import com.chatbot.enums.SessionStatus;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.SessionService;
import com.chatbot.service.agent.AgentCore;
import com.chatbot.service.human.HumanAgentService;
import com.chatbot.service.stream.GetStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageRouterTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private HumanAgentService humanAgentService;

    @Mock
    private AgentCore agentCore;

    @Mock
    private GetStreamService getStreamService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    private MessageRouter messageRouter;

    private Session session;
    private Message message;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        messageRouter = new MessageRouter(sessionService, humanAgentService, agentCore,
                getStreamService, conversationService, messageService, "转人工,转接人工,人工客服,人工服务");

        UUID convId = UUID.randomUUID();

        conversation = new Conversation();
        conversation.setConversationId(convId);
        conversation.setGetstreamChannelId("conv-" + convId);

        session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setConversationId(convId);
        session.setStatus(SessionStatus.AI_HANDLING);

        message = new Message();
        message.setMessageId(UUID.randomUUID());
        message.setConversationId(convId);
        message.setSessionId(session.getSessionId());
        message.setSenderType(SenderType.USER);
        message.setContent("Hello");
    }

    @Test
    void route_userRequestsHumanTransfer_updatesStatusAndAssignsAgent() {
        message.setContent("我想转人工");
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        messageRouter.route(session, message);

        verify(sessionService).updateStatus(session.getSessionId(), SessionStatus.HUMAN_HANDLING);
        verify(humanAgentService).assignAgent(session);
        verify(getStreamService).sendMessage(eq(conversation.getGetstreamChannelId()), eq("ai_bot"), anyString());
        verify(messageService).save(
                eq(session.getConversationId()),
                eq(session.getSessionId()),
                eq(SenderType.AI_CHATBOT),
                eq("ai_bot"),
                anyString()
        );
        verify(agentCore, never()).handleMessage(any(), any());
    }

    @Test
    void route_aiHandlingStatus_routesToAgentCore() {
        session.setStatus(SessionStatus.AI_HANDLING);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        messageRouter.route(session, message);

        verify(agentCore).handleMessage(session, message);
        verify(humanAgentService, never()).assignAgent(any());
        verify(humanAgentService, never()).forwardMessage(any(), any());
    }

    @Test
    void route_humanHandlingStatus_routesToHumanAgent() {
        session.setStatus(SessionStatus.HUMAN_HANDLING);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        messageRouter.route(session, message);

        verify(humanAgentService).forwardMessage(session, message);
        verify(agentCore, never()).handleMessage(any(), any());
    }

    @Test
    void route_unknownStatus_noRouting() {
        session.setStatus(SessionStatus.CLOSED);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        messageRouter.route(session, message);

        verify(agentCore, never()).handleMessage(any(), any());
        verify(humanAgentService, never()).forwardMessage(any(), any());
        verify(humanAgentService, never()).assignAgent(any());
    }

    @Test
    void route_conversationNotFoundInDb_fallbackChannelId() {
        session.setStatus(SessionStatus.AI_HANDLING);
        when(conversationService.getChannelId(anyString()))
                .thenReturn("conv-" + session.getConversationId());

        messageRouter.route(session, message);

        verify(agentCore).handleMessage(session, message);
    }

    @Test
    void route_transferKeywordInMiddle_triggersTransfer() {
        message.setContent("帮我转人工客服");
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        messageRouter.route(session, message);

        verify(sessionService).updateStatus(session.getSessionId(), SessionStatus.HUMAN_HANDLING);
        verify(humanAgentService).assignAgent(session);
    }
}
