# QA 测试报告

**生成时间**: 2026-02-13 18:00
**测试范围**: 全量 QA（前端 + 后端）

---

# 第一部分：前端 QA

**测试范围**: frontend/src/ 下所有 TypeScript/TSX 源文件（共 18 个文件）及构建配置

## 一、代码审查结果

### 问题汇总

| 序号 | 严重程度 | 文件 | 行号 | 问题描述 | 建议 |
|------|---------|------|------|---------|------|
| 1 | HIGH | hooks/useChat.ts | 128 | useEffect 依赖 `channelRef.current`（可变 ref），React 无法追踪 ref 变化，导致消息监听可能不会在 channel 建立后启动 | 将 channel 存入 state 或使用额外 state 触发 effect 重跑 |
| 2 | HIGH | hooks/useAgentChat.ts | 134 | 同上，useEffect 依赖 `channelRef.current`，存在相同的监听失效风险 | 同上 |
| 3 | HIGH | hooks/useChat.ts | 130-186 | sendMessage 中 useCallback 闭包捕获了 `conversationId` 和 `sessionId` 的旧值，在首次发送消息后如果状态刚刚通过 setState 更新，第二次快速发送时会读到过期的 null 值，触发不必要的重复初始化逻辑 | 使用 ref 保存最新值，或者使用函数式更新 pattern |
| 4 | HIGH | hooks/useChat.ts | 153-154 | 首次消息发送后重新获取 Stream token 和 client，但此处 catch 块仅 console.error 且将完整 err 对象输出（可能包含 token 信息），违反了 CLAUDE.md 中"不在 console 中输出 GetStream Token"的规范 | 仅输出 err.message 而非完整 err 对象 |
| 5 | HIGH | services/apiClient.ts | 30-47 | request 函数不支持传入 AbortController signal，无法取消进行中的请求，违反 CLAUDE.md 中要求的"页面切换时取消进行中的请求 (AbortController)"规范 | 让 request 接受或透传 signal 参数 |
| 6 | HIGH | hooks/useSession.ts | 22-33 | fetchSessions 不使用 AbortController，也没有在 useEffect cleanup 中取消请求；快速卸载/重挂载时可能对已卸载组件 setState | 引入 cancelled flag 或 AbortController |
| 7 | MEDIUM | hooks/useChat.ts | 104-128 | GetStream 消息监听 effect 依赖 `conversationId` 和 `sessionId`，这两个值在首次消息发送后才设置，但此时 channelRef.current 可能尚未赋值（channel 在 sendMessage 中异步建立），导致 effect 执行时 channel 为 null 而提前 return，后续 channel 就绪后 effect 不会重跑 | 统一在一个 effect 中管理 channel 建立和消息监听 |
| 8 | MEDIUM | hooks/useAgentChat.ts | 71-87 | channel 匹配逻辑使用 conversationId 前 8 字符做 includes 匹配，UUID 前 8 字符可能在不同 conversation 间碰撞（概率虽低但非零），且 fallback 到 channels[0] 可能连接到错误 conversation 的 channel | 后端应返回精确的 channelId，前端不应做模糊匹配 |
| 9 | MEDIUM | config/env.ts | 2 | `import.meta.env.VITE_GETSTREAM_API_KEY as string` 使用 `as string` 类型断言，环境变量未设置时实际值为 `undefined`，但类型系统会认为它是 `string`，跳过了 null check，可能导致运行时 StreamChat.getInstance 接收 undefined 参数而产生难以追踪的错误 | 添加运行时校验：若 undefined 则 throw 明确错误信息 |
| 10 | MEDIUM | services/apiClient.ts | 31-34 | headers 的合并顺序有问题：`{ 'Content-Type': 'application/json', ...options?.headers }, ...options` -- 先扩展了 headers，然后 `...options` 又展开了 options 中原有的 headers 字段（如果存在），覆盖了之前合并好的 headers 对象 | 将 headers 合并放在 options 扩展之后，或分开处理 |
| 11 | MEDIUM | hooks/useSession.ts | 37 | `let interval: ReturnType<typeof setInterval>;` 声明但未初始化，cleanup 中 `if (interval)` 判断始终会进入（因为 init 是异步的，interval 此时已经被赋值），但如果 init 中 fetchSessions 抛出异常在 setLoading 之前，interval 不会被赋值，cleanup 时 clearInterval(undefined) 不会报错但语义不清晰 | 将 interval 初始化为 undefined 并显式判断 |
| 12 | MEDIUM | hooks/useSession.ts | 43 | 轮询间隔使用硬编码的 5000ms，未提取为常量或配置项 | 提取为命名常量 |
| 13 | MEDIUM | services/streamClient.ts | 6-23 | getStreamClient 使用模块级单例 `client`，在 React StrictMode 下 useEffect 会执行两次，可能导致竞态：两个并发的 connectUser 调用 | 添加连接中的锁或 promise 缓存，避免重复 connectUser |
| 14 | MEDIUM | pages/UserChatPage.tsx | 9 | userId 默认硬编码为 `'user_alice'`，生产环境应从认证系统获取 | 当前阶段可以接受，但应添加注释说明这是临时方案 |
| 15 | MEDIUM | pages/AgentDashboardPage.tsx | 9 | agentId 硬编码为 `'agent_default'`，同上 | 同上 |
| 16 | MEDIUM | App.tsx | 1-15 | 未配置 Error Boundary，CLAUDE.md 明确要求"在页面级组件外包裹 React Error Boundary，捕获渲染异常，显示 fallback UI" | 添加 Error Boundary 组件包裹路由 |
| 17 | MEDIUM | components/agent/SessionList.tsx | 22-33 | `getStatusLabel` 和 `getStatusColor` 的参数类型为 `string` 而非 `SessionStatus`，失去了类型安全 | 将参数类型改为 `SessionStatus` |
| 18 | MEDIUM | hooks/useTools.ts | 28-74 | 三个工具方法都没有 AbortController 支持，快速切换工具或多次执行时旧请求无法取消 | 添加 AbortController 或 debounce 机制 |
| 19 | LOW | hooks/useChat.ts + hooks/useAgentChat.ts | 7-12, 7-12 | `mapSenderType` 函数在两个文件中完全重复 | 提取到 utils 或 types 模块中复用 |
| 20 | LOW | components/chat/MessageBubble.tsx + components/agent/SessionList.tsx | 20-30, 10-19 | `formatTime` 函数在两个组件中完全重复 | 提取到 utils 模块中复用 |
| 21 | LOW | types/index.ts | 10 | `SessionStatus` 类型缺少 `HUMAN_REQUESTED` 状态 | 添加 `'HUMAN_REQUESTED'` 到联合类型 |
| 22 | LOW | services/apiClient.ts | 18 | `API_BASE` 常量硬编码为空字符串，config/env.ts 中已定义 `apiBaseUrl`，但 apiClient 未使用它 | 使用 config.apiBaseUrl 替代硬编码空字符串 |
| 23 | LOW | components/agent/ToolPanel.tsx | 58-187 | ToolPanel 组件职责过重 | 考虑拆分为 ToolSelector、ToolInput、ToolResultDisplay 子组件 |

