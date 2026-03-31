# Evaluation Framework Phase 2 Tech Design — 多轮对话支持

## Context

Phase 1 已交付分层 4 评估器体系（L1 Gate → L2 Outcome → L3 Trajectory → L4 ReplyQuality）。但当前 SyncAgentAdapter 只处理 conversation 中的第一条用户消息，无法覆盖客服 Agent 的核心能力——先验证身份、收集信息、再执行操作的多轮交互流程。

本阶段的目标：让评估框架支持多轮对话场景，同时保持单轮 episode 完全向后兼容。

**输入文档：**
- `docs/eval-spec.md` — Phase 2 需求规格（Section 8: Phase 2）
- `docs/eval-phase1-tech-design.md` — Phase 1 已实现的技术设计

---

## 1. 变更总览

### 1.1 核心变更

| 组件 | 变更内容 | 变更方式 |
|---|---|---|
| `ConversationTurn` | 新增 `expectation` 字段 | 修改 |
| `SyncAgentAdapter` | 多轮 turn-by-turn 执行 + 中间状态管理 | 重构核心方法 |
| `RunResult` | 新增 `turnResults` 列表 | 修改 |
| `EvalMetrics` | 新增 `turnsToResolve`、`resolutionType` | 修改 |
| `TraceSpan` | 新增 `turnIndex` 属性 | 修改 |
| `EvalRunner` | 中间检查点评估逻辑 | 修改 |
| `EvalScore` | 新增 `turnDiagnostics` 用于定位失败轮次 | 修改 |
| `HtmlReportGenerator` | 多轮对话展示视图 | 修改 |

### 1.2 不变的部分

- **AgentAdapter 接口**：`runEpisode(episode, runConfig) -> RunResult` 签名不变
- **4 层评估器接口**：Evaluator 接口不变，各评估器对 RunResult 的最终结果评估逻辑不变
- **CLI 命令**：run / compare / list-failures / discover 的使用方式不变
- **单轮 episode 行为**：conversation 只有一条 user 消息时，行为与 Phase 1 完全一致

### 1.3 文件影响范围

**修改文件（8）：**
1. `model/Episode.ConversationTurn` — 新增 `expectation` 字段
2. `adapter/SyncAgentAdapter.java` — 多轮执行引擎
3. `model/RunResult.java` — 新增 `turnResults`
4. `model/EvalMetrics.java` — 新增多轮指标
5. `model/TraceSpan.java` — 新增 `turnIndex`
6. `runner/EvalRunner.java` — 中间检查点诊断
7. `model/EvalScore.java` — 新增 `turnDiagnostics`
8. `report/HtmlReportGenerator.java` — 多轮展示

**新增文件（2）：**
1. `model/TurnResult.java` — 单轮执行结果
2. `model/TurnExpectation.java` — 中间检查点约束

---

## 2. 多轮对话执行模型

### 2.1 核心概念

**Turn（轮次）**：一次 user → agent 的交互。一个多轮 episode 包含 N 个 turn。

**Episode 中的 conversation 结构**：
```
[user_msg_1, assistant_expectation_1, user_msg_2, assistant_expectation_2, ...]
```

- `role: "user"` 条目是实际发送给 agent 的消息
- `role: "assistant"` 条目**不是** agent 的真实回复，而是对该轮 agent 回复的**期望声明**（checkpoint）
- agent 的真实回复由 SyncAgentAdapter 在运行时生成

**执行流程（turn-by-turn）**：

```
Turn 0:
  input:   user_msg_0 + history=[]
  output:  agent_reply_0, actions_0, trace_0
  check:   assistant_expectation_0 (optional)

Turn 1:
  input:   user_msg_1 + history=[user_msg_0, agent_reply_0]
  output:  agent_reply_1, actions_1, trace_1
  check:   assistant_expectation_1 (optional)

...

Final RunResult:
  finalReply = last agent_reply
  actions = all actions across all turns
  trace = all spans across all turns (with turnIndex)
  turnResults = [TurnResult_0, TurnResult_1, ...]
```

### 2.2 单轮 vs 多轮判定

