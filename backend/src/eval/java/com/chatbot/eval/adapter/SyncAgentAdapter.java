package com.chatbot.eval.adapter;

import com.chatbot.eval.model.*;
import com.chatbot.service.agent.IntentResult;
import com.chatbot.service.agent.IntentRouter;
import com.chatbot.service.agent.ReactPlanner;
import com.chatbot.service.agent.ResponseComposer;
import com.chatbot.service.llm.KimiMessage;
import com.chatbot.service.tool.ToolCall;
import com.chatbot.service.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Synchronous agent adapter that recomposes sub-components without side effects.
 * Mirrors AgentCore logic but returns RunResult instead of sending messages.
 */
public class SyncAgentAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(SyncAgentAdapter.class);

    private static final String LOW_CONFIDENCE_REPLY = "抱歉，我不太确定您的意思。能否请您再详细描述一下您的问题？";

    private final IntentRouter intentRouter;
    private final ReactPlanner reactPlanner;
    private final ResponseComposer responseComposer;
    private final EvalToolDispatcher evalToolDispatcher;
    private final int maxReactRounds;
    private final double confidenceThreshold;

    public SyncAgentAdapter(IntentRouter intentRouter,
                            ReactPlanner reactPlanner,
                            ResponseComposer responseComposer,
                            EvalToolDispatcher evalToolDispatcher,
                            int maxReactRounds,
                            double confidenceThreshold) {
        this.intentRouter = intentRouter;
        this.reactPlanner = reactPlanner;
        this.responseComposer = responseComposer;
        this.evalToolDispatcher = evalToolDispatcher;
        this.maxReactRounds = maxReactRounds;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override
    public RunResult runEpisode(Episode episode, Map<String, Object> runConfig) {
        long startTime = System.currentTimeMillis();
        List<ToolAction> actions = new ArrayList<>();
        List<RetrievedContext> retrievedContexts = new ArrayList<>();
        Trace trace = new Trace();

        try {
            // 1. Extract user message from episode (Iter0: single-turn)
            if (episode.getConversation() == null || episode.getConversation().isEmpty()) {
                return buildResult(episode.getId(), "Episode has no conversation turns", actions,
                        retrievedContexts, trace, null, startTime);
            }
            String userMessage = episode.getConversation().get(0).getContent();
            List<KimiMessage> history = List.of();

            // 2. Intent recognition
            long intentStart = System.currentTimeMillis();
            IntentResult intent = intentRouter.recognize(userMessage, history);
            long intentEnd = System.currentTimeMillis();
            log.info("Eval intent: episodeId={}, intent={}, confidence={}, risk={}",
                    episode.getId(), intent.getIntent(), intent.getConfidence(), intent.getRisk());

            trace.addSpan(new TraceSpan("intent_recognition", intentStart, intentEnd,
                    Map.of("intent", intent.getIntent(),
                            "confidence", intent.getConfidence(),
                            "risk", String.valueOf(intent.getRisk()))));

            // 3. Confidence check -> early return with clarification
            if (intent.getConfidence() < confidenceThreshold) {
                return buildResult(episode.getId(), LOW_CONFIDENCE_REPLY, actions,
                        retrievedContexts, trace, intent, startTime);
            }

            // 4. ReAct loop (mirrors AgentCore logic)
            ToolResult toolResult = null;
            for (int round = 0; round < maxReactRounds; round++) {
                ToolCall toolCall = reactPlanner.plan(intent, userMessage, history, toolResult);
                if (toolCall == null) {
                    break;
                }

                long ts = System.currentTimeMillis();
                toolResult = evalToolDispatcher.dispatch(toolCall);
                long toolEnd = System.currentTimeMillis();
                actions.add(ToolAction.fromToolCallAndResult(toolCall, toolResult, ts));

                trace.addSpan(new TraceSpan("tool_call", ts, toolEnd,
                        Map.of("tool", toolCall.getToolName(),
                                "success", String.valueOf(toolResult.isSuccess()),
                                "round", String.valueOf(round))));

                // Capture retrieved contexts from FAQ search results
                if ("faq_search".equals(toolCall.getToolName()) && toolResult.isSuccess()) {
                    captureRetrievedContexts(toolResult, retrievedContexts);
                }

                if (toolResult.isSuccess() || toolResult.needsConfirmation() || !toolResult.isRetryable()) {
                    break;
                }
            }

            // 5. Response composition (mirrors AgentCore.composeReply)
            long composeStart = System.currentTimeMillis();
            String reply = composeReply(intent, userMessage, toolResult, history);
            long composeEnd = System.currentTimeMillis();

            trace.addSpan(new TraceSpan("response_composition", composeStart, composeEnd,
                    Map.of("replyLength", String.valueOf(reply.length()))));

            return buildResult(episode.getId(), reply, actions,
                    retrievedContexts, trace, intent, startTime);
        } catch (Exception e) {
            log.error("Eval episode failed: episodeId={}, error={}", episode.getId(), e.getMessage());
            String errorReply = "抱歉，AI 助手暂时遇到问题。您可以发送\"转人工\"联系人工客服。";
            return buildResult(episode.getId(), errorReply, actions,
                    retrievedContexts, trace, null, startTime);
        }
    }

    @SuppressWarnings("unchecked")
    private void captureRetrievedContexts(ToolResult toolResult, List<RetrievedContext> contexts) {
        try {
            String json = toolResult.toJson();
            if (json == null || json.isBlank()) return;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> wrapper = mapper.readValue(json, Map.class);

            // ToolResult.toJson() wraps data inside {"success":true, "data":{...}, "error":""}
            Object dataObj = wrapper.get("data");
            if (!(dataObj instanceof Map)) return;
            Map<String, Object> data = (Map<String, Object>) dataObj;

            String question = (String) data.get("question");
            double score = 0.0;
            Object scoreObj = data.get("score");
            if (scoreObj instanceof Number) {
                score = ((Number) scoreObj).doubleValue();
            }

            if (question != null && !question.isBlank() && score > 0) {
                contexts.add(new RetrievedContext(null, question, score));
            }
        } catch (Exception e) {
            log.debug("Failed to capture retrieved context: {}", e.getMessage());
        }
    }

    private String composeReply(IntentResult intent, String userMessage,
                                ToolResult toolResult, List<KimiMessage> history) {
        String intentType = intent.getIntent();

        if ("GENERAL_CHAT".equals(intentType)) {
            return responseComposer.composeWithEvidence(userMessage, intent, toolResult, history);
        }

        if ("critical".equals(intent.getRisk())) {
            return responseComposer.composeFromTemplate(intent, toolResult);
        }

        if (toolResult == null) {
            if ("POST_QUERY".equals(intentType)) {
                return "请问您要查询哪位用户的帖子？请提供用户名，例如：\"帮我查一下user_alice的帖子\"。";
            }
            return responseComposer.composeWithEvidence(userMessage, intent, toolResult, history);
        }

        return responseComposer.composeWithEvidence(userMessage, intent, toolResult, history);
    }

    private RunResult buildResult(String episodeId, String reply,
                                  List<ToolAction> actions,
                                  List<RetrievedContext> retrievedContexts,
                                  Trace trace, IntentResult intent,
                                  long startTime) {
        RunResult result = new RunResult();
        result.setEpisodeId(episodeId);
        result.setFinalReply(reply);
        result.setActions(actions);
        result.setRetrievedContexts(retrievedContexts);
        result.setTrace(trace);

        EvalMetrics metrics = new EvalMetrics();
        metrics.setLatencyMs(System.currentTimeMillis() - startTime);
        metrics.setToolCallCount(actions.size());
        result.setMetrics(metrics);

        // Populate artifacts
        EvalArtifacts artifacts = new EvalArtifacts();
        if (intent != null) {
            Map<String, Object> identityArtifacts = new LinkedHashMap<>();
            identityArtifacts.put("intent", intent.getIntent());
            identityArtifacts.put("confidence", intent.getConfidence());
            identityArtifacts.put("risk", intent.getRisk());
            artifacts.setIdentityArtifacts(identityArtifacts);
        }

        Map<String, Object> executionArtifacts = new LinkedHashMap<>();
        executionArtifacts.put("toolCallCount", actions.size());
        executionArtifacts.put("retrievedContextCount", retrievedContexts.size());
        artifacts.setExecutionArtifacts(executionArtifacts);

        Map<String, Object> composerArtifacts = new LinkedHashMap<>();
        composerArtifacts.put("replyLength", reply.length());
        artifacts.setComposerArtifacts(composerArtifacts);

        result.setArtifacts(artifacts);

        return result;
    }
}
