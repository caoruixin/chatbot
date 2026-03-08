# AI Agent Evaluation Framework - Technical Design Spec (Phase 0)

> **Note:** This is the Phase 0 tech design. It has been superseded by `eval-phase1-tech-design.md` which describes the layered 4-evaluator refactoring. This document is kept for historical reference. For the current evaluation architecture, see `eval-phase1-tech-design.md`.

## Context

The AI chatbot uses a 3-sub-agent architecture (IntentRouter -> ReactPlanner -> ResponseComposer) orchestrated by AgentCore. Currently there is no way to do repeatable regression evaluation when changing models, prompts, or workflow. This spec designs a minimal, self-hosted eval framework (per `docs/eval-spec.md`) that can run offline regression tests, compare baseline vs candidate, and pinpoint failures to specific agent components.

---

## 1. Module Strategy: Gradle Source Set (not separate project)

Use a dedicated source set `src/eval/java` within the existing backend project.

**Rationale:**
- Eval must directly call `IntentRouter`, `ReactPlanner`, `ResponseComposer`, `ToolDispatcher` at the Java class level (not via HTTP)
- A separate project would require duplicating or publishing all model/service classes
- A source set keeps eval code off the production classpath while importing all `main` classes
- Consistent with how integration tests are structured in Gradle projects

**Rejected alternative:** `src/test` — eval has a different lifecycle (runs against live LLM, produces artifacts, CLI tool) and should be runnable standalone, not as JUnit tests.

### build.gradle changes

```groovy
sourceSets {
    eval {
        java.srcDir 'src/eval/java'
        resources.srcDir 'src/eval/resources'
        compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
    }
}

task evalRun(type: JavaExec) {
    mainClass = 'com.chatbot.eval.EvalApplication'
    classpath = sourceSets.eval.runtimeClasspath
}
```

---

## 2. Package Structure

```
backend/src/eval/
  java/com/chatbot/eval/
    EvalApplication.java              # Spring Boot CLI entry point
    cli/
      EvalCommand.java                # Top-level subcommand dispatcher
      RunCommand.java                 # eval run
      CompareCommand.java             # eval compare
      ListFailuresCommand.java        # eval list-failures
      DiscoverCommand.java            # eval discover
    model/
      Episode.java                    # Test case POJO (JSONL mapping)
      EpisodeExpected.java            # expected outcomes sub-object
      MustCallConstraint.java         # must_call item {name, min}
      ReplyConstraints.java           # {language, mustMention}
      ToolArgConstraint.java          # Iter1: {name, args} for tool param validation
      SideEffect.java                 # Iter2: {type, status}
      RunResult.java                  # Adapter output
      ToolAction.java                 # Unified action record
      EvalMetrics.java                # {latencyMs, tokenIn, tokenOut, cost, toolCallCount}
      VersionFingerprint.java         # Agent version composite
      EvalScore.java                  # Per-episode evaluator scores
      EvalSummary.java                # Aggregated report
      CompareDelta.java               # baseline vs candidate diff
      TraceSpan.java                  # Iter1: single trace event
      Trace.java                      # Iter1: list of TraceSpan
      EvalArtifacts.java              # Iter1: identity/execution/composer artifacts
    adapter/
      AgentAdapter.java               # Interface: runEpisode(episode, runConfig) -> RunResult
      SyncAgentAdapter.java           # Wraps sub-components synchronously
      EvalToolDispatcher.java         # Side-effect-free tool dispatch
    evaluator/
      Evaluator.java                  # Interface: evaluate(episode, runResult) -> EvalResult
      EvalResult.java                 # {passed, score, violations, details}
      ContractEvaluator.java          # E1: hard gate checks
      TrajectoryEvaluator.java        # E2: must_call, ordering, count limits
      EfficiencyEvaluator.java        # E4: latency, tokens
      OutcomeEvaluator.java           # E3: stub in Iter0, real in Iter2
      SemanticEvaluator.java          # E5: Iter1: golden reply similarity + LLM judge + tool arg check
    runner/
      EvalRunner.java                 # Orchestrates dataset -> adapter -> evaluators -> output
      DatasetLoader.java              # JSONL reader
      ResultWriter.java               # JSON output writer
    report/
      HtmlReportGenerator.java        # Static report.html
      CompareReportGenerator.java     # Static compare.html
    fingerprint/
      FingerprintGenerator.java       # Computes version fingerprint
    discovery/
      DiscoveryExporter.java          # Exports discovery.json
  resources/
    application-eval.yml              # Eval-specific Spring profile
data/
  episodes.jsonl                      # Seed dataset (20 episodes)
```

---

## 3. Core Data Models

### 3.1 Episode (JSONL format)

```java
public class Episode {
    private String id;                           // "post_query_001"
    private String suite;                        // "post_query"
    private List<String> tags;                   // ["low_risk"]
    private InitialState initialState;
    private List<ConversationTurn> conversation;
    private EpisodeExpected expected;

    public static class InitialState {
        private Map<String, Object> user;        // {is_logged_in: true, tier: "vip"}
        private Map<String, Object> env;         // {kb_snapshot: "...", seed: 42}
    }

    public static class ConversationTurn {
        private String role;     // "user"
        private String content;
    }
}

public class EpisodeExpected {
    private List<MustCallConstraint> mustCall;   // [{name: "post_query", min: 1}]
    private List<String> mustNot;                // ["user_data_delete"]
    private String outcome;                      // Iter2
    private List<SideEffect> sideEffects;        // Iter2
    private ReplyConstraints replyConstraints;   // {language: "zh", mustMention: ["alice"]}
    private String goldenReply;                  // Iter1: reference answer for semantic comparison
    private List<ToolArgConstraint> toolArgConstraints; // Iter1: expected tool call arguments
}

public class ToolArgConstraint {
    private String name;                         // tool name, e.g. "post_query"
    private Map<String, Object> args;            // expected args, e.g. {"username": "alice"}
    private String matchMode;                    // "exact" (default) | "contains"
}
```

