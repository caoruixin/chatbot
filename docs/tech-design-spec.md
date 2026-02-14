# 技术设计规格说明书 (Technical Design Spec)

## 1. 概述

本文档基于 [PRD](./PRD.md) 和 [AI Service Agent 架构规格](./ai-service-agent.md)，定义智能客服系统的完整技术实现方案。

设计原则：
- 最少依赖：不引入不必要的框架和库
- 轻量灵活：MyBatis 替代 JPA，直接 HTTP 调用 LLM 替代 Spring AI
- 边界清晰：Controller / Service / Mapper 三层严格分离
- Bounded Agent：AI 回复必须基于工具输出或知识库证据，高风险操作仅用模板回复

---

## 2. 技术栈总览

### 2.1 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 (LTS) | 运行时 |
| Spring Boot | 3.4.x | 应用框架 |
| Spring Web (RestTemplate) | 6.2.x | REST API + HTTP 客户端 |
| MyBatis | 3.5.x | 数据库访问 |
| mybatis-spring-boot-starter | 3.0.x | MyBatis 与 Spring Boot 集成 |
| PostgreSQL | 16+ | 关系型数据库 |
| pgvector (扩展) | 0.8.x | 向量存储 |
| com.pgvector:pgvector | 0.1.x | pgvector JDBC 类型支持 |
| Flyway | 10.x | 数据库版本迁移 |
| GetStream Chat Java SDK | 1.24.x | 服务端 IM 操作 |
| Jackson | 2.17.x | JSON 序列化（Spring Boot 自带） |
| Gradle (Groovy DSL) | 8.12.x | 构建工具 |

**明确不引入的依赖：**
- ~~Spring Data JPA / Hibernate~~ → 用 MyBatis
- ~~Spring AI~~ → 直接 RestTemplate 调用 OpenAI 兼容 API（Kimi 对话 + DashScope Embedding）
- ~~Kotlin~~ → 纯 Java + Groovy Gradle
- ~~ONNX Runtime~~ → Embedding 由 DashScope API 生成（text-embedding-v4）

### 2.2 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Node.js | 22 LTS | 运行时 |
| React | 19.x | UI 框架 |
| TypeScript | 5.7.x | 类型安全 |
| Vite | 6.x | 构建工具 / 开发服务器 |
| React Router | 7.x | 客户端路由 |
| Tailwind CSS | 4.x | 样式 |
| stream-chat-react | 12.x | GetStream Chat React SDK |
| stream-chat | 8.x | GetStream Chat JS 客户端 |

### 2.3 第三方服务

| 服务 | 用途 | 集成方式 |
|------|------|---------|
| GetStream Chat | 实时消息收发 | 后端 Java SDK + 前端 React SDK |
| Kimi (Moonshot AI) | LLM 对话（意图识别 / 回复生成） | RestTemplate 调用 OpenAI 兼容 API |
| DashScope (阿里云通义千问) | 文本 Embedding（FAQ 向量化） | RestTemplate 调用 DashScope API |

### 2.4 本地环境依赖

| 依赖 | 说明 |
|------|------|
| PostgreSQL 16+ | 本地安装，需启用 pgvector 扩展 |
| JDK 21 | 后端编译运行 |
| Node.js 22 LTS | 前端构建运行 |

---

## 3. 系统架构

### 3.1 总体架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Frontend (React + Vite)                       │
│                                                                      │
│  ┌──────────────────┐                 ┌───────────────────────────┐  │
│  │   User Web       │                 │   Human Agent Web         │  │
│  │   /chat          │                 │   /agent                  │  │
│  │  ┌────────────┐  │                 │  ┌─────────────────────┐  │  │
│  │  │MessageList │  │                 │  │SessionList          │  │  │
│  │  │MessageInput│  │                 │  │MessageList          │  │  │
│  │  └────────────┘  │                 │  │MessageInput         │  │  │
│  │                   │                 │  │ToolPanel            │  │  │
│  └────────┬──────────┘                 └──────────┬──────────────┘  │
│           │        ┌────────────────────┐         │                 │
│           └───────►│ GetStream Client   │◄────────┘                 │
│            (实时)   │ (WebSocket)        │                           │
│                    └─────────┬──────────┘                           │
└──────────────────────────────┼──────────────────────────────────────┘
                               │
                   ┌───────────▼────────────┐
                   │   GetStream Cloud      │
                   └───────────┬────────────┘
                               │
     ┌─────────────────────────┼────────────────────────────────────┐
     │ REST API                │ Server-side SDK                    │
     ▼                         ▼                                    │
┌───────────────────────────────────────────────────────────────────┐│
│                    Backend (Spring Boot)                           ││
│                                                                   ││
│ ┌───────────────────────────────────────────────────────────────┐ ││
│ │                    Controller 层 (API)                         │ ││
│ │ MessageController │ ConversationController │ ToolController    │ ││
│ │ SessionController │ StreamTokenController                     │ ││
│ └─────────────────────────────┬─────────────────────────────────┘ ││
│                               │                                   ││
│ ┌─────────────────────────────▼─────────────────────────────────┐ ││
│ │                    Service 层 (业务逻辑)                        │ ││
│ │                                                               │ ││
│ │  ┌───────────────────┐    ┌──────────────┐                    │ ││
│ │  │GlobalOrchestrator │───►│MessageRouter │                    │ ││
│ │  └───────────────────┘    └──────┬───────┘                    │ ││
│ │                            ┌─────┴──────┐                     │ ││
│ │                            ▼            ▼                     │ ││
│ │               ┌─────────────────┐ ┌────────────────┐          │ ││
│ │               │ Agent Core      │ │HumanAgentSvc   │          │ ││
│ │               │ (Bounded Agent) │ └────────────────┘          │ ││
│ │               │                 │                             │ ││
│ │               │ ┌─────────────┐ │  ┌──────────────────────┐   │ ││
│ │               │ │IntentRouter │ │  │   Tool Services      │   │ ││
│ │               │ │ReAct Loop   │ │  │ ┌──────────────────┐ │   │ ││
│ │               │ │ResponseComp.│ │  │ │ToolRegistry      │ │   │ ││
│ │               │ └─────────────┘ │  │ │ToolDispatcher    │ │   │ ││
│ │               └────────┬────────┘  │ │FaqService        │ │   │ ││
│ │                        │           │ │UserDataService    │ │   │ ││
│ │               ┌────────▼────────┐  │ │PostQueryService   │ │   │ ││
│ │               │  KimiClient     │  │ └──────────────────┘ │   │ ││
│ │               │ (RestTemplate)  │  └──────────────────────┘   │ ││
│ │               └─────────────────┘                             │ ││
│ │               ┌─────────────────┐                             │ ││
│ │               │GetStreamService │                             │ ││
│ │               └─────────────────┘                             │ ││
│ └───────────────────────────┬───────────────────────────────────┘ ││
│                             │                                     ││
│ ┌───────────────────────────▼───────────────────────────────────┐ ││
│ │                    Mapper 层 (MyBatis)                         │ ││
│ │ ConversationMapper │ SessionMapper │ MessageMapper             │ ││
│ │ UserPostMapper     │ FaqDocMapper                              │ ││
│ └───────────────────────────┬───────────────────────────────────┘ ││
│                             │                                     ││
│ ┌───────────────────────────▼───────────────────────────────────┐ ││
│ │               PostgreSQL + pgvector                            │ ││
│ │ conversation │ session │ message │ user_post │ faq_doc         │ ││
│ └───────────────────────────────────────────────────────────────┘ ││
└───────────────────────────────────────────────────────────────────┘│
```

### 3.2 后端分层架构

```
┌──────────────────────────────────────────────────┐
│           Controller 层 (API 边界)                 │
│  - 接收 HTTP 请求，参数校验                         │
│  - 调用 Service 层                                 │
│  - 返回 DTO，不暴露 Model                           │
│  - 不含业务逻辑                                     │
├──────────────────────────────────────────────────┤
│           DTO 层 (数据传输)                         │
│  - Request DTO：接收前端请求                        │
│  - Response DTO：返回前端数据                       │
│  - 与 Model 隔离                                   │
├──────────────────────────────────────────────────┤
│           Service 层 (业务逻辑)                     │
│  - 核心编排 (Orchestrator / Router)                 │
│  - AI Agent Core (Router + ReAct + Composer)       │
│  - 工具系统 (Registry + Dispatcher + Services)      │
│  - 人工客服管理                                     │
│  - GetStream 集成 / Kimi LLM 客户端                │
│  - 调用 Mapper 进行数据读写                         │
├──────────────────────────────────────────────────┤
│           Model 层 (数据模型)                       │
│  - 简单 POJO，对应数据库表                           │
│  - 枚举类型                                        │
├──────────────────────────────────────────────────┤
│           Mapper 层 (MyBatis 数据访问)              │
│  - MyBatis Mapper 接口 + XML SQL 映射              │
│  - 只被 Service 层调用                              │
├──────────────────────────────────────────────────┤
│           数据库 (PostgreSQL + pgvector)            │
└──────────────────────────────────────────────────┘
```

**层间调用规则：**
- Controller → Service → Mapper（单向，不可反向）
- Controller 不可直接调用 Mapper
- Service 间可相互调用
- Mapper 不依赖上层

### 3.3 前端架构

```
┌──────────────────────────────────────────────────┐
│                React App (SPA)                    │
│                                                   │
│  ┌─────────────────────────────────────────────┐  │
│  │             React Router                     │  │
│  │  /chat   → UserChatPage                     │  │
│  │  /agent  → AgentDashboardPage               │  │
│  └─────────────────┬───────────────────────────┘  │
│                    │                              │
│  ┌─────────────────▼───────────────────────────┐  │
│  │          Page Components                     │  │
│  │  UserChatPage  │  AgentDashboardPage         │  │
│  └─────────────────┬───────────────────────────┘  │
│                    │                              │
│  ┌─────────────────▼───────────────────────────┐  │
│  │          Shared Components                   │  │
│  │  MessageList │ MessageBubble │ MessageInput  │  │
│  │  SessionList │ ToolPanel                     │  │
│  └─────────────────┬───────────────────────────┘  │
│                    │                              │
│  ┌─────────────────▼───────────────────────────┐  │
│  │          Services + Hooks                    │  │
│  │  apiClient.ts │ streamClient.ts              │  │
│  │  useChat.ts   │ useSession.ts │ useTools.ts  │  │
│  └─────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

