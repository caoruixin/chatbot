package com.chatbot.service;

import com.chatbot.exception.ConversationNotFoundException;
import com.chatbot.mapper.ConversationMapper;
import com.chatbot.model.Conversation;
import com.chatbot.service.stream.GetStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationMapper conversationMapper;
    private final GetStreamService getStreamService;

    public ConversationService(ConversationMapper conversationMapper,
                               GetStreamService getStreamService) {
        this.conversationMapper = conversationMapper;
        this.getStreamService = getStreamService;
    }

    public Conversation findOrCreate(String userId) {
        Conversation existing = conversationMapper.findByUserId(userId);
        if (existing != null) {
            log.info("Found existing conversation: conversationId={}, userId={}",
                    existing.getConversationId(), userId);
            return existing;
        }

        Conversation conv = new Conversation();
        conv.setConversationId(UUID.randomUUID());
        conv.setUserId(userId);
        conv.setStatus("ACTIVE");

        String channelId = "conv-" + conv.getConversationId().toString();
        conv.setGetstreamChannelId(channelId);

        conversationMapper.insert(conv);
        log.info("Created new conversation: conversationId={}, userId={}, channelId={}",
                conv.getConversationId(), userId, channelId);

        // Create GetStream channel and upsert user
        try {
            getStreamService.upsertUser(userId, userId);
            getStreamService.createChannel(channelId, userId);
        } catch (Exception e) {
            log.error("Failed to create GetStream channel: channelId={}, error={}",
                    channelId, e.getMessage());
        }

        return conv;
    }

    public Conversation findByUserId(String userId) {
        return conversationMapper.findByUserId(userId);
    }

    public Conversation findById(String conversationId) {
        Conversation conv = conversationMapper.findById(conversationId);
        if (conv == null) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conv;
    }
}
