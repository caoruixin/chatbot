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
 *
 * Phase 2: 支持多轮 turn-by-turn 执行，单轮走快速路径保证零行为变更。
 */
public class SyncAgentAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(SyncAgentAdapter.class);

    private static final String LOW_CONFIDENCE_REPLY = "抱歉，我不太确定您的意思。能否请您再详细描述一下您的问题？";
    private static final String IDENTITY_REJECT_REPLY = "抱歉，数据删除是敏感操作，需要先验证您的身份。请先登录后再发起此请求。";
    private static final String ERROR_REPLY = "抱歉，AI 助手暂时遇到问题。您可以发送\"转人工\"联系人工客服。";

    private static final Set<String> CONFIRMATION_KEYWORDS = Set.of(
            "确认删除", "确认", "确定", "是", "是的", "好的", "好", "同意"
    );

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
        List<Episode.ConversationTurn> turns = episode.getConversation();
        if (turns == null || turns.isEmpty()) {
            return buildSingleTurnResult(episode.getId(), "Episode has no conversation turns",
                    List.of(), List.of(), new Trace(), null, System.currentTimeMillis());
        }

        // 提取所有 user turns 和对应的 expectations
        List<UserTurnWithExpectation> userTurns = extractUserTurns(turns);

        // 单轮快速路径：走现有逻辑，保证零行为变更
        if (userTurns.size() == 1) {
            return runSingleTurn(episode, userTurns.get(0).userMessage, runConfig);
        }

        // 多轮路径
        return runMultiTurn(episode, userTurns, runConfig);
    }

    // ──────────────────────────────────────────────────────────────────
    // 单轮执行（Phase 1 原有逻辑，保证零行为变更）
    // ──────────────────────────────────────────────────────────────────

    private RunResult runSingleTurn(Episode episode, String userMessage,
                                     Map<String, Object> runConfig) {
        long startTime = System.currentTimeMillis();
        List<ToolAction> actions = new ArrayList<>();
        List<RetrievedContext> retrievedContexts = new ArrayList<>();
        Trace trace = new Trace();

        try {
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
                return buildSingleTurnResult(episode.getId(), LOW_CONFIDENCE_REPLY, actions,
                        retrievedContexts, trace, intent, startTime);
            }

            // 3.5 Identity gate: reject sensitive operations for unauthenticated users
            if ("critical".equals(intent.getRisk()) && !isUserLoggedIn(episode)) {
                return buildSingleTurnResult(episode.getId(), IDENTITY_REJECT_REPLY, actions,
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

            return buildSingleTurnResult(episode.getId(), reply, actions,
                    retrievedContexts, trace, intent, startTime);
        } catch (Exception e) {
            log.error("Eval episode failed: episodeId={}, error={}", episode.getId(), e.getMessage());
            return buildSingleTurnResult(episode.getId(), ERROR_REPLY, actions,
                    retrievedContexts, trace, null, startTime);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // 多轮执行引擎（Phase 2 新增）
    // ──────────────────────────────────────────────────────────────────

    private RunResult runMultiTurn(Episode episode,
                                   List<UserTurnWithExpectation> userTurns,
                                   Map<String, Object> runConfig) {
        long startTime = System.currentTimeMillis();
        List<TurnResult> turnResults = new ArrayList<>();
        List<ToolAction> allActions = new ArrayList<>();
        List<RetrievedContext> allContexts = new ArrayList<>();
        Trace trace = new Trace();
        List<KimiMessage> history = new ArrayList<>();

        // 跟踪上一轮是否有 pending confirmation
        PendingConfirmation pendingConfirmation = null;

        String lastReply = null;
        IntentResult lastIntent = null;

        for (int turnIdx = 0; turnIdx < userTurns.size(); turnIdx++) {
            UserTurnWithExpectation turnInput = userTurns.get(turnIdx);
            String userMessage = turnInput.userMessage;
            TurnExpectation expectation = turnInput.expectation;

            long turnStart = System.currentTimeMillis();
            List<ToolAction> turnActions = new ArrayList<>();
            List<RetrievedContext> turnContexts = new ArrayList<>();

            try {
                String reply;

                // 检查是否为确认操作（上一轮返回 needs_confirmation）
                if (pendingConfirmation != null && isConfirmation(userMessage)) {
                    reply = handleConfirmation(pendingConfirmation, turnActions, trace, turnIdx);
                    pendingConfirmation = null;
                } else {
                    pendingConfirmation = null;

                    // 1. 意图识别
                    long intentStart = System.currentTimeMillis();
                    IntentResult intent = intentRouter.recognize(userMessage, history);
                    lastIntent = intent;
                    long intentEnd = System.currentTimeMillis();

                    trace.addSpan(withTurnIndex(new TraceSpan("intent_recognition",
                            intentStart, intentEnd,
                            Map.of("intent", intent.getIntent(),
                                    "confidence", intent.getConfidence(),
                                    "risk", String.valueOf(intent.getRisk()))),
                            turnIdx));

                    // 2. Confidence check
                    if (intent.getConfidence() < confidenceThreshold) {
                        reply = LOW_CONFIDENCE_REPLY;
                    }
                    // 3. Identity gate
                    else if ("critical".equals(intent.getRisk()) && !isUserLoggedIn(episode)) {
                        reply = IDENTITY_REJECT_REPLY;
                    } else {
                        // 4. ReAct loop
                        ToolResult toolResult = null;
                        for (int round = 0; round < maxReactRounds; round++) {
                            ToolCall toolCall = reactPlanner.plan(
                                    intent, userMessage, history, toolResult);
                            if (toolCall == null) break;

                            long ts = System.currentTimeMillis();
                            toolResult = evalToolDispatcher.dispatch(toolCall);
                            long toolEnd = System.currentTimeMillis();
                            ToolAction action = ToolAction.fromToolCallAndResult(
                                    toolCall, toolResult, ts);
                            turnActions.add(action);

                            trace.addSpan(withTurnIndex(new TraceSpan("tool_call",
                                    ts, toolEnd,
                                    Map.of("tool", toolCall.getToolName(),
                                            "success", String.valueOf(toolResult.isSuccess()),
                                            "round", String.valueOf(round))),
                                    turnIdx));

                            if ("faq_search".equals(toolCall.getToolName())
                                    && toolResult.isSuccess()) {
                                captureRetrievedContexts(toolResult, turnContexts);
                            }

                            if (toolResult.needsConfirmation()) {
                                pendingConfirmation = new PendingConfirmation(
                                        toolCall.getToolName(), toolCall.getParams());
                                break;
                            }

                            if (toolResult.isSuccess() || !toolResult.isRetryable()) {
                                break;
                            }
                        }

                        // 5. 回复生成
                        reply = composeReply(intent, userMessage, toolResult, history);
                    }
                }

                lastReply = reply;

                // 6. 构建 TurnResult
                TurnResult turnResult = new TurnResult();
                turnResult.setTurnIndex(turnIdx);
                turnResult.setUserMessage(userMessage);
                turnResult.setAgentReply(reply);
                turnResult.setActions(turnActions);
                turnResult.setLatencyMs(System.currentTimeMillis() - turnStart);
                if (lastIntent != null) {
                    turnResult.setIntentSummary(new TurnResult.IntentSummary(
                            lastIntent.getIntent(), lastIntent.getConfidence(),
                            String.valueOf(lastIntent.getRisk())));
                }

                // 7. 检查中间 expectation
                if (expectation != null) {
                    List<String> violations = checkTurnExpectation(
                            expectation, reply, turnActions);
                    turnResult.setExpectation(expectation);
                    turnResult.setExpectationViolations(violations);
                }

                turnResults.add(turnResult);
                allActions.addAll(turnActions);
                allContexts.addAll(turnContexts);

                // 8. 更新 history（为下一轮提供上下文）
                history.add(new KimiMessage("user", userMessage));
                history.add(new KimiMessage("assistant", reply));

            } catch (Exception e) {
                log.error("Multi-turn episode failed at turn {}: episodeId={}, error={}",
                        turnIdx, episode.getId(), e.getMessage());
                lastReply = ERROR_REPLY;

                TurnResult errorTurn = new TurnResult();
                errorTurn.setTurnIndex(turnIdx);
                errorTurn.setUserMessage(userMessage);
                errorTurn.setAgentReply(ERROR_REPLY);
                errorTurn.setActions(List.of());
                errorTurn.setLatencyMs(System.currentTimeMillis() - turnStart);
                turnResults.add(errorTurn);
                break;
            }
        }

        // 构建最终 RunResult
        return buildMultiTurnResult(episode.getId(), lastReply, allActions,
                allContexts, trace, lastIntent, startTime, turnResults);
    }

    // ──────────────────────────────────────────────────────────────────
    // 确认流程处理
    // ──────────────────────────────────────────────────────────────────

    private static class PendingConfirmation {
        final String toolName;
        final Map<String, Object> toolParams;

        PendingConfirmation(String toolName, Map<String, Object> toolParams) {
            this.toolName = toolName;
            this.toolParams = toolParams;
        }
    }

    private boolean isConfirmation(String message) {
        return CONFIRMATION_KEYWORDS.contains(message.strip());
    }

    private String handleConfirmation(PendingConfirmation pending,
                                       List<ToolAction> turnActions,
                                       Trace trace, int turnIdx) {
        ToolCall confirmedCall = new ToolCall(
                pending.toolName, pending.toolParams, true);

        long ts = System.currentTimeMillis();
        ToolResult result = evalToolDispatcher.dispatch(confirmedCall);
        long endTs = System.currentTimeMillis();

        turnActions.add(ToolAction.fromToolCallAndResult(confirmedCall, result, ts));
        trace.addSpan(withTurnIndex(new TraceSpan("tool_call_confirmed",
                ts, endTs,
                Map.of("tool", pending.toolName,
                        "success", String.valueOf(result.isSuccess()),
                        "confirmed", "true")),
                turnIdx));

        if (result.isSuccess()) {
            return "您的数据删除请求已提交，预计 24 小时内处理完毕。如有疑问请联系人工客服。";
        } else {
            return "数据删除请求处理失败，请稍后重试或联系人工客服。";
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // UserTurn 提取
    // ──────────────────────────────────────────────────────────────────

    private static class UserTurnWithExpectation {
        final String userMessage;
        final TurnExpectation expectation;

        UserTurnWithExpectation(String userMessage, TurnExpectation expectation) {
            this.userMessage = userMessage;
            this.expectation = expectation;
        }
    }

    /**
     * 从 conversation 中提取 user turns，并关联后续的 assistant expectation。
     *
     * conversation 格式：
     *   [user, assistant?, user, assistant?, ...]
     * 其中 assistant 条目是可选的 expectation checkpoint。
     */
    private List<UserTurnWithExpectation> extractUserTurns(List<Episode.ConversationTurn> turns) {
        List<UserTurnWithExpectation> result = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            Episode.ConversationTurn turn = turns.get(i);
            if (!"user".equals(turn.getRole())) continue;

            TurnExpectation expectation = null;
            // 检查下一条是否为 assistant expectation
            if (i + 1 < turns.size()
                    && "assistant".equals(turns.get(i + 1).getRole())
                    && turns.get(i + 1).getExpectation() != null) {
                expectation = turns.get(i + 1).getExpectation();
            }
            result.add(new UserTurnWithExpectation(turn.getContent(), expectation));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    // 中间检查点评估
    // ──────────────────────────────────────────────────────────────────

    /**
     * 检查单轮 expectation。返回 violations 列表（空=通过）。
     * 不影响 overall pass/fail，仅用于诊断。
     */
    private List<String> checkTurnExpectation(TurnExpectation exp,
                                               String reply,
                                               List<ToolAction> actions) {
        List<String> violations = new ArrayList<>();

        // 1. successCondition 检查
        if (exp.getSuccessCondition() != null) {
            boolean met = checkTurnSuccessCondition(
                    exp.getSuccessCondition(), reply, actions);
            if (!met) {
                violations.add("TURN_EXPECTATION: successCondition '"
                        + exp.getSuccessCondition() + "' not met");
            }
        }

        // 2. mustNotClaim 检查
        if (exp.getMustNotClaim() != null && reply != null) {
            String lowerReply = reply.toLowerCase();
            for (String claim : exp.getMustNotClaim()) {
                if (lowerReply.contains(claim.toLowerCase())) {
                    violations.add("TURN_EXPECTATION: reply contains forbidden claim '"
                            + claim + "'");
                }
            }
        }

        // 3. mustNotCall 检查（任何状态的调用都算违规）
        if (exp.getMustNotCall() != null) {
            for (ToolAction action : actions) {
                if (exp.getMustNotCall().contains(action.getName())) {
                    violations.add("TURN_EXPECTATION: forbidden tool '"
                            + action.getName() + "' was called (status=" + action.getStatus() + ")");
                }
            }
        }

        // 4. mustCall 检查
        if (exp.getMustCall() != null) {
            for (String required : exp.getMustCall()) {
                boolean called = actions.stream()
                        .anyMatch(a -> required.equals(a.getName())
                                && ("ok".equals(a.getStatus())
                                || "needs_confirmation".equals(a.getStatus())));
                if (!called) {
                    violations.add("TURN_EXPECTATION: required tool '"
                            + required + "' was not called");
                }
            }
        }

        // 5. mustMention 检查
        if (exp.getMustMention() != null && reply != null) {
            String lowerReply = reply.toLowerCase();
            for (String keyword : exp.getMustMention()) {
                if (!lowerReply.contains(keyword.toLowerCase())) {
                    violations.add("TURN_EXPECTATION: reply missing required keyword '"
                            + keyword + "'");
                }
            }
        }

        return violations;
    }

    /**
     * 简化版 successCondition 检查，用于 turn-level 诊断。
     */
    private boolean checkTurnSuccessCondition(String condition,
                                               String reply,
                                               List<ToolAction> actions) {
        switch (condition) {
            case "clarification_asked":
                return reply != null && (reply.contains("？") || reply.contains("?")
                        || reply.contains("请问") || reply.contains("请提供")
                        || reply.contains("能否") || reply.contains("请先提供"));
            case "action_initiated":
                return actions.stream().anyMatch(a ->
                        "ok".equals(a.getStatus())
                                || "needs_confirmation".equals(a.getStatus()));
            case "faq_answered_from_kb":
                return actions.stream().anyMatch(a ->
                        "faq_search".equals(a.getName()) && "ok".equals(a.getStatus()))
                        && reply != null && !reply.isBlank();
            case "query_result_returned":
                return actions.stream().anyMatch(a ->
                        "post_query".equals(a.getName()) && "ok".equals(a.getStatus()))
                        && reply != null && !reply.isBlank();
            case "confirmation_requested":
                return actions.stream().anyMatch(a ->
                        "needs_confirmation".equals(a.getStatus()));
            case "confirmation_completed":
                return actions.stream().anyMatch(a ->
                        "ok".equals(a.getStatus()));
            default:
                return true;  // 未知条件不 fail
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // 构建结果
    // ──────────────────────────────────────────────────────────────────

    private TraceSpan withTurnIndex(TraceSpan span, int turnIndex) {
        span.setTurnIndex(turnIndex);
        return span;
    }

    private RunResult buildMultiTurnResult(String episodeId, String lastReply,
                                            List<ToolAction> allActions,
                                            List<RetrievedContext> allContexts,
                                            Trace trace, IntentResult lastIntent,
                                            long startTime,
                                            List<TurnResult> turnResults) {
        RunResult result = new RunResult();
        result.setEpisodeId(episodeId);
        result.setFinalReply(lastReply);
        result.setActions(allActions);
        result.setRetrievedContexts(allContexts);
        result.setTrace(trace);
        result.setTurnResults(turnResults);

        // Metrics
        EvalMetrics metrics = new EvalMetrics();
        metrics.setLatencyMs(System.currentTimeMillis() - startTime);
        metrics.setToolCallCount(allActions.size());
        metrics.setTurnsToResolve(turnResults.size());
        metrics.setResolutionType(determineResolutionType(turnResults, allActions));
        result.setMetrics(metrics);

        // Artifacts
        EvalArtifacts artifacts = new EvalArtifacts();
        if (lastIntent != null) {
            Map<String, Object> identityArtifacts = new LinkedHashMap<>();
            identityArtifacts.put("intent", lastIntent.getIntent());
            identityArtifacts.put("confidence", lastIntent.getConfidence());
            identityArtifacts.put("risk", lastIntent.getRisk());
            artifacts.setIdentityArtifacts(identityArtifacts);
        }
        Map<String, Object> executionArtifacts = new LinkedHashMap<>();
        executionArtifacts.put("toolCallCount", allActions.size());
        executionArtifacts.put("retrievedContextCount", allContexts.size());
        executionArtifacts.put("totalTurns", turnResults.size());
        artifacts.setExecutionArtifacts(executionArtifacts);

        Map<String, Object> composerArtifacts = new LinkedHashMap<>();
        composerArtifacts.put("replyLength", lastReply != null ? lastReply.length() : 0);
        artifacts.setComposerArtifacts(composerArtifacts);

        result.setArtifacts(artifacts);
        return result;
    }

    private String determineResolutionType(List<TurnResult> turnResults,
                                            List<ToolAction> allActions) {
        // 检查是否有转人工动作
        boolean hasEscalation = allActions.stream()
                .anyMatch(a -> "transfer_to_human".equals(a.getName()));
        if (hasEscalation) return "ESCALATED";

        // 检查最后一轮是否为 error
        if (!turnResults.isEmpty()) {
            TurnResult last = turnResults.get(turnResults.size() - 1);
            if (last.getAgentReply() != null
                    && last.getAgentReply().contains("AI 助手暂时遇到问题")) {
                return "ABANDONED";
            }
        }

        return "AI_RESOLVED";
    }

    /**
     * 单轮结果构建（Phase 1 原有逻辑，保持不变）
     */
    private RunResult buildSingleTurnResult(String episodeId, String reply,
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
        composerArtifacts.put("replyLength", reply != null ? reply.length() : 0);
        artifacts.setComposerArtifacts(composerArtifacts);

        result.setArtifacts(artifacts);

        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    // 共用辅助方法
    // ──────────────────────────────────────────────────────────────────

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

    private boolean isUserLoggedIn(Episode episode) {
        if (episode.getInitialState() == null || episode.getInitialState().getUser() == null) {
            return false;
        }
        Object loggedIn = episode.getInitialState().getUser().get("is_logged_in");
        return Boolean.TRUE.equals(loggedIn);
    }
}
