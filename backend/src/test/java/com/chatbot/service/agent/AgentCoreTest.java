package com.chatbot.service.agent;

import com.chatbot.enums.SenderType;
import com.chatbot.enums.SessionStatus;
import com.chatbot.model.Conversation;
import com.chatbot.model.Message;
import com.chatbot.model.Session;
import com.chatbot.service.ConversationService;
import com.chatbot.service.MessageService;
import com.chatbot.service.llm.KimiMessage;
import com.chatbot.service.stream.GetStreamService;
import com.chatbot.service.tool.ToolCall;
import com.chatbot.service.tool.ToolDispatcher;
import com.chatbot.service.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Mock
    private IntentRouter intentRouter;

    @Mock
    private ReactPlanner reactPlanner;

    @Mock
    private ResponseComposer responseComposer;

    @Mock
    private ToolDispatcher toolDispatcher;

    private AgentCore agentCore;

    private Session session;
    private Message userMessage;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        agentCore = new AgentCore(messageService, getStreamService, conversationService,
                intentRouter, reactPlanner, responseComposer, toolDispatcher,
                "ai_bot", 3, 0.7);

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
    void handleMessage_generalChat_savesAndSendsReply() {
        IntentResult intent = new IntentResult("GENERAL_CHAT", 0.9, "low", new HashMap<>());
        when(intentRouter.recognize(anyString(), anyList())).thenReturn(intent);
        when(reactPlanner.plan(any(), anyString(), anyList(), isNull())).thenReturn(null);
        when(responseComposer.composeWithEvidence(anyString(), any(), isNull(), anyList()))
                .thenReturn("你好！有什么可以帮助您的吗？");
        when(messageService.findBySessionId(anyString())).thenReturn(Collections.emptyList());

        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), eq(SenderType.AI_CHATBOT), eq("ai_bot"), anyString(), any()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        agentCore.handleMessage(session, userMessage);

        verify(messageService).save(
                eq(session.getConversationId()),
                eq(session.getSessionId()),
                eq(SenderType.AI_CHATBOT),
                eq("ai_bot"),
                anyString(),
                any()
        );
        verify(getStreamService).sendMessage(
                eq(conversation.getGetstreamChannelId()),
                eq("ai_bot"),
                anyString()
        );
    }

    @Test
    void handleMessage_lowConfidence_sendsClarification() {
        IntentResult intent = new IntentResult("GENERAL_CHAT", 0.3, "low", new HashMap<>());
        when(intentRouter.recognize(anyString(), anyList())).thenReturn(intent);
        when(messageService.findBySessionId(anyString())).thenReturn(Collections.emptyList());

        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), any(), any(), anyString(), any()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        agentCore.handleMessage(session, userMessage);

        verify(messageService).save(any(), any(), eq(SenderType.AI_CHATBOT), eq("ai_bot"),
                contains("不太确定"), any());
    }

    @Test
    void handleMessage_getStreamFails_doesNotThrow() {
        IntentResult intent = new IntentResult("GENERAL_CHAT", 0.9, "low", new HashMap<>());
        when(intentRouter.recognize(anyString(), anyList())).thenReturn(intent);
        when(reactPlanner.plan(any(), anyString(), anyList(), isNull())).thenReturn(null);
        when(responseComposer.composeWithEvidence(anyString(), any(), isNull(), anyList()))
                .thenReturn("Reply");
        when(messageService.findBySessionId(anyString())).thenReturn(Collections.emptyList());

        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), any(), any(), anyString(), any()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());
        doThrow(new RuntimeException("GetStream error"))
                .when(getStreamService).sendMessage(anyString(), anyString(), anyString());

        // Should not throw
        agentCore.handleMessage(session, userMessage);

        verify(messageService).save(any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void handleMessage_postQueryWithTool_callsToolDispatcher() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "alice");
        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", params);
        when(intentRouter.recognize(anyString(), anyList())).thenReturn(intent);
        when(messageService.findBySessionId(anyString())).thenReturn(Collections.emptyList());

        ToolCall toolCall = new ToolCall("post_query", Map.of("username", "alice"), false);
        when(reactPlanner.plan(any(), anyString(), anyList(), isNull())).thenReturn(toolCall);

        ToolResult toolResult = ToolResult.success(Map.of("posts", List.of()));
        when(toolDispatcher.dispatch(any())).thenReturn(toolResult);

        when(responseComposer.composeWithEvidence(anyString(), any(), any(), anyList()))
                .thenReturn("查询结果如下...");

        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), any(), any(), anyString(), any()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        agentCore.handleMessage(session, userMessage);

        verify(toolDispatcher).dispatch(any());
        verify(responseComposer).composeWithEvidence(anyString(), any(), any(), anyList());
    }

    @Test
    void handleMessage_dataDeletion_sendsConfirmationWithMetadata() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "bob");
        IntentResult intent = new IntentResult("DATA_DELETION", 0.9, "critical", params);
        when(intentRouter.recognize(anyString(), anyList())).thenReturn(intent);
        when(messageService.findBySessionId(anyString())).thenReturn(Collections.emptyList());

        ToolCall toolCall = new ToolCall("user_data_delete", Map.of("username", "bob"), false);
        when(reactPlanner.plan(any(), anyString(), anyList(), isNull())).thenReturn(toolCall);

        ToolResult toolResult = ToolResult.needsConfirmation("此操作不可逆，需要用户二次确认");
        when(toolDispatcher.dispatch(any())).thenReturn(toolResult);

        when(responseComposer.composeFromTemplate(any(), any()))
                .thenReturn("您确定要删除数据吗？");

        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), any(), any(), anyString(), any()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        agentCore.handleMessage(session, userMessage);

        verify(responseComposer).composeFromTemplate(any(), any());
        verify(responseComposer, never()).composeWithEvidence(anyString(), any(), any(), anyList());
        // Verify the confirmation reply is saved with metadata
        verify(messageService).save(any(), any(), eq(SenderType.AI_CHATBOT), eq("ai_bot"),
                anyString(), argThat(meta -> meta != null && meta.contains("pendingConfirmation")));
    }

    @Test
    void handleMessage_userConfirmsDelete_executesWithConfirmation() {
        // Set up a previous AI message with pending confirmation metadata
        Message previousAiMessage = new Message();
        previousAiMessage.setMessageId(UUID.randomUUID());
        previousAiMessage.setSenderType(SenderType.AI_CHATBOT);
        previousAiMessage.setContent("您确定要删除用户 bob 的所有数据吗？");
        previousAiMessage.setMetadataJson(
                "{\"pendingConfirmation\":true,\"toolName\":\"user_data_delete\",\"toolParams\":{\"username\":\"bob\"}}");

        userMessage.setContent("确认删除");
        when(messageService.findBySessionId(anyString())).thenReturn(List.of(previousAiMessage, userMessage));

        ToolResult toolResult = ToolResult.success(Map.of("deleted", true));
        when(toolDispatcher.dispatch(argThat(tc -> tc.isUserConfirmed()))).thenReturn(toolResult);

        Message savedMessage = new Message();
        savedMessage.setMessageId(UUID.randomUUID());
        when(messageService.save(any(), any(), any(), any(), anyString(), any()))
                .thenReturn(savedMessage);
        when(conversationService.getChannelId(anyString())).thenReturn(conversation.getGetstreamChannelId());

        agentCore.handleMessage(session, userMessage);

        // Should dispatch with userConfirmed=true, bypassing intent recognition
        verify(toolDispatcher).dispatch(argThat(tc ->
                tc.isUserConfirmed() && "user_data_delete".equals(tc.getToolName())));
        verify(intentRouter, never()).recognize(anyString(), anyList());
    }
}