| 决策 | 选型 | 理由 |
|------|------|------|
| 构建工具 | Vite | 开发热更新快，零配置 |
| 路由 | React Router | User Web 和 Agent Web 共享代码 |
| 样式 | Tailwind CSS | 实用优先，快速搭建 |
| 实时通信 | GetStream React SDK | WebSocket 自动管理 |
| HTTP | fetch API | 浏览器原生，无额外依赖 |
| 状态管理 | React hooks + Context | 应用简单，不需要 Redux |

---

## 4. AI Agent 架构设计（Bounded Agent）

基于 [ai-service-agent.md](./ai-service-agent.md)，AI Chatbot 采用**有边界的 Agent** 架构。

### 4.1 核心理念

| 原则 | 说明 |
|------|------|
| Evidence-first | 回复必须可追溯到工具输出或 KB 片段，无证据则拒答 |
| Template for high-risk | 数据删除等高风险操作，最终回复只能由模板输出，LLM 不生成承诺文本 |
| Bounded ReAct | 有限次工具调用（最多 3 轮），超出则拒答 + 引导人工 |
| Fail-safe | 低置信度最多 1 次追问 → 仍不清楚则引导人工 |

### 4.2 Agent Core 架构

```
用户消息 + Session 上下文
        │
        ▼
┌───────────────────────────────────────────────────────────┐
│                    Agent Core                              │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  Intent Router                                       │  │
│  │  ───────────────────────────────────────────────     │  │
│  │  输入：用户消息 + 对话历史                              │  │
│  │  输出：intent + confidence + risk_level              │  │
│  │                                                      │  │
│  │  Kimi LLM 调用（结构化 JSON 输出）                     │  │
│  │  → { intent: "POST_QUERY",                           │  │
│  │      confidence: 0.92,                               │  │
│  │      risk: "low",                                    │  │
│  │      extracted_params: { username: "alice" } }        │  │
│  └──────────────────────┬──────────────────────────────┘  │
│                         │                                 │
│           ┌─────────────┼─────────────┐                   │
│           ▼             ▼             ▼                   │
│    ┌────────────┐ ┌──────────┐ ┌────────────┐            │
│    │ Fast Path  │ │ DSR Path │ │ RAG Path   │            │
│    │ POST_QUERY │ │ DATA_DEL │ │ KB_QUESTION│            │
│    │ (read-only)│ │ (critical│ │ (FAQ)      │            │
│    └─────┬──────┘ │  risk)   │ └─────┬──────┘            │
│          │        └────┬─────┘       │                   │
│          ▼             ▼             ▼                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  ReAct Loop (Bounded, 最多 3 轮)                     │  │
│  │  ─────────────────────────────────────────────────   │  │
│  │  循环：                                               │  │
│  │   1. Plan → 决定调用哪个工具                           │  │
│  │   2. Tool Dispatcher → schema 校验 → 执行 → 结果      │  │
│  │   3. Observe → 检查结果是否充分                        │  │
│  │   4. 充分 → 退出循环 / 不足 → 追问或重试               │  │
│  │                                                      │  │
│  │  退出条件：                                            │  │
│  │   - 获得足够证据 → 进入 Response Composer              │  │
│  │   - 达到最大轮次 → 拒答 + 引导人工                     │  │
│  │   - 工具失败 → 降级提示                                │  │
│  └──────────────────────┬──────────────────────────────┘  │
│                         │                                 │
│  ┌──────────────────────▼──────────────────────────────┐  │
│  │  Response Composer                                   │  │
│  │  ───────────────────────────────────────────────     │  │
│  │  低风险 (POST_QUERY / KB_QUESTION)：                  │  │
│  │    → Kimi LLM 生成自然语言回复 + 引用来源               │  │
│  │  高风险 (DATA_DELETION)：                             │  │
│  │    → 仅使用预定义模板，LLM 不生成承诺文本               │  │
│  │  失败：                                               │  │
│  │    → 固定话术："抱歉，我无法处理...建议发送'转人工'"      │  │
│  └─────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
```

### 4.3 三条处理路径

#### Path A：帖子状态查询（Fast Path，read-only）

```
1. Intent Router → intent=POST_QUERY, risk=low, confidence≥0.8
2. ReAct Loop:
   - 检查 extracted_params 中是否有 username
   - 无 → 回复追问 "请提供您的用户名"（最多 1 次追问）
   - 有 → Tool Dispatcher 调用 PostQueryService.query(username)
3. Response Composer:
   - LLM 将工具结果组织为用户友好的回复
   - 附带数据来源："数据来源：帖子系统 @ {timestamp}"
4. 工具失败 → "系统暂时繁忙，请稍后再试或发送'转人工'"
```

#### Path B：数据删除请求（DSR，高风险）