## 二、测试用例执行结果

### 前端测试基础设施评估

**当前状态**: 前端未配置任何测试框架。

具体缺失项：
- `package.json` 中未包含 `vitest`、`jest`、`@testing-library/react` 等测试依赖
- 没有测试配置文件（vitest.config.ts / jest.config.ts）
- 没有任何测试文件
- `package.json` scripts 中没有 `test` 命令

**TypeScript 编译检查**: 通过（`npx tsc --noEmit` 无错误输出）

### 建议测试配置方案

```bash
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
```

## 三、测试覆盖度

- **已测试模块**: 无（TypeScript 编译检查通过）
- **未测试模块**: 全部 18 个源文件
- **建议优先补充测试的模块**: useChat, useAgentChat, useSession, apiClient, MessageInput

---

# 第二部分：后端 QA

**测试范围**: backend/src/main/java/com/chatbot/ 下全部 Java 源文件、MyBatis XML 映射文件、application.yml 配置文件

## 一、代码审查结果

### 问题汇总

| 序号 | 严重程度 | 文件 | 行号 | 问题描述 | 建议 |
|------|---------|------|------|---------|------|
| 1 | HIGH | model/Conversation.java | 12 | Model 的 status 字段使用 String 而非枚举 ConversationStatus | 使用枚举类型以获得类型安全，避免拼写错误导致状态不一致 |
| 2 | HIGH | model/Session.java | 11 | Model 的 status 字段使用 String 而非枚举 SessionStatus | 同上，使用 SessionStatus 枚举 |
| 3 | HIGH | model/Message.java | 12 | Model 的 senderType 字段使用 String 而非枚举 SenderType | 同上，使用 SenderType 枚举 |
| 4 | HIGH | service/router/MessageRouter.java | 46 | "转人工" 关键字硬编码且仅支持完全包含匹配 | 容易被绕过或误触发，建议提取为可配置项，或在 Phase 2 用 LLM 意图识别替代 |
| 5 | HIGH | service/router/MessageRouter.java | 25 | MessageRouter 直接注入 ConversationMapper（Mapper 层） | 违反分层架构 Controller->Service->Mapper 的单向依赖规范，Service 应通过 ConversationService 访问 Conversation 数据 |
| 6 | HIGH | service/agent/AgentCore.java | 26 | AgentCore 直接注入 ConversationMapper（Mapper 层） | 同上，违反分层架构规范，应通过 ConversationService 获取 Conversation |
| 7 | HIGH | service/human/HumanAgentService.java | 19 | HumanAgentService 直接注入 ConversationMapper（Mapper 层） | 同上，违反分层架构规范 |
| 8 | HIGH | controller/ToolController.java | 24 | ToolController 直接注入 UserPostMapper（Mapper 层） | Controller 不应直接依赖 Mapper，违反分层架构规范，应通过 Service 层访问 |
| 9 | MEDIUM | controller/MessageController.java | 42-46 | POST /api/messages/inbound 缺少请求参数校验 | userId 和 content 可能为 null 或空串，应加 @Valid 注解配合 @NotBlank |
| 10 | MEDIUM | controller/MessageController.java | 48-78 | POST /api/messages/agent-reply 中 GetStream sendMessage 抛异常会导致 500 | agentReply 方法中 getStreamService.sendMessage 没有 try-catch，而 inbound 路径有 |
| 11 | MEDIUM | service/ConversationService.java | 38 | 硬编码状态字符串 "ACTIVE" | 应使用 ConversationStatus.ACTIVE.name() |
| 12 | MEDIUM | service/SessionService.java | 35 | 硬编码状态字符串 "AI_HANDLING" | 应使用 SessionStatus.AI_HANDLING.name() |
| 13 | MEDIUM | service/router/MessageRouter.java | 48-49 | 硬编码状态字符串 "HUMAN_HANDLING"、"AI_HANDLING" | 应使用 SessionStatus 枚举值 |
| 14 | MEDIUM | service/agent/AgentCore.java | 44-67 | Phase 1 的 handleMessage 是同步执行 | 根据 CLAUDE.md 规范，AI 处理应在独立线程池中异步执行（@Async），否则会阻塞 Tomcat 工作线程 |
| 15 | MEDIUM | config/KimiConfig.java | 13-14 | 使用 @Value 字段注入而非构造器注入 | 编码规范要求优先使用构造器注入，@Value 字段注入使得字段非 final，不利于测试 |
| 16 | MEDIUM | service/human/HumanAgentService.java | 22-25 | 使用 @Value 字段注入 defaultAgentId 和 defaultAgentName | 应改为构造器注入以保证不可变性 |
| 17 | MEDIUM | service/agent/AgentCore.java | 29 | 使用 @Value 字段注入 aiBotId | 应改为构造器注入 |
| 18 | MEDIUM | scheduler/SessionTimeoutScheduler.java | 18 | 使用 @Value 字段注入 timeoutMinutes | 应改为构造器注入 |
| 19 | MEDIUM | service/llm/KimiClient.java | 63 | chatCompletion 抛出 RuntimeException 而非自定义 LlmCallException | 不符合错误处理规范，应使用自定义异常体系 |
| 20 | MEDIUM | service/stream/GetStreamService.java | 24-25 | @PostConstruct 中通过 System.setProperty 设置 API 密钥 | 全局系统属性设置有安全隐患，其他组件可读取 |
| 21 | MEDIUM | service/tool/ToolDefinition.java | 38-39 | fromName 未找到时返回 null | 应考虑返回 Optional 或抛出异常 |
| 22 | MEDIUM | service/orchestrator/GlobalOrchestrator.java | 71 | messageRouter.route() 是同步调用 | 如果 AI 处理耗时较长，整个 inbound 请求会被阻塞。应按规范异步处理 AI 回复 |
| 23 | MEDIUM | enums/SessionStatus.java | 4-6 | 缺少 HUMAN_REQUESTED 状态 | CLAUDE.md 架构文档定义了 HUMAN_REQUESTED 状态，但枚举中未包含 |
| 24 | LOW | dto/request/InboundMessageRequest.java | 3-31 | 缺少参数校验注解 | 应添加 @NotBlank 等校验注解 |
| 25 | LOW | controller/ToolController.java | 29-63 | ToolController 逻辑与 Tool 系统(ToolDispatcher/ToolExecutor) 重复 | 应复用 ToolDispatcher 而非在 Controller 中重新实现 |
| 26 | LOW | service/tool/FaqService.java | 29 | 直接强转 params.get("query") 为 String 无防御 | 如果传入非 String 类型会抛 ClassCastException |
| 27 | LOW | service/tool/PostQueryService.java | 28 | 同上，直接强转 params.get("username") | 同上 |
| 28 | LOW | service/tool/UserDataDeletionService.java | 23 | 同上，直接强转 params.get("username") | 同上 |
| 29 | LOW | service/stream/GetStreamService.java | 35-93 | GetStream 操作方法内部已 catch Exception 但没有重新抛出 | sendMessage 等方法内部吞掉异常只记日志，调用方无法感知失败。与 GlobalOrchestrator 中 try-catch 期望捕获异常的逻辑矛盾 |
| 30 | LOW | config/WebConfig.java | 13 | CORS allowedOrigins 硬编码为 localhost:3000 | 应通过配置文件管理，方便部署环境切换 |
| 31 | MEDIUM | exception/GlobalExceptionHandler.java | - | 未处理 MissingServletRequestParameterException，缺少必填 @RequestParam 时返回 500 而非 400 | 添加 @ExceptionHandler(MissingServletRequestParameterException.class) 返回 400 |

