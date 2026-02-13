package com.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KimiConfig {

    @Value("${kimi.api-key}")
    private String apiKey;

    @Value("${kimi.base-url}")
    private String baseUrl;

    @Value("${kimi.chat.model}")
    private String chatModel;

    @Value("${kimi.chat.temperature}")
    private double chatTemperature;

    @Value("${kimi.chat.timeout-seconds}")
    private int chatTimeoutSeconds;

    @Value("${kimi.embedding.model}")
    private String embeddingModel;

    @Bean
    public RestTemplate kimiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(chatTimeoutSeconds * 1000);
        return new RestTemplate(factory);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public double getChatTemperature() {
        return chatTemperature;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }
}
