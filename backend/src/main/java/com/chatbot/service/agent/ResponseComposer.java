package com.chatbot.service.agent;

import com.chatbot.service.llm.KimiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Response Composer - generates user-facing responses.
 * Phase 1: Placeholder.
 * Phase 2: Full implementation with template responses for high-risk operations
 *          and LLM-generated responses for low-risk operations.
 */
@Service
public class ResponseComposer {

    private static final Logger log = LoggerFactory.getLogger(ResponseComposer.class);

    private final KimiClient kimiClient;

    public ResponseComposer(KimiClient kimiClient) {
        this.kimiClient = kimiClient;
    }

    // TODO: Phase 2 - Implement response composition
    // public String composeFromTemplate(IntentResult intent, ToolResult toolResult) { ... }
    // public String composeWithEvidence(String userMessage, IntentResult intent,
    //                                   ToolResult toolResult, List<KimiMessage> history) { ... }
}