JSONL example (one line per episode):
```json
{"id":"post_query_001","suite":"post_query","tags":["low_risk"],"initialState":{"user":{"is_logged_in":true},"env":{}},"conversation":[{"role":"user","content":"帮我查一下alice的帖子"}],"expected":{"mustCall":[{"name":"post_query","min":1}],"mustNot":["user_data_delete"],"replyConstraints":{"language":"zh","mustMention":["alice"]},"goldenReply":"以下是用户 alice 的帖子信息：...","toolArgConstraints":[{"name":"post_query","args":{"username":"alice"}}]}}
{"id":"faq_001","suite":"faq","tags":["kb_question"],"initialState":{"user":{},"env":{}},"conversation":[{"role":"user","content":"你们的退款政策是什么"}],"expected":{"mustCall":[{"name":"faq_search","min":1}],"mustNot":["user_data_delete","post_query"],"replyConstraints":{"language":"zh"},"goldenReply":"我们的退款政策如下：在购买后7天内，如果商品未使用，您可以申请全额退款。"}}
{"id":"delete_001","suite":"data_deletion","tags":["critical"],"initialState":{"user":{"is_logged_in":true},"env":{}},"conversation":[{"role":"user","content":"我想删除我的所有数据，我的用户名是bob"}],"expected":{"mustCall":[{"name":"user_data_delete","min":1}],"mustNot":["claim_action_not_done"],"replyConstraints":{"language":"zh","mustMention":["确认"]},"goldenReply":"已收到您的数据删除请求。请确认：您确定要删除用户 bob 的所有数据吗？此操作不可撤销。","toolArgConstraints":[{"name":"user_data_delete","args":{"username":"bob"}}]}}
{"id":"chat_001","suite":"general","tags":["low_risk"],"initialState":{"user":{},"env":{}},"conversation":[{"role":"user","content":"你好"}],"expected":{"mustCall":[],"mustNot":["user_data_delete","post_query","faq_search"],"replyConstraints":{"language":"zh"},"goldenReply":"您好！我是AI客服助手，请问有什么可以帮您的？"}}
```

> 注意：`goldenReply` 和 `toolArgConstraints` 为 Iter1 新增的可选字段。Iter0 的 episode 数据无需包含这些字段，evaluator 会在字段缺失时跳过对应检查。

### 3.2 RunResult

```java
public class RunResult {
    private String episodeId;
    private String finalReply;
    private List<ToolAction> actions;
    private EvalArtifacts artifacts;      // Iter1: nullable
    private EvalMetrics metrics;
    private Trace trace;                  // Iter1: nullable
    private VersionFingerprint version;
}

public class ToolAction {
    private String name;                  // from ToolCall.getToolName()
    private Map<String, Object> args;     // from ToolCall.getParams()
    private String status;                // "ok" | "failed" | "needs_confirmation"
    private String resultSummary;
    private long timestampMs;

    public static ToolAction fromToolCallAndResult(ToolCall call, ToolResult result, long ts) { ... }
}

public class EvalMetrics {
    private long latencyMs;
    private Integer tokenIn;              // nullable
    private Integer tokenOut;             // nullable
    private Double cost;                  // nullable
    private int toolCallCount;
}
```

### 3.3 VersionFingerprint

```java
public class VersionFingerprint {
    private String modelId;               // "moonshot-v1-8k"
    private Map<String, String> promptHashes;  // {intent_system_prompt: "a3f2b1..."}
    private String workflowVersion;       // hash of config values
    private String toolSchemaVersion;     // hash of ToolDefinition enum
    private String kbSnapshot;            // hash of FAQ table content
    private String gitCommit;             // from git rev-parse HEAD
    private String compositeHash;         // the "agent_version" string
}
```

---

## 4. SyncAgentAdapter Design (Key Technical Challenge)

### Problem

`AgentCore.handleMessage(Session, Message)` is:
- **void return** — does not return reply text
- **@Async** — runs on aiTaskExecutor thread pool
- **Side-effecting** — writes to DB via MessageService, sends via GetStream
- **Tightly coupled** to Session/Message DB models for history

### Solution: Compose from Sub-Components

Rather than wrapping AgentCore, `SyncAgentAdapter` recomposes the same sub-components with a side-effect-free tool dispatcher:

```java
public class SyncAgentAdapter implements AgentAdapter {

    private final IntentRouter intentRouter;          // existing bean
    private final ReactPlanner reactPlanner;          // existing bean
    private final ResponseComposer responseComposer;  // existing bean
    private final EvalToolDispatcher evalToolDispatcher;
    private final int maxReactRounds;
    private final double confidenceThreshold;

    @Override
    public RunResult runEpisode(Episode episode, Map<String, Object> runConfig) {
        long startTime = System.currentTimeMillis();
        List<ToolAction> actions = new ArrayList<>();

        // 1. Extract user message from episode
        String userMessage = episode.getConversation().get(0).getContent();
        List<KimiMessage> history = List.of(); // Iter0: single-turn

        // 2. Intent recognition (IntentRouter.recognize is already synchronous)
        IntentResult intent = intentRouter.recognize(userMessage, history);

        // 3. Confidence check -> early return with clarification
        if (intent.getConfidence() < confidenceThreshold) {
            return buildResult(episode.getId(), LOW_CONFIDENCE_REPLY, actions, startTime);
        }

        // 4. ReAct loop (mirrors AgentCore logic exactly)
        ToolResult toolResult = null;
        for (int round = 0; round < maxReactRounds; round++) {
            ToolCall toolCall = reactPlanner.plan(intent, userMessage, history, toolResult);
            if (toolCall == null) break;

            long ts = System.currentTimeMillis();
            toolResult = evalToolDispatcher.dispatch(toolCall);
            actions.add(ToolAction.fromToolCallAndResult(toolCall, toolResult, ts));

            if (toolResult.isSuccess() || toolResult.needsConfirmation() || !toolResult.isRetryable())
                break;
        }

        // 5. Response composition (mirrors AgentCore.composeReply)
        String reply = composeReply(intent, userMessage, toolResult, history);

        return buildResult(episode.getId(), reply, actions, startTime);
    }
}
```

### Side-Effect Avoidance

