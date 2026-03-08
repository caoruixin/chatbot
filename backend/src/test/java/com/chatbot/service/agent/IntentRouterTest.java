package com.chatbot.service.agent;

import com.chatbot.config.PromptConfig;
import com.chatbot.exception.LlmCallException;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntentRouterTest {

    @Mock
    private KimiClient kimiClient;

    @Mock
    private PromptConfig promptConfig;

    private IntentRouter intentRouter;

    @BeforeEach
    void setUp() {
        when(promptConfig.getIntentRouterPrompt()).thenReturn("test intent prompt");
        intentRouter = new IntentRouter(kimiClient, promptConfig);
    }

    @Test
    void recognize_validPostQueryJson_returnsPostQueryIntent() {
        String json = "{\"intent\":\"POST_QUERY\",\"confidence\":0.95,\"risk\":\"low\",\"extracted_params\":{\"username\":\"alice\"}}";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("查一下alice的帖子", new ArrayList<>());

        assertEquals("POST_QUERY", result.getIntent());
        assertEquals(0.95, result.getConfidence(), 0.01);
        assertEquals("low", result.getRisk());
        assertEquals("alice", result.getExtractedParams().get("username"));
    }

    @Test
    void recognize_dataDeletionJson_returnsCriticalRisk() {
        String json = "{\"intent\":\"DATA_DELETION\",\"confidence\":0.9,\"risk\":\"critical\",\"extracted_params\":{\"username\":\"bob\"}}";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("删除我的数据，用户名bob", new ArrayList<>());

        assertEquals("DATA_DELETION", result.getIntent());
        assertEquals("critical", result.getRisk());
        assertEquals("bob", result.getExtractedParams().get("username"));
    }

    @Test
    void recognize_kbQuestionJson_returnsKbQuestionIntent() {
        String json = "{\"intent\":\"KB_QUESTION\",\"confidence\":0.85,\"risk\":\"low\",\"extracted_params\":{}}";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("退款政策是什么", new ArrayList<>());

        assertEquals("KB_QUESTION", result.getIntent());
        assertEquals(0.85, result.getConfidence(), 0.01);
        assertTrue(result.getExtractedParams().isEmpty());
    }

    @Test
    void recognize_generalChatJson_returnsGeneralChatIntent() {
        String json = "{\"intent\":\"GENERAL_CHAT\",\"confidence\":0.9,\"risk\":\"low\",\"extracted_params\":{}}";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("你好", new ArrayList<>());

        assertEquals("GENERAL_CHAT", result.getIntent());
    }

    @Test
    void recognize_jsonWithMarkdownFences_stripsAndParses() {
        String json = "```json\n{\"intent\":\"POST_QUERY\",\"confidence\":0.9,\"risk\":\"low\",\"extracted_params\":{\"username\":\"alice\"}}\n```";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("查帖子", new ArrayList<>());

        assertEquals("POST_QUERY", result.getIntent());
        assertEquals("alice", result.getExtractedParams().get("username"));
    }

    @Test
    void recognize_invalidJson_fallsBackToGeneralChat() {
        KimiChatResponse response = buildResponse("this is not JSON");
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("test", new ArrayList<>());

        assertEquals("GENERAL_CHAT", result.getIntent());
        assertEquals(0.3, result.getConfidence(), 0.01);
    }

    @Test
    void recognize_emptyResponse_fallsBackToGeneralChat() {
        KimiChatResponse response = buildResponse("");
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("test", new ArrayList<>());

        assertEquals("GENERAL_CHAT", result.getIntent());
        assertEquals(0.3, result.getConfidence(), 0.01);
    }

    @Test
    void recognize_nullContent_fallsBackToGeneralChat() {
        KimiChatResponse response = buildResponse(null);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("test", new ArrayList<>());

        assertEquals("GENERAL_CHAT", result.getIntent());
    }

    @Test
    void recognize_unknownIntent_fallsBackToGeneralChat() {
        String json = "{\"intent\":\"UNKNOWN_TYPE\",\"confidence\":0.9,\"risk\":\"low\",\"extracted_params\":{}}";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("test", new ArrayList<>());

        assertEquals("GENERAL_CHAT", result.getIntent());
    }

    @Test
    void recognize_llmThrowsException_fallsBackToGeneralChat() {
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble()))
                .thenThrow(new LlmCallException("API error"));

        IntentResult result = intentRouter.recognize("test", new ArrayList<>());

        assertEquals("GENERAL_CHAT", result.getIntent());
        assertEquals(0.3, result.getConfidence(), 0.01);
    }

    @Test
    void recognize_includesHistoryInMessages() {
        String json = "{\"intent\":\"GENERAL_CHAT\",\"confidence\":0.9,\"risk\":\"low\",\"extracted_params\":{}}";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        List<KimiMessage> history = List.of(
                new KimiMessage("user", "previous question"),
                new KimiMessage("assistant", "previous answer")
        );
        intentRouter.recognize("follow up", history);

        verify(kimiClient).chatCompletion(anyString(), argThat(messages ->
                messages.size() == 3
                        && "previous question".equals(messages.get(0).getContent())
                        && "previous answer".equals(messages.get(1).getContent())
                        && "follow up".equals(messages.get(2).getContent())
        ), anyDouble());
    }

    @Test
    void recognize_nullHistory_handledGracefully() {
        String json = "{\"intent\":\"GENERAL_CHAT\",\"confidence\":0.9,\"risk\":\"low\",\"extracted_params\":{}}";
        KimiChatResponse response = buildResponse(json);
        when(kimiClient.chatCompletion(anyString(), anyList(), anyDouble())).thenReturn(response);

        IntentResult result = intentRouter.recognize("test", null);

        assertEquals("GENERAL_CHAT", result.getIntent());
    }

    private KimiChatResponse buildResponse(String content) {
        KimiChatResponse response = new KimiChatResponse();
        if (content != null) {
            KimiChatResponse.Choice choice = new KimiChatResponse.Choice();
            KimiMessage message = new KimiMessage("assistant", content);
            choice.setMessage(message);
            response.setChoices(List.of(choice));
        } else {
            // No choices means getContent() returns null
            response.setChoices(List.of());
        }
        return response;
    }
}
