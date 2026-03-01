package com.chatbot.eval.runner;

import com.chatbot.eval.model.EvalScore;
import com.chatbot.eval.model.EvalSummary;
import com.chatbot.eval.model.RunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes eval results to the output directory structure.
 */
public class ResultWriter {

    private static final Logger log = LoggerFactory.getLogger(ResultWriter.class);

    private final ObjectMapper objectMapper;

    public ResultWriter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void writeResults(String outputDir, EvalSummary summary,
                             Map<String, RunResult> runResults,
                             Map<String, EvalScore> scores) throws IOException {
        Path outPath = Path.of(outputDir);
        Path episodesPath = outPath.resolve("episodes");
        Files.createDirectories(episodesPath);

        // Write run_meta.json
        Map<String, Object> runMeta = new LinkedHashMap<>();
        runMeta.put("fingerprint", summary.getFingerprint());
        runMeta.put("timestamp", summary.getTimestamp() != null ? summary.getTimestamp().toString() : null);
        runMeta.put("totalEpisodes", summary.getTotalEpisodes());
        writeJson(outPath.resolve("run_meta.json"), runMeta);

        // Write per-episode results
        for (Map.Entry<String, RunResult> entry : runResults.entrySet()) {
            String episodeId = entry.getKey();
            Map<String, Object> episodeData = new LinkedHashMap<>();
            episodeData.put("runResult", entry.getValue());
            episodeData.put("score", scores.get(episodeId));
            writeJson(episodesPath.resolve(episodeId + ".json"), episodeData);
        }

        // Write summary.json
        writeJson(outPath.resolve("summary.json"), summary);

        log.info("Results written to {}", outputDir);
    }

    public void writeJson(Path path, Object data) throws IOException {
        Files.writeString(path, objectMapper.writeValueAsString(data));
    }
}
