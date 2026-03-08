package com.chatbot.eval.fingerprint;

import com.chatbot.config.KimiConfig;
import com.chatbot.config.PromptConfig;
import com.chatbot.eval.model.VersionFingerprint;
import com.chatbot.service.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes a version fingerprint for the agent configuration.
 */
public class FingerprintGenerator {

    private static final Logger log = LoggerFactory.getLogger(FingerprintGenerator.class);

    private final KimiConfig kimiConfig;
    private final PromptConfig promptConfig;
    private final int maxReactRounds;
    private final double confidenceThreshold;

    public FingerprintGenerator(KimiConfig kimiConfig, PromptConfig promptConfig,
                                int maxReactRounds, double confidenceThreshold) {
        this.kimiConfig = kimiConfig;
        this.promptConfig = promptConfig;
        this.maxReactRounds = maxReactRounds;
        this.confidenceThreshold = confidenceThreshold;
    }

    public VersionFingerprint generate() {
        VersionFingerprint fp = new VersionFingerprint();

        // Model ID
        fp.setModelId(kimiConfig.getChatModel());

        // Prompt hashes
        Map<String, String> promptHashes = new LinkedHashMap<>();
        promptHashes.put("intent_system_prompt", sha256(promptConfig.getIntentRouterPrompt()));
        promptHashes.put("evidence_system_prompt", sha256(promptConfig.getResponseComposerPrompt()));
        fp.setPromptHashes(promptHashes);

        // Workflow version
        String workflowStr = String.format("max_react_rounds=%d,confidence_threshold=%.2f",
                maxReactRounds, confidenceThreshold);
        fp.setWorkflowVersion(sha256(workflowStr));

        // Tool schema version
        StringBuilder toolSchema = new StringBuilder();
        for (ToolDefinition def : ToolDefinition.values()) {
            toolSchema.append(def.getName()).append(":").append(def.getDescription())
                    .append(":").append(def.getRisk().name()).append(";");
        }
        fp.setToolSchemaVersion(sha256(toolSchema.toString()));

        // KB snapshot (placeholder - would need DB access for real implementation)
        fp.setKbSnapshot("unknown");

        // Git commit
        fp.setGitCommit(getGitCommit());

        // Composite hash
        String composite = String.join("|",
                fp.getModelId(),
                String.join(",", promptHashes.values()),
                fp.getWorkflowVersion(),
                fp.getToolSchemaVersion(),
                fp.getKbSnapshot(),
                fp.getGitCommit());
        fp.setCompositeHash(sha256(composite));

        return fp;
    }

    private String getGitCommit() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.strip() : "unknown";
            }
        } catch (Exception e) {
            log.warn("Failed to get git commit: {}", e.getMessage());
            return "unknown";
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 12); // first 12 chars
        } catch (Exception e) {
            return "hash_error";
        }
    }
}
