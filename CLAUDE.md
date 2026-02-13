# Chatbot 项目

## 项目概述

本项目旨在构建一个完整的客服系统，包括人工客服 IM 和 AI Chatbot 的集成方案。核心目标是验证 AI Chatbot 的能力，并探索如何将 AI 集成到人工客服系统中。

## 项目组成

### 1. 人工客服 IM 系统
- 基础的客服即时通讯系统
- 使用 GetStream 作为底层服务供应商
- 提供人工客服的基础功能

### 2. AI Chatbot
- 核心验证目标
- 集成 Kimi 模型作为 LLM
- 与人工客服 IM 系统集成

## 技术栈

### 后端
- **语言**: Java
- **框架**: Spring Boot
- **数据库**: PostgreSQL (本地部署)

### 前端
- **技术栈**: 现代化前端技术栈 (待选型，如 React/Vue/Next.js)
- **UI 组件**: (待补充)

### 第三方服务
- **IM 服务**: GetStream
- **LLM 服务**: Kimi 模型

## 系统架构设计

### 核心概念

#### Conversation（对话）
- 每个用户第一次发起 inbound message 时创建一个新的 conversation
- 所有后续的消息（用户、人工客服、AI Chatbot）都属于同一个 conversation
- 每个 conversation 有唯一的 conversation_id
- conversation 贯穿整个用户与客服系统的交互生命周期

#### Session（会话）
- 每次用户发起 inbound message 时，在 conversation 基础上创建或延续 session
- Session 超时机制：10 分钟内有任何消息（user/人工客服/AI chatbot），session_id 保持不变
- 10 分钟内无新消息，则创建新的 session，生成新的 session_id
- Session 用于维护当前交互的状态和上下文

#### Session 状态
- `AI_HANDLING`: AI Chatbot 处理中
- `HUMAN_REQUESTED`: 用户请求转人工
- `HUMAN_HANDLING`: 人工客服处理中
- `CLOSED`: 会话已关闭

### 消息流程

1. **User 发送 inbound message**
   - User Web 发送消息到后端

2. **Global Orchestrator**
   - 接收用户消息
   - 更新或创建 conversation
   - 更新或创建 session
   - 更新 session 状态

3. **Router（路由决策）**
   - 根据 session 状态决定消息路由
   - 判断条件：
     - 如果 session 状态为 `HUMAN_HANDLING` 或 `HUMAN_REQUESTED` → 转人工客服
     - 否则 → 转 AI Chatbot

4. **消息处理**
   - **路由到 AI Chatbot**：
     - 调用 AI Chatbot 服务
     - AI 处理消息并生成回复
     - 回复通过 GetStream 发送给用户

   - **路由到人工客服**：
     - 连接到 Human Agent Service
     - 消息发送到 Human Agent Web
     - 人工客服查看并回复
     - 回复通过 GetStream 发送给用户

5. **GetStream 消息同步**
   - 所有消息（用户、AI、人工客服）通过 GetStream 进行收发
   - 保证消息的实时性和一致性

### 路由规则

#### 初始路由（简化版）
- 同一个 session 中，用户的第一条消息 → AI Chatbot
- 用户触发转人工客服动作后 → 后续所有消息 → 人工客服
- 一旦转人工，同一个 session 内不再回到 AI Chatbot

#### 转人工触发条件
- 用户明确请求人工服务
- AI Chatbot 主动转接（如遇到无法处理的问题）
- Session 状态更新为 `HUMAN_REQUESTED` 或 `HUMAN_HANDLING`

### 系统组件

#### 后端服务组件
1. **API Gateway**
   - 接收前端请求
   - 认证和授权

2. **Global Orchestrator**
   - 核心编排服务
   - 管理 conversation 和 session
   - 更新状态

3. **Router**
   - 消息路由决策
   - 根据 session 状态分发消息

