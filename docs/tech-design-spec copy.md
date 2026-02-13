# 技术设计规格说明书 (Technical Design Spec)

## 1. 概述

本文档基于 [PRD](./PRD.md) 的功能需求，定义智能客服系统的完整技术实现方案，涵盖前后端架构、数据库设计、API 接口规格、数据流和项目结构。

---

## 2. 技术栈总览

### 2.1 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 (LTS) | 运行时 |
| Spring Boot | 3.4.x | 应用框架 |
| Spring Web | 6.2.x | REST API |
| Spring Data JPA | 3.4.x | ORM / 数据访问 |
| Spring AI | 1.0.x | LLM 集成 / Embedding / Vector Store |
| PostgreSQL | 16+ | 关系型数据库 |
| pgvector 扩展 | 0.8.x | 向量存储（FAQ 知识库） |
| Flyway | 10.x | 数据库版本迁移 |
| GetStream Chat Java SDK | 1.24.x | 服务端 IM 操作 |
| Gradle (Kotlin DSL) | 8.12.x | 构建工具 |

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
| stream-chat | 9.x | GetStream Chat JS 客户端 |

### 2.3 第三方服务

| 服务 | 用途 | 集成方式 |
|------|------|---------|
| GetStream Chat | 实时消息收发 | 后端 Java SDK + 前端 React SDK |
| Kimi (Moonshot AI) | LLM 对话 / 意图识别 / 回复生成 | Spring AI OpenAI 兼容模式 |

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
┌─────────────────────────────────────────────────────────────────────┐
│                          Frontend (React + Vite)                    │
│                                                                     │
│   ┌──────────────────┐              ┌──────────────────────────┐   │
│   │   User Web       │              │   Human Agent Web        │   │
│   │   /chat          │              │   /agent                 │   │
│   │                  │              │                          │   │
│   │ ┌──────────────┐ │              │ ┌──────────────────────┐ │   │
│   │ │ MessageList   │ │              │ │ SessionList          │ │   │
│   │ │ MessageInput  │ │              │ │ MessageList          │ │   │
│   │ └──────────────┘ │              │ │ MessageInput         │ │   │
│   │                  │              │ │ ToolPanel            │ │   │
│   └────────┬─────────┘              │ └──────────────────────┘ │   │
│            │                        └────────────┬─────────────┘   │
│            │                                     │                 │
│            │         ┌───────────────────┐       │                 │
│            └────────►│  GetStream Client ├◄──────┘                 │
│              (实时)   │  (stream-chat)    │                         │
│                      └────────┬──────────┘                         │
│                               │ WebSocket                          │
└───────────────────────────────┼─────────────────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │   GetStream Cloud     │
                    └───────────┬───────────┘
                                │
        ┌───────────────────────┼────────────────────────────────┐
        │ REST API              │ Webhook / Server-side SDK      │
        ▼                       ▼                                │
┌───────────────────────────────────────────────────────────────┐│
│                  Backend (Spring Boot)                         ││
│                                                               ││
│  ┌─────────────────────────────────────────────────────────┐  ││
│  │                   Controller 层 (API)                    │  ││
│  │  MessageController  ConversationController  ToolController│  ││
│  │  SessionController  StreamTokenController                │  ││
│  └───────────────────────────┬─────────────────────────────┘  ││
│                              │                                ││
│  ┌───────────────────────────▼─────────────────────────────┐  ││
│  │                   Service 层 (业务逻辑)                   │  ││
│  │                                                         │  ││
│  │  ┌──────────────────┐  ┌─────────────┐                  │  ││
│  │  │GlobalOrchestrator│─►│MessageRouter │                  │  ││
│  │  └──────────────────┘  └──────┬──────┘                  │  ││
│  │                         ┌─────┴──────┐                  │  ││
│  │                         ▼            ▼                  │  ││
│  │              ┌────────────────┐ ┌───────────────┐       │  ││
│  │              │AiChatbotService│ │HumanAgentSvc  │       │  ││
│  │              │  ┌───────────┐ │ └───────────────┘       │  ││
│  │              │  │IntentAgent│ │                          │  ││
│  │              │  │ToolAgent  │ │  ┌───────────────────┐  │  ││
│  │              │  │ReplyAgent │ │  │  Tool Services    │  │  ││
│  │              │  └───────────┘ │  │  FaqService       │  │  ││
│  │              └────────────────┘  │  UserDataService   │  ││
│  │                                  │  PostQueryService  │  ││
│  │              ┌────────────────┐  └───────────────────┘  │  ││
│  │              │GetStreamService│                          │  ││
│  │              └────────────────┘                          │  ││
│  └─────────────────────────────────────────────────────────┘  ││
│                              │                                ││
│  ┌───────────────────────────▼─────────────────────────────┐  ││
│  │                   Repository 层 (数据访问)                │  ││
│  │  ConversationRepo  SessionRepo  MessageRepo  PostRepo   │  ││
│  └───────────────────────────┬─────────────────────────────┘  ││
│                              │                                ││
│  ┌───────────────────────────▼─────────────────────────────┐  ││
│  │              PostgreSQL + pgvector                       │  ││
│  │  conversation | session | message | user_post | faq_doc │  ││
│  └─────────────────────────────────────────────────────────┘  ││
└───────────────────────────────────────────────────────────────┘│
```

### 3.2 后端分层架构

采用严格的三层架构，各层职责边界清晰：

```
┌─────────────────────────────────────────────────┐
│           Controller 层 (API 边界)                │
│  - 接收 HTTP 请求，参数校验                        │
│  - 调用 Service 层                                │
│  - 返回 DTO，不暴露 Entity                         │
│  - 不含业务逻辑                                    │
├─────────────────────────────────────────────────┤
│           DTO 层 (数据传输)                        │
│  - Request DTO：接收前端请求                       │
│  - Response DTO：返回前端数据                      │
│  - 与 Entity 隔离                                 │
├─────────────────────────────────────────────────┤
│           Service 层 (业务逻辑)                    │
│  - 核心业务编排 (Orchestrator/Router)              │
│  - AI Chatbot 处理 (3 Sub-Agent)                  │
│  - 人工客服管理                                    │
│  - 工具调用 (FAQ/Delete/Post)                     │
│  - GetStream 集成                                 │
│  - 调用 Repository 进行数据读写                    │
├─────────────────────────────────────────────────┤
│           Entity 层 (领域模型)                     │
│  - JPA Entity，映射数据库表                        │
│  - 枚举类型                                       │
├─────────────────────────────────────────────────┤
│           Repository 层 (数据访问)                 │
│  - Spring Data JPA Repository                    │
│  - 自定义查询                                     │
│  - 只被 Service 层调用                             │
├─────────────────────────────────────────────────┤
│           数据库 (PostgreSQL + pgvector)           │
└─────────────────────────────────────────────────┘
```

**层间调用规则：**
- Controller → Service → Repository（单向依赖，不可反向）
- Controller 不可直接调用 Repository
- Service 间可相互调用（通过接口注入）
- Repository 不依赖 Service 或 Controller

### 3.3 前端架构

```
┌──────────────────────────────────────────────────────┐
│                   React App (SPA)                     │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │              React Router                     │    │
│  │   /chat    → UserChatPage                    │    │
│  │   /agent   → AgentDashboardPage              │    │
│  └─────────────────────┬────────────────────────┘    │
│                        │                             │
│  ┌─────────────────────▼────────────────────────┐    │
│  │              Page Components                  │    │
│  │  ┌────────────────┐  ┌────────────────────┐  │    │
│  │  │ UserChatPage   │  │ AgentDashboardPage │  │    │
│  │  └───────┬────────┘  └────────┬───────────┘  │    │
│  │          │                    │               │    │
│  │  ┌───────▼────────────────────▼───────────┐  │    │
│  │  │       Shared Components                │  │    │
│  │  │  MessageList | MessageBubble           │  │    │
│  │  │  MessageInput | ToolPanel              │  │    │
│  │  │  SessionList (Agent only)              │  │    │
│  │  └───────────────────┬────────────────────┘  │    │
│  └──────────────────────┼───────────────────────┘    │
│                         │                            │
│  ┌──────────────────────▼───────────────────────┐    │
│  │              Services                         │    │
│  │  apiClient.ts      (后端 REST API 调用)       │    │
│  │  streamClient.ts   (GetStream 初始化)         │    │
│  └──────────────────────┬───────────────────────┘    │
│                         │                            │
│  ┌──────────────────────▼───────────────────────┐    │
│  │              Hooks                            │    │
│  │  useChat.ts        (聊天状态管理)              │    │
│  │  useSession.ts     (session 管理)             │    │
│  │  useTools.ts       (工具调用)                  │    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

