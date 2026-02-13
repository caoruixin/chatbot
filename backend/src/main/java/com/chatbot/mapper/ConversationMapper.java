package com.chatbot.mapper;

import com.chatbot.model.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationMapper {

    Conversation findByUserId(@Param("userId") String userId);

    Conversation findById(@Param("conversationId") String conversationId);

    void insert(Conversation conversation);

    void updateStatus(@Param("conversationId") String conversationId,
                      @Param("status") String status);
}
