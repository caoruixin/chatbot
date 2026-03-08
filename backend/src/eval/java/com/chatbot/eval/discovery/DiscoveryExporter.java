package com.chatbot.eval.discovery;

import com.chatbot.config.KimiConfig;
import com.chatbot.config.PromptConfig;
import com.chatbot.eval.fingerprint.FingerprintGenerator;
import com.chatbot.service.tool.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Exports discovery.json with tool registry, prompts, workflow, and model config.
 */
public class DiscoveryExporter {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryExporter.class);

    private final KimiConfig kimiConfig;
    private final PromptConfig promptConfig;
    private final int maxReactRounds;
    private final double confidenceThreshold;

    public DiscoveryExporter(KimiConfig kimiConfig, PromptConfig promptConfig,
                             int maxReactRounds, double confidenceThreshold) {
        this.kimiConfig = kimiConfig;
        this.promptConfig = promptConfig;
        this.maxReactRounds = maxReactRounds;
        this.confidenceThreshold = confidenceThreshold;
    }

    public void export(String outputPath) throws IOException {
        Map<String, Object> discovery = new LinkedHashMap<>();

        // Tool registry
        List<Map<String, Object>> toolRegistry = new ArrayList<>();
        for (ToolDefinition def : ToolDefinition.values()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", def.getName());
            tool.put("description", def.getDescription());
            tool.put("risk_level", def.getRisk().name());
            tool.put("params_schema", getParamsSchema(def));
            toolRegistry.add(tool);
        }
        discovery.put("tool_registry", toolRegistry);

        // Prompts
        Map<String, Object> prompts = new LinkedHashMap<>();
        String intentPrompt = promptConfig.getIntentRouterPrompt();
        prompts.put("intent_system_prompt", Map.of(
                "hash", FingerprintGenerator.sha256(intentPrompt),
                "length", intentPrompt.length()));
        String evidencePrompt = promptConfig.getResponseComposerPrompt();
        prompts.put("evidence_system_prompt", Map.of(
                "hash", FingerprintGenerator.sha256(evidencePrompt),
                "length", evidencePrompt.length()));
        discovery.put("prompts", prompts);

        // Workflow
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("sub_agents", List.of("IntentRouter", "ReactPlanner", "ResponseComposer"));
        workflow.put("max_react_rounds", maxReactRounds);
        workflow.put("confidence_threshold", confidenceThreshold);
        workflow.put("routing_strategy", "intent_based");
        discovery.put("workflow", workflow);

        // Model config
        Map<String, Object> modelConfig = new LinkedHashMap<>();
        modelConfig.put("chat_model", kimiConfig.getChatModel());
        modelConfig.put("chat_temperature", kimiConfig.getChatTemperature());
        modelConfig.put("embedding_model", "text-embedding-v4");
        discovery.put("model_config", modelConfig);

        // KB snapshot method
        discovery.put("kb_snapshot_method", "FAQ doc count + content hash from faq_doc table via pgvector");

        // Write JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writeValueAsString(discovery));
        log.info("Discovery exported to {}", outputPath);
    }

    private Map<String, Object> getParamsSchema(ToolDefinition def) {
        Map<String, Object> schema = new LinkedHashMap<>();
        switch (def) {
            case FAQ_SEARCH -> {
                schema.put("query", Map.of("type", "string", "required", true,
                        "description", "Search query for FAQ knowledge base"));
            }
            case POST_QUERY -> {
                schema.put("username", Map.of("type", "string", "required", true,
                        "description", "Username to query posts for"));
            }
            case USER_DATA_DELETE -> {
                schema.put("username", Map.of("type", "string", "required", true,
                        "description", "Username whose data should be deleted"));
            }
        }
        return schema;
    }
}
