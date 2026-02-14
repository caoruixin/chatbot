# Chatbot 系统运维与探查指南

## 1. 系统介绍

### 1.1 系统概述

本系统是一个 **AI + 人工客服协作** 的客服系统。用户通过 Web 界面发送消息，系统自动路由给 AI Chatbot 或人工客服处理。核心目标是验证 AI Chatbot 的能力，并探索 AI 与人工客服的集成方案。

系统包含两个独立的 Web 界面：
- **用户聊天页面** (`/chat`)：用户与客服系统交互的入口
- **人工客服工作台** (`/agent`)：人工客服查看和回复用户消息

### 1.2 技术栈

| 层 | 技术 |
|---|------|
| 后端框架 | Java 21 + Spring Boot 3.4.3 |
| 数据库 | PostgreSQL 16+ with pgvector |
| 数据访问 | MyBatis 3.0.4 + Flyway 迁移 |
| 前端框架 | React 19 + TypeScript 5.7 + Vite 6 |
| 样式 | Tailwind CSS 4.x |
| IM 服务 | GetStream Chat (SDK 1.24 后端 / 12.x 前端) |
| LLM 对话 | Kimi (Moonshot AI) moonshot-v1-8k |
| 文本嵌入 | DashScope (阿里云通义千问) text-embedding-v4 |

### 1.3 AI Agent 与 Human Agent 协作模型

```
用户消息
  │
  ▼
┌─────────────────────┐
│  GlobalOrchestrator  │  ← 创建/查找 Conversation & Session
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│    MessageRouter     │  ← 检查转人工关键词 + Session 状态
└──────┬────────┬─────┘
       │        │
  AI_HANDLING   HUMAN_HANDLING
       │        │
       ▼        ▼
┌──────────┐  ┌──────────────────┐
│ AgentCore│  │ HumanAgentService│
│ (AI Bot) │  │   (人工客服)      │
└──────────┘  └──────────────────┘
       │        │
       ▼        ▼
┌─────────────────────┐
│  GetStream Channel   │  ← 实时消息推送给用户
└─────────────────────┘
```

**协作规则**：
1. 用户首次发消息时，Session 默认处于 `AI_HANDLING` 状态，由 AI Chatbot 处理
2. 用户发送**转人工关键词**时，Session 切换为 `HUMAN_HANDLING`，后续消息全部转给人工客服
3. 一旦转人工，**同一 Session 内不再回到 AI Chatbot**
4. Session 超时（10 分钟无活动）后自动关闭；下次用户发消息时创建新 Session，重新从 AI 开始

---

## 2. 环境准备与安装

### 2.1 前置要求

| 软件 | 版本要求 | 验证命令 |
|------|---------|---------|
| JDK | 21+ | `java -version` |
| PostgreSQL | 16+ (需 pgvector 扩展) | `psql --version` |
| Node.js | 22+ | `node -v` |
| npm | 10+ | `npm -v` |

### 2.2 安装 PostgreSQL 和 pgvector

```bash
# macOS (Homebrew)
brew install postgresql@16
brew install pgvector

# 启动 PostgreSQL
brew services start postgresql@16

# 创建数据库
createdb chatbot

# 验证 pgvector 扩展可用
psql chatbot -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 2.3 安装 JDK 21

```bash
# macOS (Homebrew)
brew install openjdk@21

# 验证
java -version
```

### 2.4 安装 Node.js

```bash
# macOS (Homebrew)
brew install node@22

# 验证
node -v && npm -v
```

---

## 3. 配置管理

### 3.1 配置文件结构

系统使用**三层配置**：

```
chatbot/
├── .env.local                                    # 敏感凭证（API Key 等）
├── backend/src/main/resources/application.yml    # 后端应用配置
└── frontend/src/config/env.ts                    # 前端环境变量
```

### 3.2 敏感凭证配置 (`.env.local`)

项目根目录下的 `.env.local` 文件存放所有 API Key 和数据库密码。启动前需确保此文件存在并包含正确的值：

```bash
# ========================
# Database
# ========================
DB_USERNAME=postgres
DB_PASSWORD=postgres

