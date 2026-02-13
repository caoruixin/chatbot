package com.chatbot.service.tool;

import com.chatbot.mapper.FaqDocMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqDocMapper faqDocMapper;

    @InjectMocks
    private FaqService faqService;

    @Test
    void execute_validQuery_returnsPlaceholderResult() {
        ToolResult result = faqService.execute(Map.of("query", "how to reset password"));

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    void execute_emptyQuery_returnsSuccessPlaceholder() {
        ToolResult result = faqService.execute(Map.of("query", ""));

        assertTrue(result.isSuccess());
    }
}