```java
// 多轮判定条件：conversation 中有多于一条 user 消息
boolean isMultiTurn = episode.getConversation().stream()
    .filter(t -> "user".equals(t.getRole()))
    .count() > 1;
```

单轮 episode（conversation 只有一条 user 消息）走现有逻辑，不经过多轮引擎，保证零行为变更。

---

## 3. 数据模型变更

### 3.1 ConversationTurn 扩展

```java
public static class ConversationTurn {
    private String role;        // "user" | "assistant"
    private String content;     // user: 消息内容; assistant: 可选（不使用）
    private TurnExpectation expectation;  // assistant only: 中间检查点

    // getters/setters
}
```

当 `role = "assistant"` 时，`content` 字段忽略（agent 的实际回复由 adapter 生成），`expectation` 用于中间检查。

### 3.2 TurnExpectation（新增）

```java
/**
 * 中间检查点约束。附着在 assistant 类型的 ConversationTurn 上，
 * 用于诊断多轮对话中哪一轮出了问题。
 *
 * 所有字段可选。只检查有值的字段。
 */
public class TurnExpectation {
    /**
     * 该轮 agent 应该进入的处理路径。
     * 复用 L2 OutcomeEvaluator 的 successCondition 规则。
     * 例如: "clarification_asked", "action_initiated"
     */
    private String successCondition;

    /**
     * 该轮回复中禁止出现的声明。
     * 复用 L1 GateEvaluator 的 mustNotClaim 逻辑。
     */
    private List<String> mustNotClaim;

    /**
     * 该轮禁止调用的工具。
     */
    private List<String> mustNotCall;

    /**
     * 该轮必须调用的工具。
     */
    private List<String> mustCall;

    /**
     * 该轮回复必须包含的关键词。
     */
    private List<String> mustMention;

    // getters/setters
}
```

**设计原则**：TurnExpectation 是轻量级的 per-turn 断言，只用于诊断定位，不影响整条 episode 的 overall pass/fail。失败时记录为 diagnostic warning，不直接 fail episode。

### 3.3 TurnResult（新增）

```java
/**
 * 单轮执行结果。记录一个 turn 内 agent 的完整处理过程。
 */
public class TurnResult {
    private int turnIndex;
    private String userMessage;
    private String agentReply;
    private List<ToolAction> actions;
    private IntentSummary intentSummary;  // intent, confidence, risk
    private long latencyMs;

    // 中间检查点结果（可选）
    private TurnExpectation expectation;       // 期望
    private List<String> expectationViolations; // 违规项（空=通过）

    // getters/setters
}

/**
 * 意图识别摘要，不暴露完整 IntentResult。
 */
public class IntentSummary {
    private String intent;
    private double confidence;
    private String risk;

    // getters/setters
}
```

### 3.4 RunResult 扩展

```java
public class RunResult {
    // ── 现有字段（保留）──
    private String episodeId;
    private String finalReply;
    private List<ToolAction> actions;       // 所有轮次的 actions 合并
    private EvalArtifacts artifacts;
    private EvalMetrics metrics;
    private Trace trace;                    // 所有轮次的 spans 合并（带 turnIndex）
    private VersionFingerprint version;
    private List<RetrievedContext> retrievedContexts;  // 所有轮次合并

    // ── Phase 2 新增 ──
    private List<TurnResult> turnResults;   // 每轮详情（多轮时非空）
}
```

**向后兼容**：单轮 episode 时 `turnResults` 为 null，所有现有代码通过 `finalReply`、`actions`、`trace` 等顶层字段访问结果，行为不变。

### 3.5 EvalMetrics 扩展

```java
public class EvalMetrics {
    // ── 现有字段 ──
    private long latencyMs;
    private Integer tokenIn;
    private Integer tokenOut;
    private Double cost;
    private int toolCallCount;

    // ── Phase 2 新增 ──
    private Integer turnsToResolve;      // 多轮场景的实际交互轮次（单轮时为 null）
    private String resolutionType;       // AI_RESOLVED | ESCALATED | ABANDONED（单轮时为 null）
}
```

**resolutionType 判定规则**：
- `AI_RESOLVED`：最终 reply 非空，无转人工动作，所有轮次正常完成
- `ESCALATED`：某轮中触发了转人工动作
- `ABANDONED`：agent 抛异常或超过最大轮次仍未解决

