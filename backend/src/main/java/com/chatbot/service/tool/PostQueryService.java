package com.chatbot.service.tool;

import com.chatbot.dto.response.PostItem;
import com.chatbot.dto.response.PostQueryResponse;
import com.chatbot.mapper.UserPostMapper;
import com.chatbot.model.UserPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PostQueryService implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(PostQueryService.class);

    private final UserPostMapper userPostMapper;

    public PostQueryService(UserPostMapper userPostMapper) {
        this.userPostMapper = userPostMapper;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String username = (String) params.get("username");
        log.info("Post query requested: username={}", username);

        List<UserPost> posts = userPostMapper.findByUsername(username);
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
        return ToolResult.success(response);
    }
}