| AgentCore side effect | How eval avoids it |
|---|---|
| `messageService.save()` | Never called. Reply returned as `RunResult.finalReply` |
| `getStreamService.sendMessage()` | Never called |
| `messageService.findBySessionId()` | History built from `episode.conversation` |
| `@Async` thread dispatch | `runEpisode()` is synchronous |
| `UserDataDeletionService` | Production code is already a mock. Iter2 adds sandbox executors |

### EvalToolDispatcher

Wraps the real `ToolDispatcher` validation/risk-check logic but:
- Records every dispatch for action capture
- Auto-confirms IRREVERSIBLE tools (configurable) so eval can test the full flow
- Can substitute mock executors for Iter2 sandbox mode

```java
public class EvalToolDispatcher {
    private final Map<String, ToolExecutor> executors;
    private final boolean autoConfirmIrreversible;  // from runConfig

    public ToolResult dispatch(ToolCall toolCall) {
        // 1. Validate via ToolDefinition.fromName
        // 2. Schema validation (reuse ToolDispatcher patterns)
        // 3. Risk check: auto-confirm if configured
        // 4. Execute via ToolExecutor
        // 5. Return result
    }
}
```

### Spring Wiring: `application-eval.yml`

```yaml
spring:
  main:
    web-application-type: none    # No web server
  flyway:
    enabled: false                # Don't run migrations

chatbot:
  eval:
    auto-confirm-irreversible: true
    default-max-react-rounds: 3
    default-confidence-threshold: 0.7
    # Iter1: Semantic evaluator thresholds
    semantic-similarity-threshold: 0.75    # cosine similarity pass threshold
    judge-score-threshold: 3.5             # LLM judge composite score pass threshold (out of 5.0)
    judge-weights:                         # LLM judge dimension weights
      correctness: 0.5
      completeness: 0.3
      tone: 0.2
```

DB still connects for read-only tool queries (PostQueryService reads `user_post`, FaqService reads `faq_doc` + pgvector). Iter1 additionally uses DashScope embedding service (read-only) and Kimi service (read-only LLM calls for judge scoring).

---

## 5. Evaluators

### Interface

```java
public interface Evaluator {
    String name();
    EvalResult evaluate(Episode episode, RunResult runResult);
}

public class EvalResult {
    private String evaluatorName;
    private boolean passed;
    private double score;                 // 0.0-1.0
    private List<String> violations;
    private Map<String, Object> details;
}
```

### E1 ContractEvaluator (hard gate)

- `must_not` check: forbidden tool calls in `actions` + forbidden phrases in `finalReply`
- `reply_constraints.must_mention`: keywords that must appear in reply
- Tool call schema validation: every `action.name` must exist in `ToolDefinition`

### E2 TrajectoryEvaluator (process correctness)

- `must_call` check: each tool called at least `min` times (counting only status="ok")
- Tool call count upper bound (default 5, configurable)
- Iter1 addition: ordering constraints

### E4 EfficiencyEvaluator (performance)

- Latency threshold check (default 15000ms)
- Records latency, token counts, tool call count as details for reporting

### E3 OutcomeEvaluator (stub in Iter0, real in Iter2)

- Iter0: always returns pass
- Iter2: verifies `side_effects` against sandbox state

### E5 SemanticEvaluator (Iter1 — reply quality + tool arg validation)

Evaluates the **content quality** of agent replies, complementing E1-E4 which only check behavioral constraints.

**Three sub-checks (each optional — skipped if the corresponding field is absent in the episode):**

#### 5a. Golden Reply Similarity (embedding-based)

When `episode.expected.goldenReply` is present:
1. Compute embedding of `runResult.finalReply` using DashScope text-embedding-v4 (reuse existing `EmbeddingService`)
2. Compute embedding of `episode.expected.goldenReply`
3. Calculate cosine similarity
4. Pass if similarity >= threshold (default 0.75, configurable via `chatbot.eval.semantic-similarity-threshold`)

```java
double similarity = cosineSimilarity(
    embeddingService.embed(runResult.getFinalReply()),
    embeddingService.embed(episode.getExpected().getGoldenReply())
);
```

#### 5b. LLM-as-Judge Scoring

When `episode.expected.goldenReply` is present:
1. Call Kimi model (reuse existing `KimiService`) with a judge prompt containing:
   - The user's question (`episode.conversation[0].content`)
   - The golden reply (`episode.expected.goldenReply`)
   - The actual reply (`runResult.finalReply`)
2. LLM returns structured JSON scores (1-5 scale):
   - `correctness`: Is the reply factually accurate and semantically consistent with golden reply?
   - `completeness`: Does the reply cover all key information points?
   - `tone`: Is the reply appropriate for customer service (polite, professional)?
3. Composite score = weighted average (correctness: 0.5, completeness: 0.3, tone: 0.2)
4. Pass if composite score >= threshold (default 3.5/5.0, configurable via `chatbot.eval.judge-score-threshold`)

Judge system prompt (managed in eval, not production):
```
你是一个客服回复质量评估专家。请对比"参考回复"和"实际回复"，从以下三个维度打分（1-5分）：
1. correctness（正确性）：实际回复是否准确，是否与参考回复语义一致
2. completeness（完整性）：是否包含关键信息点
3. tone（语气）：是否符合客服场景（礼貌、专业）

用户问题：{user_message}
参考回复：{golden_reply}
实际回复：{actual_reply}

请以JSON格式输出：{"correctness": N, "completeness": N, "tone": N, "reasoning": "..."}
```

#### 5c. Tool Argument Validation

When `episode.expected.toolArgConstraints` is present:
1. For each constraint, find matching tool calls in `runResult.actions` by name
2. Check if actual args contain/match expected args based on `matchMode`:
   - `exact`: actual args must contain all expected key-value pairs exactly
   - `contains`: expected values are substring-matched against actual values
3. Pass if all constraints are satisfied

```java
for (ToolArgConstraint constraint : expected.getToolArgConstraints()) {
    List<ToolAction> matching = actions.stream()
        .filter(a -> a.getName().equals(constraint.getName()) && "ok".equals(a.getStatus()))
        .toList();
    // verify at least one matching action has the expected args
}
```

