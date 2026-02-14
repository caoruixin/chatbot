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
 * Both APIs are OpenAI-compatible.
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
     * Text embedding call to DashScope (Alibaba Cloud Qwen) API.
     * Uses OpenAI-compatible endpoint.
     *
     * @throws LlmCallException on API failure or empty/invalid response
     */
    @SuppressWarnings("unchecked")
    public float[] embedding(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(embeddingConfig.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", embeddingConfig.getEmbeddingModel(),
                "input", text
        );

        try {
            ResponseEntity<Map> resp = embeddingRestTemplate.exchange(
                    embeddingConfig.getBaseUrl() + "/embeddings",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> responseBody = resp.getBody();
            if (responseBody == null) {
                throw new LlmCallException("DashScope embedding API returned null body");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new LlmCallException("DashScope embedding API returned empty data");
            }

            List<Number> embedding = (List<Number>) data.get(0).get("embedding");
            if (embedding == null || embedding.isEmpty()) {
                throw new LlmCallException("DashScope embedding API returned empty embedding vector");
            }

            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            log.debug("Embedding generated: textLength={}, dimension={}", text.length(), result.length);
            return result;
        } catch (LlmCallException e) {
            throw e;
        } catch (Exception e) {
            log.error("DashScope embedding API call failed: {}", e.getMessage());
            throw new LlmCallException("DashScope embedding API call failed", e);
        }
    }
}
