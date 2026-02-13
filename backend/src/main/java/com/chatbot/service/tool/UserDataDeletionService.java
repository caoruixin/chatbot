package com.chatbot.service.tool;

import com.chatbot.dto.response.UserDataDeleteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * User data deletion tool executor.
 * This is a mock implementation - in production, this would trigger
 * an actual data deletion workflow.
 */
@Service
public class UserDataDeletionService implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(UserDataDeletionService.class);

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String username = (String) params.get("username");
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("User data deletion requested: username={}, requestId={}", username, requestId);

        // Mock implementation - simulate successful deletion request
        UserDataDeleteResponse response = new UserDataDeleteResponse(
                true,
                String.format("已收到用户 %s 的数据删除请求，预计 24 小时内处理完毕。", username),
                requestId
        );

        return ToolResult.success(response, requestId);
    }
}