# ========================
# Kimi (Moonshot AI) LLM - 用于对话和意图识别
# ========================
KIMI_API_KEY=<your-kimi-api-key>

# ========================
# GetStream Chat - 用于实时消息
# ========================
GETSTREAM_API_KEY=<your-getstream-api-key>
GETSTREAM_API_SECRET=<your-getstream-api-secret>

# ========================
# Frontend (prefix with VITE_ for Vite to expose)
# ========================
VITE_GETSTREAM_API_KEY=<same-as-GETSTREAM_API_KEY>

# ========================
# DashScope (阿里云) - 用于文本嵌入
# ========================
DASHSCOPE_API_KEY=<your-dashscope-api-key>
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

> **安全提示**：`.env.local` 包含敏感信息，不应提交到版本控制系统。

### 3.3 后端应用配置 (`application.yml`)

关键配置项说明：

```yaml
server:
  port: 8080                          # 后端服务端口

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chatbot   # 数据库连接
    username: ${DB_USERNAME:postgres}                # 从环境变量读取
    password: ${DB_PASSWORD:postgres}

  flyway:
    enabled: true                      # 自动执行数据库迁移
    locations: classpath:db/migration

kimi:
  api-key: ${KIMI_API_KEY}
  base-url: https://api.moonshot.cn/v1
  chat:
    model: moonshot-v1-8k              # Kimi 对话模型
    temperature: 0.7                   # 回复生成温度
    timeout-seconds: 10                # API 超时

dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  base-url: ${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
  embedding:
    model: text-embedding-v4           # 嵌入模型 (1024 维)
    api-url: https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding

chatbot:
  session:
    timeout-minutes: 10                # Session 超时时间
  agent:
    default-id: agent_default          # 默认人工客服 ID
    default-name: 人工客服              # 默认人工客服名称
  router:
    transfer-keywords: 转人工,转接人工,人工客服,人工服务   # 转人工触发关键词
  ai:
    bot-id: ai_bot                     # AI Bot 的用户 ID
    bot-name: AI 助手
    max-react-rounds: 3                # ReAct 循环最大轮数
    confidence-threshold: 0.7          # 意图识别置信度阈值
    faq-score-threshold: 0.75          # FAQ 向量相似度阈值
```

### 3.4 前端配置

前端通过 Vite 环境变量机制读取配置。`VITE_` 前缀的环境变量会被暴露给前端代码：

```typescript
// frontend/src/config/env.ts
export const config = {
  getstreamApiKey: import.meta.env.VITE_GETSTREAM_API_KEY,
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '',
};
```

前端开发服务器通过 Vite 代理将 `/api` 请求转发到后端：

```typescript
// vite.config.ts
server: {
  port: 3000,
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
  },
}
```

---

## 4. 启动与运行

### 4.1 Step 1: 启动 PostgreSQL

```bash
# 确认 PostgreSQL 正在运行
brew services list | grep postgresql

# 如未运行，启动服务
brew services start postgresql@16

# 验证连接
psql chatbot -c "SELECT 1;"
```

### 4.2 Step 2: 加载环境变量

后端启动前需要将 `.env.local` 中的环境变量加载到 shell 中：

```bash
# 在项目根目录
cd /Users/caoruixin/projects/chatbot

# 加载环境变量
export $(grep -v '^#' .env.local | grep -v '^$' | xargs)

# 验证环境变量已加载
echo $KIMI_API_KEY     # 应输出 API Key
echo $DB_USERNAME      # 应输出 postgres
```

### 4.3 Step 3: 启动后端服务

```bash
cd /Users/caoruixin/projects/chatbot/backend

# 构建并启动（首次会自动下载依赖）
./gradlew bootRun
```

**启动过程中关键日志**：

