package com.chatbot.service.tool;

import com.chatbot.dto.response.PostQueryResponse;
import com.chatbot.mapper.UserPostMapper;
import com.chatbot.model.UserPost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceTest {

    @Mock
    private UserPostMapper userPostMapper;

    @InjectMocks
    private PostQueryService postQueryService;

    @Test
    void execute_userHasPosts_returnsPostList() {
        UserPost post = new UserPost();
        post.setPostId(1);
        post.setUsername("user1");
        post.setTitle("My Post");
        post.setStatus("PUBLISHED");
        post.setCreatedAt(LocalDateTime.now());

        when(userPostMapper.findByUsername("user1")).thenReturn(List.of(post));

        ToolResult result = postQueryService.execute(Map.of("username", "user1"));

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        PostQueryResponse response = (PostQueryResponse) result.getData();
        assertEquals(1, response.getPosts().size());
        assertEquals("My Post", response.getPosts().get(0).getTitle());
    }

    @Test
    void execute_userHasNoPosts_returnsEmptyList() {
        when(userPostMapper.findByUsername("user2")).thenReturn(Collections.emptyList());

        ToolResult result = postQueryService.execute(Map.of("username", "user2"));

        assertTrue(result.isSuccess());
        PostQueryResponse response = (PostQueryResponse) result.getData();
        assertTrue(response.getPosts().isEmpty());
    }

    @Test
    void execute_postWithNullCreatedAt_handlesGracefully() {
        UserPost post = new UserPost();
        post.setPostId(2);
        post.setUsername("user3");
        post.setTitle("Draft");
        post.setStatus("DRAFT");
        post.setCreatedAt(null);

        when(userPostMapper.findByUsername("user3")).thenReturn(List.of(post));

        ToolResult result = postQueryService.execute(Map.of("username", "user3"));

        assertTrue(result.isSuccess());
        PostQueryResponse response = (PostQueryResponse) result.getData();
        assertNull(response.getPosts().get(0).getCreatedAt());
    }
}
