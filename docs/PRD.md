# PRD: 智能客服系统 (AI Chatbot + 人工客服 IM)

## 1. 项目概述

### 1.1 项目目标

构建一套完整的智能客服系统，验证 AI Chatbot 的能力，并实现 AI 与人工客服的无缝集成。用户发起咨询时，系统首先由 AI Chatbot 自动处理；当 AI 无法满足需求时，支持无缝转接到人工客服。

### 1.2 核心价值

- 验证 AI Chatbot 在客服场景下的技术可行性
- 验证 AI 与人工客服的集成和协作模式
- 构建可扩展的客服系统技术框架

### 1.3 技术栈

| 层级 | 技术选型 |
|------|---------|
| 后端框架 | Java + Spring Boot |
| 前端框架 | 待选型 (React/Vue/Next.js) |
| 数据库 | PostgreSQL (本地) |
| 向量数据库 | 轻量级开源 Vector DB (用于 FAQ 知识库) |
| IM 服务 | GetStream |
| LLM | Kimi 模型 |
| 部署 | 本地运行，不使用 Docker |

---

## 2. 系统角色

### 2.1 User（用户）

- 通过 User Web 发起咨询
- 发送消息、接收 AI Chatbot 或人工客服的回复
- 可通过发送"转人工"触发转接人工客服

### 2.2 AI Chatbot（AI 客服机器人）

- 后端服务，无独立 UI
- 自动处理用户咨询
- 基于 Kimi 模型的 Agent 架构
- 可调用 3 类工具完成任务

### 2.3 Human Agent（人工客服）

- 通过 Human Agent Web 处理用户咨询
- 本系统中为固定的唯一一名人工客服
- 可手动调用 3 类工具辅助处理

---

## 3. 核心概念

### 3.1 Conversation（对话）

| 属性 | 说明 |
|------|------|
| 创建时机 | 每个用户第一次发起 inbound message 时创建 |
| 生命周期 | 贯穿用户与客服系统的完整交互周期 |
| 唯一标识 | conversation_id |
| 参与方 | 用户、AI Chatbot、人工客服共享同一个 conversation |

- 同一用户的所有消息（无论由 AI 还是人工客服处理）都归属于同一个 conversation
- 只有新用户发起首条消息时才会创建新的 conversation

### 3.2 Session（会话）

| 属性 | 说明 |
|------|------|
| 创建时机 | 用户发起 inbound message 时，若不存在活跃 session 则创建 |
| 超时机制 | 10 分钟内无任何新消息（user/AI/人工客服），session 自动过期 |
| 唯一标识 | session_id |
| 从属关系 | 一个 conversation 下可有多个 session |

**Session 状态机：**

```
                    用户发送首条消息
                         │
                         ▼
                   ┌────────────┐
                   │ AI_HANDLING │ ◄── 默认初始状态
                   └─────┬──────┘
                         │
              用户发送 "转人工"
                         │
                         ▼
                ┌─────────────────┐
                │ HUMAN_HANDLING  │
                └────────┬────────┘
                         │
               10 分钟无消息 / 手动关闭
                         │
                         ▼
                   ┌──────────┐
                   │  CLOSED  │
                   └──────────┘
```

| 状态 | 说明 |
|------|------|
| `AI_HANDLING` | AI Chatbot 处理中（默认初始状态） |
| `HUMAN_HANDLING` | 人工客服处理中 |
| `CLOSED` | 会话已关闭（超时或手动关闭） |

### 3.3 Message（消息）

| 属性 | 说明 |
|------|------|
| 归属 | 每条消息同时属于一个 conversation 和一个 session |
| 发送方类型 | USER / AI_CHATBOT / HUMAN_AGENT |
| 传输方式 | 所有消息通过 GetStream 进行收发 |

---

## 4. 系统架构

### 4.1 整体架构流程

