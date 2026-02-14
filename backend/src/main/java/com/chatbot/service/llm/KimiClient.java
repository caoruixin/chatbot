package com.chatbot.service.llm;

import com.chatbot.config.EmbeddingConfig;
import com.chatbot.config.KimiConfig;
import com.chatbot.exception.LlmCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * LLM client - uses Kimi (Moonshot AI) for chat completions
 * and DashScope (Alibaba Cloud Qwen) for text embeddings.
 * Chat uses Kimi's OpenAI-compatible API.
 * Embeddings use DashScope's native API (required for text_type parameter).
 */
@Component
public class KimiClient {

    private static final Logger log = LoggerFactory.getLogger(KimiClient.class);

    private final RestTemplate restTemplate;
    private final RestTemplate embeddingRestTemplate;
    private final KimiConfig config;
    private final EmbeddingConfig embeddingConfig;

    public KimiClient(RestTemplate kimiRestTemplate,
                      RestTemplate embeddingRestTemplate,
                      KimiConfig config,
                      EmbeddingConfig embeddingConfig) {
        this.restTemplate = kimiRestTemplate;
        this.embeddingRestTemplate = embeddingRestTemplate;
        this.config = config;
        this.embeddingConfig = embeddingConfig;
    }

    /**
     * Chat completion call to Kimi LLM.
     *
     * @throws LlmCallException on API failure
     */
    public KimiChatResponse chatCompletion(String systemPrompt,
                                           List<KimiMessage> messages,
                                           double temperature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Map<String, String>> allMessages = new ArrayList<>();
        allMessages.add(Map.of("role", "system", "content", systemPrompt));
        for (KimiMessage msg : messages) {
            allMessages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        Map<String, Object> body = Map.of(
                "model", config.getChatModel(),
                "messages", allMessages,
                "temperature", temperature
        );

        try {
            ResponseEntity<KimiChatResponse> resp = restTemplate.exchange(
                    config.getBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    KimiChatResponse.class
            );
            return resp.getBody();
        } catch (Exception e) {
            log.error("Kimi chat completion failed: {}", e.getMessage());
            throw new LlmCallException("Kimi chat completion API call failed", e);
        }
    }

    /**
     * Generate embedding for a document (FAQ, knowledge base entry).
     * Uses text_type=document for optimal asymmetric retrieval.
     */
    public float[] embeddingDocument(String text) {
        return embeddingInternal(text, "document");
    }

    /**
     * Generate embedding for a user query (search input).
     * Uses text_type=query for optimal asymmetric retrieval.
     */
    public float[] embeddingQuery(String text) {
        return embeddingInternal(text, "query");
    }

    /**
     * Text embedding call to DashScope native API.
     * Uses the native API (not OpenAI-compatible) to support text_type parameter
     * for asymmetric retrieval optimization.
     *
     * Native API request format:
     * {
     *   "model": "text-embedding-v4",
     *   "input": {"texts": ["text"]},
     *   "parameters": {"text_type": "query", "dimension": 1024}
     * }
     *
     * @param text     the text to embed
     * @param textType "query" for user searches, "document" for stored content
     * @throws LlmCallException on API failure or empty/invalid response
     */
    @SuppressWarnings("unchecked")
    private float[] embeddingInternal(String text, String textType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(embeddingConfig.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Native DashScope API request format
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embeddingConfig.getEmbeddingModel());
        body.put("input", Map.of("texts", List.of(text)));
        body.put("parameters", Map.of(
                "text_type", textType,
                "dimension", 1024
        ));

        try {
            ResponseEntity<Map> resp = embeddingRestTemplate.exchange(
                    embeddingConfig.getEmbeddingApiUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> responseBody = resp.getBody();
            if (responseBody == null) {
                throw new LlmCallException("DashScope embedding API returned null body");
            }

            // Native API response format: {"output": {"embeddings": [{"embedding": [...]}]}}
            Map<String, Object> output = (Map<String, Object>) responseBody.get("output");
            if (output == null) {
                throw new LlmCallException("DashScope embedding API returned null output");
            }

            List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
            if (embeddings == null || embeddings.isEmpty()) {
                throw new LlmCallException("DashScope embedding API returned empty embeddings");
            }

            List<Number> embedding = (List<Number>) embeddings.get(0).get("embedding");
            if (embedding == null || embedding.isEmpty()) {
                throw new LlmCallException("DashScope embedding API returned empty embedding vector");
            }

            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            log.debug("Embedding generated: textType={}, textLength={}, dimension={}",
                    textType, text.length(), result.length);
            return result;
        } catch (LlmCallException e) {
            throw e;
        } catch (Exception e) {
            log.error("DashScope embedding API call failed: {}", e.getMessage());
            throw new LlmCallException("DashScope embedding API call failed", e);
        }
    }
}