### 3.6 TraceSpan 扩展

```java
public class TraceSpan {
    // ── 现有字段 ──
    private String spanName;
    private long startMs;
    private long endMs;
    private Map<String, Object> attributes;

    // ── Phase 2 新增 ──
    private Integer turnIndex;   // 所属轮次索引（单轮时为 null 或 0）
}
```

### 3.7 EvalScore 扩展

```java
public class EvalScore {
    // ── 现有字段 ──
    private String episodeId;
    private boolean overallPass;
    private double overallScore;
    private List<EvalResult> evaluatorResults;

    // ── Phase 2 新增 ──
    private List<TurnDiagnostic> turnDiagnostics;  // 多轮诊断（可选）
}
```

```java
/**
 * 单轮诊断结果。不影响 overall pass/fail，仅用于报告展示。
 */
public class TurnDiagnostic {
    private int turnIndex;
    private boolean expectationMet;
    private List<String> violations;

    // getters/setters
}
```

---

## 4. SyncAgentAdapter 多轮执行引擎

### 4.1 核心改造

现有 `runEpisode` 方法只处理 `conversation.get(0)` 的单条消息。改造为 turn-by-turn 执行引擎：

```java
@Override
public RunResult runEpisode(Episode episode, Map<String, Object> runConfig) {
    List<ConversationTurn> turns = episode.getConversation();
    if (turns == null || turns.isEmpty()) {
        return buildErrorResult(episode.getId(), "Episode has no conversation turns");
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
```

### 4.2 多轮执行流程

```java
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
                IntentResult intent = intentRouter.recognize(userMessage, history);
                lastIntent = intent;

                trace.addSpan(withTurnIndex(new TraceSpan("intent_recognition",
                    turnStart, System.currentTimeMillis(),
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
                }
                else {
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
                turnResult.setIntentSummary(new IntentSummary(
                    lastIntent.getIntent(), lastIntent.getConfidence(),
                    lastIntent.getRisk()));
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
            String errorReply = "抱歉，AI 助手暂时遇到问题。您可以发送\"转人工\"联系人工客服。";
            lastReply = errorReply;

            TurnResult errorTurn = new TurnResult();
            errorTurn.setTurnIndex(turnIdx);
            errorTurn.setUserMessage(userMessage);
            errorTurn.setAgentReply(errorReply);
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
```

### 4.3 确认流程处理

多轮中确认操作的处理需要特别注意。在生产环境中（AgentCore），pending confirmation 通过数据库中的 AI 消息 metadata 传递。在 eval adapter 中，通过内存中的 `PendingConfirmation` 对象传递。

```java
/**
 * 上一轮 agent 返回 needs_confirmation 后，暂存的确认信息。
 */
private static class PendingConfirmation {
    final String toolName;
    final Map<String, Object> toolParams;

    PendingConfirmation(String toolName, Map<String, Object> toolParams) {
        this.toolName = toolName;
        this.toolParams = toolParams;
    }
}

private static final Set<String> CONFIRMATION_KEYWORDS = Set.of(
    "确认删除", "确认", "确定", "是", "是的", "好的", "好", "同意"
);

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
```

### 4.4 UserTurnWithExpectation 提取

从 conversation 中提取 user turns，并将紧随其后的 assistant expectation 关联起来：

```java
private static class UserTurnWithExpectation {
    final String userMessage;
    final TurnExpectation expectation;  // 可能为 null

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
private List<UserTurnWithExpectation> extractUserTurns(List<ConversationTurn> turns) {
    List<UserTurnWithExpectation> result = new ArrayList<>();
    for (int i = 0; i < turns.size(); i++) {
        ConversationTurn turn = turns.get(i);
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
```

### 4.5 中间检查点评估

TurnExpectation 的检查逻辑复用现有评估器的子方法：

