package com.chatbot.mapper;

import com.chatbot.model.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

    List<Message> findByConversationId(@Param("conversationId") String conversationId);

    List<Message> findBySessionId(@Param("sessionId") String sessionId);

    List<Message> findByConversationAndSession(@Param("conversationId") String conversationId,
                                               @Param("sessionId") String sessionId);

    void insert(Message message);
}
