package com.chatbot.service;

import com.chatbot.enums.ConversationStatus;
import com.chatbot.exception.ConversationNotFoundException;
import com.chatbot.mapper.ConversationMapper;
import com.chatbot.model.Conversation;
import com.chatbot.service.stream.GetStreamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private GetStreamService getStreamService;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void findOrCreate_existingUser_returnsExistingConversation() {
        Conversation existing = new Conversation();
        existing.setConversationId(UUID.randomUUID());
        existing.setUserId("user1");
        when(conversationMapper.findByUserId("user1")).thenReturn(existing);

        Conversation result = conversationService.findOrCreate("user1");

        assertSame(existing, result);
        verify(conversationMapper, never()).insert(any());
        verify(getStreamService, never()).upsertUser(anyString(), anyString());
        verify(getStreamService, never()).createChannel(anyString(), anyString());
    }

    @Test
    void findOrCreate_newUser_createsConversation() {
        when(conversationMapper.findByUserId("user2")).thenReturn(null);

        Conversation result = conversationService.findOrCreate("user2");

        assertNotNull(result);
        assertEquals("user2", result.getUserId());
        assertEquals(ConversationStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getConversationId());
        assertTrue(result.getGetstreamChannelId().startsWith("conv-"));

        verify(conversationMapper).insert(any(Conversation.class));
        verify(getStreamService).upsertUser("user2", "user2");
        verify(getStreamService).createChannel(anyString(), eq("user2"));
    }

    @Test
    void findOrCreate_getStreamFails_stillCreatesConversation() {
        when(conversationMapper.findByUserId("user3")).thenReturn(null);
        doThrow(new RuntimeException("GetStream error"))
                .when(getStreamService).upsertUser(anyString(), anyString());

        Conversation result = conversationService.findOrCreate("user3");

        assertNotNull(result);
        assertEquals("user3", result.getUserId());
        verify(conversationMapper).insert(any());
    }

    @Test
    void findById_exists_returnsConversation() {
        Conversation conv = new Conversation();
        conv.setConversationId(UUID.randomUUID());
        String id = conv.getConversationId().toString();
        when(conversationMapper.findById(id)).thenReturn(conv);

        Conversation result = conversationService.findById(id);

        assertSame(conv, result);
    }

    @Test
    void findById_notExists_throwsException() {
        String id = UUID.randomUUID().toString();
        when(conversationMapper.findById(id)).thenReturn(null);

        assertThrows(ConversationNotFoundException.class,
                () -> conversationService.findById(id));
    }

    @Test
    void findByUserId_delegatesToMapper() {
        Conversation conv = new Conversation();
        when(conversationMapper.findByUserId("user1")).thenReturn(conv);

        Conversation result = conversationService.findByUserId("user1");

        assertSame(conv, result);
    }

    @Test
    void findByUserId_noResult_returnsNull() {
        when(conversationMapper.findByUserId("unknown")).thenReturn(null);

        Conversation result = conversationService.findByUserId("unknown");

        assertNull(result);
    }
}