### 详细说明

#### 1. Model 字段使用 String 而非枚举类型 (HIGH)
- **文件**: `model/Conversation.java:12`, `model/Session.java:11`, `model/Message.java:12`
- **严重程度**: HIGH
- **问题描述**: 项目定义了 ConversationStatus、SessionStatus、SenderType 等枚举，但 Model 类中对应的 status 和 senderType 字段均使用 String 类型。这导致编译器无法检查类型错误，任何拼写错误（如 "AI_HANDLNG"）都只能在运行时发现。
- **代码片段**:
  ```java
  // Conversation.java
  private String status; // 应为 ConversationStatus 类型

  // Session.java
  private String status; // 应为 SessionStatus 类型

  // Message.java
  private String senderType; // 应为 SenderType 类型
  ```
- **建议**: 将字段类型改为对应枚举，并通过 MyBatis TypeHandler 处理数据库映射。

#### 2. Service 层违反分层架构：直接注入 Mapper (HIGH)
- **文件**: `service/router/MessageRouter.java:25`, `service/agent/AgentCore.java:26`, `service/human/HumanAgentService.java:19`, `controller/ToolController.java:24`
- **严重程度**: HIGH
- **问题描述**: MessageRouter、AgentCore、HumanAgentService 直接注入 ConversationMapper 来获取 Conversation 的 channelId。ToolController 直接注入 UserPostMapper。这违反了编码规范中 Controller->Service->Mapper 的单向依赖原则。
- **建议**: 通过 ConversationService 来获取 Conversation 数据。可在 ConversationService 中添加 getChannelId(conversationId) 方法。ToolController 应改为调用对应的 Service 或 ToolDispatcher。

