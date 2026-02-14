package com.chatbot.service.stream;

import com.chatbot.config.GetStreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class GetStreamService {

    private static final Logger log = LoggerFactory.getLogger(GetStreamService.class);

    private final GetStreamConfig config;

    public GetStreamService(GetStreamConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        try {
            System.setProperty("io.getstream.chat.apiKey", config.getApiKey());
            System.setProperty("io.getstream.chat.apiSecret", config.getApiSecret());
            log.info("GetStream SDK initialized with API key");

            // Ensure "system" user exists for system messages
            upsertUser("system", "系统通知");
        } catch (Exception e) {
            log.error("Failed to initialize GetStream SDK", e);
        }
    }

    /**
     * Create a messaging channel with the given channelId and add the user as a member.
     */
    public void createChannel(String channelId, String userId) {
        try {
            var channelData = io.getstream.chat.java.models.Channel.ChannelRequestObject.builder()
                    .createdBy(io.getstream.chat.java.models.User.UserRequestObject.builder()
                            .id(userId)
                            .build())
                    .build();

            io.getstream.chat.java.models.Channel.getOrCreate("messaging", channelId)
                    .data(channelData)
                    .request();

            // Add user as member
            io.getstream.chat.java.models.Channel.update("messaging", channelId)
                    .addMember(userId)
                    .request();

            log.info("GetStream channel created: channelId={}, userId={}", channelId, userId);
        } catch (Exception e) {
            log.error("Failed to create GetStream channel: channelId={}, error={}",
                    channelId, e.getMessage());
        }
    }

    /**
     * Add a member to an existing channel.
     */
    public void addMember(String channelId, String userId) {
        try {
            io.getstream.chat.java.models.Channel.update("messaging", channelId)
                    .addMember(userId)
                    .request();
            log.info("Member added to channel: channelId={}, userId={}", channelId, userId);
        } catch (Exception e) {
            log.error("Failed to add member to channel: channelId={}, userId={}, error={}",
                    channelId, userId, e.getMessage());
        }
    }

    /**
     * Send a message to a channel on behalf of a user.
     */
    public void sendMessage(String channelId, String senderId, String content) {
        try {
            var messageRequest = io.getstream.chat.java.models.Message.MessageRequestObject.builder()
                    .text(content)
                    .userId(senderId)
                    .build();

            io.getstream.chat.java.models.Message.send("messaging", channelId)
                    .message(messageRequest)
                    .request();

            log.info("Message sent via GetStream: channelId={}, senderId={}", channelId, senderId);
        } catch (Exception e) {
            log.error("Failed to send message via GetStream: channelId={}, senderId={}, error={}",
                    channelId, senderId, e.getMessage());
        }
    }

    /**
     * Generate a user token for client-side authentication.
     */
    public String createToken(String userId) {
        try {
            return io.getstream.chat.java.models.User.createToken(userId, null, null);
        } catch (Exception e) {
            log.error("Failed to create token for user: userId={}, error={}", userId, e.getMessage());
            throw new RuntimeException("Failed to create GetStream token", e);
        }
    }

    /**
     * Create or update a user in GetStream.
     */
    public void upsertUser(String userId, String name) {
        try {
            var userRequest = io.getstream.chat.java.models.User.UserRequestObject.builder()
                    .id(userId)
                    .name(name)
                    .build();

            io.getstream.chat.java.models.User.upsert()
                    .user(userRequest)
                    .request();

            log.info("User upserted in GetStream: userId={}, name={}", userId, name);
        } catch (Exception e) {
            log.error("Failed to upsert user in GetStream: userId={}, error={}",
                    userId, e.getMessage());
        }
    }
}