```
# 1. Spring Boot 启动
Started ChatbotApplication in X.XXX seconds

# 2. Flyway 数据库迁移
Successfully applied X migration(s)

# 3. GetStream SDK 初始化
GetStream SDK initialized with API key

# 4. FAQ 嵌入向量初始化
Found N FAQ documents without embeddings, generating...
Embedding generated for faqId=xxx, dimension=1024
FAQ embedding initialization complete: success=N, failed=0
```

后端启动后，API 服务运行在 `http://localhost:8080`。

### 4.4 Step 4: 启动前端服务

打开**新的终端窗口**：

```bash
cd /Users/caoruixin/projects/chatbot/frontend

# 加载环境变量（前端需要 VITE_ 前缀的变量）
export $(grep -v '^#' ../.env.local | grep -v '^$' | xargs)

# 安装依赖（仅首次）
npm install

# 启动开发服务器
npm run dev
```

前端开发服务器运行在 `http://localhost:3000`。

### 4.5 Step 5: 访问系统

| 页面 | URL | 说明 |
|------|-----|------|
| 用户聊天页面 | http://localhost:3000/chat | 用户发送消息的入口 |
| 人工客服工作台 | http://localhost:3000/agent | 人工客服查看和回复用户消息 |

---

## 5. 日志查看

### 5.1 后端日志

后端使用 SLF4J + Logback（Spring Boot 默认），日志输出到控制台（启动 `./gradlew bootRun` 的终端窗口）。

#### 5.1.1 日志级别说明

| 级别 | 含义 |
|------|------|
| `INFO` | 关键业务事件：消息接收、意图识别、工具调用、Session 状态变更 |
| `WARN` | 可恢复异常：LLM 超时重试、工具调用失败但降级处理 |
| `ERROR` | 需关注异常：数据库连接失败、LLM 服务不可用 |
| `DEBUG` | 开发调试：LLM 请求/响应体、SQL 参数 |

#### 5.1.2 关键日志追踪

**追踪一条消息的完整处理流程**：

```bash
# 1. 消息接收 (GlobalOrchestrator)
grep "Handling inbound message" <后端日志>
# 输出: Handling inbound message: userId=user_xxx

# 2. Session 查找/创建 (SessionService)
grep "Found active session\|Created new session" <后端日志>
# 输出: Found active session: sessionId=xxx, conversationId=xxx

# 3. 消息路由 (MessageRouter)
grep "Routing message\|Transfer to human" <后端日志>
# 输出: Routing message: sessionId=xxx, status=AI_HANDLING

# 4. AI 处理 (AgentCore) — 注意线程名以 "ai-agent-" 开头
grep "ai-agent-" <后端日志>

# 5. 意图识别 (IntentRouter)
grep "Intent recognized" <后端日志>
# 输出: Intent recognized: intent=KB_QUESTION, confidence=0.85, risk=low

# 6. 工具调用 (ToolDispatcher)
grep "Tool dispatched" <后端日志>
# 输出: Tool dispatched: tool=faq_search, success=true, duration=123ms

# 7. 消息发送 (GetStreamService)
grep "Message sent via GetStream" <后端日志>
```

#### 5.1.3 AI Agent 线程识别

AI Agent 处理运行在独立线程池上，线程名以 `ai-agent-` 开头（如 `ai-agent-1`, `ai-agent-2`）。在日志中可以通过线程名区分 AI 处理和普通 HTTP 请求处理：

```
# AI Agent 线程的日志
[ai-agent-1] INFO  c.c.s.agent.AgentCore - AI Agent handling message: sessionId=xxx
[ai-agent-1] INFO  c.c.s.agent.IntentRouter - Intent recognized: intent=KB_QUESTION...
[ai-agent-1] INFO  c.c.s.tool.ToolDispatcher - Tool dispatched: tool=faq_search...

# HTTP 请求线程的日志
[http-nio-8080-exec-1] INFO  c.c.s.o.GlobalOrchestrator - Handling inbound message...
```

#### 5.1.4 调整日志级别

临时开启 DEBUG 日志，在 `application.yml` 中添加：

