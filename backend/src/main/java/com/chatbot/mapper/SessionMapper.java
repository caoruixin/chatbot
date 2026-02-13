package com.chatbot.mapper;

import com.chatbot.model.Session;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SessionMapper {

    Session findById(@Param("sessionId") String sessionId);

    Session findActiveByConversationId(@Param("conversationId") String conversationId);

    List<Session> findByConversationId(@Param("conversationId") String conversationId);

    List<Session> findByStatus(@Param("status") String status);

    List<Session> findActiveByAgentId(@Param("agentId") String agentId);

    void insert(Session session);

    void updateStatus(@Param("sessionId") String sessionId,
                      @Param("status") String status);

    void updateLastActivity(@Param("sessionId") String sessionId);

    void closeExpiredSessions(@Param("timeoutMinutes") int timeoutMinutes);
}