**前端关键设计决策：**

| 决策 | 选型 | 理由 |
|------|------|------|
| 构建工具 | Vite | 开发热更新快，构建速度优于 Webpack |
| 路由 | React Router | 单应用多页面，User Web 和 Agent Web 共享代码 |
| 样式 | Tailwind CSS | 实用优先，快速搭建 UI |
| 实时通信 | GetStream React SDK | 原生 React 组件，WebSocket 自动管理 |
| HTTP 请求 | fetch API | 浏览器原生，无需额外依赖 |
| 状态管理 | React hooks + Context | 应用简单，不需要 Redux 等重框架 |

---

## 4. 数据库设计

### 4.1 ER 图

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
└──────────────────┘       │ updated_at       │       │ getstream_msg_id │
                           │ last_activity_at │       │ created_at       │
                           └──────────────────┘       └──────────────────┘

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

### 4.2 DDL（Flyway 迁移脚本）

#### V1__init_schema.sql

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- conversation 表
-- ============================================
CREATE TABLE conversation (
    conversation_id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             VARCHAR(255) NOT NULL,
    getstream_channel_id VARCHAR(255),
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_conversation_user_id ON conversation(user_id);

-- ============================================
-- session 表
-- ============================================
CREATE TABLE session (
    session_id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID         NOT NULL REFERENCES conversation(conversation_id),
    status              VARCHAR(32)  NOT NULL DEFAULT 'AI_HANDLING',
    assigned_agent_id   VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_activity_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_conversation_id ON session(conversation_id);
CREATE INDEX idx_session_status ON session(status);
CREATE INDEX idx_session_last_activity ON session(last_activity_at);

-- ============================================
-- message 表
-- ============================================
CREATE TABLE message (
    message_id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      UUID         NOT NULL REFERENCES conversation(conversation_id),
    session_id           UUID         NOT NULL REFERENCES session(session_id),
    sender_type          VARCHAR(32)  NOT NULL,
    sender_id            VARCHAR(255) NOT NULL,
    content              TEXT         NOT NULL,
    getstream_message_id VARCHAR(255),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_message_conversation_id ON message(conversation_id);
CREATE INDEX idx_message_session_id ON message(session_id);
CREATE INDEX idx_message_created_at ON message(created_at);

-- ============================================
-- user_post 表 (Mock 数据)
-- ============================================
CREATE TABLE user_post (
    post_id    SERIAL       PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    title      VARCHAR(512) NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_post_username ON user_post(username);

-- ============================================
-- faq_doc 表 (向量存储)
-- ============================================
CREATE TABLE faq_doc (
    faq_id     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    question   TEXT          NOT NULL,
    answer     TEXT          NOT NULL,
    embedding  vector(384),
    created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

> **说明**：`embedding vector(384)` 中 384 是 `all-MiniLM-L6-v2` 模型输出的向量维度。使用 Spring AI Transformers 模块在本地生成 embedding，无需外部 API。

#### V2__mock_data.sql

```sql
-- Mock 用户帖子数据
INSERT INTO user_post (username, title, status, created_at) VALUES
('user_alice', '如何重置密码',   'PUBLISHED',    '2025-06-01 10:00:00'),
('user_alice', '账号被锁定',     'UNDER_REVIEW', '2025-06-05 14:30:00'),
('user_alice', '修改绑定邮箱',   'PUBLISHED',    '2025-07-10 09:00:00'),
('user_bob',   '无法登录',       'REMOVED',      '2025-06-03 11:00:00'),
('user_bob',   '充值未到账',     'PUBLISHED',    '2025-06-10 16:00:00'),
('user_bob',   '申请退款',       'UNDER_REVIEW', '2025-07-15 08:30:00'),
('user_carol', '举报违规内容',   'PUBLISHED',    '2025-06-20 12:00:00'),
('user_carol', '隐私设置咨询',   'DRAFT',        '2025-07-01 10:00:00');

-- Mock FAQ 数据 (embedding 由应用启动时通过 Spring AI 生成)
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

### 4.3 枚举值定义

| 枚举 | 值 | 说明 |
|------|---|------|
| ConversationStatus | `ACTIVE` | 对话进行中 |
| | `CLOSED` | 对话已关闭 |
| SessionStatus | `AI_HANDLING` | AI 处理中 |
| | `HUMAN_HANDLING` | 人工客服处理中 |
| | `CLOSED` | 会话已关闭 |
| SenderType | `USER` | 用户 |
| | `AI_CHATBOT` | AI 客服 |
| | `HUMAN_AGENT` | 人工客服 |
| PostStatus | `PUBLISHED` | 已发布 |
| | `UNDER_REVIEW` | 审核中 |
| | `REMOVED` | 已删除 |
| | `DRAFT` | 草稿 |

---

## 5. 数据结构 (DTO)

### 5.1 Request DTOs

```java
// --- 消息相关 ---

// 用户发送消息
record InboundMessageRequest(
    String userId,
    String content
)

// 人工客服回复
record AgentReplyRequest(
    String sessionId,
    String agentId,
    String content
)

// --- 工具相关 ---

record FaqSearchRequest(
    String query
)

record UserDataDeleteRequest(
    String username
)

record PostQueryRequest(
    String username
)
```

### 5.2 Response DTOs

```java
// --- 通用 ---

record ApiResponse<T>(
    boolean success,
    T data,
    String error
)

// --- 消息相关 ---

record MessageResponse(
    String messageId,
    String conversationId,
    String sessionId,
    String senderType,   // USER | AI_CHATBOT | HUMAN_AGENT
    String senderId,
    String content,
    String createdAt
)

record InboundMessageResponse(
    String conversationId,
    String sessionId,
    String messageId
)

// --- Conversation ---

record ConversationResponse(
    String conversationId,
    String userId,
    String status,
    String getstreamChannelId,
    String createdAt,
    String updatedAt
)

// --- Session ---

record SessionResponse(
    String sessionId,
    String conversationId,
    String status,
    String assignedAgentId,
    String createdAt,
    String lastActivityAt
)

// --- 工具响应 ---

record FaqSearchResponse(
    String question,
    String answer,
    double score
)

record UserDataDeleteResponse(
    boolean success,
    String message
)

record PostQueryResponse(
    List<PostItem> posts
)

record PostItem(
    int postId,
    String username,
    String title,
    String status,
    String createdAt
)

// --- GetStream ---

record StreamTokenResponse(
    String token,
    String userId
)
```

---

## 6. API 接口设计

所有接口以 `/api` 为前缀，返回统一的 `ApiResponse<T>` 结构。

### 6.1 消息 API (MessageController)

#### POST /api/messages/inbound

用户发送消息的唯一入口。触发 Orchestrator → Router → AI/Human 全流程。

```
请求:
POST /api/messages/inbound
Content-Type: application/json

{
    "userId": "user_alice",
    "content": "我想查一下我的帖子状态"
}

响应 (200):
{
    "success": true,
    "data": {
        "conversationId": "550e8400-e29b-41d4-a716-446655440000",
        "sessionId": "660e8400-e29b-41d4-a716-446655440001",
        "messageId": "770e8400-e29b-41d4-a716-446655440002"
    },
    "error": null
}
```

> **说明**：该接口为同步返回，仅确认消息已接收。AI/人工客服的回复通过 GetStream 实时推送到前端，不在此接口返回。

#### POST /api/messages/agent-reply

人工客服发送回复。

```
请求:
POST /api/messages/agent-reply
Content-Type: application/json

{
    "sessionId": "660e8400-e29b-41d4-a716-446655440001",
    "agentId": "agent_default",
    "content": "您好，我已经查到您的帖子状态..."
}

响应 (200):
{
    "success": true,
    "data": {
        "conversationId": "550e8400-...",
        "sessionId": "660e8400-...",
        "messageId": "880e8400-..."
    },
    "error": null
}
```

#### GET /api/messages?conversationId={id}&sessionId={id}

获取消息历史。支持按 conversation 或 session 过滤。

```
请求:
GET /api/messages?conversationId=550e8400-...&sessionId=660e8400-...

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
        },
        {
            "messageId": "770e8401-...",
            "conversationId": "550e8400-...",
            "sessionId": "660e8400-...",
            "senderType": "AI_CHATBOT",
            "senderId": "ai_bot",
            "content": "请提供您的用户名，我来帮您查询。",
            "createdAt": "2025-07-20T10:30:05Z"
        }
    ],
    "error": null
}
```

### 6.2 Conversation API (ConversationController)

#### GET /api/conversations?userId={userId}

根据 userId 获取 conversation。

```
响应 (200):
{
    "success": true,
    "data": {
        "conversationId": "550e8400-...",
        "userId": "user_alice",
        "status": "ACTIVE",
        "getstreamChannelId": "messaging:conv-550e8400",
        "createdAt": "2025-07-20T10:00:00Z",
        "updatedAt": "2025-07-20T10:30:00Z"
    },
    "error": null
}
```

#### GET /api/conversations/{conversationId}/sessions

获取 conversation 下的 session 列表。

```
响应 (200):
{
    "success": true,
    "data": [
        {
            "sessionId": "660e8400-...",
            "conversationId": "550e8400-...",
            "status": "CLOSED",
            "assignedAgentId": null,
            "createdAt": "2025-07-20T10:00:00Z",
            "lastActivityAt": "2025-07-20T10:08:00Z"
        },
        {
            "sessionId": "660e8401-...",
            "conversationId": "550e8400-...",
            "status": "AI_HANDLING",
            "assignedAgentId": null,
            "createdAt": "2025-07-20T10:30:00Z",
            "lastActivityAt": "2025-07-20T10:30:05Z"
        }
    ],
    "error": null
}
```

### 6.3 Session API (SessionController)

#### GET /api/sessions/{sessionId}

获取 session 详情。

```
响应 (200):
{
    "success": true,
    "data": {
        "sessionId": "660e8400-...",
        "conversationId": "550e8400-...",
        "status": "HUMAN_HANDLING",
        "assignedAgentId": "agent_default",
        "createdAt": "2025-07-20T10:00:00Z",
        "lastActivityAt": "2025-07-20T10:35:00Z"
    },
    "error": null
}
```

#### GET /api/sessions/active?agentId={agentId}

获取指定人工客服的活跃 session 列表（状态为 HUMAN_HANDLING）。

```
响应 (200):
{
    "success": true,
    "data": [
        {
            "sessionId": "660e8400-...",
            "conversationId": "550e8400-...",
            "status": "HUMAN_HANDLING",
            "assignedAgentId": "agent_default",
            "createdAt": "...",
            "lastActivityAt": "..."
        }
    ],
    "error": null
}
```

### 6.4 Tools API (ToolController)

#### POST /api/tools/faq/search

FAQ 知识库搜索。使用 pgvector 进行语义相似度检索。

```
请求:
POST /api/tools/faq/search
Content-Type: application/json

{
    "query": "密码忘了怎么办"
}

响应 (200):
{
    "success": true,
    "data": {
        "question": "如何重置密码？",
        "answer": "请前往\"设置 > 账号安全 > 重置密码\"...",
        "score": 0.92
    },
    "error": null
}
```

#### POST /api/tools/user-data/delete

用户数据删除（Mock）。

```
请求:
POST /api/tools/user-data/delete
Content-Type: application/json

{
    "username": "user_alice"
}

响应 (200):
{
    "success": true,
    "data": {
        "success": true,
        "message": "用户 user_alice 的数据删除请求已提交，将在 24 小时内处理完毕。"
    },
    "error": null
}
```

#### POST /api/tools/posts/query

用户帖子状态查询。

```
请求:
POST /api/tools/posts/query
Content-Type: application/json

{
    "username": "user_alice"
}

响应 (200):
{
    "success": true,
    "data": {
        "posts": [
            {
                "postId": 1,
                "username": "user_alice",
                "title": "如何重置密码",
                "status": "PUBLISHED",
                "createdAt": "2025-06-01T10:00:00Z"
            },
            {
                "postId": 2,
                "username": "user_alice",
                "title": "账号被锁定",
                "status": "UNDER_REVIEW",
                "createdAt": "2025-06-05T14:30:00Z"
            }
        ]
    },
    "error": null
}
```

### 6.5 GetStream Token API (StreamTokenController)

#### GET /api/stream/token?userId={userId}

为前端生成 GetStream 连接 token。

```
响应 (200):
{
    "success": true,
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "userId": "user_alice"
    },
    "error": null
}
```

### 6.6 API 汇总

| 方法 | 路径 | 说明 | 调用方 |
|------|------|------|--------|
| POST | `/api/messages/inbound` | 用户发送消息 | User Web |
| POST | `/api/messages/agent-reply` | 人工客服回复 | Agent Web |
| GET  | `/api/messages` | 获取消息历史 | User Web / Agent Web |
| GET  | `/api/conversations` | 获取用户 conversation | User Web |
| GET  | `/api/conversations/{id}/sessions` | 获取 session 列表 | Agent Web |
| GET  | `/api/sessions/{id}` | 获取 session 详情 | Agent Web |
| GET  | `/api/sessions/active` | 获取活跃 session 列表 | Agent Web |
| POST | `/api/tools/faq/search` | FAQ 搜索 | Agent Web / AI 内部 |
| POST | `/api/tools/user-data/delete` | 用户数据删除 | Agent Web / AI 内部 |
| POST | `/api/tools/posts/query` | 帖子状态查询 | Agent Web / AI 内部 |
| GET  | `/api/stream/token` | 获取 GetStream token | User Web / Agent Web |

---

## 7. 数据流图

### 7.1 用户发送消息（AI 处理）

```
User Web                    Backend                              GetStream          User Web
  │                           │                                      │                 │
  │  POST /messages/inbound   │                                      │                 │
  │ {userId, content}         │                                      │                 │
  │──────────────────────────►│                                      │                 │
  │                           │                                      │                 │
  │                    ┌──────┴──────┐                                │                 │
  │                    │Orchestrator │                                │                 │
  │                    │ 1.查找/创建  │                                │                 │
  │                    │  conversation│                               │                 │
  │                    │ 2.查找/创建  │                                │                 │
  │                    │  session     │                               │                 │
  │                    │ 3.保存消息   │                                │                 │
  │                    └──────┬──────┘                                │                 │
  │                           │                                      │                 │
  │                    ┌──────┴──────┐                                │                 │
  │                    │   Router    │                                │                 │
  │                    │ status=     │                                │                 │
  │                    │ AI_HANDLING │                                │                 │
  │                    └──────┬──────┘                                │                 │
  │                           │                                      │                 │
  │                    ┌──────▼──────┐                                │                 │
  │                    │ AI Chatbot  │                                │                 │
  │                    │ Service     │                                │                 │
  │                    │             │                                │                 │
  │                    │ 1.Intent    │   POST /tools/...              │                 │
  │                    │   Agent ───►│──────────────►Tool Service     │                 │
  │                    │             │◄──────────────                 │                 │
  │                    │ 2.Tool      │                                │                 │
  │                    │   Agent     │                                │                 │
  │                    │ 3.Reply     │                                │                 │
  │                    │   Agent     │                                │                 │
  │                    └──────┬──────┘                                │                 │
  │                           │                                      │                 │
  │                           │  sendMessage(channelId, aiReply)     │                 │
  │                           │─────────────────────────────────────►│                 │
  │                           │                                      │  WebSocket push │
  │                           │                                      │────────────────►│
  │  200 OK                   │                                      │                 │
  │  {conversationId,         │                                      │                 │
  │   sessionId, messageId}   │                                      │                 │
  │◄──────────────────────────│                                      │                 │
```

### 7.2 用户请求转人工

```
User Web                    Backend                              GetStream      Agent Web
  │                           │                                      │               │
  │  POST /messages/inbound   │                                      │               │
  │ {userId, "转人工"}         │                                      │               │
  │──────────────────────────►│                                      │               │
  │                           │                                      │               │
  │                    ┌──────┴──────┐                                │               │
  │                    │Orchestrator │                                │               │
  │                    │ 更新session  │                                │               │
  │                    └──────┬──────┘                                │               │
  │                           │                                      │               │
  │                    ┌──────┴──────┐                                │               │
  │                    │   Router    │                                │               │
  │                    │ 检测"转人工" │                                │               │
  │                    │ → HUMAN     │                                │               │
  │                    └──────┬──────┘                                │               │
  │                           │                                      │               │
  │                    ┌──────▼──────┐                                │               │
  │                    │HumanAgent   │                                │               │
  │                    │ Service     │                                │               │
  │                    │ 1.更新status │                               │               │
  │                    │  =HUMAN_    │                                │               │
  │                    │  HANDLING   │                                │               │
  │                    │ 2.分配客服   │                                │               │
  │                    └──────┬──────┘                                │               │
  │                           │                                      │               │
  │                           │  addMember(channelId, agentId)       │               │
  │                           │─────────────────────────────────────►│               │
  │                           │                                      │  channel event│
  │                           │  sendMessage("转接人工客服中...")      │──────────────►│
  │                           │─────────────────────────────────────►│               │
  │                           │                                      │  WebSocket    │
  │                           │                                      │──────────────►│
  │◄────────────────────────────────────────────────────────────────────(实时推送)    │
```

### 7.3 人工客服回复

```
Agent Web                   Backend                              GetStream      User Web
  │                           │                                      │               │
  │  POST /messages/agent-reply│                                     │               │
  │ {sessionId, agentId,      │                                      │               │
  │  content}                 │                                      │               │
  │──────────────────────────►│                                      │               │
  │                           │                                      │               │
  │                    ┌──────┴──────┐                                │               │
  │                    │HumanAgent   │                                │               │
  │                    │ Service     │                                │               │
  │                    │ 1.保存消息   │                                │               │
  │                    │ 2.更新       │                               │               │
  │                    │  activity   │                                │               │
  │                    └──────┬──────┘                                │               │
  │                           │                                      │               │
  │                           │  sendMessage(channelId, reply)       │               │
  │                           │─────────────────────────────────────►│               │
  │                           │                                      │  WebSocket    │
  │  200 OK                   │                                      │──────────────►│
  │◄──────────────────────────│                                      │               │
```

### 7.4 人工客服调用工具

```
Agent Web                   Backend                         Agent Web
  │                           │                                 │
  │  POST /api/tools/faq/search                                │
  │ {query: "密码重置"}        │                                │
  │──────────────────────────►│                                 │
  │                           │                                 │
  │                    ┌──────┴──────┐                          │
  │                    │ FaqService  │                          │
  │                    │ 1.生成embedding                        │
  │                    │ 2.pgvector查询                         │
  │                    │ 3.返回最匹配FAQ                        │
  │                    └──────┬──────┘                          │
  │                           │                                 │
  │  200 OK                   │                                 │
  │  {question, answer, score}│                                 │
  │◄──────────────────────────│                                 │
  │                           │                                 │
  │  (客服查看结果，自行编辑回复)                                  │
  │                           │                                 │
  │  POST /messages/agent-reply                                │
  │──────────────────────────►│                                 │
```

### 7.5 GetStream Token 获取流程

```
Frontend (启动时)           Backend                         GetStream
  │                           │                                 │
  │  GET /api/stream/token    │                                 │
  │  ?userId=user_alice       │                                 │
  │──────────────────────────►│                                 │
  │                           │  ServerClient.createToken(userId)│
  │                           │────────────────────────────────►│
  │                           │◄────────────────────────────────│
  │  200 {token, userId}      │                                 │
  │◄──────────────────────────│                                 │
  │                           │                                 │
  │  StreamChat.connect(      │                                 │
  │    apiKey, token, userId) │                                 │
  │─────────────────────────────────────────────(WebSocket)────►│
  │◄────────────────────────────────────────────────────────────│
```

---

## 8. 核心后端组件设计

### 8.1 GlobalOrchestrator

```java
@Service
public class GlobalOrchestrator {

    // 核心入口方法
    public InboundMessageResponse handleInboundMessage(InboundMessageRequest request) {
        // 1. 查找或创建 Conversation
        Conversation conversation = conversationService.findOrCreate(request.userId());

        // 2. 查找活跃 Session 或创建新 Session
        Session session = sessionService.findActiveOrCreate(conversation.getId());

        // 3. 保存用户消息到数据库
        Message message = messageService.save(conversation.getId(), session.getId(),
                SenderType.USER, request.userId(), request.content());

        // 4. 通过 GetStream 发送用户消息
        getStreamService.sendMessage(conversation.getGetstreamChannelId(),
                request.userId(), request.content());

        // 5. 路由决策
        messageRouter.route(session, message);

        // 6. 更新 session 活跃时间
        sessionService.updateLastActivity(session.getId());

        return new InboundMessageResponse(
            conversation.getId().toString(),
            session.getId().toString(),
            message.getId().toString()
        );
    }
}
```

### 8.2 MessageRouter

```java
@Service
public class MessageRouter {

    public void route(Session session, Message message) {
        // 1. 检查是否包含"转人工"关键词
        if (message.getContent().contains("转人工")) {
            sessionService.updateStatus(session.getId(), SessionStatus.HUMAN_HANDLING);
            humanAgentService.assignAgent(session);
            // 发送系统提示
            getStreamService.sendSystemMessage(
                session.getConversationId(),
                "正在为您转接人工客服，请稍候..."
            );
            return;
        }

        // 2. 根据 Session 状态路由
        switch (session.getStatus()) {
            case HUMAN_HANDLING -> humanAgentService.forwardMessage(session, message);
            case AI_HANDLING    -> aiChatbotService.handleMessage(session, message);
        }
    }
}
```

### 8.3 AiChatbotService (3 Sub-Agent)

```java
@Service
public class AiChatbotService {

    public void handleMessage(Session session, Message message) {
        // 1. 意图识别
        String intent = intentRecognitionAgent.recognize(
            message.getContent(),
            getSessionContext(session)
        );

        // 2. 工具编排与调用
        ToolResult toolResult = toolOrchestrationAgent.execute(
            intent,
            message.getContent(),
            getSessionContext(session)
        );

        // 3. 回复组织
        String reply = responseCompositionAgent.compose(
            message.getContent(),
            intent,
            toolResult
        );

        // 4. 保存 AI 回复到数据库
        messageService.save(session.getConversationId(), session.getId(),
            SenderType.AI_CHATBOT, "ai_bot", reply);

        // 5. 通过 GetStream 发送回复
        getStreamService.sendMessage(
            getChannelId(session.getConversationId()),
            "ai_bot",
            reply
        );
    }
}
```

### 8.4 Sub-Agent 与 Kimi LLM 集成

```java
// IntentRecognitionAgent - 使用 Spring AI ChatClient 调用 Kimi
@Service
public class IntentRecognitionAgent {

    private final ChatClient chatClient;

    public String recognize(String userMessage, SessionContext context) {
        String systemPrompt = """
            你是一个意图识别助手。根据用户消息判断意图类型。
            仅返回以下之一：FAQ_QUERY, DATA_DELETE, POST_QUERY, GENERAL_CHAT
            """;

        ChatResponse response = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .chatResponse();

        return response.getResult().getOutput().getText().trim();
    }
}

// ToolOrchestrationAgent - 根据意图调用工具
@Service
public class ToolOrchestrationAgent {

    public ToolResult execute(String intent, String userMessage, SessionContext context) {
        return switch (intent) {
            case "FAQ_QUERY"   -> faqService.search(userMessage);
            case "DATA_DELETE" -> userDataService.delete(extractUsername(userMessage, context));
            case "POST_QUERY"  -> postQueryService.query(extractUsername(userMessage, context));
            default            -> ToolResult.empty();
        };
    }
}

// ResponseCompositionAgent - 组织用户友好的回复
@Service
public class ResponseCompositionAgent {

    private final ChatClient chatClient;

    public String compose(String userMessage, String intent, ToolResult toolResult) {
        String systemPrompt = """
            你是一个友好的客服助手。根据用户的问题和工具查询结果，
            组织一个简洁、友好的回复。使用中文回复。
            """;

        String userPrompt = String.format("""
            用户问题：%s
            查询结果：%s
            请组织一个友好的回复。
            """, userMessage, toolResult.toJson());

        ChatResponse response = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .chatResponse();

        return response.getResult().getOutput().getText();
    }
}
```

### 8.5 Session 超时调度

```java
@Component
public class SessionTimeoutScheduler {

    @Scheduled(fixedRate = 60_000)  // 每 60 秒检查一次
    public void checkSessionTimeout() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<Session> expiredSessions = sessionRepository
            .findByStatusInAndLastActivityAtBefore(
                List.of(SessionStatus.AI_HANDLING, SessionStatus.HUMAN_HANDLING),
                threshold
            );
        for (Session session : expiredSessions) {
            sessionService.updateStatus(session.getId(), SessionStatus.CLOSED);
        }
    }
}
```

---

## 9. GetStream 集成设计

### 9.1 服务端 (Java SDK)

| 操作 | API | 时机 |
|------|-----|------|
| 创建用户 | `upsertUser()` | 首次发消息时 |
| 创建 Channel | `createChannel("messaging", channelId)` | 创建 Conversation 时 |
| 添加成员 | `addMembers(channelId, members)` | 创建 Conversation / 转人工时 |
| 发送消息 | `sendMessage(channelId, message)` | AI 回复 / 系统提示 |
| 生成 Token | `createToken(userId)` | 前端请求 Token 时 |

**用户映射：**

| 系统角色 | GetStream 用户 ID | 创建时机 |
|---------|-------------------|---------|
| 用户 | `user_{userId}` | 用户首次发消息 |
| AI Chatbot | `ai_bot` | 系统启动时 |
| 人工客服 | `agent_default` | 系统启动时 |

**Channel 映射：**

| 系统概念 | GetStream Channel |
|---------|-------------------|
| Conversation | `messaging:conv-{conversationId}` |

### 9.2 客户端 (React SDK)

```typescript
// streamClient.ts
import { StreamChat } from 'stream-chat';