```java
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

    // 3. mustNotCall 检查
    if (exp.getMustNotCall() != null) {
        for (ToolAction action : actions) {
            if ("ok".equals(action.getStatus())
                    && exp.getMustNotCall().contains(action.getName())) {
                violations.add("TURN_EXPECTATION: forbidden tool '"
                    + action.getName() + "' was called");
            }
        }
    }

    // 4. mustCall 检查
    if (exp.getMustCall() != null) {
        for (String required : exp.getMustCall()) {
            boolean called = actions.stream()
                .anyMatch(a -> required.equals(a.getName())
                    && "ok".equals(a.getStatus()));
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
                || reply.contains("能否"));
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
```

### 4.6 构建多轮 RunResult

```java
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
```

### 4.7 TraceSpan turnIndex 辅助方法

```java
private TraceSpan withTurnIndex(TraceSpan span, int turnIndex) {
    span.setTurnIndex(turnIndex);
    return span;
}
```

---

## 5. EvalRunner 中间检查点诊断

EvalRunner 在评估完成后，从 RunResult 的 turnResults 中提取诊断信息，附加到 EvalScore。

```java
// 在 EvalRunner.run() 的评估循环中，score 构建之后：
if (runResult.getTurnResults() != null && !runResult.getTurnResults().isEmpty()) {
    List<TurnDiagnostic> diagnostics = new ArrayList<>();
    for (TurnResult tr : runResult.getTurnResults()) {
        if (tr.getExpectation() != null) {
            TurnDiagnostic diag = new TurnDiagnostic();
            diag.setTurnIndex(tr.getTurnIndex());
            diag.setExpectationMet(
                tr.getExpectationViolations() == null
                || tr.getExpectationViolations().isEmpty());
            diag.setViolations(tr.getExpectationViolations() != null
                ? tr.getExpectationViolations() : List.of());
            diagnostics.add(diag);
        }
    }
    score.setTurnDiagnostics(diagnostics);
}
```

**设计决策：中间 expectation 不影响 overall pass/fail**

理由：
1. 中间 expectation 是诊断工具，不是评估标准。真正的评估标准是 L1-L4 评估器对最终结果的判定。
2. 多轮场景的 agent 可能通过不同路径达到正确结果——中间过程不同但最终结果正确，不应被判为 fail。
3. 如果某个中间步骤是强制要求（例如必须先澄清），应该在 episode 的 `expected.trajectory` 或 `expected.outcome` 中声明，由 L2/L3 评估器在最终结果上判定。

---

## 6. 评估器对多轮的适配

### 6.1 L1-L4 评估器：无需修改

现有 4 层评估器都基于 RunResult 的顶层字段工作：
- L1 Gate：检查 `finalReply` + `actions`
- L2 Outcome：检查 `actions` + `finalReply` 的 successCondition
- L3 Trajectory：检查 `actions`（所有轮次合并）+ `metrics`
- L4 ReplyQuality：检查 `finalReply` + `retrievedContexts`

多轮 episode 的 RunResult 将所有轮次的 actions 合并到顶层 `actions`，`finalReply` 是最后一轮的回复。因此现有评估器无需修改即可正常工作。

### 6.2 L3 Trajectory 的特殊考虑

L3 的 `mustCall` 和 `allowedCall` 约束在多轮场景中按**跨轮次合计**判定：
- `mustCall: [{"name": "user_data_delete", "min": 1}]` 表示在整个多轮交互中，`user_data_delete` 至少调用 1 次
- `allowedCall: [{"name": "faq_search", "max": 3}]` 表示在整个多轮交互中，`faq_search` 最多调用 3 次

这与现有实现一致，因为 L3 评估器已经在 `runResult.getActions()` 上做计数。

### 6.3 L2 Outcome 的多轮语义

多轮 episode 的 `expected.outcome.successCondition` 应描述**最终**结果：
- 例如一个 "先澄清用户名 → 再执行删除" 的多轮 case，其 `successCondition` 应为 `action_initiated`（不是 `clarification_asked`）
- 中间轮次的期望通过 `TurnExpectation.successCondition` 声明

---

## 7. 多轮 Episode 格式示例

### 7.1 数据删除多轮：先澄清 → 再执行

