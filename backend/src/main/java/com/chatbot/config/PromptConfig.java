package com.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads prompt templates from external resource files.
 * Prompt files are stored in src/main/resources/prompts/ by default,
 * and can be overridden via application.yml configuration.
 */
@Component
public class PromptConfig {

    private static final Logger log = LoggerFactory.getLogger(PromptConfig.class);

    @Value("${chatbot.prompts.intent-router:classpath:prompts/intent-router.md}")
    private Resource intentRouterResource;

    @Value("${chatbot.prompts.response-composer:classpath:prompts/response-composer.md}")
    private Resource responseComposerResource;

    @Value("${chatbot.prompts.faq-matcher:classpath:prompts/faq-matcher.md}")
    private Resource faqMatcherResource;

    private String intentRouterPrompt;
    private String responseComposerPrompt;
    private String faqMatcherPrompt;

    @PostConstruct
    public void init() {
        intentRouterPrompt = loadPrompt(intentRouterResource, "intent-router");
        responseComposerPrompt = loadPrompt(responseComposerResource, "response-composer");
        faqMatcherPrompt = loadPrompt(faqMatcherResource, "faq-matcher");
        log.info("Loaded {} prompt templates", 3);
    }

    private String loadPrompt(Resource resource, String name) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.info("Loaded prompt '{}' from {}", name, resource.getDescription());
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt '" + name + "' from " + resource, e);
        }
    }

    public String getIntentRouterPrompt() {
        return intentRouterPrompt;
    }

    public String getResponseComposerPrompt() {
        return responseComposerPrompt;
    }

    public String getFaqMatcherPrompt() {
        return faqMatcherPrompt;
    }
}
