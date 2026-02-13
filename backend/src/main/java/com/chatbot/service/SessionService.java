package com.chatbot.service;

import com.chatbot.exception.SessionNotFoundException;
import com.chatbot.mapper.SessionMapper;
import com.chatbot.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionMapper sessionMapper;

    public SessionService(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    public Session findActiveOrCreate(UUID conversationId) {
        Session existing = sessionMapper.findActiveByConversationId(conversationId.toString());
        if (existing != null) {
            log.info("Found active session: sessionId={}, conversationId={}",
                    existing.getSessionId(), conversationId);
            return existing;
        }

        Session session = new Session();
        session.setSessionId(UUID.randomUUID());
        session.setConversationId(conversationId);
        session.setStatus("AI_HANDLING");

        sessionMapper.insert(session);
        log.info("Created new session: sessionId={}, conversationId={}",
                session.getSessionId(), conversationId);
        return session;
    }

    public Session findById(String sessionId) {
        Session session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return session;
    }

    public void updateStatus(UUID sessionId, String status) {
        sessionMapper.updateStatus(sessionId.toString(), status);
        log.info("Session status updated: sessionId={}, newStatus={}", sessionId, status);
    }

    public void updateLastActivity(UUID sessionId) {
        sessionMapper.updateLastActivity(sessionId.toString());
    }

    public List<Session> findActiveByAgentId(String agentId) {
        return sessionMapper.findActiveByAgentId(agentId);
    }

    public List<Session> findByConversationId(String conversationId) {
        return sessionMapper.findByConversationId(conversationId);
    }
}