```yaml
logging:
  level:
    com.chatbot: DEBUG
```

### 5.2 前端日志

前端日志通过浏览器开发者工具（F12 → Console）查看。开发模式下可以看到 API 请求和 GetStream 事件。

---

## 6. 数据库探查

### 6.1 连接数据库

```bash
psql chatbot
```

### 6.2 数据库表结构

系统有 5 张表，通过 Flyway 迁移自动创建：

| 表名 | 用途 | 迁移文件 |
|------|------|---------|
| `conversation` | 用户对话（每个用户一个） | V1__init_schema.sql |
| `session` | 会话（对话下的多个会话） | V1__init_schema.sql |
| `message` | 消息记录 | V1__init_schema.sql |
| `user_post` | 用户帖子（Mock 数据） | V1 + V2__mock_data.sql |
| `faq_doc` | FAQ 知识库（含向量嵌入） | V1 + V2__mock_data.sql |

> **注意**：还有 V3__clear_kimi_embeddings.sql 和 V4__clear_v3_embeddings_for_v4_upgrade.sql 两个嵌入向量清理迁移，用于从旧嵌入模型升级到 text-embedding-v4。

### 6.3 常用查询

#### 查看所有对话

```sql
SELECT conversation_id, user_id, status, getstream_channel_id,
       created_at, updated_at
FROM conversation
ORDER BY updated_at DESC;
```

#### 查看某用户的所有 Session

```sql
SELECT s.session_id, s.status, s.assigned_agent_id,
       s.created_at, s.last_activity_at
FROM session s
JOIN conversation c ON s.conversation_id = c.conversation_id
WHERE c.user_id = 'user_xxx'
ORDER BY s.created_at DESC;
```

#### 查看某 Session 的所有消息

```sql
SELECT message_id, sender_type, sender_id,
       content, metadata_json, created_at
FROM message
WHERE session_id = 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
ORDER BY created_at ASC;
```

#### 查看消息流转全貌（对话 → Session → 消息）

```sql
SELECT c.user_id,
       s.session_id,
       s.status AS session_status,
       m.sender_type,
       m.sender_id,
       LEFT(m.content, 50) AS content_preview,
       m.created_at
FROM message m
JOIN session s ON m.session_id = s.session_id
JOIN conversation c ON m.conversation_id = c.conversation_id
WHERE c.user_id = 'user_xxx'
ORDER BY m.created_at ASC;
```

#### 查看 AI → 人工切换记录

```sql
-- 查找所有已转人工的 Session
SELECT s.session_id, s.assigned_agent_id, s.created_at, s.last_activity_at
FROM session s
WHERE s.status = 'HUMAN_HANDLING'
ORDER BY s.last_activity_at DESC;

-- 查看转人工前后的消息（包含 AI 的系统转接提示）
SELECT sender_type, sender_id, content, created_at
FROM message
WHERE session_id = 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
ORDER BY created_at ASC;
```

#### 查看待确认的数据删除操作

```sql
-- metadata_json 中包含 pendingConfirmation 的 AI 消息
SELECT message_id, content, metadata_json, created_at
FROM message
WHERE sender_type = 'AI_CHATBOT'
  AND metadata_json LIKE '%pendingConfirmation%'
ORDER BY created_at DESC;
```

#### 查看 FAQ 知识库和嵌入状态

```sql
-- 查看所有 FAQ 及嵌入状态
SELECT faq_id, question,
       LEFT(answer, 40) AS answer_preview,
       CASE WHEN embedding IS NOT NULL THEN 'YES' ELSE 'NO' END AS has_embedding
FROM faq_doc;
```

#### 查看用户帖子 Mock 数据

```sql
SELECT post_id, username, title, status, created_at
FROM user_post
ORDER BY username, created_at;
```

#### 查看 Session 超时关闭情况

