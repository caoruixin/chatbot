package com.chatbot.controller;

import com.chatbot.service.stream.GetStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StreamTokenController.class)
class StreamTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStreamService getStreamService;

    @Test
    void getToken_validUserId_returnsToken() throws Exception {
        when(getStreamService.createToken("user1")).thenReturn("test-token-123");

        mockMvc.perform(get("/api/stream/token")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("test-token-123"))
                .andExpect(jsonPath("$.data.userId").value("user1"));

        verify(getStreamService).upsertUser("user1", "user1");
    }

    @Test
    void getToken_missingUserId_returns500() throws Exception {
        // BUG: GlobalExceptionHandler 未处理 MissingServletRequestParameterException，
        // 缺少必填参数应返回 400 而非 500
        mockMvc.perform(get("/api/stream/token"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getToken_getStreamThrows_returns500() throws Exception {
        when(getStreamService.createToken("user2"))
                .thenThrow(new RuntimeException("GetStream error"));

        mockMvc.perform(get("/api/stream/token")
                        .param("userId", "user2"))
                .andExpect(status().isInternalServerError());
    }
}
