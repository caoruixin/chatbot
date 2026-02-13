package com.chatbot.controller;

import com.chatbot.dto.ApiResponse;
import com.chatbot.dto.request.FaqSearchRequest;
import com.chatbot.dto.request.PostQueryRequest;
import com.chatbot.dto.request.UserDataDeleteRequest;
import com.chatbot.dto.response.FaqSearchResponse;
import com.chatbot.dto.response.PostItem;
import com.chatbot.dto.response.PostQueryResponse;
import com.chatbot.dto.response.UserDataDeleteResponse;
import com.chatbot.mapper.UserPostMapper;
import com.chatbot.model.UserPost;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final UserPostMapper userPostMapper;

    public ToolController(UserPostMapper userPostMapper) {
        this.userPostMapper = userPostMapper;
    }

    @PostMapping("/faq/search")
    public ApiResponse<FaqSearchResponse> searchFaq(@RequestBody FaqSearchRequest request) {
        // Phase 1: Return placeholder response
        FaqSearchResponse response = new FaqSearchResponse("", "FAQ 搜索功能将在 Phase 2 实现", 0.0);
        return ApiResponse.success(response);
    }

    @PostMapping("/user-data/delete")
    public ApiResponse<UserDataDeleteResponse> deleteUserData(@RequestBody UserDataDeleteRequest request) {
        // Mock implementation
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        UserDataDeleteResponse response = new UserDataDeleteResponse(
                true,
                String.format("已收到用户 %s 的数据删除请求，预计 24 小时内处理完毕。", request.getUsername()),
                requestId
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/posts/query")
    public ApiResponse<PostQueryResponse> queryPosts(@RequestBody PostQueryRequest request) {
        List<UserPost> posts = userPostMapper.findByUsername(request.getUsername());
        List<PostItem> items = posts.stream()
                .map(p -> new PostItem(
                        p.getPostId(),
                        p.getUsername(),
                        p.getTitle(),
                        p.getStatus(),
                        p.getCreatedAt() != null ? p.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        PostQueryResponse response = new PostQueryResponse(items);
        return ApiResponse.success(response);
    }
}