**Overall E5 scoring:**
- If all three sub-checks are present: score = 0.4 * similarity + 0.4 * (judgeComposite/5.0) + 0.2 * (toolArgPass ? 1.0 : 0.0)
- If only some sub-checks are present: score = weighted average of available sub-checks
- Pass if score >= configurable threshold (default 0.7)
- Details include: `similarityScore`, `judgeScores` (per-dimension), `toolArgResults` (per-constraint)

**No new external services:** embedding reuses DashScope, LLM judge reuses Kimi. Judge prompt is internal to eval.

---

## 6. Data Flow: `eval run`

```
eval run --dataset episodes.jsonl --agent-config candidate.yaml --out results/

DatasetLoader -> List<Episode>
FingerprintGenerator -> VersionFingerprint

EvalRunner: for each Episode:
  |-- SyncAgentAdapter.runEpisode(episode, config) -> RunResult
  |     |-- IntentRouter.recognize()
  |     |-- ReactPlanner.plan() loop
  |     |-- EvalToolDispatcher.dispatch()
  |     +-- ResponseComposer.compose()
  |
  |-- ContractEvaluator.evaluate() -> EvalResult
  |-- TrajectoryEvaluator.evaluate() -> EvalResult
  |-- EfficiencyEvaluator.evaluate() -> EvalResult
  |-- SemanticEvaluator.evaluate() -> EvalResult    # Iter1: golden reply + LLM judge + tool args
  +-- Aggregate -> EvalScore

ResultWriter -> output directory:
  results/
    run_meta.json          # fingerprint, config, timestamp
    episodes/
      post_query_001.json  # RunResult + EvalScore per episode
      faq_001.json
      ...
    summary.json           # aggregated pass rates, suite/tag breakdown
    report.html            # static HTML dashboard
```

---

## 7. CLI Design

Spring Boot `CommandLineRunner` with manual arg parsing (no picocli -- YAGNI):

```java
@Component
public class EvalCommand implements CommandLineRunner {
    @Override
    public void run(String... args) {
        switch (args[0]) {
            case "run" -> runCommand.execute(args);
            case "compare" -> compareCommand.execute(args);
            case "list-failures" -> listFailuresCommand.execute(args);
            case "discover" -> discoverCommand.execute(args);
        }
    }
}
```

Invocation:
```bash
./gradlew evalRun --args="run --dataset data/episodes.jsonl --out results/run_20260223"
./gradlew evalRun --args="compare --baseline results/baseline --candidate results/candidate --out results/compare"
./gradlew evalRun --args="list-failures --results results/run_20260223 --filter contract_fail"
./gradlew evalRun --args="discover --out discovery.json"
```

---

## 8. HTML Report (Static, Zero Dependencies)

Generated via `StringBuilder` in Java -- no template engine (KISS).

### report.html: 3 views

1. **Summary**: total episodes, pass rate per evaluator, latency p50/p95, version fingerprint
2. **Suite/Tag table**: per-suite breakdown (total, pass, fail, rate, avg latency)
3. **Episode details**: collapsible list with final_reply, actions, evaluator results, trace (Iter1)

Inline CSS + minimal inline JS for collapse/expand. No external dependencies.

### compare.html

- Side-by-side pass rate (baseline vs candidate) with delta highlighting
- Top Regressions list (passed in baseline, failed in candidate)
- New Passes list
- Per-suite delta table

---

## 9. Fingerprint Generation

```java
public class FingerprintGenerator {
    public VersionFingerprint generate() {
        // modelId:         kimiConfig.getChatModel()
        // promptHashes:    SHA-256 of IntentRouter.getSystemPrompt(), ResponseComposer.getSystemPrompt()
        // workflowVersion: SHA-256 of "max_react_rounds=3,confidence_threshold=0.7,..."
        // toolSchema:      SHA-256 of ToolDefinition enum serialization
        // kbSnapshot:      FAQ doc count + content hash from DB
        // gitCommit:       git rev-parse HEAD
        // compositeHash:   SHA-256 of all above concatenated
    }
}
```

Requires adding `static String getSystemPrompt()` methods to `IntentRouter` and `ResponseComposer` (the only production code change for fingerprinting).

---

## 10. Discovery Command

Exports `discovery.json` with:
- **tool_registry**: name, description, risk_level, params_schema for each ToolDefinition
- **prompts**: hash + length for each system prompt
- **workflow**: sub_agents list, max_react_rounds, confidence_threshold, routing_strategy
- **model_config**: chat_model, temperatures, embedding_model
- **kb_snapshot_method**: description of how KB versioning works

---

## 11. Modifications to Existing Code

Only 3 minimal changes to production code:

| File | Change | Why |
|---|---|---|
| `backend/build.gradle` | Add `eval` sourceSet + `evalRun` task | Module setup |
| `backend/.../agent/IntentRouter.java` | Add `static String getSystemPrompt()` | Fingerprint needs prompt hash |
| `backend/.../agent/ResponseComposer.java` | Add `static String getSystemPrompt()` | Fingerprint needs prompt hash |

Note: If `FaqEmbeddingInitializer` exists and runs on startup, it should be conditionally disabled in eval profile via `@Profile("!eval")` or a config flag.

---

## 12. Iteration Scope

### Iter0 (this implementation)
- All model POJOs (Episode, RunResult, ToolAction, EvalMetrics, VersionFingerprint, etc.)
- AgentAdapter interface + SyncAgentAdapter + EvalToolDispatcher
- ContractEvaluator, TrajectoryEvaluator, EfficiencyEvaluator (OutcomeEvaluator as no-op stub)
- EvalRunner, DatasetLoader, ResultWriter
- CLI commands: run, compare, list-failures, discover
- HtmlReportGenerator, CompareReportGenerator
- FingerprintGenerator, DiscoveryExporter
- Seed dataset: 20 episodes covering post_query, faq, data_deletion, general_chat
- application-eval.yml + build.gradle changes

### Iter1 (golden answer + semantic evaluation + RAG quality + observability)
- **SemanticEvaluator** (E5) with three sub-checks:
  - Golden reply embedding similarity (reuse DashScope text-embedding-v4 via EmbeddingService)
  - LLM-as-Judge structured scoring (judge prompt managed in eval)
  - Tool argument validation (exact/contains matching)
