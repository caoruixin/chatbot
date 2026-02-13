package com.chatbot.service;

import com.chatbot.enums.SenderType;
import com.chatbot.mapper.MessageMapper;
import com.chatbot.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageMapper messageMapper;

    public MessageService(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public Message save(UUID conversationId, UUID sessionId,
                        SenderType senderType, String senderId, String content) {
        Message msg = new Message();
        msg.setMessageId(UUID.randomUUID());
        msg.setConversationId(conversationId);
        msg.setSessionId(sessionId);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setContent(content);

        messageMapper.insert(msg);
        log.info("Message saved: messageId={}, conversationId={}, sessionId={}, senderType={}",
                msg.getMessageId(), conversationId, sessionId, senderType);
        return msg;
    }

    public List<Message> findByConversationId(String conversationId) {
        return messageMapper.findByConversationId(conversationId);
    }

    public List<Message> findBySessionId(String sessionId) {
        return messageMapper.findBySessionId(sessionId);
    }

    public List<Message> findByConversationAndSession(String conversationId, String sessionId) {
        return messageMapper.findByConversationAndSession(conversationId, sessionId);
    }
}