```sql
-- 已关闭的 Session
SELECT session_id, status, created_at, last_activity_at,
       EXTRACT(EPOCH FROM (last_activity_at - created_at)) / 60 AS duration_minutes
FROM session
WHERE status = 'CLOSED'
ORDER BY last_activity_at DESC;
```

---

## 7. API 接口一览

### 7.1 消息接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/messages/inbound` | 用户发送消息（核心入口） |
| `POST` | `/api/messages/agent-reply` | 人工客服回复消息 |
| `GET` | `/api/messages?conversationId=&sessionId=` | 查询消息列表 |

#### 发送用户消息示例

```bash
curl -X POST http://localhost:8080/api/messages/inbound \
  -H "Content-Type: application/json" \
  -d '{"userId": "user_test", "content": "你好"}'
```

响应：

```json
{
  "success": true,
  "data": {
    "conversationId": "uuid...",
    "sessionId": "uuid...",
    "messageId": "uuid..."
  }
}
```

### 7.2 其他接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/stream/token?userId=` | 获取 GetStream 用户 Token |
| `GET` | `/api/conversations?userId=` | 查询用户的对话 |
| `GET` | `/api/conversations/{id}/sessions` | 查询对话下的所有 Session |
| `GET` | `/api/sessions/{id}` | 查询 Session 详情 |
| `GET` | `/api/sessions/active?agentId=` | 查询分配给该客服的所有 Session |
| `POST` | `/api/tools/posts/query` | 手动调用帖子查询工具 |
| `POST` | `/api/tools/user-data/delete` | 手动调用数据删除工具 |
| `POST` | `/api/tools/faq/search` | 手动调用 FAQ 搜索工具 |

---

## 8. AI Agent 详细架构

### 8.1 处理流程

当消息路由到 AI Agent 时，`AgentCore.handleMessage()` 在独立线程池 (`ai-agent-*`) 上异步执行以下步骤：

```
用户消息
  │
  ▼
┌─────────────────────────────┐
│ 1. 构建会话历史              │  ← 从 DB 加载当前 Session 的历史消息
│    buildSessionHistory()     │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ 2. 检查待确认操作            │  ← 查找上一条 AI 消息中的 pendingConfirmation
│    checkPendingConfirmation()│     如果用户回复了确认词 → 直接执行工具
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ 3. 意图识别 (IntentRouter)   │  ← 调用 Kimi LLM，temperature=0.1
│    识别为以下之一:            │
│    - POST_QUERY (查帖子)     │
│    - DATA_DELETION (删数据)  │
│    - KB_QUESTION (知识库问答) │
│    - GENERAL_CHAT (闲聊)     │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ 4. 置信度检查                │  ← confidence < 0.7 → 要求用户澄清
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ 5. ReAct 循环 (最多 3 轮)    │
│    ReactPlanner → ToolCall   │
│    ToolDispatcher → ToolResult│
│    成功/无需工具 → 退出循环   │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ 6. 回复生成 (ResponseComposer)│
│    critical 风险 → 模板回复   │
│    low 风险 → LLM 生成回复   │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ 7. 发送回复                  │  ← 保存到 DB + 发送到 GetStream
│    sendReply()               │
└─────────────────────────────┘
```

### 8.2 工具系统

AI Agent 拥有 3 个工具：

| 工具名 | 功能 | 风险等级 | 触发意图 |
|--------|------|---------|---------|
| `faq_search` | 搜索 FAQ 知识库 | READ (低风险) | KB_QUESTION |
| `post_query` | 查询用户帖子状态 | READ (低风险) | POST_QUERY |
| `user_data_delete` | 删除用户数据 | IRREVERSIBLE (不可逆) | DATA_DELETION |

工具调用通过 `ToolDispatcher` 统一调度：
1. **参数校验** — 检查必需参数是否存在
2. **风险检查** — IRREVERSIBLE 工具需要用户二次确认
3. **执行** — 通过 `ToolExecutor` 接口执行

### 8.3 FAQ 语义搜索

