package com.chatbot.service.tool;

import com.chatbot.dto.response.FaqSearchResponse;
import com.chatbot.exception.LlmCallException;
import com.chatbot.mapper.FaqDocMapper;
import com.chatbot.model.FaqDoc;
import com.chatbot.service.llm.KimiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqDocMapper faqDocMapper;

    @Mock
    private KimiClient kimiClient;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqDocMapper, kimiClient, 0.75);
    }

    @Test
    void execute_validQuery_returnsMatchingResult() {
        float[] mockEmbedding = {0.1f, 0.2f, 0.3f};
        when(kimiClient.embeddingQuery(anyString())).thenReturn(mockEmbedding);

        FaqDoc faqDoc = new FaqDoc();
        faqDoc.setFaqId(UUID.randomUUID());
        faqDoc.setQuestion("How to reset password?");
        faqDoc.setAnswer("Go to settings and click reset.");
        faqDoc.setScore(0.85);

        when(faqDocMapper.searchByEmbedding(anyString(), eq(3))).thenReturn(List.of(faqDoc));

        ToolResult result = faqService.execute(Map.of("query", "how to reset password"));

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    void execute_noResults_returnsEmptyResult() {
        float[] mockEmbedding = {0.1f, 0.2f, 0.3f};
        when(kimiClient.embeddingQuery(anyString())).thenReturn(mockEmbedding);
        when(faqDocMapper.searchByEmbedding(anyString(), eq(3))).thenReturn(Collections.emptyList());

        ToolResult result = faqService.execute(Map.of("query", "something random"));

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_embeddingThrowsLlmCallException_returnsError() {
        when(kimiClient.embeddingQuery(anyString()))
                .thenThrow(new LlmCallException("Kimi embedding API returned empty embedding vector"));

        ToolResult result = faqService.execute(Map.of("query", "test"));

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_belowThreshold_stillReturnsSuccess() {
        float[] mockEmbedding = {0.1f, 0.2f, 0.3f};
        when(kimiClient.embeddingQuery(anyString())).thenReturn(mockEmbedding);

        FaqDoc faqDoc = new FaqDoc();
        faqDoc.setFaqId(UUID.randomUUID());
        faqDoc.setQuestion("Some question");
        faqDoc.setAnswer("Some answer");
        faqDoc.setScore(0.5);

        when(faqDocMapper.searchByEmbedding(anyString(), eq(3))).thenReturn(List.of(faqDoc));

        ToolResult result = faqService.execute(Map.of("query", "test query"));

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_embeddingApiFails_returnsError() {
        when(kimiClient.embeddingQuery(anyString())).thenThrow(new RuntimeException("API error"));

        ToolResult result = faqService.execute(Map.of("query", "test"));

        assertFalse(result.isSuccess());
    }
}
