package com.chatbot.service.agent;

import com.chatbot.service.llm.KimiMessage;
import com.chatbot.service.tool.ToolCall;
import com.chatbot.service.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct Planner - plans tool calls based on intent and conversation context.
 * Determines which tool to call based on the recognized intent, extracted parameters,
 * and any previous tool results. Returns null when no tool call is needed.
 */
@Service
public class ReactPlanner {

    private static final Logger log = LoggerFactory.getLogger(ReactPlanner.class);

    /**
     * Plan the next tool call based on the intent, user message, history, and previous tool result.
     *
     * @param intent           the recognized intent
     * @param userMessage      the original user message
     * @param history          conversation history as KimiMessages
     * @param previousResult   the result from a previous tool call (null if first round)
     * @return ToolCall to execute, or null if no tool call is needed (or need follow-up question)
     */
    public ToolCall plan(IntentResult intent, String userMessage,
                         List<KimiMessage> history, ToolResult previousResult) {
        String intentType = intent.getIntent();
        log.info("Planning tool call: intent={}, hasPreviousResult={}", intentType, previousResult != null);

        // If we already have a successful result from a previous round, no more tool calls needed
        if (previousResult != null && previousResult.isSuccess()) {
            log.info("Previous tool call succeeded, no further tool calls needed");
            return null;
        }

        switch (intentType) {
            case "POST_QUERY":
                return planPostQuery(intent);

            case "DATA_DELETION":
                return planDataDeletion(intent);

            case "KB_QUESTION":
                return planKbQuestion(userMessage);

            case "GENERAL_CHAT":
            default:
                log.info("No tool call needed for intent: {}", intentType);
                return null;
        }
    }

    private ToolCall planPostQuery(IntentResult intent) {
        String username = intent.getExtractedParams().get("username");
        if (username == null || username.isBlank()) {
            log.info("POST_QUERY intent missing username parameter, need follow-up question");
            return null;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        return new ToolCall("post_query", params, false);
    }

    private ToolCall planDataDeletion(IntentResult intent) {
        String username = intent.getExtractedParams().get("username");
        if (username == null || username.isBlank()) {
            log.info("DATA_DELETION intent missing username parameter, need follow-up question");
            return null;
        }

        // DATA_DELETION requires user confirmation - set userConfirmed to false
        // The ToolDispatcher will return needsConfirmation if not confirmed
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        return new ToolCall("user_data_delete", params, false);
    }

    private ToolCall planKbQuestion(String userMessage) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", userMessage);
        return new ToolCall("faq_search", params, false);
    }
}