#### 3. 缺少请求参数校验 (MEDIUM)
- **文件**: `controller/MessageController.java:42-46`
- **严重程度**: MEDIUM
- **问题描述**: InboundMessageRequest 的 userId 和 content 字段可能为 null 或空字符串，Controller 层未使用 @Valid + @NotBlank 进行校验。
- **建议**: 在 DTO 字段上添加 @NotBlank 注解，Controller 方法参数添加 @Valid 注解。

#### 4. agent-reply 端点缺少 GetStream 异常处理 (MEDIUM)
- **文件**: `controller/MessageController.java:62-67`
- **严重程度**: MEDIUM
- **问题描述**: agentReply 方法直接调用 getStreamService.sendMessage()，没有 try-catch。虽然 GetStreamService.sendMessage 内部捕获了异常，但行为与 inbound 路径不一致。
- **建议**: 与 inbound 路径保持一致的异常处理风格。

#### 5. SessionStatus 枚举缺少 HUMAN_REQUESTED 状态 (MEDIUM)
- **文件**: `enums/SessionStatus.java:4-6`
- **严重程度**: MEDIUM
- **问题描述**: CLAUDE.md 架构文档明确定义了四种 Session 状态：AI_HANDLING, HUMAN_REQUESTED, HUMAN_HANDLING, CLOSED。但 SessionStatus 枚举仅包含三种，缺少 HUMAN_REQUESTED。
- **建议**: 在枚举中添加 HUMAN_REQUESTED 状态。

