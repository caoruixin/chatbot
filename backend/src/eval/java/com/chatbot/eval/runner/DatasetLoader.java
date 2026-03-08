package com.chatbot.eval.runner;

import com.chatbot.eval.model.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Supports both Phase 0 flat format and Phase 1 layered format.
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
                    // Detect layered format: check if expected.outcome is a JSON object
                    JsonNode node = objectMapper.readTree(line);
                    JsonNode expectedNode = node.get("expected");
                    if (expectedNode != null && expectedNode.has("outcome") && expectedNode.get("outcome").isObject()) {
                        // Layered format: rename "outcome" to "outcomeExpected" for Jackson
                        Episode episode = parseLayeredEpisode(node);
                        episodes.add(episode);
                    } else {
                        // Flat format: parse directly, then normalize
                        Episode episode = objectMapper.treeToValue(node, Episode.class);
                        normalizeExpected(episode);
                        episodes.add(episode);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse episode at line {}: {}", lineNum, e.getMessage());
                }
            }
        }

        log.info("Loaded {} episodes from {}", episodes.size(), datasetPath);
        return episodes;
    }

    /**
     * Parse layered format episode where expected.outcome is an object (OutcomeExpected).
     */
    private Episode parseLayeredEpisode(JsonNode node) throws IOException {
        Episode episode = new Episode();
        episode.setId(node.has("id") ? node.get("id").asText() : null);
        episode.setSuite(node.has("suite") ? node.get("suite").asText() : null);

        if (node.has("tags")) {
            List<String> tags = new ArrayList<>();
            node.get("tags").forEach(t -> tags.add(t.asText()));
            episode.setTags(tags);
        }

        if (node.has("initialState")) {
            episode.setInitialState(objectMapper.treeToValue(node.get("initialState"), Episode.InitialState.class));
        }

        if (node.has("conversation")) {
            List<Episode.ConversationTurn> turns = new ArrayList<>();
            for (JsonNode turn : node.get("conversation")) {
                turns.add(objectMapper.treeToValue(turn, Episode.ConversationTurn.class));
            }
            episode.setConversation(turns);
        }

        // Parse expected with layered structure
        JsonNode expectedNode = node.get("expected");
        if (expectedNode != null) {
            EpisodeExpected expected = new EpisodeExpected();

            if (expectedNode.has("gate")) {
                expected.setGate(objectMapper.treeToValue(expectedNode.get("gate"), GateExpected.class));
            }
            if (expectedNode.has("outcome")) {
                expected.setOutcomeExpected(objectMapper.treeToValue(expectedNode.get("outcome"), OutcomeExpected.class));
            }
            if (expectedNode.has("trajectory")) {
                expected.setTrajectory(objectMapper.treeToValue(expectedNode.get("trajectory"), TrajectoryExpected.class));
            }
            if (expectedNode.has("replyQuality")) {
                expected.setReplyQuality(objectMapper.treeToValue(expectedNode.get("replyQuality"), ReplyQualityExpected.class));
            }

            episode.setExpected(expected);
        }

        return episode;
    }

    /**
     * Normalize Phase 0 flat expected to Phase 1 layered structure.
     * If layered fields already exist, skip.
     */
    private void normalizeExpected(Episode episode) {
        EpisodeExpected exp = episode.getExpected();
        if (exp == null) return;

        // If any layered field already exists, this is already normalized
        if (exp.getGate() != null || exp.getOutcomeExpected() != null
                || exp.getTrajectory() != null || exp.getReplyQuality() != null) {
            return;
        }

        // L1 Gate: mustNot + mustMention (from replyConstraints)
        GateExpected gate = new GateExpected();
        gate.setMustNot(exp.getMustNot());
        if (exp.getReplyConstraints() != null) {
            gate.setMustMention(exp.getReplyConstraints().getMustMention());
        }
        exp.setGate(gate);

        // L2 Outcome: sideEffects
        OutcomeExpected outcome = new OutcomeExpected();
        outcome.setSideEffects(exp.getSideEffects());
        exp.setOutcomeExpected(outcome);

        // L3 Trajectory: mustCall + toolArgConstraints
        TrajectoryExpected trajectory = new TrajectoryExpected();
        trajectory.setMustCall(exp.getMustCall());
        trajectory.setToolArgConstraints(exp.getToolArgConstraints());
        exp.setTrajectory(trajectory);

        // L4 ReplyQuality: replyConstraints + goldenReply + expectedContexts + faithfulnessCheck
        ReplyQualityExpected replyQuality = new ReplyQualityExpected();
        replyQuality.setReplyConstraints(exp.getReplyConstraints());
        replyQuality.setGoldenReply(exp.getGoldenReply());
        replyQuality.setExpectedContexts(exp.getExpectedContexts());
        replyQuality.setFaithfulnessCheck(exp.getFaithfulnessCheck());
        exp.setReplyQuality(replyQuality);
    }
}