```
User Web                                                Human Agent Web
   │                                                          ▲
   │ send inbound message                                     │
   ▼                                                          │
┌──────────────────────┐                                      │
│  Global Orchestrator │──── update session status             │
└──────────┬───────────┘                                      │
           │                                                  │
           ▼                                                  │
      ┌──────────┐                                            │
      │  Router  │                                            │
      └────┬─────┘                                            │
           │                                                  │
     ┌─────┴──────┐                                           │
     │ session    │                                           │
     │ status?    │                                           │
     └─────┬──────┘                                           │
     │           │                                            │
HUMAN_HANDLING  AI_HANDLING                                   │
     │           │                                            │
     ▼           ▼                                            │
┌─────────┐ ┌───────────┐                                     │
│ Human   │ │ AI        │                                     │
│ Agent   │ │ Chatbot   │                                     │
│ Service │ │ Service   │                                     │
└────┬────┘ └─────┬─────┘                                     │
     │            │                                           │
     └──────┬─────┘                                           │
            ▼                                                 │
     ┌─────────────┐          receive/send                    │
     │  GetStream  │ ────────────────────────────────────────►│
     └─────────────┘
```

### 4.2 后端服务组件

#### 4.2.1 Global Orchestrator（全局编排器）

**职责：**
- 接收用户 inbound message
- 管理 conversation 生命周期（创建 / 查找已有）
- 管理 session 生命周期（创建 / 延续 / 超时关闭）
- 更新 session 状态
- 调用 Router 进行消息路由

**处理流程：**
1. 收到用户消息
2. 查找或创建 conversation（根据 user_id）
3. 查找当前活跃 session 或创建新 session
4. 更新 session 的 `last_activity_at`
5. 调用 Router 决策
6. 分发消息到对应处理服务

#### 4.2.2 Router（路由模块）

**职责：**
- 根据 session 状态决定消息路由目标

**路由规则（简化版）：**

```
IF session.status == HUMAN_HANDLING:
    → 路由到 Human Agent Service
ELSE IF 用户消息包含 "转人工":
    → 更新 session.status = HUMAN_HANDLING
    → 将人工客服加入 conversation
    → 路由到 Human Agent Service
ELSE:
    → 路由到 AI Chatbot Service
```

**关键规则：**
- 同一 session 中，只要用户未发送"转人工"，消息始终由 AI Chatbot 处理
- 一旦用户发送"转人工"，当前 session 内所有后续消息都路由到人工客服
- 转人工是 session 级别的，新 session 重新从 AI 开始

#### 4.2.3 AI Chatbot Service（AI 客服服务）

**职责：**
- 接收路由过来的用户消息
- 调用 Kimi LLM 处理
- 调用工具完成任务
- 生成回复并通过 GetStream 发送

**Agent 架构（3 个 Sub-Agent）：**

| Sub-Agent | 职责 | 说明 |
|-----------|------|------|
| Intent Recognition Agent | 意图识别 | 分析用户消息，识别用户意图（FAQ 查询 / 数据删除 / 帖子查询 / 闲聊等） |
| Tool Orchestration Agent | 工具编排与调用 | 根据识别的意图和用户输入，编排和调用对应的工具，获取结果 |
| Response Composition Agent | 回复组织优化 | 将工具调用结果组织成用户友好的格式和表述进行回复 |

**处理流程：**
1. 收到用户消息 + session 上下文
2. Intent Recognition Agent 识别用户意图
3. Tool Orchestration Agent 根据意图调用对应工具
4. Response Composition Agent 组织回复内容
5. 通过 GetStream 将回复发送给用户

#### 4.2.4 Human Agent Service（人工客服服务）

**职责：**
- 当 session 转人工时，将人工客服加入 conversation
- 管理人工客服与用户的消息传递
- 支持人工客服调用工具

**简化设计：**
- 系统中只有一名固定的人工客服（即项目开发者本人）
- 转人工后，自动将该人工客服分配到当前 session
- 人工客服通过 Human Agent Web 接收和回复消息

#### 4.2.5 GetStream Integration（消息通信层）

**职责：**
- 封装 GetStream SDK
- 管理 channel 的创建和生命周期
- 用户、AI Chatbot、人工客服的消息收发
- 实时消息推送

**集成方式：**
- 每个 conversation 对应一个 GetStream channel
- User、AI Chatbot、Human Agent 作为 channel 的成员
- AI Chatbot 和 Human Agent 以服务端用户身份发送消息