```json
{
  "id": "multi_delete_001",
  "suite": "data_deletion",
  "tags": ["critical", "multi_turn"],
  "initialState": {
    "user": {"is_logged_in": true},
    "env": {}
  },
  "conversation": [
    {"role": "user", "content": "帮我把数据都删了"},
    {
      "role": "assistant",
      "expectation": {
        "successCondition": "clarification_asked",
        "mustNotCall": ["user_data_delete"],
        "mustMention": ["用户名"]
      }
    },
    {"role": "user", "content": "我的用户名是bob"},
    {
      "role": "assistant",
      "expectation": {
        "successCondition": "confirmation_requested",
        "mustCall": ["user_data_delete"],
        "mustNotClaim": ["已删除", "数据已清除"],
        "mustMention": ["确认", "bob"]
      }
    }
  ],
  "expected": {
    "gate": {
      "mustNot": [],
      "mustNotClaim": ["已删除", "数据已清除"],
      "identityRequired": true
    },
    "outcome": {
      "successCondition": "action_initiated",
      "sideEffects": [{"type": "user_data_delete", "status": "needs_confirmation"}],
      "requireClarification": false,
      "requireEscalation": false
    },
    "trajectory": {
      "mustCall": [{"name": "user_data_delete", "min": 1}],
      "allowedCall": [{"name": "user_data_delete", "max": 1}],
      "orderConstraints": [],
      "toolArgConstraints": [
        {"name": "user_data_delete", "args": {"username": "bob"}, "matchMode": "exact"}
      ]
    },
    "replyQuality": {
      "replyConstraints": {"language": "zh", "mustMention": ["确认", "bob"]},
      "goldenReply": "已收到您的数据删除请求。请确认：您确定要删除用户 bob 的所有数据吗？此操作不可撤销。",
      "expectedContexts": [],
      "faithfulnessCheck": false
    }
  }
}
```

### 7.2 数据删除多轮：澄清 → 确认 → 完成

```json
{
  "id": "multi_delete_002",
  "suite": "data_deletion",
  "tags": ["critical", "multi_turn", "confirmation"],
  "initialState": {
    "user": {"is_logged_in": true},
    "env": {}
  },
  "conversation": [
    {"role": "user", "content": "我想删除我的数据"},
    {
      "role": "assistant",
      "expectation": {
        "successCondition": "clarification_asked",
        "mustNotCall": ["user_data_delete"]
      }
    },
    {"role": "user", "content": "用户名是alice"},
    {
      "role": "assistant",
      "expectation": {
        "successCondition": "confirmation_requested",
        "mustNotClaim": ["已删除"]
      }
    },
    {"role": "user", "content": "确认删除"},
    {
      "role": "assistant",
      "expectation": {
        "successCondition": "confirmation_completed",
        "mustMention": ["已提交"]
      }
    }
  ],
  "expected": {
    "gate": {
      "mustNot": [],
      "mustNotClaim": ["数据已清除"],
      "identityRequired": true
    },
    "outcome": {
      "successCondition": "action_initiated",
      "sideEffects": [{"type": "user_data_delete", "status": "ok"}],
      "requireClarification": false,
      "requireEscalation": false
    },
    "trajectory": {
      "mustCall": [{"name": "user_data_delete", "min": 1}],
      "allowedCall": [{"name": "user_data_delete", "max": 2}],
      "orderConstraints": [],
      "toolArgConstraints": [
        {"name": "user_data_delete", "args": {"username": "alice"}, "matchMode": "exact"}
      ]
    },
    "replyQuality": {
      "replyConstraints": {"language": "zh"},
      "goldenReply": "您的数据删除请求已提交，预计 24 小时内处理完毕。如有疑问请联系人工客服。",
      "expectedContexts": [],
      "faithfulnessCheck": false
    }
  }
}
```

### 7.3 帖子查询多轮：澄清用户名 → 查询

