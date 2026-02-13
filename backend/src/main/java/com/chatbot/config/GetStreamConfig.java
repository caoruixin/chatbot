package com.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GetStreamConfig {

    private final String apiKey;
    private final String apiSecret;

    public GetStreamConfig(@Value("${getstream.api-key}") String apiKey,
                           @Value("${getstream.api-secret}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }
}
