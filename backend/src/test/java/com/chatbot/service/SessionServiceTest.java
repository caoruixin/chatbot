package com.chatbot.service;

import com.chatbot.enums.SessionStatus;
import com.chatbot.exception.SessionNotFoundException;
import com.chatbot.mapper.SessionMapper;
import com.chatbot.model.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionMapper sessionMapper;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void findActiveOrCreate_activeSessionExists_returnsExisting() {
        UUID conversationId = UUID.randomUUID();
        Session existing = new Session();
        existing.setSessionId(UUID.randomUUID());
        existing.setConversationId(conversationId);
        when(sessionMapper.findActiveByConversationId(conversationId.toString())).thenReturn(existing);

        Session result = sessionService.findActiveOrCreate(conversationId);

        assertSame(existing, result);
        verify(sessionMapper, never()).insert(any());
    }

    @Test
    void findActiveOrCreate_noActiveSession_createsNew() {
        UUID conversationId = UUID.randomUUID();
        when(sessionMapper.findActiveByConversationId(conversationId.toString())).thenReturn(null);

        Session result = sessionService.findActiveOrCreate(conversationId);

        assertNotNull(result);
        assertEquals(conversationId, result.getConversationId());
        assertEquals(SessionStatus.AI_HANDLING, result.getStatus());
        assertNotNull(result.getSessionId());
        verify(sessionMapper).insert(any(Session.class));
    }

    @Test
    void findById_exists_returnsSession() {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID());
        String id = session.getSessionId().toString();
        when(sessionMapper.findById(id)).thenReturn(session);

        Session result = sessionService.findById(id);

        assertSame(session, result);
    }

    @Test
    void findById_notExists_throwsException() {
        String id = UUID.randomUUID().toString();
        when(sessionMapper.findById(id)).thenReturn(null);

        assertThrows(SessionNotFoundException.class,
                () -> sessionService.findById(id));
    }

    @Test
    void updateStatus_delegatesToMapper() {
        UUID sessionId = UUID.randomUUID();

        sessionService.updateStatus(sessionId, SessionStatus.HUMAN_HANDLING);

        verify(sessionMapper).updateStatus(sessionId.toString(), "HUMAN_HANDLING");
    }

    @Test
    void updateLastActivity_delegatesToMapper() {
        UUID sessionId = UUID.randomUUID();

        sessionService.updateLastActivity(sessionId);

        verify(sessionMapper).updateLastActivity(sessionId.toString());
    }

    @Test
    void findActiveByAgentId_delegatesToMapper() {
        List<Session> sessions = List.of(new Session());
        when(sessionMapper.findActiveByAgentId("agent1")).thenReturn(sessions);

        List<Session> result = sessionService.findActiveByAgentId("agent1");

        assertEquals(1, result.size());
    }

    @Test
    void findByConversationId_delegatesToMapper() {
        when(sessionMapper.findByConversationId("conv1")).thenReturn(Collections.emptyList());

        List<Session> result = sessionService.findByConversationId("conv1");

        assertTrue(result.isEmpty());
    }
}