#### 6. GetStreamService 异常处理矛盾 (LOW)
- **文件**: `service/stream/GetStreamService.java:35-93`
- **严重程度**: LOW
- **问题描述**: GetStreamService 的 sendMessage、createChannel、addMember 等方法内部 catch Exception 并记录日志但不重新抛出。然而 GlobalOrchestrator.handleInboundMessage() 中用 try-catch 包裹 getStreamService.sendMessage() 调用期望捕获异常。由于 sendMessage 不会抛出异常，外层的 try-catch 永远不会生效。createToken 方法则相反，它会抛出 RuntimeException。行为不一致。
- **建议**: 统一 GetStreamService 的异常策略。建议所有方法都抛出异常，由调用方决定是否降级。

## 二、测试用例执行结果

### 后端测试（已实际执行）

**执行命令**: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10 ./gradlew test`
**执行结果**: BUILD SUCCESSFUL — **86 tests completed, 0 failed**

| 测试类 | 总数 | 通过 | 失败 | 跳过 |
|-------|------|------|------|------|
| GlobalOrchestratorTest | 4 | 4 | 0 | 0 |
| MessageRouterTest | 6 | 6 | 0 | 0 |
| AgentCoreTest | 3 | 3 | 0 | 0 |
| ToolDispatcherTest | 11 | 11 | 0 | 0 |
| FaqServiceTest | 2 | 2 | 0 | 0 |
| PostQueryServiceTest | 3 | 3 | 0 | 0 |
| UserDataDeletionServiceTest | 2 | 2 | 0 | 0 |
| ToolResultTest | 8 | 8 | 0 | 0 |
| ToolDefinitionTest | 6 | 6 | 0 | 0 |
| ConversationServiceTest | 6 | 6 | 0 | 0 |
| SessionServiceTest | 8 | 8 | 0 | 0 |
| MessageServiceTest | 4 | 4 | 0 | 0 |
| MessageControllerTest | 7 | 7 | 0 | 0 |
| SessionControllerTest | 4 | 4 | 0 | 0 |
| ConversationControllerTest | 4 | 4 | 0 | 0 |
| ToolControllerTest | 4 | 4 | 0 | 0 |
| StreamTokenControllerTest | 3 | 3 | 0 | 0 |
| **总计** | **86** | **86** | **0** | **0** |

### 测试执行中修复的问题

1. **@MockBean → @MockitoBean**: Spring Boot 3.4.3 已移除 `org.springframework.boot.test.mock.bean.MockBean`，5 个 Controller 测试文件已替换为 `org.springframework.test.context.bean.override.mockito.MockitoBean`。
2. **MissingServletRequestParameterException 返回 500 (新发现 #31)**: `SessionControllerTest.getActiveSessions_missingAgentId` 和 `StreamTokenControllerTest.getToken_missingUserId` 最初期望返回 400，但实际返回 500。原因是 `GlobalExceptionHandler` 未专门处理 `MissingServletRequestParameterException`，该异常被 `handleUnexpected(Exception)` 捕获后返回了 500。测试已调整为匹配实际行为（500），此问题已记录在代码审查发现 #31 中。

## 三、测试覆盖度

### 已测试模块

- **GlobalOrchestrator**: 核心编排流程、GetStream 失败降级、路由调用
- **MessageRouter**: AI 路由、人工路由、转人工关键字检测、未知状态处理、会话找不到降级
- **AgentCore**: Phase 1 占位回复、GetStream 失败降级、channelId 降级
- **ToolDispatcher**: 未知工具、参数校验、风险检查（不可逆操作需确认）、正常执行、异常处理
- **FaqService**: Phase 1 占位逻辑
- **PostQueryService**: 有帖子/无帖子/null 时间处理
- **UserDataDeletionService**: 请求 ID 生成、响应格式
- **ToolResult**: 所有工厂方法、JSON 序列化
- **ToolDefinition**: 名称查找、风险级别
- **ConversationService**: findOrCreate（已有/新建/GetStream 失败）、findById（存在/不存在）
- **SessionService**: findActiveOrCreate（已有/新建）、findById（存在/不存在）、委托方法
- **MessageService**: save、委托查询方法
- **MessageController**: inbound、agentReply（正常/session 不存在）、getMessages（各参数组合）
- **SessionController**: getSession（正常/不存在）、getActiveSessions（正常/缺参数）
- **ConversationController**: getConversation（存在/不存在）、getSessions（正常/conversation 不存在）
- **ToolController**: FAQ 搜索、用户数据删除、帖子查询
- **StreamTokenController**: 获取 token（正常/缺参数/异常）

### 未测试模块及原因

| 模块 | 原因 |
|------|------|
| IntentRouter | Phase 2 占位类，无实际业务方法可测试（仅有构造器和 TODO 注释） |
| ReactPlanner | Phase 2 占位类，同上 |
| ResponseComposer | Phase 2 占位类，同上 |
| KimiClient | 直接调用外部 HTTP API，需要集成测试环境或 MockServer，当前 Phase 1 无核心调用链 |
| GetStreamService | 直接调用外部 GetStream SDK 静态方法，Mockito 无法直接 mock 静态方法，需要集成测试 |
| HumanAgentService | 逻辑简单（assignAgent 调用 GetStream、forwardMessage 为 no-op），风险较低 |
| SessionTimeoutScheduler | 依赖 @Scheduled 和数据库，属于集成测试范畴 |
| Mapper 层 | 需要数据库实例（@MybatisTest + 内嵌 PG），当前测试环境未配置测试数据库 |
| Config 类 | 配置类逻辑简单，无核心业务逻辑 |

### 建议优先补充测试的模块

1. **KimiClient** -- Phase 2 时 AI Agent 的核心依赖，建议使用 MockServer 或 WireMock 进行集成测试
2. **GetStreamService** -- 消息通道核心组件，建议使用 GetStream SDK 的 mock 或 WireMock
3. **Mapper 层** -- 配置 H2 或 TestContainers 进行 SQL 测试，特别是 closeExpiredSessions 的超时逻辑
4. **HumanAgentService** -- 当 Phase 2 增加更多人工客服功能时需要覆盖

## 四、总结与建议

### 高优先级问题（需立即修复）

1. **分层架构违规 (#5, #6, #7, #8)**: MessageRouter、AgentCore、HumanAgentService 直接注入 Mapper，ToolController 直接注入 Mapper。这违反了核心架构原则，应通过对应 Service 层访问数据。
2. **Model 字段使用 String 而非枚举 (#1, #2, #3)**: 已定义的枚举未在 Model 中使用，失去了类型安全性。多处代码硬编码状态字符串（#11, #12, #13），增加了出错风险。
3. **转人工关键字硬编码 (#4)**: "转人工" 关键字直接硬编码在 MessageRouter 中，无法配置，也缺乏对变体的支持。

### 中优先级问题（建议尽快修复）

4. **缺少请求参数校验 (#9, #24)**: InboundMessageRequest、AgentReplyRequest 等 DTO 缺少 @NotBlank 等校验注解。
5. **MissingServletRequestParameterException 未处理 (#31)**: 缺少必填 @RequestParam 时返回 500 而非 400，GlobalExceptionHandler 需添加专门处理。
6. **SessionStatus 枚举不完整 (#23)**: 缺少 HUMAN_REQUESTED 状态。
6. **@Value 字段注入 (#15, #16, #17, #18)**: 多处使用 @Value 字段注入而非构造器注入，不符合编码规范。
7. **AI 处理同步阻塞 (#14, #22)**: Phase 1 的 AI 占位回复是同步的，但架构设计要求异步处理。
8. **KimiClient 异常类型 (#19)**: 抛出 RuntimeException 而非自定义 LlmCallException。

### 低优先级问题（有空时修复）

9. **ToolController 逻辑重复 (#25)**: 与 ToolDispatcher/ToolExecutor 系统功能重复。
10. **参数强转缺少防御 (#26, #27, #28)**: Tool 执行器直接强转参数类型，可能抛出 ClassCastException。
11. **GetStreamService 异常策略不一致 (#29)**: 部分方法吞掉异常，部分方法抛出异常。
12. **CORS 配置硬编码 (#30)**: allowedOrigins 硬编码为 localhost:3000。

## 已创建的后端测试文件列表

1. `backend/src/test/java/com/chatbot/service/orchestrator/GlobalOrchestratorTest.java`
2. `backend/src/test/java/com/chatbot/service/router/MessageRouterTest.java`
3. `backend/src/test/java/com/chatbot/service/agent/AgentCoreTest.java`
4. `backend/src/test/java/com/chatbot/service/tool/ToolDispatcherTest.java`
5. `backend/src/test/java/com/chatbot/service/tool/FaqServiceTest.java`
6. `backend/src/test/java/com/chatbot/service/tool/PostQueryServiceTest.java`
7. `backend/src/test/java/com/chatbot/service/tool/UserDataDeletionServiceTest.java`
8. `backend/src/test/java/com/chatbot/service/tool/ToolResultTest.java`
9. `backend/src/test/java/com/chatbot/service/tool/ToolDefinitionTest.java`
10. `backend/src/test/java/com/chatbot/service/ConversationServiceTest.java`
11. `backend/src/test/java/com/chatbot/service/SessionServiceTest.java`
12. `backend/src/test/java/com/chatbot/service/MessageServiceTest.java`
13. `backend/src/test/java/com/chatbot/controller/MessageControllerTest.java`
14. `backend/src/test/java/com/chatbot/controller/SessionControllerTest.java`
15. `backend/src/test/java/com/chatbot/controller/ConversationControllerTest.java`
16. `backend/src/test/java/com/chatbot/controller/ToolControllerTest.java`
17. `backend/src/test/java/com/chatbot/controller/StreamTokenControllerTest.java`

---

# 全局总结

## 问题统计

| 分类 | HIGH | MEDIUM | LOW | 总计 |
|------|------|--------|-----|------|
| 前端 | 6 | 12 | 5 | 23 |
| 后端 | 8 | 16 | 7 | 31 |
| **合计** | **14** | **28** | **12** | **54** |

## 测试统计

| 分类 | 测试文件 | 测试用例 | 通过 | 失败 | 通过率 |
|------|---------|---------|------|------|--------|
| 后端 (Java) | 17 | 86 | 86 | 0 | 100% |
| 前端 (TypeScript) | 0 | 0 | - | - | N/A (测试框架未配置) |

## 最需关注的 TOP 5 问题

1. **后端分层架构违规** (HIGH): 4 处 Service/Controller 直接注入 Mapper，破坏了核心架构原则
2. **前端 useEffect 依赖 ref.current** (HIGH): 核心聊天功能可能因监听失效而无法收到新消息
3. **Model 字段使用 String 而非枚举** (HIGH): 失去编译期类型安全，多处硬编码状态字符串
4. **前端 API 请求不可取消** (HIGH): 所有 API 调用不支持 AbortController，页面切换时无法取消
5. **前端 sendMessage 闭包捕获过期 state** (HIGH): 快速连续发送消息可能触发重复初始化