FAQ 搜索使用**双层策略**：
1. **主路径**：DashScope text-embedding-v4 生成查询向量 → pgvector 余弦相似度搜索 → 返回最匹配的 FAQ
2. **降级路径**：如果嵌入 API 不可用，使用 Kimi 对话模型直接从 FAQ 列表中匹配

启动时，`FaqEmbeddingInitializer` 自动为没有嵌入向量的 FAQ 文档生成嵌入。

### 8.4 数据删除的二次确认流程

```
用户: "我想删除我的数据，用户名是bob"
  │
  ▼ 意图识别: DATA_DELETION, username=bob
  │
  ▼ ReactPlanner → ToolCall(user_data_delete, {username: bob}, confirmed=false)
  │
  ▼ ToolDispatcher → 风险检查: IRREVERSIBLE 且未确认 → needsConfirmation
  │
  ▼ ResponseComposer 模板回复:
    "您确定要删除用户 bob 的所有数据吗？此操作不可逆。请回复"确认删除"以继续。"
  │
  ▼ AI 消息保存时附带 metadata_json:
    {"pendingConfirmation": true, "toolName": "user_data_delete", "toolParams": {"username": "bob"}}
  │
用户: "确认删除"
  │
  ▼ checkPendingConfirmation() → 匹配确认关键词 + 找到 pendingConfirmation 元数据
  │
  ▼ ToolCall(user_data_delete, {username: bob}, confirmed=true)
  │
  ▼ ToolDispatcher → 执行删除
  │
  ▼ "您的数据删除请求已提交，预计 24 小时内处理完毕。"
```

确认关键词包括：`确认删除`, `确认`, `确定`, `是`, `是的`, `好的`, `好`, `同意`

---

## 9. AI → 人工客服切换规则详解

### 9.1 切换触发条件

AI 到人工客服的切换**仅通过用户主动触发**，触发条件如下：

#### 转人工关键词

用户消息**包含**以下任一关键词时，立即触发转人工：

| 关键词 | 配置位置 |
|--------|---------|
| `转人工` | `chatbot.router.transfer-keywords` |
| `转接人工` | 同上 |
| `人工客服` | 同上 |
| `人工服务` | 同上 |

**代码逻辑** (`MessageRouter.java:54`)：
```java
if (containsTransferKeyword(message.getContent())) {
    sessionService.updateStatus(session.getSessionId(), SessionStatus.HUMAN_HANDLING);
    humanAgentService.assignAgent(session);
    // 发送系统提示: "正在为您转接人工客服，请稍候..."
}
```

关键词检测使用**包含匹配**（`content.contains(keyword)`），因此消息中任何位置出现关键词都会触发。

### 9.2 切换过程

1. `MessageRouter` 检测到转人工关键词
2. Session 状态更新为 `HUMAN_HANDLING`
3. `HumanAgentService.assignAgent()` 执行：
   - 在 DB 中设置 `assigned_agent_id = agent_default`
   - 在 GetStream 中 upsert 人工客服用户
   - 将人工客服添加到 GetStream Channel 的成员列表
4. AI Bot 发送系统消息：`"正在为您转接人工客服，请稍候..."`
5. 后续该 Session 的所有消息直接路由到 `HumanAgentService.forwardMessage()`（实际上是 no-op，因为消息已在 GetStream Channel 中，人工客服通过 WebSocket 实时看到）

### 9.3 切换后的行为

- **同一 Session 内**：一旦 status 变为 `HUMAN_HANDLING`，所有后续消息都路由给人工客服，**不会回退到 AI**
- **Session 超时后**：定时任务每 60 秒检查一次，10 分钟无活动的 Session 自动关闭（status → `CLOSED`）
- **新 Session**：用户下次发消息时创建新 Session，status 默认为 `AI_HANDLING`，重新从 AI 开始

### 9.4 Session 状态机