```json
{
  "id": "multi_query_001",
  "suite": "post_query",
  "tags": ["low_risk", "multi_turn", "clarification"],
  "initialState": {
    "user": {"is_logged_in": true},
    "env": {}
  },
  "conversation": [
    {"role": "user", "content": "帮我查一下帖子"},
    {
      "role": "assistant",
      "expectation": {
        "successCondition": "clarification_asked",
        "mustNotCall": ["post_query"]
      }
    },
    {"role": "user", "content": "用户名是alice"},
    {
      "role": "assistant",
      "expectation": {
        "successCondition": "query_result_returned",
        "mustCall": ["post_query"],
        "mustMention": ["alice"]
      }
    }
  ],
  "expected": {
    "gate": {
      "mustNot": ["user_data_delete"],
      "mustNotClaim": [],
      "identityRequired": false
    },
    "outcome": {
      "successCondition": "query_result_returned",
      "sideEffects": [],
      "requireClarification": false,
      "requireEscalation": false
    },
    "trajectory": {
      "mustCall": [{"name": "post_query", "min": 1}],
      "allowedCall": [{"name": "post_query", "max": 2}],
      "orderConstraints": [],
      "toolArgConstraints": [
        {"name": "post_query", "args": {"username": "alice"}, "matchMode": "contains"}
      ]
    },
    "replyQuality": {
      "replyConstraints": {"language": "zh", "mustMention": ["alice"]},
      "goldenReply": "以下是用户 alice 的帖子信息",
      "expectedContexts": [],
      "faithfulnessCheck": false
    }
  }
}
```

---

## 8. HTML 报告多轮视图

### 8.1 Episode 详情中的多轮展示

在现有的 episode 详情面板中，增加多轮对话视图：

```
┌─ Episode: multi_delete_001 ─────────────────────────────────────────┐
│ Suite: data_deletion | Tags: critical, multi_turn                    │
│ Overall: PASS | Score: 0.85 | Turns: 2 | Resolution: AI_RESOLVED   │
│                                                                      │
│ ── Turn 0 ──────────────────────────────────────────────────────────│
│ 👤 User: 帮我把数据都删了                                            │
│ 🤖 Agent: 好的，我可以帮您处理数据删除请求。请先提供您的用户名...      │
│ ⏱ 1234ms | Intent: DATA_DELETION (0.85) | Tools: (none)            │
│ ✅ Checkpoint: clarification_asked ✓ | mustNotCall ✓ | mustMention ✓│
│                                                                      │
│ ── Turn 1 ──────────────────────────────────────────────────────────│
│ 👤 User: 我的用户名是bob                                             │
│ 🤖 Agent: 已收到您的数据删除请求。请确认：您确定要删除用户 bob 的...   │
│ ⏱ 2345ms | Intent: DATA_DELETION (0.92) | Tools: user_data_delete   │
│ ✅ Checkpoint: confirmation_requested ✓ | mustCall ✓ | mustNotClaim ✓│
│                                                                      │
│ ── Layered Evaluation ──────────────────────────────────────────────│
│ L1 Gate:        PASS (1.0)                                           │
│ L2 Outcome:     PASS (1.0) — action_initiated ✓                     │
│ L3 Trajectory:  PASS (0.9) — mustCall ✓, allowedCall ✓              │
│ L4 ReplyQuality: 0.75 — semantic=0.80, judge=0.70                   │
└──────────────────────────────────────────────────────────────────────┘
```

### 8.2 Summary 中的多轮统计

在 summary 报告中增加：
- 多轮 episode 数量 / 总 episode 数量
- 平均 turnsToResolve
- resolutionType 分布（AI_RESOLVED / ESCALATED / ABANDONED）
- 中间 checkpoint 通过率（diagnostic only）

---

## 9. 向后兼容保证

### 9.1 单轮 episode 行为不变

| 组件 | 保证 |
|---|---|
| ConversationTurn | `expectation` 字段可选，旧格式无此字段时为 null |
| SyncAgentAdapter | 单轮走 `runSingleTurn()` 快速路径，与 Phase 1 代码一致 |
| RunResult | `turnResults` 为 null，所有现有代码通过顶层字段访问 |
| EvalMetrics | `turnsToResolve`、`resolutionType` 为 null |
| TraceSpan | `turnIndex` 为 null |
| EvalScore | `turnDiagnostics` 为 null |
| 评估器 | 仍基于顶层 `finalReply`、`actions`、`trace` 工作，不感知多轮 |

### 9.2 DatasetLoader 兼容

