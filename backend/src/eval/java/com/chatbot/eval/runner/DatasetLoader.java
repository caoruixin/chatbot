package com.chatbot.eval.runner;

import com.chatbot.eval.model.Episode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads episodes from a JSONL file (one JSON object per line).
 */
public class DatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);

    private final ObjectMapper objectMapper;

    public DatasetLoader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<Episode> load(String datasetPath) throws IOException {
        Path path = Path.of(datasetPath);
        if (!Files.exists(path)) {
            throw new IOException("Dataset file not found: " + datasetPath);
        }

        List<Episode> episodes = new ArrayList<>();
        int lineNum = 0;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                try {
                    Episode episode = objectMapper.readValue(line, Episode.class);
                    episodes.add(episode);
                } catch (Exception e) {
                    log.warn("Failed to parse episode at line {}: {}", lineNum, e.getMessage());
                }
            }
        }

        log.info("Loaded {} episodes from {}", episodes.size(), datasetPath);
        return episodes;
    }
}
