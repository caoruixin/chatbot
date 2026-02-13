package com.chatbot.service.tool;

import com.chatbot.dto.response.UserDataDeleteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UserDataDeletionServiceTest {

    @InjectMocks
    private UserDataDeletionService userDataDeletionService;

    @Test
    void execute_validUsername_returnsSuccessWithRequestId() {
        ToolResult result = userDataDeletionService.execute(Map.of("username", "user1"));

        assertTrue(result.isSuccess());
        assertNotNull(result.getRequestId());
        assertTrue(result.getRequestId().startsWith("req-"));

        UserDataDeleteResponse response = (UserDataDeleteResponse) result.getData();
        assertTrue(response.isSuccess());
        assertTrue(response.getMessage().contains("user1"));
    }

    @Test
    void execute_differentUsers_differentRequestIds() {
        ToolResult result1 = userDataDeletionService.execute(Map.of("username", "user1"));
        ToolResult result2 = userDataDeletionService.execute(Map.of("username", "user2"));

        assertNotEquals(result1.getRequestId(), result2.getRequestId());
    }
}