---

## 5. 工具系统 (Tools)

AI Chatbot 和人工客服共享 3 类工具。每类工具对应后端一组 API 接口。

### 5.1 Tool 1: FAQ 知识库

| 属性 | 说明 |
|------|------|
| 功能 | 根据用户查询的问题，匹配预置的 Question-Answer 对 |
| 实现方式 | 使用轻量级开源 Vector DB |
| 数据来源 | 预先整理好的 FAQ 数据（Q&A 对） |

**工作流程：**
1. 接收用户问题文本
2. 将问题文本向量化
3. 在 Vector DB 中进行相似度检索
4. 返回最匹配的 Q&A 结果

**接口设计：**
```
POST /api/tools/faq/search
Request:  { "query": "用户的问题文本" }
Response: { "question": "匹配到的问题", "answer": "对应的答案", "score": 0.95 }
```

### 5.2 Tool 2: 用户数据删除

| 属性 | 说明 |
|------|------|
| 功能 | 根据用户名删除该用户在系统中的所有数据 |
| 实现方式 | Mock 实现，提供接口但不执行真实删除 |
| 触发条件 | 用户提供用户名并请求删除数据 |

**接口设计：**
```
POST /api/tools/user-data/delete
Request:  { "username": "用户名" }
Response: { "success": true, "message": "用户 xxx 的数据已标记为删除" }
```

### 5.3 Tool 3: 用户帖子状态查询

| 属性 | 说明 |
|------|------|
| 功能 | 查询指定用户的帖子状态 |
| 实现方式 | 预先在 PostgreSQL 中写入 Mock 数据，通过后端接口查询 |
| 数据存储 | 本地 PostgreSQL |

**Mock 数据示例：**

| post_id | username | title | status | created_at |
|---------|----------|-------|--------|------------|
| 1 | user_alice | "如何重置密码" | PUBLISHED | 2025-01-01 |
| 2 | user_alice | "账号被锁定" | UNDER_REVIEW | 2025-01-05 |
| 3 | user_bob | "无法登录" | REMOVED | 2025-01-03 |
| 4 | user_bob | "充值未到账" | PUBLISHED | 2025-01-10 |

**接口设计：**
```
POST /api/tools/posts/query
Request:  { "username": "user_alice" }
Response: {
  "posts": [
    { "post_id": 1, "title": "如何重置密码", "status": "PUBLISHED", "created_at": "..." },
    { "post_id": 2, "title": "账号被锁定", "status": "UNDER_REVIEW", "created_at": "..." }
  ]
}
```

### 5.4 工具调用方式

| 调用方 | 方式 |
|--------|------|
| AI Chatbot | 通过 Tool Orchestration Agent 自动编排和调用 |
| Human Agent | 通过 Human Agent Web 界面上的工具按钮手动调用 |

---

## 6. UI 设计

### 6.1 User Web（用户端）

```
┌──────────────────────────────────────────────────┐
│                                                  │
│   message history of user, aichatbot,            │
│   and im-human-service-agent                     │
│                                                  │
│   ┌──────────────────────────┐                   │
│   │ [User] 我想查一下帖子状态  │                   │
│   └──────────────────────────┘                   │
│                   ┌──────────────────────────┐   │
│                   │ [AI] 请提供您的用户名      │   │
│                   └──────────────────────────┘   │
│   ┌──────────────────────────┐                   │
│   │ [User] user_alice        │                   │
│   └──────────────────────────┘                   │
│                   ┌──────────────────────────┐   │
│                   │ [AI] 您有2个帖子...       │   │
│                   └──────────────────────────┘   │
│                                                  │
├──────────────────────────────────────────────────┤
│  user message box                                │
│                                                  │
│  can edit message here ...                       │
│                                                  │
│                                     ┌──────────┐ │
│                                     │   send   │ │
│                                     └──────────┘ │
└──────────────────────────────────────────────────┘
```

**功能说明：**

| 区域 | 功能 |
|------|------|
| 消息历史区 | 展示当前 conversation 的所有消息（来自 user、AI chatbot、human agent），按时间顺序排列 |
| 消息输入框 | 用户输入文本消息 |
| Send 按钮 | 发送消息 |