```
                 创建
                  │
                  ▼
            ┌───────────┐
            │ AI_HANDLING│ ◄── 新 Session 的默认状态
            └─────┬─────┘
                  │ 用户发送转人工关键词
                  ▼
          ┌───────────────┐
          │ HUMAN_HANDLING │ ◄── 不可逆（同一 Session 内）
          └───────┬───────┘
                  │ 10 分钟无活动（定时任务）
                  ▼
            ┌──────────┐
            │  CLOSED  │
            └──────────┘

       AI_HANDLING ──(超时)──► CLOSED   也可直接超时关闭
```

### 9.5 路由优先级

`MessageRouter.route()` 的判断顺序：

1. **最高优先级**：检查转人工关键词 → 如果匹配，立即转人工，不进入 AI
2. **次优先级**：检查 Session 状态
   - `HUMAN_HANDLING` → `HumanAgentService.forwardMessage()`
   - `AI_HANDLING` → `AgentCore.handleMessage()`

这意味着即使 Session 处于 `AI_HANDLING`，只要用户消息包含转人工关键词，也会立即切换。

---

## 10. 端到端消息处理流程示例

### 场景 1: 用户提问 FAQ

```
用户发送: "怎么重置密码"

1. [http-nio-8080-exec-1] GlobalOrchestrator: 找到/创建 Conversation + Session
2. [http-nio-8080-exec-1] MessageRouter: status=AI_HANDLING → 路由到 AgentCore
3. API 立即返回 {conversationId, sessionId, messageId}  ← 用户不需要等待 AI 处理
4. [ai-agent-1] AgentCore: 开始异步处理
5. [ai-agent-1] IntentRouter: intent=KB_QUESTION, confidence=0.85, risk=low
6. [ai-agent-1] ReactPlanner: KB_QUESTION → faq_search(query="怎么重置密码")
7. [ai-agent-1] ToolDispatcher: 执行 FaqService
   - DashScope embedding API → 生成查询向量
   - pgvector 余弦相似度搜索 → 匹配到 "如何重置密码？"
8. [ai-agent-1] ResponseComposer: LLM 生成友好回复
9. [ai-agent-1] sendReply: 保存到 DB + GetStream 推送
10. 用户在聊天页面实时收到 AI 回复
```

### 场景 2: 用户转人工

```
用户发送: "转人工"

1. [http-nio-8080-exec-2] GlobalOrchestrator: 找到 Conversation + 活跃 Session
2. [http-nio-8080-exec-2] MessageRouter:
   - containsTransferKeyword("转人工") → true
   - Session status → HUMAN_HANDLING
   - HumanAgentService.assignAgent() → DB 更新 + GetStream 添加成员
   - AI Bot 发送 "正在为您转接人工客服，请稍候..."
3. 后续消息全部路由到 HumanAgentService
4. 人工客服在 /agent 页面看到用户消息并回复
```

### 场景 3: 数据删除（带二次确认）

```
用户发送: "我想删除我的所有数据，我的用户名是bob"

1. IntentRouter: intent=DATA_DELETION, confidence=0.9, risk=critical
2. ReactPlanner: → user_data_delete({username: "bob"}, confirmed=false)
3. ToolDispatcher: IRREVERSIBLE + 未确认 → needsConfirmation
4. ResponseComposer (模板): "您确定要删除用户 bob 的所有数据吗？..."
5. AI 消息保存，metadata_json 含 pendingConfirmation

用户发送: "确认"

6. checkPendingConfirmation() → 匹配 "确认" + 找到元数据
7. ToolDispatcher: 执行 user_data_delete(confirmed=true)
8. sendReply: "您的数据删除请求已提交，预计 24 小时内处理完毕。"
```

---

## 11. 故障排查

### 11.1 后端启动失败

| 症状 | 可能原因 | 解决方案 |
|------|---------|---------|
| `Connection refused: localhost:5432` | PostgreSQL 未启动 | `brew services start postgresql@16` |
| `database "chatbot" does not exist` | 数据库未创建 | `createdb chatbot` |
| `KIMI_API_KEY not set` | 环境变量未加载 | `export $(grep -v '^#' .env.local \| grep -v '^$' \| xargs)` |
| `Could not resolve type: vector` | pgvector 未安装 | `brew install pgvector && psql chatbot -c "CREATE EXTENSION vector;"` |