const apiKey = import.meta.env.VITE_GETSTREAM_API_KEY;

export const chatClient = StreamChat.getInstance(apiKey);

export async function connectUser(userId: string, token: string) {
  await chatClient.connectUser(
    { id: userId, name: userId },
    token
  );
}

export function getChannel(channelId: string) {
  return chatClient.channel('messaging', channelId);
}
```

**前端使用 GetStream 的方式：**

| 功能 | 实现方式 |
|------|---------|
| 实时接收消息 | `channel.on('message.new', callback)` |
| 显示消息列表 | `channel.state.messages` |
| 用户在线状态 | GetStream presence 机制 |
| 消息已读 | `channel.markRead()` |

> **注意**：前端不通过 GetStream SDK 直接发送消息。所有消息先发送到后端 API，由后端处理后再通过 GetStream 服务端 SDK 发送。这样确保所有消息都经过 Orchestrator → Router 的编排流程。

---

## 10. Kimi LLM 集成设计

### 10.1 配置

Kimi (Moonshot AI) 提供 OpenAI 兼容 API，通过 Spring AI 的 OpenAI 模块接入：

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${KIMI_API_KEY}
      base-url: https://api.moonshot.cn/v1
      chat:
        options:
          model: moonshot-v1-8k
          temperature: 0.7
```