- **LLM Judge model independence**:
  - Judge uses a different Kimi model variant from production (configured separately in `application-eval.yml`)
  - Judge prompt includes scoring calibration anchors and explicit deduction criteria
- **RAG Retrieval Quality Evaluator** (E6):
  - Context Precision / Recall (episode provides `expectedContexts`)
  - Faithfulness check (LLM determines if reply is grounded in retrieved contexts)
  - SyncAgentAdapter records `retrievedContexts` in RunResult
- **Episode model extension**: `goldenReply`, `toolArgConstraints`, `expectedContexts`, `faithfulnessCheck` in EpisodeExpected
- **New models**: ToolArgConstraint {name, args, matchMode}, RetrievedContext {faqId, question, score}
- **Dataset enrichment**: Add goldenReply, toolArgConstraints to all 20 episodes; add expectedContexts to faq suite episodes
- **Configuration**: `chatbot.eval.semantic-similarity-threshold`, `chatbot.eval.judge-score-threshold`, `chatbot.eval.judge-model-id` in application-eval.yml
- TraceSpan, Trace, EvalArtifacts population in SyncAgentAdapter
- Enhanced HTML report with trace/artifact rendering + semantic score + RAG quality display
- Compare report includes semantic score delta and RAG quality delta

### Iter2 (golden episode auto-generation + outcome verification)
- **Satisfaction Survey feature** (independent module):
  - New `satisfaction_survey` table (独立表，不修改 session 表)
  - 通过 `session_id` 字段关联到 session 表
  - 收集用户 1-5 星评价
- **Golden Episode auto-export** (`eval export-golden` CLI command):
  - 自动从高满意度 session (5星) 导出为 episode JSONL
  - 从 message 表提取完整对话流程，转换为 episode 格式
  - 根据 session 中的工具调用记录自动推断 `mustCall`、`suite`、`toolArgConstraints`
- **Manual session import** (`eval import` CLI command):
  - 支持手动整理的真实 session 导入格式
  - 自动转换为 episode JSONL，支持 `overrides` 人工标注覆盖自动推断
- **Episode model extension**: 新增可选 `source` 字段用于溯源（评估器忽略此字段）
- SandboxToolExecutor for side-effect recording
- OutcomeEvaluator real implementation

### Iter3 (CI gate + online monitoring)
- GateCommand + gate-config.yml + CI workflow
- Gate rules include semantic score thresholds (e.g., avg semantic score >= 0.7)
- **Online monitoring infrastructure**:
  - Production tracing: log session_id, intent, retrieved contexts, tool calls, reply, latency, tokens per conversation
  - Structured trace table (`eval_trace`) in existing PostgreSQL
  - Business metrics collection: escalation rate, FCR (first contact resolution), AHT (average handling time)
  - Simple metrics dashboard (reuse HTML report pattern or integrate with existing console)

### Iter4 (multi-turn + human calibration + security)
- **Multi-turn conversation support**:
  - SyncAgentAdapter processes full `conversation` list (not just first message)
  - Multi-turn coherence evaluation (context consistency, coreference resolution)
  - New multi-turn episodes (e.g., ask post status → ask how to appeal → request human transfer)