```
1. Intent Router → intent=DATA_DELETION, risk=critical, confidence≥0.9
2. ReAct Loop (对话引导式信息收集):
   - 第 1 轮：提取 username → 无则追问
   - 第 2 轮：二次确认 → 模板："您确认要删除用户 {username} 的所有数据吗？此操作不可撤销。"
   - 用户确认 → Tool Dispatcher 调用 UserDataDeletionService.delete(username, idempotencyKey)
3. Response Composer:
   - 仅使用模板回复，不自由生成：
     "已收到您的数据删除请求（请求编号：{requestId}）。预计 24 小时内处理完毕。"
4. 信息不足 / 用户未确认 → 不执行，引导人工
```

#### Path C：通用咨询（RAG Path）

```
1. Intent Router → intent=KB_QUESTION, risk=low
2. ReAct Loop:
   - Tool Dispatcher 调用 FaqService.search(query)
   - 检查返回结果 score ≥ 阈值 (0.75)
   - 低于阈值 → "抱歉，我没有找到相关信息。建议发送'转人工'获取帮助"
3. Response Composer:
   - LLM 基于 FAQ 结果生成回复 + 引用问题来源
   - "参考：{matched_question}"
4. 检索无结果 → 拒答 + 引导人工
```

#### 低置信度 / 无法识别意图

```
1. Intent Router → confidence < 0.7 或 intent=UNKNOWN
2. 回复一次澄清问题："请问您是想查询帖子状态、删除数据，还是有其他问题？"
3. 用户再次输入后重新走 Intent Router
4. 仍无法识别 → "抱歉，我暂时无法理解您的问题。建议发送'转人工'获取帮助"
```

### 4.4 Tool 系统设计

#### Tool Registry（工具注册表）

```java
// 硬编码注册，v1 不需要动态注册
public enum ToolDefinition {
    FAQ_SEARCH(
        "faq_search",
        "搜索 FAQ 知识库",
        RiskLevel.READ,       // read-only
        FaqSearchRequest.class
    ),
    POST_QUERY(
        "post_query",
        "查询用户帖子状态",
        RiskLevel.READ,       // read-only
        PostQueryRequest.class
    ),
    USER_DATA_DELETE(
        "user_data_delete",
        "删除用户数据",
        RiskLevel.IRREVERSIBLE, // 不可逆
        UserDataDeleteRequest.class
    );
}
```

#### Tool Dispatcher（工具防火墙）

```
收到工具调用请求
    │
    ▼
┌──────────────┐     失败
│ Schema 校验   │────────────► 返回错误，给 LLM 一次修正机会
│ (参数完整性)   │
└──────┬───────┘
       │ 通过
       ▼
┌──────────────┐     IRREVERSIBLE 且未确认
│ 风险检查      │────────────► 要求二次确认
│ (risk level)  │
└──────┬───────┘
       │ 通过
       ▼
┌──────────────┐     超时/异常
│ 执行工具      │────────────► 有限重试 (最多 1 次) → 降级提示
│ (调用 Service)│
└──────┬───────┘
       │ 成功
       ▼
┌──────────────┐
│ 规范化输出    │────────────► 统一 ToolResult 格式返回
└──────────────┘
```

### 4.5 失败模式与降级策略

| 场景 | 策略 |
|------|------|
| Intent 置信度 < 0.7 | 最多 1 次澄清追问 → 仍不清楚则引导人工 |
| FAQ 检索无结果 / score 低 | 拒答 + "建议发送'转人工'" |
| 工具 schema 校验失败 | 给 LLM 一次修正参数机会 → 再失败则拒答 |
| 工具调用超时 (>5s) | 1 次重试 → 降级提示 "系统繁忙" + 引导人工 |
| ReAct 循环超过 3 轮 | 强制退出 + 引导人工 |
| 高风险操作用户未确认 | 不执行，提示用户需确认或联系人工 |

---

## 5. 数据库设计

### 5.1 ER 图

```
┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│   conversation   │       │     session      │       │     message      │
├──────────────────┤       ├──────────────────┤       ├──────────────────┤
│ conversation_id  │◄──┐   │ session_id       │◄──┐   │ message_id       │
│ user_id          │   │   │ conversation_id  │───┘   │ conversation_id  │
│ getstream_channel│   │   │ status           │       │ session_id       │
│ status           │   └───│                  │       │ sender_type      │
│ created_at       │       │ assigned_agent_id│       │ sender_id        │
│ updated_at       │       │ created_at       │       │ content          │
└──────────────────┘       │ updated_at       │       │ metadata_json    │
                           │ last_activity_at │       │ getstream_msg_id │
                           └──────────────────┘       │ created_at       │
                                                      └──────────────────┘
┌──────────────────┐       ┌──────────────────┐
│    user_post     │       │     faq_doc      │
├──────────────────┤       ├──────────────────┤
│ post_id          │       │ faq_id           │
│ username         │       │ question         │
│ title            │       │ answer           │
│ status           │       │ embedding (vector)│
│ created_at       │       │ created_at       │
└──────────────────┘       └──────────────────┘
```

### 5.2 DDL（Flyway 迁移脚本）

