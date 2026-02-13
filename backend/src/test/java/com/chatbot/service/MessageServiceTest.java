package com.chatbot.service;

import com.chatbot.enums.SenderType;
import com.chatbot.mapper.MessageMapper;
import com.chatbot.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private MessageService messageService;

    @Test
    void save_validMessage_insertsAndReturnsMessage() {
        UUID convId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Message result = messageService.save(convId, sessionId, SenderType.USER, "user1", "Hello");

        assertNotNull(result);
        assertNotNull(result.getMessageId());
        assertEquals(convId, result.getConversationId());
        assertEquals(sessionId, result.getSessionId());
        assertEquals(SenderType.USER, result.getSenderType());
        assertEquals("user1", result.getSenderId());
        assertEquals("Hello", result.getContent());

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(captor.capture());
        Message inserted = captor.getValue();
        assertEquals("Hello", inserted.getContent());
    }

    @Test
    void findByConversationId_delegatesToMapper() {
        when(messageMapper.findByConversationId("conv1")).thenReturn(List.of(new Message()));

        List<Message> result = messageService.findByConversationId("conv1");

        assertEquals(1, result.size());
    }

    @Test
    void findBySessionId_delegatesToMapper() {
        when(messageMapper.findBySessionId("sess1")).thenReturn(Collections.emptyList());

        List<Message> result = messageService.findBySessionId("sess1");

        assertTrue(result.isEmpty());
    }

    @Test
    void findByConversationAndSession_delegatesToMapper() {
        when(messageMapper.findByConversationAndSession("conv1", "sess1"))
                .thenReturn(List.of(new Message(), new Message()));

        List<Message> result = messageService.findByConversationAndSession("conv1", "sess1");

        assertEquals(2, result.size());
    }
}