### 11.2 AI Agent 无响应

1. 检查后端日志中是否有 `ai-agent-*` 线程的输出
2. 检查 Kimi API Key 是否有效：`grep "Kimi\|kimi\|LLM" <日志>`
3. 检查 DashScope API Key：`grep "DashScope\|embedding" <日志>`
4. 确认 FAQ 嵌入初始化是否成功：`grep "FAQ embedding" <日志>`

### 11.3 消息未实时到达

1. 检查 GetStream 初始化日志：`grep "GetStream" <日志>`
2. 确认 GetStream API Key 和 Secret 是否正确
3. 浏览器开发者工具检查 WebSocket 连接状态

---

## 12. 项目目录结构

```
chatbot/
├── .env.local                      # 敏感凭证配置
├── CLAUDE.md                       # 项目总览指引
├── docs/
│   ├── system-guide.md             # 本文档
│   ├── tech-design-spec.md         # 技术设计规格
│   ├── backup-tech-design-spec.md  # 技术设计规格备份
│   ├── ai-service-agent.md         # AI Agent 设计文档
│   └── PRD.md                      # 产品需求文档
├── backend/
│   ├── build.gradle                # Gradle 构建配置
│   ├── CLAUDE.md                   # 后端开发指南
│   └── src/main/
│       ├── java/com/chatbot/
│       │   ├── config/             # 配置类 (KimiConfig, EmbeddingConfig, AsyncConfig, UUIDTypeHandler, ...)
│       │   ├── controller/         # REST API 控制器
│       │   ├── dto/                # 请求/响应 DTO
│       │   ├── enums/              # 枚举 (SessionStatus, SenderType, RiskLevel)
│       │   ├── exception/          # 自定义异常 + 全局异常处理
│       │   ├── mapper/             # MyBatis Mapper 接口
│       │   ├── model/              # 数据模型 POJO
│       │   ├── scheduler/          # 定时任务 (SessionTimeoutScheduler)
│       │   └── service/
│       │       ├── orchestrator/   # GlobalOrchestrator (核心编排)
│       │       ├── router/         # MessageRouter (消息路由)
│       │       ├── agent/          # AI Agent (AgentCore, IntentRouter, IntentResult, ReactPlanner, ResponseComposer)
│       │       ├── human/          # HumanAgentService (人工客服)
│       │       ├── tool/           # 工具系统 (ToolDispatcher, FaqService, FaqEmbeddingInitializer, ...)
│       │       ├── llm/            # KimiClient (LLM 调用)
│       │       └── stream/         # GetStreamService (IM 服务)
│       └── resources/
│           ├── application.yml     # 应用配置
│           ├── db/migration/       # Flyway 迁移脚本 (V1~V4)
│           └── mapper/             # MyBatis XML 映射
└── frontend/
    ├── package.json                # Node.js 依赖
    ├── vite.config.ts              # Vite 配置 (代理, 端口)
    ├── CLAUDE.md                   # 前端开发指南
    └── src/
        ├── App.tsx                 # 路由配置 (/chat, /agent)
        ├── main.tsx                # 入口文件
        ├── index.css               # Tailwind CSS 入口
        ├── pages/                  # 页面组件 (UserChatPage, AgentDashboardPage)
        ├── components/
        │   ├── chat/               # 消息展示 (MessageList, MessageBubble, MessageInput, TypingIndicator)
        │   ├── agent/              # 人工客服 (SessionList, ToolPanel)
        │   ├── user/               # 用户端 (UserToolPanel)
        │   └── common/             # 通用 (Layout)
        ├── services/               # API 客户端 (apiClient, streamClient)
        ├── hooks/                  # 自定义 Hooks (useChat, useSession, useAgentChat, useTools)
        ├── types/                  # TypeScript 类型
        └── config/env.ts           # 环境变量
```