### 10.2 Embedding 生成

FAQ 知识库的向量化使用 Spring AI Transformers（本地 ONNX 模型），不依赖外部 API：

```yaml
spring:
  ai:
    embedding:
      transformer:
        onnx:
          model-uri: classpath:all-MiniLM-L6-v2.onnx
        tokenizer-uri: classpath:tokenizer.json
```

### 10.3 PGVector 向量存储

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        dimensions: 384
        index-type: HNSW
        distance-type: COSINE_DISTANCE
```

---

## 11. 项目目录结构

```
chatbot/
├── CLAUDE.md                                    # 项目说明
├── docs/
│   ├── PRD.md                                   # 产品需求文档
│   └── tech-design-spec.md                      # 本文档
│
├── backend/                                     # Spring Boot 后端
│   ├── build.gradle.kts                         # Gradle 构建脚本
│   ├── settings.gradle.kts
│   ├── gradle/
│   │   └── wrapper/
│   └── src/
│       ├── main/
│       │   ├── java/com/chatbot/
│       │   │   ├── ChatbotApplication.java      # 启动类
│       │   │   │
│       │   │   ├── config/                      # === 配置层 ===
│       │   │   │   ├── GetStreamConfig.java     # GetStream 客户端配置
│       │   │   │   ├── KimiAiConfig.java        # Kimi LLM / Spring AI 配置
│       │   │   │   ├── VectorStoreConfig.java   # PGVector 向量存储配置
│       │   │   │   └── WebConfig.java           # CORS 等 Web 配置
│       │   │   │
│       │   │   ├── controller/                  # === API 层 ===
│       │   │   │   ├── MessageController.java   # POST /messages/inbound, /agent-reply, GET /messages
│       │   │   │   ├── ConversationController.java  # GET /conversations
│       │   │   │   ├── SessionController.java   # GET /sessions/{id}, /sessions/active
│       │   │   │   ├── ToolController.java      # POST /tools/faq/search, /user-data/delete, /posts/query
│       │   │   │   └── StreamTokenController.java   # GET /stream/token
│       │   │   │
│       │   │   ├── dto/                         # === 数据传输对象 ===
│       │   │   │   ├── ApiResponse.java         # 统一响应包装
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
│       │   │   ├── service/                     # === 业务逻辑层 ===
│       │   │   │   ├── orchestrator/
│       │   │   │   │   └── GlobalOrchestrator.java      # 全局编排器
│       │   │   │   ├── router/
│       │   │   │   │   └── MessageRouter.java           # 消息路由器
│       │   │   │   ├── chatbot/
│       │   │   │   │   ├── AiChatbotService.java        # AI 主服务
│       │   │   │   │   ├── IntentRecognitionAgent.java  # 意图识别 Sub-Agent
│       │   │   │   │   ├── ToolOrchestrationAgent.java  # 工具编排 Sub-Agent
│       │   │   │   │   └── ResponseCompositionAgent.java # 回复组织 Sub-Agent
│       │   │   │   ├── agent/
│       │   │   │   │   └── HumanAgentService.java       # 人工客服服务
│       │   │   │   ├── tools/
│       │   │   │   │   ├── FaqService.java              # FAQ 知识库服务
│       │   │   │   │   ├── UserDataDeletionService.java # 用户数据删除服务
│       │   │   │   │   └── PostQueryService.java        # 帖子查询服务
│       │   │   │   ├── stream/
│       │   │   │   │   └── GetStreamService.java        # GetStream 集成服务
│       │   │   │   ├── ConversationService.java         # Conversation 管理
│       │   │   │   ├── SessionService.java              # Session 管理
│       │   │   │   └── MessageService.java              # Message 管理
│       │   │   │
│       │   │   ├── entity/                      # === 实体层 ===
│       │   │   │   ├── Conversation.java
│       │   │   │   ├── Session.java
│       │   │   │   ├── Message.java
│       │   │   │   ├── UserPost.java
│       │   │   │   └── FaqDoc.java
│       │   │   │
│       │   │   ├── enums/                       # === 枚举 ===
│       │   │   │   ├── ConversationStatus.java
│       │   │   │   ├── SessionStatus.java
│       │   │   │   ├── SenderType.java
│       │   │   │   └── PostStatus.java
│       │   │   │
│       │   │   ├── repository/                  # === 数据访问层 ===
│       │   │   │   ├── ConversationRepository.java
│       │   │   │   ├── SessionRepository.java
│       │   │   │   ├── MessageRepository.java
│       │   │   │   ├── UserPostRepository.java
│       │   │   │   └── FaqDocRepository.java
│       │   │   │
│       │   │   └── scheduler/                   # === 定时任务 ===
│       │   │       └── SessionTimeoutScheduler.java
│       │   │
│       │   └── resources/
│       │       ├── application.yml              # 主配置文件
│       │       ├── application-local.yml        # 本地开发配置
│       │       └── db/migration/                # Flyway 迁移脚本
│       │           ├── V1__init_schema.sql
│       │           └── V2__mock_data.sql
│       │
│       └── test/java/com/chatbot/               # 测试代码
│           ├── controller/
│           ├── service/
│           └── repository/
│
├── frontend/                                    # React 前端
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx                             # 入口
│       ├── App.tsx                              # 根组件 + 路由
│       │
│       ├── pages/                               # === 页面组件 ===
│       │   ├── UserChatPage.tsx                 # 用户聊天页 /chat
│       │   └── AgentDashboardPage.tsx           # 客服工作台 /agent
│       │
│       ├── components/                          # === UI 组件 ===
│       │   ├── chat/
│       │   │   ├── MessageList.tsx              # 消息列表
│       │   │   ├── MessageBubble.tsx            # 消息气泡
│       │   │   └── MessageInput.tsx             # 消息输入框
│       │   ├── agent/
│       │   │   ├── SessionList.tsx              # Session 列表 (客服端)
│       │   │   └── ToolPanel.tsx                # 工具面板 (客服端)
│       │   └── common/
│       │       └── Layout.tsx                   # 通用布局
│       │
│       ├── services/                            # === API 调用 ===
│       │   ├── apiClient.ts                     # 后端 REST API 封装
│       │   └── streamClient.ts                  # GetStream 客户端初始化
│       │
│       ├── hooks/                               # === 自定义 Hooks ===
│       │   ├── useChat.ts                       # 聊天逻辑 (发消息、接收消息)
│       │   ├── useSession.ts                    # Session 管理
│       │   └── useTools.ts                      # 工具调用
│       │
│       ├── types/                               # === TypeScript 类型 ===
│       │   └── index.ts                         # 所有类型定义
│       │
│       └── config/                              # === 配置 ===
│           └── env.ts                           # 环境变量
│
└── scripts/                                     # 辅助脚本
    ├── init-db.sh                               # 数据库初始化
    └── start-dev.sh                             # 一键启动开发环境
