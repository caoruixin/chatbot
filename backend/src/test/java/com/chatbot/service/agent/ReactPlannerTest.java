package com.chatbot.service.agent;

import com.chatbot.service.llm.KimiMessage;
import com.chatbot.service.tool.ToolCall;
import com.chatbot.service.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReactPlannerTest {

    private ReactPlanner reactPlanner;

    @BeforeEach
    void setUp() {
        reactPlanner = new ReactPlanner();
    }

    // --- POST_QUERY intent ---

    @Test
    void plan_postQueryWithUsername_returnsPostQueryToolCall() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "alice");
        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", params);

        ToolCall result = reactPlanner.plan(intent, "查一下alice的帖子", List.of(), null);

        assertNotNull(result);
        assertEquals("post_query", result.getToolName());
        assertEquals("alice", result.getParams().get("username"));
        assertFalse(result.isUserConfirmed());
    }

    @Test
    void plan_postQueryMissingUsername_returnsNull() {
        IntentResult intent = new IntentResult("POST_QUERY", 0.9, "low", new HashMap<>());

        ToolCall result = reactPlanner.plan(intent, "查一下帖子", List.of(), null);

        assertNull(result);
    }

    @Test
    void plan_postQueryBlankUsername_returnsNull() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "  ");
        IntentResult intent = new IntentResult("POST_QUERY", 0.9, "low", params);

        ToolCall result = reactPlanner.plan(intent, "查一下帖子", List.of(), null);

        assertNull(result);
    }

    // --- DATA_DELETION intent ---

    @Test
    void plan_dataDeletionWithUsername_returnsDeleteToolCall() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "bob");
        IntentResult intent = new IntentResult("DATA_DELETION", 0.9, "critical", params);

        ToolCall result = reactPlanner.plan(intent, "删除bob的数据", List.of(), null);

        assertNotNull(result);
        assertEquals("user_data_delete", result.getToolName());
        assertEquals("bob", result.getParams().get("username"));
        assertFalse(result.isUserConfirmed());
    }

    @Test
    void plan_dataDeletionMissingUsername_returnsNull() {
        IntentResult intent = new IntentResult("DATA_DELETION", 0.9, "critical", new HashMap<>());

        ToolCall result = reactPlanner.plan(intent, "删除我的数据", List.of(), null);

        assertNull(result);
    }

    // --- KB_QUESTION intent ---

    @Test
    void plan_kbQuestion_returnsFaqSearchToolCall() {
        IntentResult intent = new IntentResult("KB_QUESTION", 0.85, "low", new HashMap<>());

        ToolCall result = reactPlanner.plan(intent, "退款政策是什么", List.of(), null);

        assertNotNull(result);
        assertEquals("faq_search", result.getToolName());
        assertEquals("退款政策是什么", result.getParams().get("query"));
    }

    // --- GENERAL_CHAT intent ---

    @Test
    void plan_generalChat_returnsNull() {
        IntentResult intent = new IntentResult("GENERAL_CHAT", 0.9, "low", new HashMap<>());

        ToolCall result = reactPlanner.plan(intent, "你好", List.of(), null);

        assertNull(result);
    }

    // --- Previous result behavior ---

    @Test
    void plan_previousResultSuccess_returnsNull() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "alice");
        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", params);
        ToolResult previousResult = ToolResult.success(Map.of("posts", List.of()));

        ToolCall result = reactPlanner.plan(intent, "查一下帖子", List.of(), previousResult);

        assertNull(result);
    }

    @Test
    void plan_previousResultFailed_continuesPlanning() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "alice");
        IntentResult intent = new IntentResult("POST_QUERY", 0.95, "low", params);
        ToolResult previousResult = ToolResult.error("查询失败");

        ToolCall result = reactPlanner.plan(intent, "查一下帖子", List.of(), previousResult);

        assertNotNull(result);
        assertEquals("post_query", result.getToolName());
    }

    // --- Unknown intent ---

    @Test
    void plan_unknownIntent_returnsNull() {
        IntentResult intent = new IntentResult("UNKNOWN", 0.5, "low", new HashMap<>());

        ToolCall result = reactPlanner.plan(intent, "test", List.of(), null);

        assertNull(result);
    }
}
