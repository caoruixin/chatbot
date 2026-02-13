package com.chatbot.service.llm;

import com.chatbot.config.KimiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Kimi LLM client - placeholder for Phase 1.
 * Full implementation will be done in Phase 2 when AI agent features are added.
 */
@Component
public class KimiClient {

    private static final Logger log = LoggerFactory.getLogger(KimiClient.class);

    private final RestTemplate restTemplate;
    private final KimiConfig config;

    public KimiClient(RestTemplate kimiRestTemplate, KimiConfig config) {
        this.restTemplate = kimiRestTemplate;
        this.config = config;
    }

    /**
     * Chat completion call to Kimi LLM.
     * TODO: Phase 2 - Full implementation for AI agent intent recognition and response generation.
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
            throw new RuntimeException("Kimi API call failed", e);
        }
    }

    /**
     * Text embedding call to Kimi API.
     * TODO: Phase 2 - Implement for FAQ vector search.
     */
    public float[] embedding(String text) {
        // TODO: Phase 2 - Implement embedding API call
        log.warn("Embedding API not implemented yet (Phase 2)");
        return new float[0];
    }
}