```

---

## 12. 配置文件

### 12.1 后端 application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chatbot
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true
    locations: classpath:db/migration

  ai:
    openai:
      api-key: ${KIMI_API_KEY}
      base-url: https://api.moonshot.cn/v1
      chat:
        options:
          model: moonshot-v1-8k
          temperature: 0.7

    vectorstore:
      pgvector:
        dimensions: 384
        index-type: HNSW
        distance-type: COSINE_DISTANCE

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
  ai:
    bot-id: ai_bot
    bot-name: AI 助手
```

### 12.2 后端 build.gradle.kts

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.chatbot"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring AI (Kimi via OpenAI-compatible)
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")

    // Spring AI PGVector
    implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")

    // Spring AI Transformers (本地 Embedding)
    implementation("org.springframework.ai:spring-ai-transformers-spring-boot-starter")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // GetStream Chat Java SDK
    implementation("io.getstream:stream-chat-java:1.24.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### 12.3 前端 package.json

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
    "stream-chat": "^9.0.0",
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

### 12.4 前端 vite.config.ts

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

---

## 13. 前端 TypeScript 类型定义

```typescript
// types/index.ts

// === API 响应 ===
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}

// === 消息 ===
export interface MessageResponse {
  messageId: string;
  conversationId: string;
  sessionId: string;
  senderType: 'USER' | 'AI_CHATBOT' | 'HUMAN_AGENT';
  senderId: string;
  content: string;
  createdAt: string;
}

export interface InboundMessageRequest {
  userId: string;
  content: string;
}

export interface AgentReplyRequest {
  sessionId: string;
  agentId: string;
  content: string;
}

export interface InboundMessageResponse {
  conversationId: string;
  sessionId: string;
  messageId: string;
}

// === Conversation ===
export interface ConversationResponse {
  conversationId: string;
  userId: string;
  status: 'ACTIVE' | 'CLOSED';
  getstreamChannelId: string;
  createdAt: string;
  updatedAt: string;
}

// === Session ===
export interface SessionResponse {
  sessionId: string;
  conversationId: string;
  status: 'AI_HANDLING' | 'HUMAN_HANDLING' | 'CLOSED';
  assignedAgentId: string | null;
  createdAt: string;
  lastActivityAt: string;
}

// === 工具 ===
export interface FaqSearchRequest {
  query: string;
}

export interface FaqSearchResponse {
  question: string;
  answer: string;
  score: number;
}

export interface UserDataDeleteRequest {
  username: string;
}

export interface UserDataDeleteResponse {
  success: boolean;
  message: string;
}

export interface PostQueryRequest {
  username: string;
}

export interface PostItem {
  postId: number;
  username: string;
  title: string;
  status: 'PUBLISHED' | 'UNDER_REVIEW' | 'REMOVED' | 'DRAFT';
  createdAt: string;
}

export interface PostQueryResponse {
  posts: PostItem[];
}

// === GetStream ===
export interface StreamTokenResponse {
  token: string;
  userId: string;
}
```

---

## 14. 前端 API 客户端

```typescript
// services/apiClient.ts

const BASE_URL = '/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const json = await response.json();
  if (!json.success) throw new Error(json.error);
  return json.data;
}

// 消息
export const messageApi = {
  sendInbound: (body: InboundMessageRequest) =>
    request<InboundMessageResponse>('/messages/inbound', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  sendAgentReply: (body: AgentReplyRequest) =>
    request<InboundMessageResponse>('/messages/agent-reply', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  getMessages: (conversationId: string, sessionId?: string) => {
    const params = new URLSearchParams({ conversationId });
    if (sessionId) params.append('sessionId', sessionId);
    return request<MessageResponse[]>(`/messages?${params}`);
  },
};

// Conversation
export const conversationApi = {
  getByUserId: (userId: string) =>
    request<ConversationResponse>(`/conversations?userId=${userId}`),

  getSessions: (conversationId: string) =>
    request<SessionResponse[]>(`/conversations/${conversationId}/sessions`),
};

// Session
export const sessionApi = {
  getById: (sessionId: string) =>
    request<SessionResponse>(`/sessions/${sessionId}`),

  getActive: (agentId: string) =>
    request<SessionResponse[]>(`/sessions/active?agentId=${agentId}`),
};

// Tools
export const toolApi = {
  faqSearch: (body: FaqSearchRequest) =>
    request<FaqSearchResponse>('/tools/faq/search', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  deleteUserData: (body: UserDataDeleteRequest) =>
    request<UserDataDeleteResponse>('/tools/user-data/delete', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  queryPosts: (body: PostQueryRequest) =>
    request<PostQueryResponse>('/tools/posts/query', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
};

// GetStream Token
export const streamApi = {
  getToken: (userId: string) =>
    request<StreamTokenResponse>(`/stream/token?userId=${userId}`),
};
```

---

## 15. 环境变量

### 后端 (.env 或环境变量)

```
DB_USERNAME=postgres
DB_PASSWORD=postgres
KIMI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
GETSTREAM_API_KEY=xxxxxxxxxxxxxxxxx
GETSTREAM_API_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 前端 (.env)

```
VITE_API_BASE_URL=http://localhost:8080
VITE_GETSTREAM_API_KEY=xxxxxxxxxxxxxxxxx
```

---

## 16. 本地环境搭建步骤

```bash
# 1. PostgreSQL 安装与配置
brew install postgresql@16
brew services start postgresql@16
# 安装 pgvector 扩展
brew install pgvector
# 创建数据库
createdb chatbot
psql -d chatbot -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 2. Java 21
brew install openjdk@21

# 3. Node.js 22
brew install node@22

# 4. 启动后端
cd backend
./gradlew bootRun

# 5. 启动前端
cd frontend
npm install
npm run dev
```

---

## 17. 关键设计决策记录

| 决策 | 选择 | 替代方案 | 理由 |
|------|------|---------|------|
| 向量存储 | PGVector (PostgreSQL 扩展) | Chroma / Qdrant / Milvus | 已使用 PostgreSQL，无需额外服务，Spring AI 原生支持 |
| Embedding 模型 | 本地 ONNX (all-MiniLM-L6-v2) | Kimi API / OpenAI Embedding | 零成本、零外部依赖、384 维足够 FAQ 场景 |
| 前端框架 | React + Vite | Next.js / Vue | 无需 SSR，Vite 开发体验好，GetStream React SDK 成熟 |
| 消息发送路径 | 前端 → 后端 API → GetStream | 前端直接通过 GetStream 发送 | 所有消息必须经过 Orchestrator 路由，保证业务逻辑一致性 |
| LLM 集成 | Spring AI + OpenAI 兼容模式 | 直接 HTTP 调用 Kimi API | 统一抽象，未来可替换 LLM 供应商 |
| 构建工具 | Gradle Kotlin DSL | Maven | 更简洁的依赖管理，Spring Boot 官方推荐 |
| Session 超时 | Spring @Scheduled 定时任务 | Redis TTL / 消息队列 | 本地单机运行，无需引入额外中间件 |
