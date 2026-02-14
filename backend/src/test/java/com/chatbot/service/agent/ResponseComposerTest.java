package com.chatbot.service.agent;

import com.chatbot.exception.LlmCallException;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiMessage;
import com.chatbot.service.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResponseComposerTest {

    @Mock
    private KimiClient kimiClient;

    private ResponseComposer responseComposer;

    @BeforeEach
    void setUp() {
        responseComposer = new ResponseComposer(kimiClient);
    }

    // --- composeFromTemplate tests ---

    @Test
    void composeFromTemplate_dataDeletionNullResult_asksForUsername() {
        IntentResult intent = new IntentResult("DATA_DELETION", 0.9, "critical", new HashMap<>());

        String reply = responseComposer.composeFromTemplate(intent, null);

        assertTrue(reply.contains("用户名"));
    }

    @Test
    void composeFromTemplate_dataDeletionNeedsConfirmation_asksForConfirmation() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "bob");
        IntentResult intent = new IntentResult("DATA_DELETION", 0.9, "critical", params);
        ToolResult toolResult = ToolResult.needsConfirmation("需要确认");

        String reply = responseComposer.composeFromTemplate(intent, toolResult);

        assertTrue(reply.contains("bob"));
        assertTrue(reply.contains("确认删除"));
    }

    @Test
    void composeFromTemplate_dataDeletionSuccess_returnsSuccessMessage() {
        IntentResult intent = new IntentResult("DATA_DELETION", 0.9, "critical", new HashMap<>());
        ToolResult toolResult = ToolResult.success(Map.of("deleted", true));

        String reply = responseComposer.composeFromTemplate(intent, toolResult);

        assertTrue(reply.contains("已提交"));
    }

    @Test
    void composeFromTemplate_dataDeletionError_returnsErrorMessage() {
        IntentResult intent = new IntentResult("DATA_DELETION", 0.9, "critical", new HashMap<>());
        ToolResult toolResult = ToolResult.error("删除失败");

        String reply = responseComposer.composeFromTemplate(intent, toolResult);

        assertTrue(reply.contains("失败"));
    }

    @Test
    void composeFromTemplate_nonDeletionCriticalSuccess_returnsGenericSuccess() {
        IntentResult intent = new IntentResult("OTHER_CRITICAL", 0.9, "critical", new HashMap<>());
        ToolResult toolResult = ToolResult.success(Map.of("ok", true));

        String reply = responseComposer.composeFromTemplate(intent, toolResult);

        assertTrue(reply.contains("已完成"));
    }

    @Test
    void composeFromTemplate_nonDeletionCriticalFailure_returnsFallback() {
        IntentResult intent = new IntentResult("OTHER_CRITICAL", 0.9, "critical", new HashMap<>());
        ToolResult toolResult = ToolResult.error("失败");

        String reply = responseComposer.composeFromTemplate(intent, toolResult);

        assertTrue(reply.contains("失败"));
    }

    // --- composeWithEvidence tests ---

    @Test
    void composeWithEvidence_llmReturnsContent_returnsLlmResponse() {
        KimiChatResponse response = buildResponse("这是一个友好的回复。");
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", new HashMap<>());
        ToolResult toolResult = ToolResult.success(Map.of("posts", List.of()));

        String reply = responseComposer.composeWithEvidence("查帖子", intent, toolResult, new ArrayList<>());

        assertEquals("这是一个友好的回复。", reply);
    }

    @Test
    void composeWithEvidence_llmReturnsEmpty_usesFallback() {
        KimiChatResponse response = buildResponse("");
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", new HashMap<>());
        ToolResult toolResult = ToolResult.success(Map.of("posts", List.of()));

        String reply = responseComposer.composeWithEvidence("查帖子", intent, toolResult, new ArrayList<>());

        assertTrue(reply.contains("已查询到"));
    }

    @Test
    void composeWithEvidence_llmThrowsException_usesFallback() {
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble()))
                .thenThrow(new LlmCallException("API error"));

        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", new HashMap<>());
        ToolResult toolResult = ToolResult.success(Map.of("posts", List.of()));

        String reply = responseComposer.composeWithEvidence("查帖子", intent, toolResult, new ArrayList<>());

        assertTrue(reply.contains("已查询到"));
    }

    @Test
    void composeWithEvidence_nullToolResult_usesFallback() {
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble()))
                .thenThrow(new LlmCallException("API error"));

        IntentResult intent = new IntentResult("GENERAL_CHAT", 0.9, "low", new HashMap<>());

        String reply = responseComposer.composeWithEvidence("你好", intent, null, new ArrayList<>());

        assertTrue(reply.contains("无法获取"));
    }

    @Test
    void composeWithEvidence_failedToolResult_showsErrorFallback() {
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble()))
                .thenThrow(new LlmCallException("API error"));

        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", new HashMap<>());
        ToolResult toolResult = ToolResult.error("查询超时");

        String reply = responseComposer.composeWithEvidence("查帖子", intent, toolResult, new ArrayList<>());

        assertTrue(reply.contains("无法获取") || reply.contains("转人工"));
    }

    @Test
    void composeWithEvidence_includesHistoryInLlmCall() {
        KimiChatResponse response = buildResponse("回复内容");
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        List<KimiMessage> history = List.of(
                new KimiMessage("user", "之前的问题"),
                new KimiMessage("assistant", "之前的回答")
        );
        IntentResult intent = new IntentResult("GENERAL_CHAT", 0.9, "low", new HashMap<>());

        responseComposer.composeWithEvidence("新问题", intent, null, history);

        verify(kimiClient).chatCompletion(anyString(), argThat(messages ->
                messages.size() == 4  // 2 history + 1 user + 1 tool context
                        && "之前的问题".equals(messages.get(0).getContent())
                        && "之前的回答".equals(messages.get(1).getContent())
                        && "新问题".equals(messages.get(2).getContent())
        ), anyDouble());
    }

    private KimiChatResponse buildResponse(String content) {
        KimiChatResponse response = new KimiChatResponse();
        KimiChatResponse.Choice choice = new KimiChatResponse.Choice();
        KimiMessage message = new KimiMessage("assistant", content);
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        return response;
    }
}