4. **AI Chatbot Service**
   - 集成 Kimi LLM
   - 处理 AI 对话逻辑
   - 生成 AI 回复

5. **Human Agent Service**
   - 管理人工客服连接
   - 处理人工客服相关功能
   - 消息转发和状态管理

6. **GetStream Integration**
   - GetStream SDK 集成
   - 消息收发接口
   - 实时通信管理

#### 前端组件
1. **User Web**
   - 用户聊天界面
   - 发送和接收消息
   - 转人工操作

2. **Human Agent Web**
   - 人工客服工作台
   - 查看和回复用户消息
   - 会话管理

### 数据模型

#### Conversation 表
```sql
conversation_id (UUID, PK)
user_id (VARCHAR)
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
status (VARCHAR) -- ACTIVE, CLOSED
```

#### Session 表
```sql
session_id (UUID, PK)
conversation_id (UUID, FK)
status (VARCHAR) -- AI_HANDLING, HUMAN_REQUESTED, HUMAN_HANDLING, CLOSED
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
last_activity_at (TIMESTAMP)
assigned_agent_id (UUID, NULLABLE)
```

#### Message 表
```sql
message_id (UUID, PK)
conversation_id (UUID, FK)
session_id (UUID, FK)
sender_type (VARCHAR) -- USER, AI, HUMAN_AGENT
sender_id (VARCHAR)
content (TEXT)
created_at (TIMESTAMP)
getstream_message_id (VARCHAR)
```

### 技术优化建议

1. **Session 超时管理**
   - 使用定时任务或消息队列检查 session 超时
   - 考虑使用 Redis 缓存 session 状态，提高查询性能

2. **消息可靠性**
   - 实现消息重试机制
   - 记录消息发送状态（pending, sent, delivered, failed）

3. **并发处理**
   - 考虑同一 conversation 的消息并发处理
   - 使用乐观锁或分布式锁避免状态冲突

4. **监控和日志**
   - 记录路由决策日志
   - 监控 AI 和人工客服的响应时间
   - 跟踪转人工的触发原因

5. **扩展性考虑**
   - 预留人工客服能力路由（根据客服负载分配）
   - 预留更复杂的路由规则（如基于用户画像、问题类型等）

## 部署方式

- **环境**: 本地 MacBook (Darwin 23.6.0)
- **部署方式**: 直接在本机运行各个服务，不使用 Docker
- **数据库**: 本地 PostgreSQL 实例

## 开发规范

### 代码组织
- 后端和前端分离
- 模块化设计
- 清晰的项目结构

### 安全性
- 避免常见漏洞 (XSS, SQL 注入, 命令注入等)
- API 密钥和敏感信息使用环境变量管理

### 简洁原则
- 避免过度工程
- 只实现必要的功能
- 保持代码简单直接

## 项目阶段

### Phase 1: 人工客服 IM 系统
- [ ] 设计系统架构
- [ ] 集成 GetStream
- [ ] 实现基础 IM 功能
- [ ] 前后端开发

### Phase 2: AI Chatbot
- [ ] 集成 Kimi 模型
- [ ] 实现 AI 对话能力
- [ ] 验证 AI Chatbot 功能

### Phase 3: 系统集成
- [ ] AI Chatbot 与人工 IM 集成
- [ ] 人机协作流程设计
- [ ] 完整功能测试

## 环境配置

### 必需服务
- PostgreSQL (本地)
- Java 运行环境
- Node.js (前端开发)

### API 密钥
- GetStream API 密钥
- Kimi API 密钥

## 注意事项

1. 所有服务直接在本机运行，降低部署复杂度
2. 数据库使用本地 PostgreSQL
3. 重点关注 AI 集成的技术验证
4. 保持架构简洁，避免过度设计

## 开发指导

- 先读取现有代码再提出修改建议
- 优先编辑已有文件，避免不必要的新文件创建
- 关注代码安全性
- 保持代码简洁，避免过度抽象
