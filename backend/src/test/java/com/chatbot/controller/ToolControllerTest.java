package com.chatbot.controller;

import com.chatbot.dto.response.PostItem;
import com.chatbot.dto.response.PostQueryResponse;
import com.chatbot.service.tool.PostQueryService;
import com.chatbot.service.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ToolController.class)
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostQueryService postQueryService;

    @Test
    void searchFaq_validRequest_returnsPlaceholder() throws Exception {
        String body = "{\"query\":\"how to reset password\"}";

        mockMvc.perform(post("/api/tools/faq/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.score").value(0.0));
    }

    @Test
    void deleteUserData_validRequest_returnsSuccessWithRequestId() throws Exception {
        String body = "{\"username\":\"user1\"}";

        mockMvc.perform(post("/api/tools/user-data/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.requestId").exists());
    }

    @Test
    void queryPosts_userHasPosts_returnsPostList() throws Exception {
        PostItem item = new PostItem(1, "user1", "Test Post", "PUBLISHED", "2026-01-01T00:00:00");
        PostQueryResponse response = new PostQueryResponse(List.of(item));
        when(postQueryService.execute(anyMap())).thenReturn(ToolResult.success(response));

        String body = "{\"username\":\"user1\"}";

        mockMvc.perform(post("/api/tools/posts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.posts").isArray())
                .andExpect(jsonPath("$.data.posts[0].title").value("Test Post"));
    }

    @Test
    void queryPosts_noPostsFound_returnsEmptyList() throws Exception {
        PostQueryResponse response = new PostQueryResponse(Collections.emptyList());
        when(postQueryService.execute(anyMap())).thenReturn(ToolResult.success(response));

        String body = "{\"username\":\"user2\"}";

        mockMvc.perform(post("/api/tools/posts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.posts").isEmpty());
    }
}