- **Human annotation calibration**:
  - Build 30-50 human-labeled validation set (3 annotators + Cohen's Kappa inter-annotator agreement)
  - Calibrate LLM Judge vs human agreement rate (target >= 80%)
  - Iterate judge prompt if agreement is below threshold
  - Simple annotation tooling (CSV/JSONL + scripts)
- **Security / red-team testing**:
  - New `security` suite: prompt injection, jailbreak, PII leakage, role-play attacks
  - SafetyEvaluator (E7): detect PII in replies, system prompt leakage, role deviation
  - Minimum 20 security episodes covering common attack patterns
  - Security suite: zero tolerance (100% pass rate required)

---

## 13. Verification Plan

1. **Build**: `./gradlew compileEvalJava` -- eval source set compiles against main
2. **Discovery**: `./gradlew evalRun --args="discover --out discovery.json"` -- produces valid JSON
3. **Single episode**: `./gradlew evalRun --args="run --dataset data/single_test.jsonl --out results/test"` -- runs 1 episode, produces RunResult + scores
4. **Full dataset**: Run all 20 seed episodes, verify summary.json pass rates
5. **Compare**: Run same dataset with a config change (e.g., different confidence threshold), then compare -- verify delta report highlights differences
6. **List failures**: Filter for contract_fail -- verify output matches actual failures
7. **HTML report**: Open report.html in browser -- verify 3 views render correctly
8. **Iter1 — Semantic eval**: Run with goldenReply-enriched dataset, verify each episode has similarity score + judge scores in output
9. **Iter1 — Tool arg validation**: Verify episodes with toolArgConstraints report pass/fail correctly for argument matching
10. **Iter1 — Judge scoring**: Verify LLM judge returns valid JSON with correctness/completeness/tone scores for each episode
11. **Iter1 — Judge model independence**: Verify judge uses a different model variant from production (check application-eval.yml config)
12. **Iter1 — RAG quality**: Run faq suite episodes, verify context precision/recall scores and faithfulness check results in output
13. **Iter1 — Retrieved contexts**: Verify RunResult contains retrievedContexts for faq-related episodes

---

## 14. File Listing (Iter0 -- 39 new files + 3 modifications)

New files:
1. `src/eval/java/com/chatbot/eval/EvalApplication.java`
2. `src/eval/java/com/chatbot/eval/cli/EvalCommand.java`
3. `src/eval/java/com/chatbot/eval/cli/RunCommand.java`
4. `src/eval/java/com/chatbot/eval/cli/CompareCommand.java`
5. `src/eval/java/com/chatbot/eval/cli/ListFailuresCommand.java`
6. `src/eval/java/com/chatbot/eval/cli/DiscoverCommand.java`
7. `src/eval/java/com/chatbot/eval/model/Episode.java`
8. `src/eval/java/com/chatbot/eval/model/EpisodeExpected.java`
9. `src/eval/java/com/chatbot/eval/model/MustCallConstraint.java`
10. `src/eval/java/com/chatbot/eval/model/ReplyConstraints.java`
11. `src/eval/java/com/chatbot/eval/model/SideEffect.java`
12. `src/eval/java/com/chatbot/eval/model/RunResult.java`
13. `src/eval/java/com/chatbot/eval/model/ToolAction.java`
14. `src/eval/java/com/chatbot/eval/model/EvalMetrics.java`
15. `src/eval/java/com/chatbot/eval/model/VersionFingerprint.java`
16. `src/eval/java/com/chatbot/eval/model/EvalScore.java`
17. `src/eval/java/com/chatbot/eval/model/EvalSummary.java`
18. `src/eval/java/com/chatbot/eval/model/CompareDelta.java`
19. `src/eval/java/com/chatbot/eval/model/TraceSpan.java`
20. `src/eval/java/com/chatbot/eval/model/Trace.java`
21. `src/eval/java/com/chatbot/eval/model/EvalArtifacts.java`
22. `src/eval/java/com/chatbot/eval/adapter/AgentAdapter.java`
23. `src/eval/java/com/chatbot/eval/adapter/SyncAgentAdapter.java`
24. `src/eval/java/com/chatbot/eval/adapter/EvalToolDispatcher.java`
25. `src/eval/java/com/chatbot/eval/evaluator/Evaluator.java`
26. `src/eval/java/com/chatbot/eval/evaluator/EvalResult.java`
27. `src/eval/java/com/chatbot/eval/evaluator/ContractEvaluator.java`
28. `src/eval/java/com/chatbot/eval/evaluator/TrajectoryEvaluator.java`
29. `src/eval/java/com/chatbot/eval/evaluator/EfficiencyEvaluator.java`
30. `src/eval/java/com/chatbot/eval/evaluator/OutcomeEvaluator.java`
31. `src/eval/java/com/chatbot/eval/runner/EvalRunner.java`
32. `src/eval/java/com/chatbot/eval/runner/DatasetLoader.java`
33. `src/eval/java/com/chatbot/eval/runner/ResultWriter.java`
34. `src/eval/java/com/chatbot/eval/report/HtmlReportGenerator.java`
35. `src/eval/java/com/chatbot/eval/report/CompareReportGenerator.java`
36. `src/eval/java/com/chatbot/eval/fingerprint/FingerprintGenerator.java`
37. `src/eval/java/com/chatbot/eval/discovery/DiscoveryExporter.java`
38. `src/eval/resources/application-eval.yml`
39. `data/episodes.jsonl`

Modified files:
1. `backend/build.gradle` -- add eval sourceSet + evalRun task
2. `backend/.../agent/IntentRouter.java` -- add `static String getSystemPrompt()`
3. `backend/.../agent/ResponseComposer.java` -- add `static String getSystemPrompt()`

---

## 15. Iter1 New Files (Golden Answer + Semantic Evaluation + RAG Quality)

New files:
1. `src/eval/java/com/chatbot/eval/model/ToolArgConstraint.java` -- {name, args, matchMode} for tool param validation
2. `src/eval/java/com/chatbot/eval/model/RetrievedContext.java` -- {faqId, question, score} for RAG quality tracking
3. `src/eval/java/com/chatbot/eval/evaluator/SemanticEvaluator.java` -- E5: golden reply similarity + LLM judge + tool arg check
4. `src/eval/java/com/chatbot/eval/evaluator/RagQualityEvaluator.java` -- E6: context precision/recall + faithfulness

Modified files:
1. `src/eval/java/com/chatbot/eval/model/EpisodeExpected.java` -- add `goldenReply`, `toolArgConstraints`, `expectedContexts`, `faithfulnessCheck`
2. `src/eval/java/com/chatbot/eval/model/RunResult.java` -- add `retrievedContexts` (List\<RetrievedContext\>)
3. `src/eval/java/com/chatbot/eval/adapter/SyncAgentAdapter.java` -- capture retrieved contexts from FAQ search into RunResult
4. `src/eval/java/com/chatbot/eval/runner/EvalRunner.java` -- wire SemanticEvaluator + RagQualityEvaluator into evaluator chain
5. `src/eval/java/com/chatbot/eval/report/HtmlReportGenerator.java` -- render semantic scores + RAG quality in episode details
6. `src/eval/java/com/chatbot/eval/report/CompareReportGenerator.java` -- render semantic score + RAG quality delta
7. `src/eval/resources/application-eval.yml` -- add `semantic-similarity-threshold`, `judge-score-threshold`, `judge-model-id`
8. `data/episodes.jsonl` -- add goldenReply, toolArgConstraints to all 20 episodes; add expectedContexts to faq suite episodes

---

## 16. Iter2 Detailed Design: Golden Episode Auto-Generation

### 16.1 设计目标

减少人工制造评估数据的成本，通过两条路径自动/半自动生成 golden episode：

1. **自动路径**：用户在真实使用中给出 5 星好评的 session → 自动导出为 golden episode
2. **手动路径**：人工整理的高质量 session（来自 AI 模拟处理或人工客服处理）→ 通过导入工具转为 episode

生成的 episode 与现有 `episodes.jsonl` 格式完全兼容，6 个评估器 (E1-E6) 无需任何修改。

### 16.2 Satisfaction Survey — 独立 Feature

满意度调查作为独立功能模块，**不修改现有 session 表**。通过新建 `satisfaction_survey` 表，以 `session_id` 外键关联。

#### 数据库设计

```sql
-- Flyway migration: V5__satisfaction_survey.sql
CREATE TABLE satisfaction_survey (
    survey_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID         NOT NULL REFERENCES session(session_id),
    conversation_id UUID         NOT NULL REFERENCES conversation(conversation_id),
    user_id         VARCHAR(255) NOT NULL,
    score           SMALLINT     NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment         TEXT,                          -- 用户可选的文字评价
    resolved_by     VARCHAR(32)  NOT NULL,         -- 'AI' | 'HUMAN' | 'MIXED'
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_survey_session ON satisfaction_survey(session_id);
CREATE INDEX idx_survey_score ON satisfaction_survey(score);
CREATE INDEX idx_survey_created ON satisfaction_survey(created_at);
CREATE UNIQUE INDEX idx_survey_session_unique ON satisfaction_survey(session_id);
```

设计要点：
- **独立表**：不污染 session 表结构，满意度是后加的扩展功能
- **session_id 关联**：通过外键与 session 表关联，可 JOIN 查询完整会话数据
- **conversation_id 冗余**：方便直接查询某用户所有满意度记录，无需 JOIN session
- **唯一约束**：每个 session 只能有一条满意度记录（`idx_survey_session_unique`）
- **resolved_by**：记录该 session 最终由谁解决（AI 全程处理 / 转人工 / 混合），便于分析

#### API 设计

```
POST /api/sessions/{sessionId}/satisfaction
Request Body: { "score": 5, "comment": "很快就解决了问题" }
Response: { "surveyId": "uuid", "sessionId": "...", "score": 5 }
```

发放时机：session 状态变为 CLOSED 后，前端推送满意度调查弹窗。

### 16.3 Auto-Export: 高满意度 Session → Golden Episode

#### CLI 命令

```bash
./gradlew evalRun --args="export-golden --min-score=5 --output data/golden_episodes.jsonl"

# 可选参数：
#   --min-score=N        最低满意度分数 (默认 5)
#   --resolved-by=AI     筛选处理方式 (AI|HUMAN|MIXED|ALL, 默认 ALL)
#   --since=2026-02-01   起始日期
#   --limit=100          最大导出数量
#   --output=FILE        输出文件路径
```

#### 导出流程

```
satisfaction_survey 表 (score >= min_score)
    ↓
JOIN session 表 (获取 session 状态信息)
    ↓
JOIN message 表 (按 created_at ASC 获取完整对话)
    ↓
对每个 session:
    ↓
[1] 提取用户消息和客服回复
    ├─ 第一条 sender_type=USER 的消息 → conversation[0].content
    ├─ 第一条 sender_type IN (AI_CHATBOT, HUMAN_AGENT) 的回复 → expected.goldenReply
    └─ 如果有多轮对话，仅取第一轮（Iter4 支持多轮）
    ↓
[2] 从 message.metadata_json 推断工具调用信息
    ├─ 解析 AI_CHATBOT 消息的 metadata_json，提取 intent、tool_calls
    ├─ 根据 intent 映射 suite:
    │     POST_QUERY    → suite: "post_query"
    │     KB_QUESTION   → suite: "faq"
    │     DATA_DELETION → suite: "data_deletion"
    │     GENERAL_CHAT  → suite: "general"
    │     未知          → suite: "uncategorized"
    ├─ 根据工具调用记录推断:
    │     mustCall: 实际调用过的工具列表
    │     mustNot: 根据 intent 排除的工具 (同现有 episode 逻辑)
    │     toolArgConstraints: 实际传递的工具参数
    └─ 如果是 FAQ 类，从工具返回结果中提取 expectedContexts
    ↓
[3] 生成 episode JSONL
    ├─ id: "golden_{suite}_{序号}" (如 golden_faq_001)
    ├─ tags: ["golden", "high_satisfaction", "auto_exported"]
    ├─ source: 溯源信息 (sessionId, satisfactionScore, resolvedBy, exportedAt)
    └─ 写入输出文件
```

#### 导出数据质量保障

- **去重**：已导出的 session 通过 `satisfaction_survey` 表的 `exported_as_episode` 标记（新增布尔字段）避免重复导出
- **最低消息数**：session 至少包含 1 条 USER 消息 + 1 条回复，否则跳过
- **内容脱敏**：导出时检查 content 中是否包含敏感模式（手机号、邮箱、身份证号），如有则标记 `tags: ["needs_review"]` 而非直接跳过

### 16.4 Manual Import: 手动整理的 Session → Episode

#### 导入格式定义

用于人工整理高质量 session 时的标准格式（`real_sessions.jsonl`，一行一个 session）：

```jsonl
{"sourceSession":{"sessionId":"abc-123","userId":"user_tom","resolvedBy":"AI","satisfactionScore":5,"capturedAt":"2026-02-15T10:30:00Z"},"messages":[{"senderType":"USER","content":"你们的退款政策是什么"},{"senderType":"AI_CHATBOT","content":"我们的退款政策是：自购买之日起7天内可申请无理由退款。请前往\"订单 > 申请退款\"提交申请。"}],"overrides":{"suite":"faq","mustCall":["faq_search"],"mustNot":["user_data_delete"],"expectedContexts":["退款政策是什么？"],"faithfulnessCheck":true}}
```

**字段说明：**

| 字段 | 必填 | 说明 |
|------|------|------|
| `sourceSession.sessionId` | 是 | 原始 session ID，用于溯源 |
| `sourceSession.userId` | 否 | 原始用户 ID |
| `sourceSession.resolvedBy` | 否 | `"AI"` / `"HUMAN"` / `"MIXED"` |
| `sourceSession.satisfactionScore` | 否 | 1-5 星 |
| `sourceSession.capturedAt` | 否 | 原始时间戳 |
| `messages` | 是 | 按时间顺序的完整消息列表 |
| `messages[].senderType` | 是 | `"USER"` / `"AI_CHATBOT"` / `"HUMAN_AGENT"` / `"SYSTEM"` |
| `messages[].content` | 是 | 消息内容 |
| `overrides` | 否 | 人工标注覆盖自动推断 |
| `overrides.suite` | 否 | 强制指定 suite（优先于自动推断） |
| `overrides.mustCall` | 否 | 强制指定期望调用的工具列表 |
| `overrides.mustNot` | 否 | 强制指定禁止调用的工具列表 |
| `overrides.expectedContexts` | 否 | 强制指定期望检索的 FAQ 文档 |
| `overrides.faithfulnessCheck` | 否 | 是否开启忠实度检查 |

#### CLI 命令

```bash
./gradlew evalRun --args="import --file data/real_sessions.jsonl --output data/episodes_merged.jsonl"

# 可选参数：
#   --file=FILE          导入文件路径 (必填)
#   --output=FILE        输出 episode 文件路径 (必填)
#   --append             追加到已有 episode 文件（而非覆盖）
#   --id-prefix=PREFIX   自定义 episode ID 前缀 (默认 "real")
```

#### 转换规则

| 导入字段 | → Episode 字段 | 转换逻辑 |
|----------|---------------|----------|
| `messages[0]` (首条 USER 消息) | `conversation[0]` | `{"role": "user", "content": "..."}` |
| `messages[1]` (首条非 USER 回复) | `expected.goldenReply` | 直接作为参考答案 |
| `overrides.suite` 或自动推断 | `suite` | 优先 override；否则尝试从 goldenReply 内容关键词推断 |
| `overrides.mustCall` 或自动推断 | `expected.mustCall` | 优先 override；否则从 suite 映射默认工具 |
| `overrides.mustNot` 或自动推断 | `expected.mustNot` | 优先 override；否则从 suite 排除无关工具 |
| `overrides.expectedContexts` | `expected.expectedContexts` | 直接映射（仅 FAQ 类 episode） |
| `overrides.faithfulnessCheck` | `expected.faithfulnessCheck` | 直接映射 |
| `sourceSession` | `source` | 保留完整溯源信息 |
| 自动生成 | `id` | `{prefix}_{suite}_{序号}`，如 `real_faq_001` |
| 自动生成 | `tags` | `["real_user", "imported"]` + 满意度标签 |
| 默认值 | `expected.replyConstraints` | `{"language": "zh"}` |
| 默认值 | `initialState` | `{"user": {}, "env": {}}` |

**自动推断 suite 的规则**（当 `overrides.suite` 未提供时）：

```
goldenReply 包含帖子/发布/审核相关关键词 → "post_query"
goldenReply 包含知识库/FAQ 类内容特征     → "faq"
goldenReply 包含删除/数据相关关键词       → "data_deletion"
以上均不匹配                             → "general"
```

### 16.5 Episode Model Extension

在 `Episode.java` 中新增可选的 `source` 字段：

```java
public class Episode {
    private String id;
    private String suite;
    private List<String> tags;
    private InitialState initialState;
    private List<ConversationTurn> conversation;
    private EpisodeExpected expected;
    private SourceInfo source;                    // Iter2: 可选，溯源信息

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceInfo {
        private String sessionId;                 // 原始 session ID
        private String userId;                    // 原始用户 ID
        private String resolvedBy;                // "AI" | "HUMAN" | "MIXED"
        private Integer satisfactionScore;        // 1-5
        private String capturedAt;                // ISO 8601 timestamp
        private String exportMethod;              // "auto" | "manual"
    }
}
```

**兼容性保障：**
- `source` 字段使用 `@JsonIgnoreProperties(ignoreUnknown = true)`，Jackson 反序列化时缺失该字段不报错
- 所有评估器 (E1-E6) 仅读取 `expected` 字段，对 `source` 无感知
- `DatasetLoader` 无需修改（已使用 Jackson ObjectMapper 的宽松模式）
- 现有的 20 条 episode 不包含 `source` 字段，继续正常工作

### 16.6 完整数据流闭环

```
┌──────────────────────────────────────────────────────────────┐
│                   评估数据生成 (Iter2)                        │
│                                                              │
│  路径 A: 自动导出                                             │
│  ────────────────                                            │
│  真实用户进线 → AI/人工客服处理 → session CLOSED               │
│       ↓                                                      │
│  推送满意度调查 → 用户评 5 星 → satisfaction_survey 表          │
│       ↓                                                      │
│  eval export-golden → 从 DB 提取 session + messages           │
│       ↓                                                      │
│  自动推断 suite/mustCall/toolArgConstraints                   │
│       ↓                                                      │
│  golden_episodes.jsonl                                       │
│                                                              │
│  路径 B: 手动导入                                             │
│  ────────────────                                            │
│  人工整理高质量 session → real_sessions.jsonl                  │
│       ↓                                                      │
│  eval import → 转换 + overrides 覆盖                          │
│       ↓                                                      │
│  imported_episodes.jsonl                                     │
│                                                              │
└───────────────────────────┬──────────────────────────────────┘
                            ↓
              合并到 episodes.jsonl (或独立数据集文件)
                            ↓
              eval run --dataset episodes.jsonl
                            ↓
              AI Chatbot 处理同样的用户输入
                            ↓
              E1-E6 评估器对比打分
                            ↓
              summary.json + report.html
                            ↓
              eval compare (baseline vs candidate)
                            ↓
              发现回归 / 衡量改进效果
```

### 16.7 Iter2 New Files & Modifications

**New files:**
1. `src/main/resources/db/migration/V5__satisfaction_survey.sql` — 满意度调查表
2. `src/main/java/com/chatbot/model/SatisfactionSurvey.java` — 满意度 POJO
3. `src/main/java/com/chatbot/mapper/SatisfactionSurveyMapper.java` — MyBatis 接口
4. `src/main/resources/mapper/SatisfactionSurveyMapper.xml` — SQL 映射
5. `src/main/java/com/chatbot/service/SatisfactionService.java` — 满意度业务逻辑
6. `src/main/java/com/chatbot/controller/SatisfactionController.java` — API 接口
7. `src/eval/java/com/chatbot/eval/cli/ExportGoldenCommand.java` — 自动导出命令
8. `src/eval/java/com/chatbot/eval/cli/ImportCommand.java` — 手动导入命令
9. `src/eval/java/com/chatbot/eval/model/ImportSession.java` — 导入格式 POJO
10. `src/eval/java/com/chatbot/eval/model/SourceInfo.java` — 溯源信息 POJO
11. `src/eval/java/com/chatbot/eval/converter/SessionToEpisodeConverter.java` — 转换逻辑

**Modified files:**
1. `src/eval/java/com/chatbot/eval/model/Episode.java` — 新增 `source` 字段
2. `src/eval/java/com/chatbot/eval/cli/EvalCommand.java` — 注册 `export-golden` 和 `import` 子命令
3. `src/eval/java/com/chatbot/eval/adapter/EvalToolDispatcher.java` — Iter2: SandboxToolExecutor 支持
4. `src/eval/java/com/chatbot/eval/evaluator/OutcomeEvaluator.java` — 真实实现（不再是 stub）
