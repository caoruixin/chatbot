package com.chatbot.scheduler;

import com.chatbot.mapper.SessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionTimeoutScheduler.class);

    private final SessionMapper sessionMapper;

    @Value("${chatbot.session.timeout-minutes}")
    private int timeoutMinutes;

    public SessionTimeoutScheduler(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Scheduled(fixedRate = 60_000)
    public void checkTimeout() {
        try {
            sessionMapper.closeExpiredSessions(timeoutMinutes);
            log.debug("Session timeout check completed: timeoutMinutes={}", timeoutMinutes);
        } catch (Exception e) {
            log.error("Failed to close expired sessions: {}", e.getMessage());
        }
    }
}
