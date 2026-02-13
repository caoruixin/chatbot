package com.chatbot.service.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolDispatcherTest {

    @Mock
    private FaqService faqService;

    @Mock
    private PostQueryService postQueryService;

    @Mock
    private UserDataDeletionService userDataDeletionService;

    private ToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ToolDispatcher(faqService, postQueryService, userDataDeletionService);
    }

    @Test
    void dispatch_unknownTool_returnsError() {
        ToolCall call = new ToolCall("unknown_tool", Map.of("key", "value"), false);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("未知工具"));
    }

    @Test
    void dispatch_nullParams_returnsSchemaError() {
        ToolCall call = new ToolCall("faq_search", null, false);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertEquals("参数不能为空", result.getError());
    }

    @Test
    void dispatch_faqSearchMissingQuery_returnsSchemaError() {
        ToolCall call = new ToolCall("faq_search", Map.of("foo", "bar"), false);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertTrue(result.getError().contains("query"));
    }

    @Test
    void dispatch_faqSearchNullQuery_returnsSchemaError() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", null);
        ToolCall call = new ToolCall("faq_search", params, false);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
    }

    @Test
    void dispatch_faqSearchValid_executesSuccessfully() {
        ToolResult expectedResult = ToolResult.success("faq result");
        when(faqService.execute(any())).thenReturn(expectedResult);

        ToolCall call = new ToolCall("faq_search", Map.of("query", "how to reset password"), false);

        ToolResult result = dispatcher.dispatch(call);

        assertTrue(result.isSuccess());
        verify(faqService).execute(any());
    }

    @Test
    void dispatch_postQueryMissingUsername_returnsSchemaError() {
        ToolCall call = new ToolCall("post_query", Map.of("foo", "bar"), false);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("username"));
    }

    @Test
    void dispatch_postQueryValid_executesSuccessfully() {
        ToolResult expectedResult = ToolResult.success("post result");
        when(postQueryService.execute(any())).thenReturn(expectedResult);

        ToolCall call = new ToolCall("post_query", Map.of("username", "user1"), false);

        ToolResult result = dispatcher.dispatch(call);

        assertTrue(result.isSuccess());
        verify(postQueryService).execute(any());
    }

    @Test
    void dispatch_userDataDeleteNotConfirmed_needsConfirmation() {
        ToolCall call = new ToolCall("user_data_delete", Map.of("username", "user1"), false);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.needsConfirmation());
    }

    @Test
    void dispatch_userDataDeleteConfirmed_executesSuccessfully() {
        ToolResult expectedResult = ToolResult.success("deleted");
        when(userDataDeletionService.execute(any())).thenReturn(expectedResult);

        ToolCall call = new ToolCall("user_data_delete", Map.of("username", "user1"), true);

        ToolResult result = dispatcher.dispatch(call);

        assertTrue(result.isSuccess());
        verify(userDataDeletionService).execute(any());
    }

    @Test
    void dispatch_executorThrowsException_returnsError() {
        when(faqService.execute(any())).thenThrow(new RuntimeException("DB error"));

        ToolCall call = new ToolCall("faq_search", Map.of("query", "test"), false);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("工具执行失败"));
    }

    @Test
    void dispatch_userDataDeleteMissingUsername_returnsSchemaError() {
        ToolCall call = new ToolCall("user_data_delete", Map.of("foo", "bar"), true);

        ToolResult result = dispatcher.dispatch(call);

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertTrue(result.getError().contains("username"));
    }
}