**消息展示规则：**
- 用户消息靠左展示
- AI Chatbot / Human Agent 回复靠右展示
- 每条消息标注发送方身份（AI / 人工客服）
- 支持实时接收新消息（通过 GetStream）

### 6.2 Human Agent Web（人工客服端）

```
┌──────────────────────────────────────────────────┐
│                                                  │
│   message history of user, aichatbot,            │
│   and im-human-service-agent                     │
│                                                  │
│   ┌──────────────────────────┐                   │
│   │ [User] 转人工             │                   │
│   └──────────────────────────┘                   │
│                   ┌──────────────────────────┐   │
│                   │ [AI] 正在为您转接人工客服   │   │
│                   └──────────────────────────┘   │
│   ┌──────────────────────────┐                   │
│   │ [User] 我想删除我的数据   │                   │
│   └──────────────────────────┘                   │
│                                                  │
├──────────────────────────────────────────────────┤
│  im-human-service-agent message box              │
│                                                  │
│  can edit message here ...                       │
│                                                  │
│  ┌────────┐ ┌────────┐ ┌────────┐  ┌──────────┐ │
│  │  FAQ   │ │ 删除   │ │ 帖子   │  │   send   │ │
│  │  查询  │ │ 数据   │ │ 查询   │  │          │ │
│  └────────┘ └────────┘ └────────┘  └──────────┘ │
└──────────────────────────────────────────────────┘
```

**功能说明：**

| 区域 | 功能 |
|------|------|
| 消息历史区 | 展示当前 session 的所有消息（user + AI chatbot 历史 + human agent），按时间顺序排列 |
| 消息输入框 | 人工客服输入回复文本 |
| Tool 1: FAQ 查询 | 点击后弹出输入框，输入问题关键词，调用 FAQ 知识库查询，结果展示在工作区 |
| Tool 2: 删除数据 | 点击后弹出输入框，输入用户名，调用用户数据删除接口 |
| Tool 3: 帖子查询 | 点击后弹出输入框，输入用户名，调用帖子状态查询接口，结果展示在工作区 |
| Send 按钮 | 发送消息给用户 |

**与 User Web 的区别：**
- 底部增加 3 个工具按钮
- 工具调用结果仅在人工客服端展示，不直接发送给用户
- 人工客服参考工具结果后，自行组织回复内容发送给用户

### 6.3 AI Chatbot（无 UI）

- 纯后端服务，无前端界面
- 通过 API 接收消息，处理后通过 GetStream 发送回复
- 在 User Web 中以 "AI" 身份展示其回复消息

---

## 7. 核心流程

### 7.1 用户首次发起咨询

```
1. User 在 User Web 发送第一条消息
2. 后端创建 Conversation (conversation_id)
3. 后端创建 Session (session_id, status=AI_HANDLING)
4. Router 判断 session.status == AI_HANDLING → 路由到 AI Chatbot
5. AI Chatbot 处理消息并回复
6. 回复通过 GetStream 推送到 User Web
```

### 7.2 AI Chatbot 处理用户消息

```
1. Intent Recognition Agent 分析用户消息
   - 识别意图：FAQ_QUERY / DATA_DELETE / POST_QUERY / GENERAL_CHAT
2. Tool Orchestration Agent 根据意图执行：
   - FAQ_QUERY → 调用 FAQ 知识库
   - DATA_DELETE → 调用用户数据删除接口
   - POST_QUERY → 调用帖子状态查询接口
   - GENERAL_CHAT → 直接由 LLM 回复
3. Response Composition Agent 组织回复
   - 将工具返回结果转化为用户友好的自然语言回复
4. 回复通过 GetStream 发送给用户
```

### 7.3 用户请求转人工

```
1. User 发送包含 "转人工" 的消息
2. Global Orchestrator 接收消息
3. Router 检测到 "转人工" 关键词
4. 更新 session.status = HUMAN_HANDLING
5. Human Agent Service 将人工客服加入当前 conversation 的 GetStream channel
6. AI Chatbot 发送提示消息："正在为您转接人工客服，请稍候..."
7. Human Agent Web 收到新 session 通知
8. 人工客服开始处理
```

