# Chatbot AI Customer Service System - Architecture Overview

> 由 `codebase-architect` 技能自动生成，生成日期：2026-04-06。
> 本文档面向**使用者和采用者**视角，不涉及向主仓库贡献代码。
> 架构发生结构性变化后，重新运行 `/codebase-architect` 即可刷新。

## 目录

1. [核心概念与定位](#核心概念与定位)
2. [架构设计](#架构设计)
3. [技术栈](#技术栈)
4. [部署与运维](#部署与运维)
5. [开发实战：扩展工具与评估器](#开发实战扩展工具与评估器)
6. [项目核心机制](#项目核心机制)
7. [用户常见问题解答](#用户常见问题解答)
8. [真实场景与最佳实践](#真实场景与最佳实践)

---

## 核心概念与定位

### 项目定位

AI customer service system that integrates a human agent IM platform with an AI Chatbot. It routes inbound user messages to either an AI agent (powered by Kimi/Moonshot LLM with RAG-based FAQ search) or a human agent via GetStream real-time messaging. The core goal is to validate AI chatbot capabilities and demonstrate seamless AI-to-human handoff.

### 设计理念

1. **Bounded Agent** — The AI agent operates within strict guardrails: evidence-first responses, risk-aware tool firewall (READ vs IRREVERSIBLE), confidence thresholds, and template-only responses for high-risk operations. No hallucination-prone freeform answers for critical actions.
2. **Simplicity over abstraction** — No Spring AI, no JPA/Hibernate, no Redux. Direct RestTemplate calls, MyBatis raw SQL, React hooks. Every layer is explicit and debuggable.
3. **Async-first AI processing** — User messages return immediately; AI processing runs on a dedicated thread pool (`@Async`) to prevent blocking Tomcat threads during multi-second LLM calls.
4. **Safety by design** — IRREVERSIBLE tool operations require explicit user confirmation before execution. Session state is managed in the database (not in-memory), ensuring consistency under concurrent access.

### 核心抽象词汇表

| Concept | Description |
|---------|-------------|
| **Conversation** | A persistent relationship between a user and the system. Created on first message, identified by `conversation_id`. One user has one active conversation. |
| **Session** | A time-bounded interaction window within a conversation. Expires after 10 minutes of inactivity. Carries state: `AI_HANDLING`, `HUMAN_HANDLING`, or `CLOSED`. |
| **GlobalOrchestrator** | The main entry point for all inbound messages. Creates/updates conversations and sessions, then delegates to the Router. |
| **MessageRouter** | Decides whether a message goes to the AI agent or a human agent based on transfer keywords and session status. |
| **AgentCore** | The AI processing pipeline: IntentRouter -> ReactPlanner -> ToolDispatcher -> ResponseComposer. Runs asynchronously. |
| **ToolExecutor** | Interface for tool implementations. Each tool receives `Map<String, Object> params` and returns a `ToolResult`. |
| **Episode** | An evaluation test case containing user input, expected behavior, and multi-layer pass/fail criteria (L1-L4). |

---

## 架构设计

### Overall Architecture

```
+------------------------------------------------------------------+
|                        Client Layer                               |
|                                                                    |
|  +-------------------+              +------------------------+    |
|  |   User Chat Web   |              |  Human Agent Dashboard |    |
|  |   (React 19)      |              |  (React 19)            |    |
|  |   /chat            |              |  /agent                |    |
|  +--------+-----------+              +----------+-------------+    |
|           |                                     |                  |
+-----------+-------------------------------------+------------------+
            |         HTTPS REST + WebSocket      |
            +------------------+------------------+
                               |
+------------------------------+-------------------------------+
|                     Spring Boot Backend                       |
|                        (Port 8080)                            |
|                                                               |
|  +------------------+   +----------------+   +-----------+   |
|  | MessageController|   | SessionController  | StreamToken|   |
|  | /api/messages/*  |   | /api/sessions/*|   | /api/stream|   |
|  +--------+---------+   +--------+-------+   +-----+-----+   |
|           |                      |                   |        |
|  +--------v-----------------------------------------v------+  |
|  |              GlobalOrchestrator                          |  |
|  |  (Conversation + Session lifecycle management)           |  |
|  +-------------------------+--------------------------------+  |
|                            |                                   |
|              +-------------v--------------+                    |
|              |       MessageRouter        |                    |
|              | (AI vs Human routing)      |                    |
|              +------+-------------+-------+                    |
|                     |             |                             |
|          +----------v---+   +----v-----------------+           |
|          | HumanAgent   |   |    AgentCore         |           |
|          | Service      |   |    (@Async)          |           |
|          +--------------+   +----+-----------------+           |
|                                  |                             |
|                    +-------------v--------------+              |
|                    |       AI Pipeline          |              |
|                    | IntentRouter (LLM call)    |              |
|                    | ReactPlanner (deterministic)|             |
|                    | ToolDispatcher (risk check) |             |
|                    | ResponseComposer (LLM call) |             |
|                    +-------------+--------------+              |
|                                  |                             |
|  +----------+  +----------+  +--v----------+  +-----------+   |
|  | FaqService|  |PostQuery |  |UserDataDel  |  | KimiClient|   |
|  | (pgvector)|  |Service   |  |Service      |  | (LLM API) |   |
|  +-----+----+  +----+-----+  +------+------+  +-----+-----+   |
|        |             |               |               |          |
+--------+-------------+---------------+---------------+----------+
         |             |               |               |
   +-----v-------------v---------------v-----+  +-----v------+
   |          PostgreSQL + pgvector          |  | Moonshot AI |
   |  conversation | session | message       |  | (Kimi LLM)  |
   |  faq_doc (vector 1024) | user_post      |  +-------------+
   +------------------+----------------------+
                      |
              +-------v--------+
              | GetStream      |
              | (WebSocket IM) |
              +----------------+
```

The system has three layers: a React frontend (two interfaces), a Spring Boot backend (orchestration + AI pipeline + tools), and external services (PostgreSQL, Kimi LLM, GetStream). All messages flow through the GlobalOrchestrator, which manages conversation/session lifecycle before routing to AI or human handlers. The AI pipeline runs asynchronously on a dedicated thread pool.

### Frontend Architecture

```
+--------------------------------------------------+
|                  React SPA (Vite 6)               |
|                                                    |
|  main.tsx --> BrowserRouter --> App.tsx             |
|                                  |                 |
|              +-------------------+---------------+ |
|              |                                   | |
|     /chat ---v---                       /agent --v-+---+
|     UserChatPage                       AgentDashboardPage
|     |                                  |               |
|     +-- useChat(userId)                +-- useSession  |
|     |   +-- api.getStreamToken         |   (polls /5s) |
|     |   +-- api.getConversation        +-- useAgentChat|
|     |   +-- streamClient (WebSocket)   |   +-- api.*   |
|     |                                  |   +-- stream  |
|     +-- HelpPanel                      +-- useTools    |
|         +-- HelpLauncherButton             |           |
|         +-- HelpComposer              SessionList      |
|         +-- HelpMessageList           ToolPanel        |
|         +-- HelpMessageBubble         MessageList      |
|         +-- HelpTypingIndicator       MessageInput     |
+--------------------------------------------------+
```

Two independent page components share lower-level UI components (`MessageList`, `MessageInput`). State is managed entirely via custom React hooks (`useChat`, `useSession`, `useAgentChat`, `useTools`) — no Redux or global state library. Real-time messages arrive via GetStream WebSocket; the agent dashboard also polls for new sessions every 5 seconds.

### Data Flow

```
User sends message via HelpComposer
       |
       v
useChat.sendMessage() --> POST /api/messages/inbound
       |
       v
MessageController.handleInbound()
       |
       v
GlobalOrchestrator.handleInboundMessage()
  |-- conversationService.findOrCreate()          <-- DB: conversation table
  |-- sessionService.findActiveOrCreate()         <-- DB: session table
  |-- messageService.save(USER message)           <-- DB: message table
  |-- getStreamService.sendMessage()              <-- GetStream WebSocket
  |-- messageRouter.route(session, message)
  |       |
  |       +--> [Transfer keyword detected?]
  |       |       YES --> humanAgentService (HUMAN_HANDLING)
  |       |       NO  --> agentCore.handleMessage()  @Async
  |       |                    |
  |       |              IntentRouter.recognize()    <-- Kimi LLM (temp=0.1)
  |       |                    |
  |       |              [confidence >= 0.7?]
  |       |                NO --> send clarification
  |       |               YES --> ReactPlanner.plan()  (deterministic)
  |       |                    |
  |       |              ToolDispatcher.dispatch()
  |       |                |       |            |
  |       |           FaqService  PostQuery  UserDataDel
  |       |           (pgvector)  (DB query) (risk gate)
  |       |                |
  |       |              ResponseComposer
  |       |                |-- composeFromTemplate() (high-risk: fixed text)
  |       |                |-- composeWithEvidence() (low-risk: LLM, temp=0.7)
  |       |                    |
  |       |              messageService.save(AI reply)   <-- DB
  |       |              getStreamService.sendMessage()   <-- GetStream
  |       |
  |-- return InboundMessageResponse (immediate)
       |
       v
HTTP 200 returned to client immediately (AI processes async)
       |
       v
GetStream WebSocket pushes AI reply to user's HelpPanel
```

The key architectural decision is the async split: the HTTP response returns immediately with message IDs, while the AI pipeline (potentially 5-15 seconds of LLM calls) runs on a separate `aiTaskExecutor` thread pool. Responses are delivered via GetStream WebSocket push.

---

## 技术栈

### Frontend

```
+---------------------+----------------------------------------------+
| Layer               | Technology                                   |
+---------------------+----------------------------------------------+
| Framework           | React 19.x                                   |
| Language            | TypeScript 5.7 (strict mode)                 |
| Build Tool          | Vite 6.x                                     |
| Styling             | Tailwind CSS 4.x (utility classes in JSX)    |
| Routing             | React Router 7.x (flat, 2 routes)            |
| State Management    | React hooks only (no Redux/Zustand)          |
| Real-time Chat      | stream-chat 8.x + stream-chat-react 12.x    |
| API Client          | Native fetch (no axios)                      |
| Testing             | Vitest 4.x + Testing Library                |
+---------------------+----------------------------------------------+
```

- No global state library by design — hooks + props propagation sufficient for two-page app
- GetStream SDK handles WebSocket connection, reconnection, and event dispatching
- Vite proxy forwards `/api` requests to backend during development

### Backend

```
+---------------------+----------------------------------------------+
| Layer               | Technology                                   |
+---------------------+----------------------------------------------+
| Language            | Java 21 (LTS)                                |
| Framework           | Spring Boot 3.4.3                            |
| Data Access         | MyBatis 3.0.4 (XML mappers, raw SQL)         |
| Database            | PostgreSQL 16+ with pgvector extension       |
| Migration           | Flyway 10.x (4 migrations: V1-V4)           |
| Vector Search       | pgvector <=> operator (cosine distance)      |
| LLM (Conversation)  | Kimi/Moonshot AI (moonshot-v1-8k)            |
| LLM (Embedding)     | DashScope text-embedding-v4 (1024-dim)       |
| Real-time Chat      | GetStream Java SDK 1.24.0                    |
| HTTP Client (LLM)   | Spring RestTemplate (not Spring AI)          |
| Async Processing    | @Async + ThreadPoolTaskExecutor              |
| Build Tool          | Gradle 8.12 (Groovy DSL)                     |
| Testing             | JUnit 5 + Mockito (20 test files)            |
+---------------------+----------------------------------------------+
```

- MyBatis chosen over JPA for explicit SQL control, especially pgvector queries
- RestTemplate chosen over Spring AI for simplicity and direct control of LLM API calls
- Separate Gradle `eval` source set for the evaluation framework (isolated from main app)

---

## 部署与运维

### Prerequisites

- JDK 21+
- PostgreSQL 16+ with pgvector extension enabled
- Node.js 22+ (for frontend)

### Installation

```bash
# 1. Clone and set up environment
cd chatbot
cp .env.local.example .env.local
# Edit .env.local: fill in DB_USERNAME, DB_PASSWORD, KIMI_API_KEY,
# DASHSCOPE_API_KEY, GETSTREAM_API_KEY, GETSTREAM_API_SECRET

# 2. Set up PostgreSQL
psql -c "CREATE DATABASE chatbot;"
psql -d chatbot -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 3. Start backend (Flyway auto-migrates on first run)
cd backend && ./gradlew bootRun

# 4. Start frontend (separate terminal)
cd frontend && npm install && npm run dev
```

### Key Configuration

| Config Key | Default | Purpose | When to Change |
|-----------|---------|---------|----------------|
| `server.port` | 8080 | Backend HTTP port | Port conflict |
| `chatbot.session.timeout-minutes` | 10 | Session inactivity timeout | Adjust conversation window |
| `chatbot.ai.max-react-rounds` | 3 | Max tool-call iterations per message | If agent loops too many times |
| `chatbot.ai.confidence-threshold` | 0.7 | Min confidence to proceed with intent | Tune intent recognition sensitivity |
| `chatbot.ai.faq-score-threshold` | 0.75 | Min vector similarity for FAQ match | Tune FAQ retrieval recall/precision |
| `kimi.chat.model` | moonshot-v1-8k | LLM model for conversation | Switch to larger context model |
| `kimi.chat.temperature` | 0.7 | Response generation randomness | Lower for more deterministic replies |
| `chatbot.router.transfer-keywords` | 转人工,转接人工,人工客服,人工服务 | Keywords that trigger human handoff | Add/remove transfer triggers |

### Debugging

- **Backend logs**: Standard Spring Boot console output (SLF4J + Logback). Key events logged at INFO level: message received, intent recognized, tool dispatched, reply sent.
- **Session state**: Query directly: `SELECT * FROM session WHERE status != 'CLOSED' ORDER BY last_activity_at DESC;`
- **FAQ vectors**: Verify embeddings loaded: `SELECT faq_id, question FROM faq_doc WHERE embedding IS NOT NULL;`
- **GetStream**: Messages visible in GetStream Dashboard (requires API key login).

### Demo: Minimal Success Path

```bash
# 1. Start both services (backend + frontend)
# 2. Open http://localhost:3000/chat?userId=user_alice
# 3. Type: "你好" → AI responds with greeting
# 4. Type: "帮我查一下alice的帖子" → AI calls post_query tool, returns post status
# 5. Type: "转人工" → Session switches to HUMAN_HANDLING
# 6. Open http://localhost:3000/agent → Agent sees the session
# 7. Agent types reply → User receives it in real-time
```

### Running the Evaluation Framework

```bash
cd backend
./gradlew evalRun
# Reads episodes from src/eval/resources/episodes.jsonl
# Generates HTML report in build/eval-reports/
```

---

## 开发实战：扩展工具与评估器

> This section is for users who want to extend the system's capabilities — adding new AI tools or evaluation criteria — without modifying core framework code.

### Adding a New Tool (4 Steps)

**Step 1**: Add entry to `ToolDefinition` enum

```java
// backend/src/main/java/com/chatbot/service/tool/ToolDefinition.java
public enum ToolDefinition {
    FAQ_SEARCH("faq_search", "搜索 FAQ 知识库", RiskLevel.READ),
    POST_QUERY("post_query", "查询用户帖子状态", RiskLevel.READ),
    USER_DATA_DELETE("user_data_delete", "删除用户数据", RiskLevel.IRREVERSIBLE),
    // ADD:
    ORDER_QUERY("order_query", "查询用户订单状态", RiskLevel.READ);
}
```

**Step 2**: Implement `ToolExecutor` interface

```java
// backend/src/main/java/com/chatbot/service/tool/OrderQueryService.java
@Service
public class OrderQueryService implements ToolExecutor {
    private final OrderMapper orderMapper;

    public OrderQueryService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String orderId = (String) params.get("order_id");
        if (orderId == null || orderId.isBlank()) {
            return ToolResult.error("Missing order_id parameter");
        }
        Order order = orderMapper.findByOrderId(orderId);
        if (order == null) {
            return ToolResult.success("未找到订单 " + orderId);
        }
        return ToolResult.success(order.toJson());
    }
}
```

**Step 3**: Register in `ToolDispatcher` constructor

```java
// The ToolDispatcher already auto-discovers ToolExecutor beans.
// Just ensure your @Service is in the component scan path.
// Add param validation in ToolDispatcher.validateParams():
case ORDER_QUERY -> {
    if (!params.containsKey("order_id")) return "Missing order_id";
}
```

**Step 4**: Update intent recognition

- Add the new intent to `prompts/intent-router.md` template
- Add a case to `ReactPlanner.plan()` for the new intent
- Update `ResponseComposer` if the tool needs special response formatting

### Adding a New Evaluator

**Step 1**: Implement `Evaluator` interface

```java
// backend/src/eval/java/com/chatbot/eval/evaluator/ToneEvaluator.java
public class ToneEvaluator implements Evaluator {
    @Override
    public String name() { return "L5_Tone"; }

    @Override
    public EvalResult evaluate(Episode episode, RunResult runResult) {
        // Check reply tone against expected criteria
        // Return EvalResult.pass(name()) or EvalResult.fail(name(), violations)
    }
}
```

**Step 2**: Add expected fields to `EpisodeExpected` model and register in `RunCommand`

### Extension Points Summary

| Extension Point | Interface | Registration | Example |
|----------------|-----------|-------------|---------|
| New AI tool | `ToolExecutor` | `ToolDefinition` enum + `@Service` | `FaqService`, `PostQueryService` |
| New evaluator | `Evaluator` | `RunCommand` evaluator list | `GateEvaluator`, `LayeredOutcomeEvaluator` |
| New LLM prompt | Markdown file | `application.yml` prompt paths | `prompts/intent-router.md` |
| New DB table | Flyway migration + MyBatis mapper | `db/migration/V{N}__*.sql` | `V1__init_schema.sql` |

---

## 项目核心机制

> This section covers the technical mechanisms that are unique to this project and critical for architects to understand deeply.

### Message Processing Pipeline

The system processes messages through a layered pipeline with a critical async boundary:

```
Synchronous (Tomcat thread)          Async (aiTaskExecutor thread)
+-------------------------------+    +--------------------------------+
| 1. Save user message to DB   |    | 4. IntentRouter: LLM classifies|
| 2. Broadcast via GetStream   |--->| 5. ReactPlanner: pick tool     |
| 3. Return HTTP 200 immediately|    | 6. ToolDispatcher: execute     |
+-------------------------------+    | 7. ResponseComposer: LLM reply |
                                     | 8. Save AI reply to DB         |
                                     | 9. Broadcast via GetStream     |
                                     +--------------------------------+
```

Steps 1-3 complete in ~50ms. Steps 4-9 take 2-15 seconds depending on LLM latency and tool complexity. The `aiTaskExecutor` thread pool is configured with `corePoolSize=5, maxPoolSize=20, queueCapacity=50`.

### Risk-Aware Tool Firewall

The `ToolDispatcher` enforces a risk gate before tool execution:

```
ToolCall arrives
     |
     v
ToolDefinition.getRisk()
     |
     +-- READ --> execute immediately
     |
     +-- IRREVERSIBLE --> check toolCall.isUserConfirmed()
              |
              +-- false --> return needsConfirmation (pause, ask user)
              |
              +-- true  --> execute
```

For IRREVERSIBLE operations (e.g., `user_data_delete`), the system stores a `pendingConfirmation` flag in the message's `metadata_json`. On the next user message, `AgentCore.checkPendingConfirmation()` looks for confirmation keywords before re-dispatching with `userConfirmed=true`.

High-risk operations also bypass LLM response generation entirely — they use fixed templates from `ResponseComposer.composeFromTemplate()` to prevent the LLM from producing incorrect or misleading text about irreversible actions.

### Session State Machine

```
                   +-- new message within 10 min --+
                   |                                |
                   v                                |
[NEW] --first msg--> AI_HANDLING --transfer keyword--> HUMAN_HANDLING
                        |                                    |
                        +-- 10 min inactivity --> CLOSED <---+
                                                     |
                                                     v
                                            (new message creates
                                             new session, AI_HANDLING)
```

Session state transitions are enforced in the database (not in-memory) via conditional SQL updates (`WHERE status = 'AI_HANDLING'`). The `SessionTimeoutScheduler` runs every 60 seconds and closes stale sessions with a single bulk SQL update — no per-session iteration in Java.

### Vector-Based FAQ Retrieval (RAG)

The FAQ pipeline uses pgvector for semantic search:

1. At startup, `FaqEmbeddingInitializer` calls DashScope to embed all FAQ questions into 1024-dim vectors
2. When `FaqService.execute()` runs, it embeds the user query via DashScope
3. pgvector `<=>` operator finds the closest FAQ by cosine distance
4. If similarity score >= `faq-score-threshold` (0.75), the FAQ answer is included as context
5. `ResponseComposer.composeWithEvidence()` sends the FAQ context + user question to Kimi LLM for a natural language response

This is a single-stage RAG (no reranker or hybrid retrieval in v1).

### Evaluation Framework (4-Layer System)

The evaluation framework mirrors the agent pipeline in a synchronous adapter (`SyncAgentAdapter`) for deterministic testing:

```
Episode (test case)
     |
     v
SyncAgentAdapter.runEpisode()
     |-- IntentRouter (real LLM call)
     |-- ReactPlanner (deterministic)
     |-- EvalToolDispatcher (auto-confirms for testing)
     |-- ResponseComposer (real LLM call)
     |
     v
RunResult (actions, trace, reply)
     |
     +-> L1 GateEvaluator        (hard gates: forbidden tools, false claims)
     +-> L2 LayeredOutcomeEvaluator (task success: correct tool called?)
     +-> L3 LayeredTrajectoryEvaluator (process: efficient tool usage?)
     +-> L4 ReplyQualityEvaluator (output: relevant, concise, accurate?)
     |
     v
EvalScore --> HTML Report
```

L1 failure blocks overall pass regardless of L2-L4 scores. Each layer evaluates independently, providing granular diagnostics.

### Architect's Must-Read Files

| Priority | File | Why |
|----------|------|-----|
| 1 | `service/agent/AgentCore.java` | The complete AI pipeline in one file — async entry, intent routing, ReAct loop, confirmation workflow, error handling |
| 2 | `service/tool/ToolDispatcher.java` | Risk gate logic, param validation, executor dispatch — the safety boundary |
| 3 | `service/orchestrator/GlobalOrchestrator.java` | Message lifecycle entry point — conversation/session management |
| 4 | `service/router/MessageRouter.java` | AI vs human routing decision logic |
| 5 | `resources/prompts/intent-router.md` | The LLM prompt that classifies user intent — controls all downstream behavior |

---

## 用户常见问题解答

> 站在**使用这个系统的人**的角度整理。

**Q：这个系统解决了什么核心痛点？**
A：传统客服系统要么全靠人工（成本高、响应慢），要么纯 AI（处理不了复杂问题）。本系统将两者结合：简单问题（FAQ 查询、帖子状态查询）由 AI 即时处理，复杂问题（数据删除、投诉等）无缝转接人工客服。用户只需说"转人工"即可随时切换。

**Q：我需要具备什么技术背景才能部署它？**
A：需要 Java 21 + PostgreSQL + Node.js 环境。熟悉 Spring Boot 基本配置即可。主要门槛在于获取三个外部服务的 API Key：GetStream（IM 通道）、Moonshot AI（Kimi LLM）、DashScope（Embedding）。本地部署约 30 分钟。

**Q：AI 能处理哪些类型的用户问题？**
A：当前支持三类工具：(1) FAQ 知识库语义搜索（基于向量相似度），(2) 用户帖子状态查询，(3) 用户数据删除请求。通用闲聊也能回复，但不调用工具。新工具可通过实现 `ToolExecutor` 接口添加。

**Q：高风险操作（如删除用户数据）有什么安全机制？**
A：三重保护：(1) 工具定义为 `RiskLevel.IRREVERSIBLE`，ToolDispatcher 自动拦截要求用户确认；(2) 确认后才执行，且 AI 回复使用固定模板（不经 LLM 生成），避免误导；(3) 未登录用户无法触发高风险操作（Identity Gate）。

**Q：AI 回答不准确时怎么办？**
A：系统设计为 evidence-first（证据优先）：AI 的回复基于工具返回的真实数据，而非自由生成。如果 FAQ 相似度低于阈值（0.75），系统会明确告知"未找到相关信息"而非编造答案。用户随时可说"转人工"切换到人工客服。

**Q：系统支持多少并发用户？**
A：单机部署下，Tomcat 线程池默认 200 线程处理 HTTP 请求。AI 处理在独立线程池（核心 5 线程，最大 20 线程）异步执行。瓶颈在 LLM API 响应时间（2-10 秒/次），20 路并发 AI 对话是实际上限。人工客服侧无此限制。

**Q：如何扩展支持新的 AI 工具？**
A：四步：(1) 在 `ToolDefinition` 枚举添加工具条目，(2) 实现 `ToolExecutor` 接口，(3) 更新意图识别 prompt，(4) 在 `ReactPlanner` 添加规划逻辑。无需修改 `AgentCore` 或 `ToolDispatcher` 核心代码。详见本文「开发实战」章节。

**Q：评估框架有什么用？**
A：用于离线验证 AI 质量。编写 JSONL 格式的测试用例（Episode），运行 `./gradlew evalRun` 后生成 HTML 报告，覆盖四个维度：硬门禁（L1）、任务成功率（L2）、执行效率（L3）、回复质量（L4）。支持回归测试：修改 prompt 或工具后，跑一遍评估看是否退化。

**Q：数据存在哪里？会上传到云端吗？**
A：所有对话数据存在本地 PostgreSQL 数据库中。发送到云端的数据：(1) 用户消息发送至 Kimi API 用于生成回复（受 Moonshot AI 隐私政策约束），(2) 用户查询发送至 DashScope 用于向量化（受阿里云隐私政策约束），(3) 消息通过 GetStream 实时推送（受 GetStream 隐私政策约束）。

---

## 真实场景与最佳实践

> 以下场景基于项目设计意图和代码实现推断，适用于此类 AI + 人工协作客服系统。

**场景一：FAQ 知识库冷启动**
> 来源：基于项目实现推断

**问题背景**：新部署系统时，FAQ 知识库为空或条目很少，AI 无法有效回答用户问题。
**使用方式**：通过 Flyway migration SQL 批量导入 FAQ 条目到 `faq_doc` 表（question + answer）。系统启动时 `FaqEmbeddingInitializer` 自动调用 DashScope 为所有无 embedding 的条目生成向量。
**实际效果**：首次启动需要等待所有 FAQ 完成向量化（取决于条目数量和 DashScope 速率限制），之后即可进行语义搜索。
**注意事项**：DashScope 有调用频率限制；大批量导入建议分批进行。`faq-score-threshold` 初始建议 0.75，根据实际匹配效果调优。

**场景二：高风险操作的二次确认流程**
> 来源：基于 AgentCore 确认机制实现

**问题背景**：用户说"帮我删除数据"，但可能是误操作或表述模糊。直接执行会造成不可逆后果。
**使用方式**：系统自动拦截：ToolDispatcher 检测到 IRREVERSIBLE 风险级别后暂停执行，返回确认提示。用户需要回复确认关键词（"确认删除"、"是的"等）才会实际执行。
**实际效果**：避免误操作。确认信息存储在消息 `metadata_json` 中，跨消息传递状态。
**注意事项**：确认关键词列表在 `AgentCore.checkPendingConfirmation()` 中硬编码。如需更严格的确认（如验证码），需自行扩展确认逻辑。

**场景三：AI 到人工的无缝切换**
> 来源：基于 MessageRouter 路由逻辑

**问题背景**：AI 无法解决用户问题（或用户主动要求人工服务），需要平滑切换到人工客服，且保留对话上下文。
**使用方式**：用户发送包含转人工关键词（"转人工"、"人工客服"等）的消息。系统自动发送一条系统消息"正在为您转接人工客服..."，将 session 状态更新为 `HUMAN_HANDLING`，后续消息直接路由到人工客服界面。
**实际效果**：人工客服在 `/agent` 页面看到该 session，可查看完整历史消息（包括 AI 对话部分），无需用户重复描述问题。
**注意事项**：同一 session 内一旦转人工，不会自动回到 AI。新 session（10 分钟无活动后自动创建）会重新从 AI 开始。

**场景四：评估驱动的 Prompt 调优**
> 来源：基于评估框架设计意图推断

**问题背景**：修改了意图识别 prompt 或调整了 confidence threshold，担心影响其他场景的准确性。
**使用方式**：编写 JSONL 格式的评估 Episode，覆盖各种意图场景（FAQ、帖子查询、数据删除、闲聊、边界情况）。修改 prompt 后运行 `./gradlew evalRun`，对比 L1-L4 各层得分。
**实际效果**：自动化回归测试。L1 Gate 检查是否调用了禁止的工具，L2 检查任务是否成功，L3 检查是否高效（没有冗余工具调用），L4 检查回复质量。
**注意事项**：评估依赖真实 LLM 调用（非 mock），每次运行消耗 API 额度。建议控制 Episode 数量（20-50 条覆盖核心场景即可）。

**场景五：并发消息的安全处理**
> 来源：基于 Session 并发安全设计

**问题背景**：用户快速连续发送多条消息，可能导致 session 状态竞态（同时触发两个 AI 处理线程）。
**使用方式**：系统设计中 session 状态更新依赖数据库条件更新（`WHERE status = 'AI_HANDLING'`），而非内存锁。即使两个线程同时处理，只有一个能成功更新状态。
**实际效果**：数据一致性有保障，但用户可能收到多条 AI 回复。
**注意事项**：v1 未做消息排队或去重。如果用户高频发送消息成为实际问题，可考虑在 `GlobalOrchestrator` 层添加基于 session 的消息队列。
