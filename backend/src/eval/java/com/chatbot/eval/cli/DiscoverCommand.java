package com.chatbot.eval.cli;

import com.chatbot.config.KimiConfig;
import com.chatbot.config.PromptConfig;
import com.chatbot.eval.discovery.DiscoveryExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DiscoverCommand {

    private static final Logger log = LoggerFactory.getLogger(DiscoverCommand.class);

    private final KimiConfig kimiConfig;
    private final PromptConfig promptConfig;
    private final int maxReactRounds;
    private final double confidenceThreshold;

    public DiscoverCommand(KimiConfig kimiConfig,
                           PromptConfig promptConfig,
                           @Value("${chatbot.ai.max-react-rounds:3}") int maxReactRounds,
                           @Value("${chatbot.ai.confidence-threshold:0.7}") double confidenceThreshold) {
        this.kimiConfig = kimiConfig;
        this.promptConfig = promptConfig;
        this.maxReactRounds = maxReactRounds;
        this.confidenceThreshold = confidenceThreshold;
    }

    public void execute(String[] args) {
        String outputPath = getArg(args, "--out", "discovery.json");

        log.info("Discover: output={}", outputPath);

        try {
            DiscoveryExporter exporter = new DiscoveryExporter(
                    kimiConfig, promptConfig, maxReactRounds, confidenceThreshold);
            exporter.export(outputPath);

            System.out.println("\n=== Discovery Complete ===");
            System.out.println("Output: " + outputPath);

        } catch (Exception e) {
            log.error("Discover failed: {}", e.getMessage(), e);
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private String getArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