### 7.4 人工客服处理用户消息

```
1. 人工客服在 Human Agent Web 看到用户消息和历史记录
2. 人工客服可点击工具按钮辅助处理：
   - 点击 "FAQ 查询" → 查看知识库
   - 点击 "删除数据" → 执行数据删除
   - 点击 "帖子查询" → 查询帖子状态
3. 人工客服在输入框编辑回复内容
4. 点击 Send 发送
5. 消息通过 GetStream 推送到 User Web
```

### 7.5 Session 超时

```
1. 系统定时检查所有活跃 session 的 last_activity_at
2. 若距最后一条消息超过 10 分钟，标记 session.status = CLOSED
3. 用户再次发送消息时，创建新的 session (status=AI_HANDLING)
4. 新 session 重新从 AI Chatbot 开始处理
```

---

## 8. 数据模型

### 8.1 Conversation 表

```sql
CREATE TABLE conversation (
    conversation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         VARCHAR(255) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / CLOSED
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### 8.2 Session 表

```sql
CREATE TABLE session (
    session_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID         NOT NULL REFERENCES conversation(conversation_id),
    status           VARCHAR(32)  NOT NULL DEFAULT 'AI_HANDLING',  -- AI_HANDLING / HUMAN_HANDLING / CLOSED
    assigned_agent_id VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### 8.3 Message 表

```sql
CREATE TABLE message (
    message_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID         NOT NULL REFERENCES conversation(conversation_id),
    session_id          UUID         NOT NULL REFERENCES session(session_id),
    sender_type         VARCHAR(32)  NOT NULL,  -- USER / AI_CHATBOT / HUMAN_AGENT
    sender_id           VARCHAR(255) NOT NULL,
    content             TEXT         NOT NULL,
    getstream_message_id VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### 8.4 User Post 表 (Mock 数据)

```sql
CREATE TABLE user_post (
    post_id    SERIAL PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    title      VARCHAR(512) NOT NULL,
    status     VARCHAR(32)  NOT NULL,  -- PUBLISHED / UNDER_REVIEW / REMOVED / DRAFT
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### 8.5 FAQ 数据

FAQ 数据存储在 Vector DB 中，每条记录包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | FAQ 条目 ID |
| question | string | 问题文本 |
| answer | string | 答案文本 |
| embedding | vector | 问题文本的向量表示 |

---

## 9. API 设计

### 9.1 消息 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/messages/inbound` | 用户发送消息（入口，触发编排和路由） |
| POST | `/api/messages/agent-reply` | 人工客服发送回复 |
| GET  | `/api/messages/{conversationId}` | 获取 conversation 的消息历史 |

### 9.2 Conversation API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/conversations/{userId}` | 获取用户的 conversation |
| GET  | `/api/conversations/{conversationId}/sessions` | 获取 conversation 下的 session 列表 |

### 9.3 Session API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/sessions/{sessionId}` | 获取 session 详情 |
| GET  | `/api/sessions/active` | 获取人工客服当前待处理的 session 列表 |

### 9.4 Tools API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tools/faq/search` | FAQ 知识库搜索 |
| POST | `/api/tools/user-data/delete` | 用户数据删除 (Mock) |
| POST | `/api/tools/posts/query` | 用户帖子状态查询 |

### 9.5 GetStream API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/stream/token/{userId}` | 获取 GetStream 用户 token（前端连接 GetStream 用） |

---

## 10. AI Chatbot Agent 详细设计

### 10.1 Agent 架构

```
用户消息
    │
    ▼
┌──────────────────────────┐
│  Intent Recognition      │
│  Agent                   │
│  ─────────────────────   │
│  输入: 用户消息 +         │
│       session 上下文      │
│  输出: 识别的意图          │
│       (FAQ_QUERY /       │
│        DATA_DELETE /     │
│        POST_QUERY /      │
│        GENERAL_CHAT)     │
└───────────┬──────────────┘
            │
            ▼
┌──────────────────────────┐
│  Tool Orchestration      │
│  Agent                   │
│  ─────────────────────   │
│  输入: 意图 + 用户消息 +  │
│       session 上下文      │
│  处理: 根据意图调用对应    │
│       工具并获取结果       │
│  输出: 工具调用结果        │
└───────────┬──────────────┘
            │
            ▼
┌──────────────────────────┐
│  Response Composition    │
│  Agent                   │
│  ─────────────────────   │
│  输入: 工具结果 +         │
│       用户原始消息        │
│  处理: 组织成用户友好的    │
│       自然语言回复        │
│  输出: 最终回复文本        │
└───────────┬──────────────┘
            │
            ▼
      通过 GetStream 发送回复
```

### 10.2 意图类型

| 意图 | 触发条件 | 对应工具 |
|------|---------|---------|
| FAQ_QUERY | 用户咨询常见问题 | FAQ 知识库 |
| DATA_DELETE | 用户请求删除个人数据 | 用户数据删除接口 |
| POST_QUERY | 用户查询帖子状态 | 帖子状态查询接口 |
| GENERAL_CHAT | 其他一般性对话 | 无（LLM 直接回复） |

### 10.3 LLM 集成

- 使用 Kimi 模型作为 LLM 引擎
- 3 个 Sub-Agent 分别对应 3 次 LLM 调用（或可优化合并）
- System Prompt 需针对客服场景定制

---

## 11. 非功能性需求

### 11.1 性能

- AI Chatbot 回复延迟：可接受 3-10 秒（受 LLM 调用限制）
- 人工客服消息传递：实时（依赖 GetStream）

### 11.2 可靠性

- 消息不丢失：通过 GetStream 保证消息可靠送达
- Session 状态一致性：通过数据库事务保证

### 11.3 安全性

- API 密钥通过环境变量管理，不硬编码
- 用户数据删除为 Mock 实现，不执行真实删除

### 11.4 可扩展性

- 工具系统可方便添加新工具
- 路由规则可后续扩展为更复杂的策略
- Agent 架构支持增加新的 Sub-Agent

---

## 12. 项目边界与简化

本项目为技术验证性质，以下做出的简化：

| 简化项 | 说明 |
|--------|------|
| 人工客服 | 固定一名，不做客服分配和排队 |
| 转人工触发 | 仅通过用户发送"转人工"关键词触发，不做复杂意图判断 |
| 用户认证 | 简化处理，不实现完整的登录体系 |
| 用户数据删除 | Mock 实现，接口可调通即可 |
| 帖子数据 | Mock 数据预置在 PostgreSQL，不做 CRUD 管理 |
| FAQ 数据 | 预置少量 Q&A 对，验证向量检索能力即可 |
| 消息格式 | 仅支持纯文本消息 |
| 多语言 | 仅支持中文 |

---

## 13. 项目结构（建议）

```
chatbot/
├── CLAUDE.md
├── docs/
│   └── PRD.md
├── backend/                      # Spring Boot 后端
│   ├── src/main/java/
│   │   └── com/chatbot/
│   │       ├── config/           # 配置类
│   │       ├── controller/       # API 控制器
│   │       ├── service/
│   │       │   ├── orchestrator/ # Global Orchestrator
│   │       │   ├── router/       # Router
│   │       │   ├── chatbot/      # AI Chatbot Service
│   │       │   ├── agent/        # Human Agent Service
│   │       │   ├── tools/        # 工具服务 (FAQ/Delete/Post)
│   │       │   └── stream/       # GetStream 集成
│   │       ├── model/            # 数据模型 / Entity
│   │       └── repository/       # 数据访问层
│   └── src/main/resources/
│       ├── application.yml
│       └── db/
│           └── migration/        # 数据库迁移脚本
├── frontend/                     # 前端项目
│   ├── src/
│   │   ├── pages/
│   │   │   ├── user/             # User Web
│   │   │   └── agent/            # Human Agent Web
│   │   ├── components/           # 公共组件
│   │   └── services/             # API 调用
│   └── ...
└── scripts/                      # 辅助脚本
    └── init-mock-data.sql        # Mock 数据初始化
```