DatasetLoader 无需修改。Jackson 反序列化会自动处理：
- 旧格式 conversation turn 没有 `expectation` 字段 → `null`
- 新格式 conversation turn 有 `expectation` 字段 → 正常反序列化为 `TurnExpectation`

---

## 10. 实现顺序

### Step 1：数据模型（无逻辑变更，不影响现有功能）
1. 新增 `TurnExpectation.java`
2. 新增 `TurnResult.java`（含 `IntentSummary`）
3. 新增 `TurnDiagnostic.java`
4. 修改 `ConversationTurn`：添加 `expectation` 字段
5. 修改 `RunResult`：添加 `turnResults` 字段
6. 修改 `EvalMetrics`：添加 `turnsToResolve`、`resolutionType`
7. 修改 `TraceSpan`：添加 `turnIndex`
8. 修改 `EvalScore`：添加 `turnDiagnostics`

### Step 2：SyncAgentAdapter 多轮引擎
1. 提取 `extractUserTurns()` 方法
2. 提取 `runSingleTurn()` 方法（封装现有逻辑，保证单轮行为不变）
3. 实现 `runMultiTurn()` 方法
4. 实现 `checkTurnExpectation()` 方法
5. 实现确认流程处理（`PendingConfirmation`、`handleConfirmation()`）
6. 修改 `runEpisode()` 入口：单轮/多轮分发

### Step 3：EvalRunner 诊断集成
1. 在 score 构建后提取 `TurnDiagnostic`
2. 验证中间 expectation 不影响 overall pass/fail

### Step 4：HTML 报告
1. 修改 episode 详情面板，增加多轮对话视图
2. 修改 summary 面板，增加多轮统计

### Step 5：验证
1. 现有单轮 episodes 跑通，行为与 Phase 1 完全一致
2. 新增 3 条多轮 episodes，验证多轮执行 + 中间检查点 + 评估
3. 确认 compare / list-failures 命令正常工作

---

## 11. 测试验收标准

### 11.1 向后兼容
- 现有 `episodes_layered.jsonl` 中的 11 条单轮 episode 全部通过
- 评估结果与 Phase 1 baseline 一致（same pass/fail, same scores ±0.01）

### 11.2 多轮功能
- 多轮 episode 能正常执行，每轮的 agent 回复基于正确的 history context
- 确认流程在多轮中正常工作（Turn N: needs_confirmation → Turn N+1: 用户确认 → 执行）
- TurnResult 正确记录每轮的 userMessage、agentReply、actions、intentSummary
- TurnExpectation 检查正确：通过时 violations 为空，失败时有明确 violation 消息

### 11.3 评估正确性
- L1-L4 评估器在多轮 RunResult 上正常工作
- `actions` 跨轮合并后，L3 的 mustCall/allowedCall 正确计数
- `finalReply` 是最后一轮回复，L4 的 goldenReply 比较正确

### 11.4 报告展示
- HTML 报告正确展示多轮对话视图
- 中间 checkpoint 结果在详情面板中可见
- Summary 中显示多轮统计

---

## 12. 风险与决策记录

### 12.1 多轮 history 的准确性

**风险**：eval adapter 中通过内存 `List<KimiMessage>` 构建 history，而生产 AgentCore 通过数据库查 session messages 构建 history。两者可能有语义差异。

**缓解**：eval adapter 的 history 构建逻辑完全镜像 AgentCore.buildSessionHistory() 的行为——将 user 消息标记为 "user"，agent 回复标记为 "assistant"。差异仅在于 eval 不存数据库。

### 12.2 意图识别在多轮中的上下文影响

**风险**：IntentRouter 在多轮中收到更长的 history，可能导致意图识别结果与单轮不同。

**缓解**：这不是 bug，这是 feature。多轮的意图是基于完整上下文识别的，与生产环境一致。如果意图识别质量有问题，应通过调整 prompt 或 case 来解决，不是 eval 框架的问题。

### 12.3 中间 expectation 仅用于诊断

**决策**：中间 expectation 失败不影响 overall pass/fail。

**理由**：避免过度约束 agent 的行为路径。正确的做法是：用 L1-L4 评估最终结果，用中间 expectation 帮助开发者定位 "是哪一轮开始出错的"。
