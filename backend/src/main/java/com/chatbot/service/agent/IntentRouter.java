package com.chatbot.service.agent;

import com.chatbot.config.PromptConfig;
import com.chatbot.service.llm.KimiChatResponse;
import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Intent Router - recognizes user intent using Kimi LLM.
 * Calls Kimi with a system prompt for structured JSON intent classification.
 * Returns IntentResult with intent, confidence, risk, and extracted parameters.
 */
@Service
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    private static final double INTENT_TEMPERATURE = 0.1;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final KimiClient kimiClient;
    private final PromptConfig promptConfig;

    public IntentRouter(KimiClient kimiClient, PromptConfig promptConfig) {
        this.kimiClient = kimiClient;
        this.promptConfig = promptConfig;
    }

    public String getSystemPrompt() {
        return promptConfig.getIntentRouterPrompt();
    }

    /**
     * Recognize the user's intent from their message and conversation history.
     * Uses Kimi LLM with low temperature for deterministic classification.
     */
    public IntentResult recognize(String userMessage, List<KimiMessage> history) {
        log.info("Recognizing intent for message: length={}", userMessage.length());

        try {
            List<KimiMessage> messages = new ArrayList<>();

            // Include recent history for context
            if (history != null) {
                messages.addAll(history);
            }

            // Add the current user message
            messages.add(new KimiMessage("user", userMessage));

            KimiChatResponse response = kimiClient.chatCompletion(
                    promptConfig.getIntentRouterPrompt(), messages, INTENT_TEMPERATURE);

            String content = response.getContent();
            if (content == null || content.isBlank()) {
                log.warn("Empty response from intent recognition LLM, falling back to GENERAL_CHAT");
                return fallbackIntent();
            }

            return parseIntentResponse(content);
        } catch (Exception e) {
            log.error("Intent recognition failed: {}", e.getMessage());
            return fallbackIntent();
        }
    }

    @SuppressWarnings("unchecked")
    private IntentResult parseIntentResponse(String content) {
        try {
            // Strip any markdown code fences the LLM might add
            String json = content.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String intent = (String) parsed.get("intent");
            Number confidenceNum = (Number) parsed.get("confidence");
            double confidence = confidenceNum != null ? confidenceNum.doubleValue() : 0.0;
            String risk = (String) parsed.get("risk");

            Map<String, String> extractedParams = new HashMap<>();
            Object paramsObj = parsed.get("extracted_params");
            if (paramsObj instanceof Map) {
                Map<String, Object> rawParams = (Map<String, Object>) paramsObj;
                for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                    if (entry.getValue() != null) {
                        extractedParams.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }

            // Validate intent value
            if (!isValidIntent(intent)) {
                log.warn("Unknown intent '{}' from LLM, falling back to GENERAL_CHAT", intent);
                return fallbackIntent();
            }

            IntentResult result = new IntentResult(intent, confidence, risk, extractedParams);
            log.info("Intent recognized: intent={}, confidence={}, risk={}, params={}",
                    intent, confidence, risk, extractedParams.keySet());
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse intent JSON: {}, falling back to GENERAL_CHAT", e.getMessage());
            return fallbackIntent();
        }
    }

    private boolean isValidIntent(String intent) {
        return "POST_QUERY".equals(intent)
                || "DATA_DELETION".equals(intent)
                || "KB_QUESTION".equals(intent)
                || "GENERAL_CHAT".equals(intent);
    }

    private IntentResult fallbackIntent() {
        return new IntentResult("GENERAL_CHAT", 0.3, "low", new HashMap<>());
    }
}
