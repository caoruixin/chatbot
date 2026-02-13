package com.chatbot.service.agent;

import com.chatbot.service.llm.KimiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ReAct Planner - plans tool calls based on intent and conversation context.
 * Phase 1: Placeholder.
 * Phase 2: Full implementation with bounded ReAct loop (max 3 rounds).
 */
@Service
public class ReactPlanner {

    private static final Logger log = LoggerFactory.getLogger(ReactPlanner.class);

    private final KimiClient kimiClient;

    public ReactPlanner(KimiClient kimiClient) {
        this.kimiClient = kimiClient;
    }

    // TODO: Phase 2 - Implement ReAct planning
    // public ToolCall plan(IntentResult intent, String userMessage,
    //                      List<KimiMessage> history, ToolResult previousResult) { ... }
}
