package com.chatbot.service.agent;

import com.chatbot.service.llm.KimiClient;
import com.chatbot.service.llm.KimiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Intent Router - recognizes user intent using Kimi LLM.
 * Phase 1: Placeholder.
 * Phase 2: Full implementation with structured JSON output for intent classification.
 */
@Service
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    private final KimiClient kimiClient;

    public IntentRouter(KimiClient kimiClient) {
        this.kimiClient = kimiClient;
    }

    // TODO: Phase 2 - Implement intent recognition
    // public IntentResult recognize(String userMessage, List<KimiMessage> history) { ... }
}