#### V1__init_schema.sql

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- =====================
-- conversation
-- =====================
CREATE TABLE conversation (
    conversation_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              VARCHAR(255) NOT NULL,
    getstream_channel_id VARCHAR(255),
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_conversation_user ON conversation(user_id);

-- =====================
-- session
-- =====================
CREATE TABLE session (
    session_id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID         NOT NULL REFERENCES conversation(conversation_id),
    status              VARCHAR(32)  NOT NULL DEFAULT 'AI_HANDLING',
    assigned_agent_id   VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_activity_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_session_conv ON session(conversation_id);
CREATE INDEX idx_session_status ON session(status);
CREATE INDEX idx_session_activity ON session(last_activity_at);

-- =====================
-- message
-- =====================
CREATE TABLE message (
    message_id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      UUID         NOT NULL REFERENCES conversation(conversation_id),
    session_id           UUID         NOT NULL REFERENCES session(session_id),
    sender_type          VARCHAR(32)  NOT NULL,
    sender_id            VARCHAR(255) NOT NULL,
    content              TEXT         NOT NULL,
    metadata_json        TEXT,
    getstream_message_id VARCHAR(255),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_message_conv ON message(conversation_id);
CREATE INDEX idx_message_session ON message(session_id);
CREATE INDEX idx_message_time ON message(created_at);

-- =====================
-- user_post (Mock 数据)
-- =====================
CREATE TABLE user_post (
    post_id    SERIAL       PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    title      VARCHAR(512) NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_post_user ON user_post(username);

-- =====================
-- faq_doc (向量存储)
-- =====================
CREATE TABLE faq_doc (
    faq_id     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    question   TEXT          NOT NULL,
    answer     TEXT          NOT NULL,
    embedding  vector(1024),
    created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_faq_embedding ON faq_doc
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);
```

> **说明**：`embedding vector(1024)` 维度取决于 DashScope text-embedding-v4 模型输出维度（默认 1024 维）。应用启动时为无 embedding 的 FAQ 记录自动生成向量。

#### V2__mock_data.sql

```sql
INSERT INTO user_post (username, title, status, created_at) VALUES
('user_alice', '如何重置密码',   'PUBLISHED',    '2025-06-01 10:00:00'),
('user_alice', '账号被锁定',     'UNDER_REVIEW', '2025-06-05 14:30:00'),
('user_alice', '修改绑定邮箱',   'PUBLISHED',    '2025-07-10 09:00:00'),
('user_bob',   '无法登录',       'REMOVED',      '2025-06-03 11:00:00'),
('user_bob',   '充值未到账',     'PUBLISHED',    '2025-06-10 16:00:00'),
('user_bob',   '申请退款',       'UNDER_REVIEW', '2025-07-15 08:30:00'),
('user_carol', '举报违规内容',   'PUBLISHED',    '2025-06-20 12:00:00'),
('user_carol', '隐私设置咨询',   'DRAFT',        '2025-07-01 10:00:00');

INSERT INTO faq_doc (question, answer) VALUES
('如何重置密码？',          '请前往"设置 > 账号安全 > 重置密码"，点击"忘记密码"链接，系统将向您的注册邮箱发送重置链接。'),
('如何修改绑定的手机号？',   '请前往"设置 > 账号安全 > 手机绑定"，验证当前手机号后即可修改。'),
('充值未到账怎么办？',       '请稍等几分钟，充值通常在5分钟内到账。如超过30分钟未到账，请联系客服并提供订单号。'),
('如何删除我的账号？',       '请前往"设置 > 账号管理 > 注销账号"，按提示操作。注销后数据将在30天内清除。'),
('如何举报违规内容？',       '点击内容右上角的"..."按钮，选择"举报"，填写举报原因后提交，我们将在24小时内处理。'),
('退款政策是什么？',         '自购买之日起7天内可申请无理由退款。请前往"订单 > 申请退款"提交申请。'),
('如何修改昵称？',          '请前往"设置 > 个人资料 > 昵称"，输入新昵称后保存即可。'),
('帖子审核需要多久？',       '帖子通常在发布后2-4小时内完成审核。高峰期可能延长至12小时。'),
('如何开启两步验证？',       '请前往"设置 > 账号安全 > 两步验证"，选择验证方式（短信或验证器应用）并按提示操作。'),
('如何联系人工客服？',       '您可以在对话中发送"转人工"，系统将为您接入人工客服。');
```

### 5.3 枚举值

| 枚举 | 值 | 说明 |
|------|---|------|
| ConversationStatus | `ACTIVE`, `CLOSED` | |
| SessionStatus | `AI_HANDLING`, `HUMAN_HANDLING`, `CLOSED` | |
| SenderType | `USER`, `AI_CHATBOT`, `HUMAN_AGENT`, `SYSTEM` | |
| PostStatus | `PUBLISHED`, `UNDER_REVIEW`, `REMOVED`, `DRAFT` | |
| RiskLevel | `READ`, `WRITE`, `IRREVERSIBLE` | 工具风险级别 |

---

## 6. 数据结构 (DTO)

### 6.1 Request DTO

```java
// 用户发送消息
public class InboundMessageRequest {
    private String userId;
    private String content;
}

// 人工客服回复
public class AgentReplyRequest {
    private String sessionId;
    private String agentId;
    private String content;
}

// 工具请求
public class FaqSearchRequest {
    private String query;
}

public class UserDataDeleteRequest {
    private String username;
}

public class PostQueryRequest {
    private String username;
}
```

### 6.2 Response DTO

```java
// 统一响应
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String error;
}

// 消息
public class MessageResponse {
    private String messageId;
    private String conversationId;
    private String sessionId;
    private String senderType;
    private String senderId;
    private String content;
    private String createdAt;
}

public class InboundMessageResponse {
    private String conversationId;
    private String sessionId;
    private String messageId;
}

// Conversation
public class ConversationResponse {
    private String conversationId;
    private String userId;
    private String status;
    private String getstreamChannelId;
    private String createdAt;
    private String updatedAt;
}

// Session
public class SessionResponse {
    private String sessionId;
    private String conversationId;
    private String status;
    private String assignedAgentId;
    private String createdAt;
    private String lastActivityAt;
}

// 工具响应
public class FaqSearchResponse {
    private String question;
    private String answer;
    private double score;
}

public class UserDataDeleteResponse {
    private boolean success;
    private String message;
    private String requestId;
}

public class PostQueryResponse {
    private List<PostItem> posts;
}

public class PostItem {
    private int postId;
    private String username;
    private String title;
    private String status;
    private String createdAt;
}

// GetStream
public class StreamTokenResponse {
    private String token;
    private String userId;
}
```

---

## 7. API 接口设计

所有接口以 `/api` 为前缀，返回 `ApiResponse<T>`。

### 7.1 消息 API (MessageController)

#### POST /api/messages/inbound

用户发送消息入口。触发 Orchestrator → Router → AI/Human 全流程。

```
请求:
{
    "userId": "user_alice",
    "content": "我想查一下我的帖子状态"
}

响应 (200):
{
    "success": true,
    "data": {
        "conversationId": "550e8400-...",
        "sessionId": "660e8400-...",
        "messageId": "770e8400-..."
    },
    "error": null
}
```

> AI/人工客服的回复通过 GetStream WebSocket 实时推送，不在此接口返回。

#### POST /api/messages/agent-reply

人工客服发送回复。

```
请求:
{
    "sessionId": "660e8400-...",
    "agentId": "agent_default",
    "content": "您好，我已查到您的帖子状态..."
}

响应 (200):
{
    "success": true,
    "data": { "conversationId": "...", "sessionId": "...", "messageId": "..." },
    "error": null
}
```

#### GET /api/messages?conversationId={id}&sessionId={id}

获取消息历史。支持按 conversation 或 session 过滤。

```
响应 (200):
{
    "success": true,
    "data": [
        {
            "messageId": "770e8400-...",
            "conversationId": "550e8400-...",
            "sessionId": "660e8400-...",
            "senderType": "USER",
            "senderId": "user_alice",
            "content": "我想查一下我的帖子状态",
            "createdAt": "2025-07-20T10:30:00Z"
        }
    ],
    "error": null
}
```

### 7.2 Conversation API (ConversationController)

#### GET /api/conversations?userId={userId}

```
响应 (200):
{
    "success": true,
    "data": {
        "conversationId": "550e8400-...",
        "userId": "user_alice",
        "status": "ACTIVE",
        "getstreamChannelId": "messaging:conv-550e8400",
        "createdAt": "...",
        "updatedAt": "..."
    },
    "error": null
}
```

#### GET /api/conversations/{conversationId}/sessions

```
响应 (200):
{
    "success": true,
    "data": [
        { "sessionId": "...", "status": "CLOSED", ... },
        { "sessionId": "...", "status": "AI_HANDLING", ... }
    ],
    "error": null
}
```

### 7.3 Session API (SessionController)

#### GET /api/sessions/{sessionId}

获取 session 详情。

#### GET /api/sessions/active?agentId={agentId}

获取分配给该客服的所有 session 列表（包括已关闭的）。

### 7.4 Tools API (ToolController)

#### POST /api/tools/faq/search

```
请求: { "query": "密码忘了怎么办" }
响应: { "success": true, "data": { "question": "如何重置密码？", "answer": "...", "score": 0.92 } }
```

#### POST /api/tools/user-data/delete

```
请求: { "username": "user_alice" }
响应: { "success": true, "data": { "success": true, "message": "...", "requestId": "req-xxx" } }
```

#### POST /api/tools/posts/query

```
请求: { "username": "user_alice" }
响应: { "success": true, "data": { "posts": [ { "postId": 1, "title": "...", "status": "PUBLISHED", ... } ] } }
```

### 7.5 GetStream Token API (StreamTokenController)

#### GET /api/stream/token?userId={userId}

```
响应: { "success": true, "data": { "token": "eyJ...", "userId": "user_alice" } }
```

### 7.6 API 汇总

| 方法 | 路径 | 说明 | 调用方 |
|------|------|------|--------|
| POST | `/api/messages/inbound` | 用户发消息 | User Web |
| POST | `/api/messages/agent-reply` | 人工客服回复 | Agent Web |
| GET  | `/api/messages` | 消息历史 | 双端 |
| GET  | `/api/conversations` | 获取 conversation | User Web |
| GET  | `/api/conversations/{id}/sessions` | session 列表 | Agent Web |
| GET  | `/api/sessions/{id}` | session 详情 | Agent Web |
| GET  | `/api/sessions/active` | 客服的所有 session | Agent Web |
| POST | `/api/tools/faq/search` | FAQ 搜索 | Agent Web / AI |
| POST | `/api/tools/user-data/delete` | 数据删除 | Agent Web / AI |
| POST | `/api/tools/posts/query` | 帖子查询 | Agent Web / AI |
| GET  | `/api/stream/token` | GetStream token | 双端 |

---

## 8. 数据流图

### 8.1 用户发送消息 → AI Bounded Agent 处理

```
User Web                     Backend                                  GetStream      User Web
  │                            │                                          │              │
  │ POST /messages/inbound     │                                          │              │
  │ {userId, content}          │                                          │              │
  │───────────────────────────►│                                          │              │
  │                            │                                          │              │
  │                     ┌──────┴───────┐                                  │              │
  │                     │Orchestrator  │                                  │              │
  │                     │ 1.找/建 conv │                                  │              │
  │                     │ 2.找/建 sess │                                  │              │
  │                     │ 3.存消息     │                                  │              │
  │                     └──────┬───────┘                                  │              │
  │                            │                                          │              │
  │                     ┌──────┴───────┐                                  │              │
  │                     │MessageRouter │                                  │              │
  │                     │ status=AI    │                                  │              │
  │                     └──────┬───────┘                                  │              │
  │                            │                                          │              │
  │                     ┌──────▼───────────────────────────────┐          │              │
  │                     │ Agent Core                            │          │              │
  │                     │                                       │          │              │
  │                     │ 1. Intent Router (Kimi LLM)           │          │              │
  │                     │    → POST_QUERY, confidence=0.92      │          │              │
  │                     │                                       │          │              │
  │                     │ 2. ReAct Loop:                        │          │              │
  │                     │    Plan → 调用 post_query 工具         │          │              │
  │                     │    Dispatcher → schema校验 → 执行      │          │              │
  │                     │    Observe → 拿到结果                  │          │              │
  │                     │                                       │          │              │
  │                     │ 3. Response Composer (Kimi LLM)        │          │              │
  │                     │    工具结果 → 友好回复 + 来源引用        │          │              │
  │                     └──────┬────────────────────────────────┘          │              │
  │                            │                                          │              │
  │                            │ sendMessage(channelId, aiReply)          │              │
  │                            │─────────────────────────────────────────►│  WebSocket   │
  │  200 OK                    │                                          │─────────────►│
  │◄───────────────────────────│                                          │              │
```

### 8.2 用户请求转人工

```
User Web                     Backend                                  GetStream      Agent Web
  │                            │                                          │              │
  │ POST /messages/inbound     │                                          │              │
  │ {userId, "转人工"}          │                                          │              │
  │───────────────────────────►│                                          │              │
  │                            │                                          │              │
  │                     ┌──────┴───────┐                                  │              │
  │                     │MessageRouter │                                  │              │
  │                     │ 检测"转人工"  │                                  │              │
  │                     │ → HUMAN      │                                  │              │
  │                     └──────┬───────┘                                  │              │
  │                            │                                          │              │
  │                     ┌──────▼───────┐                                  │              │
  │                     │HumanAgentSvc │                                  │              │
  │                     │ 1.status→    │                                  │              │
  │                     │  HUMAN       │                                  │              │
  │                     │ 2.分配客服    │                                  │              │
  │                     └──────┬───────┘                                  │              │
  │                            │ addMember + sendMessage("转接中...")      │              │
  │                            │─────────────────────────────────────────►│ channel event│
  │◄───────────────────────────────────────────────────────────(推送)─────│─────────────►│
```

### 8.3 人工客服调用工具 → 手动回复

```
Agent Web                    Backend                           Agent Web
  │                            │                                   │
  │ POST /api/tools/posts/query                                   │
  │ {username: "user_alice"}    │                                   │
  │───────────────────────────►│                                   │
  │                            │ PostQueryService.query()          │
  │  200 {posts: [...]}        │                                   │
  │◄───────────────────────────│                                   │
  │                            │                                   │
  │ (客服查看结果，编辑回复)                                          │
  │                            │                                   │
  │ POST /messages/agent-reply │                                   │
  │ {sessionId, content}       │                                   │
  │───────────────────────────►│ → GetStream → User Web            │
```

---

## 9. 核心后端组件设计

### 9.1 LLM 客户端（直接 HTTP 调用）

```java
@Component
public class KimiClient {

    private final RestTemplate restTemplate;          // Kimi 对话用
    private final RestTemplate embeddingRestTemplate;  // DashScope Embedding 用
    private final KimiConfig config;                   // Kimi 配置
    private final EmbeddingConfig embeddingConfig;     // DashScope 配置

    // 聊天补全（意图识别 / 回复生成）→ Kimi (Moonshot AI)
    public KimiChatResponse chatCompletion(String systemPrompt,
                                           List<KimiMessage> messages,
                                           double temperature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "model", config.getChatModel(),  // moonshot-v1-8k
            "messages", buildMessages(systemPrompt, messages),
            "temperature", temperature
        );

        ResponseEntity<KimiChatResponse> resp = restTemplate.exchange(
            config.getBaseUrl() + "/chat/completions",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            KimiChatResponse.class
        );
        return resp.getBody();
    }

    // 文本 Embedding → DashScope (阿里云通义千问)
    public float[] embedding(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(embeddingConfig.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "model", embeddingConfig.getEmbeddingModel(),  // text-embedding-v4
            "input", text
        );
        // 调用 DashScope OpenAI 兼容 /embeddings 端点
        // 返回 float[] 向量 (1024 维)
    }
}
```

### 9.2 GlobalOrchestrator

```java
@Service
public class GlobalOrchestrator {

    public InboundMessageResponse handleInboundMessage(InboundMessageRequest request) {
        // 1. 查找或创建 Conversation
        Conversation conv = conversationService.findOrCreate(request.getUserId());

        // 2. 查找活跃 Session 或创建新 Session
        Session session = sessionService.findActiveOrCreate(conv.getConversationId());

        // 3. 存储用户消息
        Message msg = messageService.save(conv.getConversationId(), session.getSessionId(),
                "USER", request.getUserId(), request.getContent());

        // 4. 通过 GetStream 发送用户消息到 channel
        getStreamService.sendMessage(conv.getGetstreamChannelId(),
                request.getUserId(), request.getContent());

        // 5. 路由决策 + 处理
        messageRouter.route(session, msg);

        // 6. 更新 session 活跃时间
        sessionService.updateLastActivity(session.getSessionId());

        return new InboundMessageResponse(
            conv.getConversationId().toString(),
            session.getSessionId().toString(),
            msg.getMessageId().toString()
        );
    }
}
```

### 9.3 MessageRouter

```java
@Service
public class MessageRouter {

    public void route(Session session, Message message) {
        // 1. 检查 "转人工" 关键词
        if (message.getContent().contains("转人工")) {
            sessionService.updateStatus(session.getSessionId(), "HUMAN_HANDLING");
            humanAgentService.assignAgent(session);
            getStreamService.sendMessage(
                getChannelId(session.getConversationId()),
                "ai_bot",
                "正在为您转接人工客服，请稍候..."
            );
            return;
        }

        // 2. 根据 session 状态路由
        switch (session.getStatus()) {
            case "HUMAN_HANDLING" -> humanAgentService.forwardMessage(session, message);
            case "AI_HANDLING"    -> agentCore.handleMessage(session, message);
        }
    }
}
```

### 9.4 Agent Core（Bounded Agent 主逻辑）

```java
@Service
public class AgentCore {

    private static final int MAX_REACT_ROUNDS = 3;

    public void handleMessage(Session session, Message message) {
        List<KimiMessage> history = getConversationHistory(session);

        // ========== 1. Intent Router ==========
        IntentResult intent = intentRouter.recognize(message.getContent(), history);

        // 低置信度 → 追问
        if (intent.getConfidence() < 0.7) {
            String clarification = "请问您是想查询帖子状态、删除数据，还是有其他问题？";
            sendAiReply(session, clarification);
            return;
        }

        // ========== 2. ReAct Loop (Bounded) ==========
        ToolResult toolResult = null;
        for (int round = 0; round < MAX_REACT_ROUNDS; round++) {
            // Plan: 决定调用哪个工具，提取参数
            ToolCall toolCall = reactPlanner.plan(intent, message.getContent(), history, toolResult);

            if (toolCall == null) break;  // 无需工具调用或已有足够信息

            // Dispatch: schema 校验 → 风险检查 → 执行
            toolResult = toolDispatcher.dispatch(toolCall);

            if (toolResult.isSuccess()) break;  // 成功则退出循环

            // schema 失败 → 给 LLM 一次修正机会（下一轮 plan 会看到错误信息）
            if (!toolResult.isRetryable()) break;
        }

        // ========== 3. Response Composer ==========
        String reply;
        if (intent.getRisk().equals("critical")) {
            // 高风险 → 模板回复
            reply = responseComposer.composeFromTemplate(intent, toolResult);
        } else if (toolResult != null && toolResult.isSuccess()) {
            // 低风险 + 有工具结果 → LLM 生成
            reply = responseComposer.composeWithEvidence(
                message.getContent(), intent, toolResult, history);
        } else {
            // 无结果 → 拒答
            reply = "抱歉，我暂时无法处理您的问题。建议发送\"转人工\"获取人工客服帮助。";
        }

        sendAiReply(session, reply);
    }

    private void sendAiReply(Session session, String reply) {
        messageService.save(session.getConversationId(), session.getSessionId(),
            "AI_CHATBOT", "ai_bot", reply);
        getStreamService.sendMessage(
            getChannelId(session.getConversationId()), "ai_bot", reply);
    }
}
```

### 9.5 Intent Router

```java
@Service
public class IntentRouter {

    private final KimiClient kimiClient;

    private static final String SYSTEM_PROMPT = """
        你是一个意图识别引擎。分析用户消息，返回严格 JSON 格式（不要返回其他内容）：
        {
          "intent": "POST_QUERY" | "DATA_DELETION" | "KB_QUESTION" | "GENERAL_CHAT",
          "confidence": 0.0~1.0,
          "risk": "low" | "critical",
          "extracted_params": { ... }
        }

        规则：
        - POST_QUERY：用户想查帖子状态。提取 username（如有）
        - DATA_DELETION：用户想删除数据。risk 必须为 "critical"。提取 username（如有）
        - KB_QUESTION：用户问常见问题（密码/充值/举报等政策性问题）
        - GENERAL_CHAT：其他
        """;

    public IntentResult recognize(String userMessage, List<KimiMessage> history) {
        List<KimiMessage> messages = new ArrayList<>(history);
        messages.add(new KimiMessage("user", userMessage));

        KimiChatResponse resp = kimiClient.chatCompletion(SYSTEM_PROMPT, messages, 0.1);
        String json = resp.getContent();
        return parseIntentResult(json);
    }
}
```

### 9.6 Tool Dispatcher

```java
@Service
public class ToolDispatcher {

    private final Map<String, ToolExecutor> executors;  // toolName → executor

    public ToolResult dispatch(ToolCall toolCall) {
        // 1. 查找工具
        ToolDefinition def = ToolDefinition.fromName(toolCall.getToolName());
        if (def == null) {
            return ToolResult.error("未知工具: " + toolCall.getToolName());
        }

        // 2. Schema 校验（参数完整性）
        String validationError = validateParams(def, toolCall.getParams());
        if (validationError != null) {
            return ToolResult.schemaError(validationError);  // retryable
        }

        // 3. 风险检查
        if (def.getRisk() == RiskLevel.IRREVERSIBLE) {
            if (!toolCall.isUserConfirmed()) {
                return ToolResult.needsConfirmation(
                    "此操作不可逆，需要用户二次确认");
            }
        }

        // 4. 执行
        ToolExecutor executor = executors.get(def.getName());
        try {
            return executor.execute(toolCall.getParams());
        } catch (Exception e) {
            return ToolResult.error("工具执行失败: " + e.getMessage());
        }
    }
}
```

### 9.7 Response Composer

```java
@Service
public class ResponseComposer {

    private final KimiClient kimiClient;

    // 高风险 → 模板回复
    public String composeFromTemplate(IntentResult intent, ToolResult toolResult) {
        if ("DATA_DELETION".equals(intent.getIntent())) {
            if (toolResult != null && toolResult.isSuccess()) {
                return String.format(
                    "已收到您的数据删除请求（请求编号：%s）。预计 24 小时内处理完毕。" +
                    "如需了解进度，请联系人工客服。",
                    toolResult.getRequestId()
                );
            }
            if (toolResult != null && toolResult.needsConfirmation()) {
                String username = intent.getExtractedParams().get("username");
                return String.format(
                    "您确认要删除用户 %s 的所有数据吗？此操作不可撤销。请回复\"确认删除\"继续。",
                    username
                );
            }
            return "数据删除请求处理失败，建议发送\"转人工\"联系人工客服。";
        }
        return "处理完成。";
    }

    // 低风险 → LLM 生成（附带证据引用）
    public String composeWithEvidence(String userMessage, IntentResult intent,
                                      ToolResult toolResult, List<KimiMessage> history) {
        String systemPrompt = """
            你是一个友好的客服助手。根据用户问题和查询结果，组织简洁友好的中文回复。
            规则：
            1. 回复必须基于提供的数据，不要编造信息
            2. 在回复末尾注明数据来源
            3. 如果数据不足以回答，坦诚告知并建议转人工
            """;

        String userPrompt = String.format(
            "用户问题：%s\n查询结果：%s",
            userMessage, toolResult.toJson()
        );

        KimiChatResponse resp = kimiClient.chatCompletion(systemPrompt,
            List.of(new KimiMessage("user", userPrompt)), 0.7);
        return resp.getContent();
    }
}
```

### 9.8 Session 超时调度

```java
@Component
public class SessionTimeoutScheduler {

    @Scheduled(fixedRate = 60_000)
    public void checkTimeout() {
        sessionMapper.closeExpiredSessions(10);  // 10 分钟超时
    }
}
```

---

## 10. MyBatis Mapper 设计

### 10.1 Mapper 接口示例

```java
@Mapper
public interface ConversationMapper {
    Conversation findByUserId(String userId);
    Conversation findById(String conversationId);
    void insert(Conversation conversation);
    void updateStatus(String conversationId, String status);
}

@Mapper
public interface SessionMapper {
    Session findById(String sessionId);
    Session findActiveByConversationId(String conversationId);
    List<Session> findByStatus(String status);
    List<Session> findActiveByAgentId(String agentId);
    void insert(Session session);
    void updateStatus(String sessionId, String status);
    void updateLastActivity(String sessionId);
    void closeExpiredSessions(int timeoutMinutes);
}

@Mapper
public interface MessageMapper {
    List<Message> findByConversationId(String conversationId);
    List<Message> findBySessionId(String sessionId);
    List<Message> findByConversationAndSession(String conversationId, String sessionId);
    void insert(Message message);
}

@Mapper
public interface UserPostMapper {
    List<UserPost> findByUsername(String username);
}

@Mapper
public interface FaqDocMapper {
    List<FaqDoc> searchByEmbedding(String embeddingVector, int limit);
    void updateEmbedding(String faqId, String embeddingVector);
    List<FaqDoc> findWithoutEmbedding();
}
```

### 10.2 Mapper XML 示例

```xml
<!-- SessionMapper.xml -->
<mapper namespace="com.chatbot.mapper.SessionMapper">

    <select id="findActiveByConversationId" resultType="com.chatbot.model.Session">
        SELECT * FROM session
        WHERE conversation_id = #{conversationId}::uuid
          AND status IN ('AI_HANDLING', 'HUMAN_HANDLING')
        ORDER BY created_at DESC
        LIMIT 1
    </select>

    <update id="closeExpiredSessions">
        UPDATE session SET status = 'CLOSED', updated_at = NOW()
        WHERE status IN ('AI_HANDLING', 'HUMAN_HANDLING')
          AND last_activity_at &lt; NOW() - (#{timeoutMinutes} || ' minutes')::interval
    </update>

</mapper>

<!-- FaqDocMapper.xml -->
<mapper namespace="com.chatbot.mapper.FaqDocMapper">

    <select id="searchByEmbedding" resultType="com.chatbot.model.FaqDoc">
        SELECT faq_id, question, answer,
               1 - (embedding &lt;=&gt; #{embeddingVector}::vector) AS score
        FROM faq_doc
        WHERE embedding IS NOT NULL
        ORDER BY embedding &lt;=&gt; #{embeddingVector}::vector
        LIMIT #{limit}
    </select>

</mapper>
```

---

## 11. GetStream 集成

### 11.1 服务端

| 操作 | 时机 |
|------|------|
| `upsertUser()` | 用户首次发消息 |
| `createChannel("messaging", id)` | 创建 Conversation |
| `addMembers(channelId, members)` | 创建 Conversation / 转人工 |
| `sendMessage(channelId, msg)` | AI 回复 / 系统提示 / 人工回复 |
| `createToken(userId)` | 前端请求 Token |

用户映射：

| 角色 | GetStream ID |
|------|-------------|
| 用户 | `user_{userId}` |
| AI | `ai_bot` |
| 人工客服 | `agent_default` |

### 11.2 客户端

```typescript
import { StreamChat } from 'stream-chat';

const chatClient = StreamChat.getInstance(import.meta.env.VITE_GETSTREAM_API_KEY);

// 连接
await chatClient.connectUser({ id: userId }, token);

// 监听消息
const channel = chatClient.channel('messaging', channelId);
await channel.watch();
channel.on('message.new', (event) => { /* 更新消息列表 */ });
```

> 前端不直接通过 GetStream 发送消息。所有消息先走后端 API → Orchestrator → Router，再由后端通过 GetStream SDK 发送。

---

## 12. LLM 与 Embedding 集成

### 12.1 调用方式

通过 `RestTemplate` 直接调用 OpenAI 兼容 API。对话和 Embedding 使用不同的服务商：

| 服务商 | 端点 | 用途 | 模型 |
|--------|------|------|------|
| Kimi (Moonshot AI) | `POST /v1/chat/completions` | 意图识别 / 回复生成 | `moonshot-v1-8k` |
| DashScope (阿里云) | `POST /v1/embeddings` | FAQ 文本向量化 | `text-embedding-v4` |

### 12.2 配置

```yaml
# Kimi LLM（对话）
kimi:
  api-key: ${KIMI_API_KEY}
  base-url: https://api.moonshot.cn/v1
  chat:
    model: moonshot-v1-8k
    temperature: 0.7
    timeout-seconds: 10

# DashScope Embedding（阿里云通义千问）
dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  base-url: ${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
  embedding:
    model: text-embedding-v4
    api-url: https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding
```

不使用 Spring AI，直接在 `KimiClient` 中封装 HTTP 调用。对话调用 Kimi API，Embedding 调用 DashScope API。

---

## 13. 项目目录结构

```
chatbot/
├── CLAUDE.md
├── docs/
│   ├── PRD.md
│   ├── tech-design-spec.md                      # 本文档
│   └── ai-service-agent.md                      # AI Agent 架构参考
│
├── backend/
│   ├── build.gradle                             # Groovy DSL (非 Kotlin)
│   ├── settings.gradle
│   ├── gradle/wrapper/
│   └── src/
│       ├── main/
│       │   ├── java/com/chatbot/
│       │   │   ├── ChatbotApplication.java
│       │   │   │
│       │   │   ├── config/                      # === 配置 ===
│       │   │   │   ├── WebConfig.java           # CORS
│       │   │   │   ├── AsyncConfig.java         # 异步线程池 (aiTaskExecutor)
│       │   │   │   ├── GetStreamConfig.java     # GetStream 客户端 Bean
│       │   │   │   ├── KimiConfig.java          # Kimi 对话 Bean + RestTemplate
│       │   │   │   ├── EmbeddingConfig.java     # DashScope Embedding Bean + RestTemplate
│       │   │   │   └── UUIDTypeHandler.java     # MyBatis UUID 类型处理器
│       │   │   │
│       │   │   ├── controller/                  # === API 层 ===
│       │   │   │   ├── MessageController.java
│       │   │   │   ├── ConversationController.java
│       │   │   │   ├── SessionController.java
│       │   │   │   ├── ToolController.java
│       │   │   │   └── StreamTokenController.java
│       │   │   │
│       │   │   ├── dto/                         # === DTO ===
│       │   │   │   ├── ApiResponse.java
│       │   │   │   ├── request/
│       │   │   │   │   ├── InboundMessageRequest.java
│       │   │   │   │   ├── AgentReplyRequest.java
│       │   │   │   │   ├── FaqSearchRequest.java
│       │   │   │   │   ├── UserDataDeleteRequest.java
│       │   │   │   │   └── PostQueryRequest.java
│       │   │   │   └── response/
│       │   │   │       ├── InboundMessageResponse.java
│       │   │   │       ├── MessageResponse.java
│       │   │   │       ├── ConversationResponse.java
│       │   │   │       ├── SessionResponse.java
│       │   │   │       ├── FaqSearchResponse.java
│       │   │   │       ├── UserDataDeleteResponse.java
│       │   │   │       ├── PostQueryResponse.java
│       │   │   │       └── StreamTokenResponse.java
│       │   │   │
│       │   │   ├── service/                     # === 业务逻辑 ===
│       │   │   │   ├── orchestrator/
│       │   │   │   │   └── GlobalOrchestrator.java
│       │   │   │   ├── router/
│       │   │   │   │   └── MessageRouter.java
│       │   │   │   ├── agent/                   # Bounded Agent
│       │   │   │   │   ├── AgentCore.java       # Agent 主循环
│       │   │   │   │   ├── IntentRouter.java    # 意图识别
│       │   │   │   │   ├── IntentResult.java    # 意图识别结果
│       │   │   │   │   ├── ReactPlanner.java    # ReAct 规划
│       │   │   │   │   └── ResponseComposer.java # 回复组织
│       │   │   │   ├── tool/                    # 工具系统
│       │   │   │   │   ├── ToolDefinition.java  # 工具注册表 (enum)
│       │   │   │   │   ├── ToolDispatcher.java  # 工具防火墙
│       │   │   │   │   ├── ToolCall.java        # 工具调用参数
│       │   │   │   │   ├── ToolResult.java      # 工具调用结果
│       │   │   │   │   ├── ToolExecutor.java    # 执行器接口
│       │   │   │   │   ├── FaqService.java
│       │   │   │   │   ├── FaqEmbeddingInitializer.java  # 启动时初始化嵌入
│       │   │   │   │   ├── UserDataDeletionService.java
│       │   │   │   │   └── PostQueryService.java
│       │   │   │   ├── human/
│       │   │   │   │   └── HumanAgentService.java
│       │   │   │   ├── stream/
│       │   │   │   │   └── GetStreamService.java
│       │   │   │   ├── llm/
│       │   │   │   │   ├── KimiClient.java      # Kimi HTTP 客户端
│       │   │   │   │   ├── KimiMessage.java     # 消息结构
│       │   │   │   │   └── KimiChatResponse.java # 响应结构
│       │   │   │   ├── ConversationService.java
│       │   │   │   ├── SessionService.java
│       │   │   │   └── MessageService.java
│       │   │   │
│       │   │   ├── model/                       # === 数据模型 (POJO) ===
│       │   │   │   ├── Conversation.java
│       │   │   │   ├── Session.java
│       │   │   │   ├── Message.java
│       │   │   │   ├── UserPost.java
│       │   │   │   └── FaqDoc.java
│       │   │   │
│       │   │   ├── enums/
│       │   │   │   ├── ConversationStatus.java
│       │   │   │   ├── SessionStatus.java
│       │   │   │   ├── SenderType.java          # USER, AI_CHATBOT, HUMAN_AGENT, SYSTEM
│       │   │   │   ├── PostStatus.java
│       │   │   │   └── RiskLevel.java
│       │   │   │
│       │   │   ├── exception/                   # === 异常处理 ===
│       │   │   │   ├── ChatbotException.java
│       │   │   │   ├── SessionNotFoundException.java
│       │   │   │   ├── ConversationNotFoundException.java
│       │   │   │   ├── LlmCallException.java
│       │   │   │   └── GlobalExceptionHandler.java
│       │   │   │
│       │   │   ├── mapper/                      # === MyBatis Mapper ===
│       │   │   │   ├── ConversationMapper.java
│       │   │   │   ├── SessionMapper.java
│       │   │   │   ├── MessageMapper.java
│       │   │   │   ├── UserPostMapper.java
│       │   │   │   └── FaqDocMapper.java
│       │   │   │
│       │   │   └── scheduler/
│       │   │       └── SessionTimeoutScheduler.java
│       │   │
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── mapper/                      # MyBatis XML 映射
│       │       │   ├── ConversationMapper.xml
│       │       │   ├── SessionMapper.xml
│       │       │   ├── MessageMapper.xml
│       │       │   ├── UserPostMapper.xml
│       │       │   └── FaqDocMapper.xml
│       │       └── db/migration/                # Flyway
│       │           ├── V1__init_schema.sql
│       │           ├── V2__mock_data.sql
│       │           ├── V3__clear_kimi_embeddings.sql
│       │           └── V4__clear_v3_embeddings_for_v4_upgrade.sql
│       │
│       └── test/java/com/chatbot/
│
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── index.css
│       ├── vite-env.d.ts
│       ├── pages/
│       │   ├── UserChatPage.tsx
│       │   └── AgentDashboardPage.tsx
│       ├── components/
│       │   ├── chat/
│       │   │   ├── MessageList.tsx
│       │   │   ├── MessageBubble.tsx
│       │   │   ├── MessageInput.tsx
│       │   │   └── TypingIndicator.tsx
│       │   ├── agent/
│       │   │   ├── SessionList.tsx
│       │   │   └── ToolPanel.tsx
│       │   ├── user/
│       │   │   └── UserToolPanel.tsx
│       │   └── common/
│       │       └── Layout.tsx
│       ├── services/
│       │   ├── apiClient.ts
│       │   └── streamClient.ts
│       ├── hooks/
│       │   ├── useChat.ts
│       │   ├── useSession.ts
│       │   ├── useAgentChat.ts
│       │   └── useTools.ts
│       ├── types/
│       │   └── index.ts
│       └── config/
│           └── env.ts
│
└── scripts/
    ├── start-backend.sh
    └── start-frontend.sh
```

---

## 14. 配置文件

### 14.1 build.gradle (Groovy DSL)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.chatbot'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Web
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // MyBatis
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4'

    // PostgreSQL
    runtimeOnly 'org.postgresql:postgresql'

    // pgvector JDBC 类型
    implementation 'com.pgvector:pgvector:0.1.6'

    // Flyway
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    // GetStream
    implementation 'io.getstream:stream-chat-java:1.24.0'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

test {
    useJUnitPlatform()
}
```

> 总共 7 个显式依赖（不含 test）。不引入 Spring AI、JPA、Kotlin、ONNX 等重型框架。

### 14.2 application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chatbot
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  flyway:
    enabled: true
    locations: classpath:db/migration

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.chatbot.model
  type-handlers-package: com.chatbot.config
  configuration:
    map-underscore-to-camel-case: true

# Kimi LLM（对话）
kimi:
  api-key: ${KIMI_API_KEY}
  base-url: https://api.moonshot.cn/v1
  chat:
    model: moonshot-v1-8k
    temperature: 0.7
    timeout-seconds: 10

# DashScope Embedding（阿里云通义千问）
dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  base-url: ${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
  embedding:
    model: text-embedding-v4
    api-url: https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding

# GetStream
getstream:
  api-key: ${GETSTREAM_API_KEY}
  api-secret: ${GETSTREAM_API_SECRET}

# 系统配置
chatbot:
  session:
    timeout-minutes: 10
  agent:
    default-id: agent_default
    default-name: 人工客服
  router:
    transfer-keywords: 转人工,转接人工,人工客服,人工服务
  ai:
    bot-id: ai_bot
    bot-name: AI 助手
    max-react-rounds: 3
    confidence-threshold: 0.7
    faq-score-threshold: 0.75
```

### 14.3 package.json

```json
{
  "name": "chatbot-frontend",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router": "^7.0.0",
    "stream-chat": "^8.55.0",
    "stream-chat-react": "^12.0.0"
  },
  "devDependencies": {
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^4.0.0",
    "tailwindcss": "^4.0.0",
    "@tailwindcss/vite": "^4.0.0",
    "typescript": "^5.7.0",
    "vite": "^6.0.0"
  }
}
```

### 14.4 vite.config.ts

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
```

---

## 15. 环境变量

### 后端

```
DB_USERNAME=postgres
DB_PASSWORD=postgres
KIMI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
GETSTREAM_API_KEY=xxxxxxxxxxxxxxxxx
GETSTREAM_API_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 前端 (.env)

```
VITE_GETSTREAM_API_KEY=xxxxxxxxxxxxxxxxx
```

---

## 16. 本地环境搭建

```bash
# 1. PostgreSQL + pgvector
brew install postgresql@16
brew services start postgresql@16
brew install pgvector
createdb chatbot
psql -d chatbot -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 2. JDK 21
brew install openjdk@21

# 3. Node.js 22
brew install node@22

# 4. 后端
cd backend && ./gradlew bootRun

# 5. 前端
cd frontend && npm install && npm run dev
```

---

## 17. 设计决策记录

| 决策 | 选择 | 替代方案 | 理由 |
|------|------|---------|------|
| 数据访问 | MyBatis | JPA / Hibernate | 灵活轻量，SQL 可控，pgvector 原生 SQL 支持好 |
| 构建脚本 | Gradle Groovy DSL | Gradle Kotlin DSL / Maven | 不引入 Kotlin 依赖 |
| LLM 调用 | RestTemplate 直调 Kimi API | Spring AI | 零额外依赖，OpenAI 兼容格式简单 |
| Embedding | DashScope text-embedding-v4 API | Kimi embedding / 本地 ONNX 模型 | 1024 维向量，支持 50+ 语言 |
| 向量存储 | pgvector (PG 扩展) | Chroma / Qdrant | 已有 PG，无额外服务 |
| AI 架构 | Bounded Agent (Router+ReAct+Composer) | 简单 3-Agent 流水线 | 参考 ai-service-agent.md，支持置信度、风险级别、失败降级 |
| 高风险回复 | 模板输出 | LLM 自由生成 | 避免 LLM 幻觉导致误承诺 |
| 前端框架 | React + Vite | Next.js | 无需 SSR，更轻量 |
| 消息路径 | 前端→后端 API→GetStream | 前端直发 GetStream | 确保消息经过路由编排 |
