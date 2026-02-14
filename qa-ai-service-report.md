# AI Chatbot Service (AI Service Agent) QA 测试报告

**生成时间**: 2026-02-13
**测试范围**: AI Chatbot Service 全部核心组件的代码审查、单元测试验证、设计规范合规性检查
**对标文档**: `docs/ai-service-agent.md` (Production Architecture Spec v1)、`backend/CLAUDE.md` (开发规范)

---

## 一、测试概述

本次 QA 聚焦于 AI Chatbot Service（AI Service Agent）相关的全部功能模块，包括：

| 模块 | 文件路径 | 职责 |
|------|---------|------|
| **AgentCore** | `service/agent/AgentCore.java` | AI Agent 主流程编排 |
| **IntentRouter** | `service/agent/IntentRouter.java` | 意图识别 (Kimi LLM) |
| **ReactPlanner** | `service/agent/ReactPlanner.java` | 工具调用规划 |
| **ResponseComposer** | `service/agent/ResponseComposer.java` | 回复生成 (模板/LLM) |
| **IntentResult** | `service/agent/IntentResult.java` | 意图识别结果模型 |
| **KimiClient** | `service/llm/KimiClient.java` | Kimi LLM API 客户端 |
| **KimiConfig** | `config/KimiConfig.java` | Kimi 配置 |
| **ToolDispatcher** | `service/tool/ToolDispatcher.java` | 工具分发与防火墙 |
| **ToolDefinition** | `service/tool/ToolDefinition.java` | 工具注册表 |
| **FaqService** | `service/tool/FaqService.java` | FAQ 搜索工具 |
| **PostQueryService** | `service/tool/PostQueryService.java` | 帖子查询工具 |
| **UserDataDeletionService** | `service/tool/UserDataDeletionService.java` | 用户数据删除工具 |
| **MessageRouter** | `service/router/MessageRouter.java` | 消息路由 (AI/人工) |
| **GlobalOrchestrator** | `service/orchestrator/GlobalOrchestrator.java` | 全局消息编排 |

---

## 二、单元测试执行结果

**执行命令**: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10 ./gradlew test`
**执行结果**: BUILD SUCCESSFUL

### AI Service 相关测试用例汇总

| 测试类 | 测试数 | 通过 | 失败 | 测试范围 |
|-------|--------|------|------|---------|
| AgentCoreTest | 5 | 5 | 0 | AI Agent 主流程 |
| ToolDispatcherTest | 11 | 11 | 0 | 工具分发与校验 |
| FaqServiceTest | 5 | 5 | 0 | FAQ 搜索工具 |
| PostQueryServiceTest | 3 | 3 | 0 | 帖子查询工具 |
| UserDataDeletionServiceTest | 2 | 2 | 0 | 数据删除工具 |
| ToolResultTest | 8 | 8 | 0 | 工具结果模型 |
| ToolDefinitionTest | 6 | 6 | 0 | 工具定义枚举 |
| MessageRouterTest | 6 | 6 | 0 | 消息路由 |
| GlobalOrchestratorTest | 4 | 4 | 0 | 全局编排 |
| **AI 相关小计** | **50** | **50** | **0** | - |

### AgentCoreTest 详细用例

| 用例名 | 状态 | 测试路径 |
|-------|------|---------|
| `handleMessage_generalChat_savesAndSendsReply` | PASS | GENERAL_CHAT 意图 → 无工具调用 → LLM 生成回复 → 保存 + 发送 |
| `handleMessage_lowConfidence_sendsClarification` | PASS | 低置信度 (0.3) → 发送澄清提示 |
| `handleMessage_getStreamFails_doesNotThrow` | PASS | GetStream 发送失败 → 不抛异常、优雅降级 |
| `handleMessage_postQueryWithTool_callsToolDispatcher` | PASS | POST_QUERY 意图 → 调用 ToolDispatcher → LLM 格式化回复 |
| `handleMessage_dataDeletion_usesTemplateResponse` | PASS | DATA_DELETION (critical) → 模板回复、不调用 LLM 生成 |

---

## 三、代码审查发现

### 问题汇总

| 序号 | 严重程度 | 组件 | 问题 | 影响 |
|------|---------|------|------|------|
| 1 | **CRITICAL** | AgentCore + IntentRouter | 用户消息在 LLM 上下文中重复出现 | LLM 收到双份用户消息，影响意图识别准确性 |
| 2 | **CRITICAL** | AgentCore + ReactPlanner | DATA_DELETION 确认流程不完整 | 用户无法完成数据删除操作 |
| 3 | **HIGH** | IntentRouter / ReactPlanner / ResponseComposer | 三个核心 AI 组件缺少独立单元测试 | 无法验证各组件独立行为的正确性 |
| 4 | **HIGH** | ReactPlanner | 注入了 KimiClient 但未使用 | 冗余依赖，违反 YAGNI 原则 |
| 5 | **HIGH** | KimiClient | 异常类型不符合规范 | 抛出 RuntimeException 而非 LlmCallException |
| 6 | **HIGH** | KimiClient | 错误处理策略不一致 | chatCompletion 抛异常，embedding 返回空数组 |
| 7 | **HIGH** | AgentCore | @Value 字段注入而非构造器注入 | 违反 backend/CLAUDE.md 编码规范 |
| 8 | **MEDIUM** | IntentRouter | 缺少输入长度校验 | 超长消息直接发送给 LLM，可能导致 token 超限 |
| 9 | **MEDIUM** | ResponseComposer | 工具结果伪装为 assistant 消息 | 可能混淆 LLM 的对话上下文理解 |
| 10 | **MEDIUM** | KimiClient | 缺少 HTTP 429 限流处理 | 无法应对 Kimi API 限流 |
| 11 | **MEDIUM** | KimiClient | 缺少重试机制 | 临时网络错误直接失败 |
| 12 | **MEDIUM** | FaqService | @Value 字段注入 faqScoreThreshold | 违反构造器注入规范 |
| 13 | **MEDIUM** | ToolDispatcher | 参数校验不检查空字符串 | `username=""` 可以通过校验 |
| 14 | **MEDIUM** | AgentCore | ReAct 循环耗尽后无专门处理 | maxReactRounds 次全部失败时静默使用最后结果 |
| 15 | **LOW** | FaqService / PostQueryService / UserDataDeletionService | 参数直接强转无防御 | 非 String 类型参数会抛 ClassCastException |
| 16 | **LOW** | IntentResult | 使用 String 而非枚举表示 intent 和 risk | 失去编译期类型安全 |
| 17 | **LOW** | ToolResult.toJson() | data 为 null 时替换为空字符串 | Map.of() 不允许 null 值，但此处已处理 |
| 18 | **LOW** | KimiConfig | 所有字段使用 @Value 注入 | 应使用 @ConfigurationProperties 或构造器注入 |

### 详细说明

#### BUG-1: 用户消息在 LLM 上下文中重复 (CRITICAL)

**文件**: `AgentCore.java:79` + `IntentRouter.java:75-76`

**问题描述**:
- `GlobalOrchestrator.handleInboundMessage()` 在路由前已将用户消息保存到数据库 (line 51-57)
- `AgentCore.buildSessionHistory()` 从数据库获取 session 所有消息，**包括刚保存的当前用户消息**
- `IntentRouter.recognize()` 又将 `userMessage` 作为新的 `KimiMessage("user", userMessage)` 追加到消息列表

**结果**: Kimi LLM 收到的消息序列中，当前用户消息出现**两次**：
```
[...历史消息..., user: "查询alice的帖子", user: "查询alice的帖子"]
```

**影响**: 可能影响意图识别的准确性，增加不必要的 token 消耗。

**修复建议**: 在 `buildSessionHistory()` 中排除当前消息（按 messageId 过滤），或在 `IntentRouter.recognize()` 中不再重复添加 userMessage。

---

#### BUG-2: DATA_DELETION 确认流程不完整 (CRITICAL)

**文件**: `ReactPlanner.java:91` + `ToolDispatcher.java:46-49` + `AgentCore.java:111-114`

**问题描述**:
1. `ReactPlanner.planDataDeletion()` 始终创建 `ToolCall("user_data_delete", params, false)` — `userConfirmed` 永远为 `false`
2. `ToolDispatcher` 检测到 IRREVERSIBLE 工具且 `userConfirmed=false` 时返回 `ToolResult.needsConfirmation()`
3. `AgentCore` 收到 `needsConfirmation` 后 break 退出 ReAct 循环
4. `ResponseComposer.composeFromTemplate()` 返回确认提示："您确定要删除用户 X 的所有数据吗？请回复"确认删除"以继续。"

**但是**：当用户回复"确认删除"时：
- 新消息进入 `AgentCore.handleMessage()`
- `IntentRouter` 可能将"确认删除"识别为 `GENERAL_CHAT` 或低置信度
- **没有任何机制将 `userConfirmed` 设为 `true`**
- 用户永远无法完成数据删除操作

**修复建议**: 需要实现确认状态管理：
- 方案 A：在 Session 级别存储待确认的操作（pending_confirmation），下一条消息时检查是否为确认回复
- 方案 B：在 IntentRouter 中识别"确认删除"关键字，并结合 session 上下文恢复确认流程

---

#### BUG-3: IntentRouter / ReactPlanner / ResponseComposer 缺少独立测试 (HIGH)

**问题描述**: 三个核心 AI Agent 组件没有独立的单元测试文件。仅在 `AgentCoreTest` 中通过 mock 间接测试，无法验证：

| 组件 | 缺少的测试场景 |
|------|--------------|
| **IntentRouter** | JSON 解析各种格式、markdown 代码围栏剥离、未知 intent 回退、空响应处理、LLM 异常回退、confidence 边界值 |
| **ReactPlanner** | 各 intent 的工具规划、缺少参数时返回 null、previousResult 成功时停止规划、KB_QUESTION 参数构建 |
| **ResponseComposer** | DATA_DELETION 各状态的模板输出、evidence-based 响应生成、LLM 失败回退、空 toolResult 处理 |

**影响**: 无法保证各组件在各种输入条件下的行为正确性。

---

#### BUG-4: ReactPlanner 注入了未使用的 KimiClient (HIGH)

**文件**: `ReactPlanner.java:26-29`

```java
private final KimiClient kimiClient;

public ReactPlanner(KimiClient kimiClient) {
    this.kimiClient = kimiClient;
}
```

`kimiClient` 在整个类中从未被使用。当前实现完全基于 intent 和参数进行规则化的工具规划，不需要 LLM 调用。

**影响**: 违反 YAGNI 原则，创建不必要的依赖关系。

---

#### BUG-5: KimiClient 异常类型不符合规范 (HIGH)

**文件**: `KimiClient.java:63`

```java
throw new RuntimeException("Kimi API call failed", e);
```

**规范要求** (backend/CLAUDE.md): 应使用自定义 `LlmCallException` 而非 `RuntimeException`。

**影响**: 调用方无法区分 LLM 调用异常与其他运行时异常，无法实现针对性的错误处理策略。

---

#### BUG-6: KimiClient 错误处理策略不一致 (HIGH)

**文件**: `KimiClient.java`

| 方法 | 错误行为 |
|------|---------|
| `chatCompletion()` (line 63) | 抛出 `RuntimeException` |
| `embedding()` null body (line 93) | 返回 `new float[0]`（不抛异常） |
| `embedding()` empty data (line 99) | 返回 `new float[0]`（不抛异常） |
| `embedding()` 网络异常 (line 116) | 抛出 `RuntimeException` |

**影响**: 调用方（如 FaqService）需要处理两种不同的错误模式（异常 vs 空返回），增加了代码复杂度和出错风险。

---

#### BUG-7: AgentCore @Value 字段注入 (HIGH)

**文件**: `AgentCore.java:40-47`

```java
@Value("${chatbot.ai.bot-id}")
private String aiBotId;

@Value("${chatbot.ai.max-react-rounds:3}")
private int maxReactRounds;

@Value("${chatbot.ai.confidence-threshold:0.7}")
private double confidenceThreshold;
```

**规范要求** (backend/CLAUDE.md): "优先使用构造器注入而非 @Autowired 字段注入，保证依赖不可变且易于测试。"

**影响**: 字段非 final，测试中必须使用 `ReflectionTestUtils.setField()`（AgentCoreTest:65-67 已验证此问题）。

---

#### BUG-8: 缺少输入长度校验 (MEDIUM)

**文件**: `IntentRouter.java:64-91`

用户消息不经任何长度校验直接发送给 Kimi LLM。如果用户发送超长消息（如粘贴大段文本），可能：
- 超出 `moonshot-v1-8k` 的 8K token 限制
- 增加 LLM 调用延迟和成本
- 导致 API 调用失败

**修复建议**: 对 `userMessage` 进行截断（如前 1000 字符用于意图识别）。

---

#### BUG-9: 工具结果伪装为 assistant 消息 (MEDIUM)

**文件**: `ResponseComposer.java:95-96`

```java
messages.add(new KimiMessage("assistant",
    "我查询了系统，以下是查询结果：\n" + toolContext + "\n请根据以上结果回复用户。"));
```

将工具查询结果作为 `assistant` 角色的消息加入对话历史，可能让 LLM 误以为这是它之前的回复而非系统提供的上下文信息。

**修复建议**: 使用 `system` 角色注入工具结果，或在 system prompt 中明确说明上下文来源。

---

## 四、设计规范合规性检查

对照 `docs/ai-service-agent.md` Production Architecture Spec v1：

| 规范要求 | 实现状态 | 说明 |
|---------|---------|------|
| **意图识别 + 置信度 + 风险等级** | PASS | IntentRouter 返回 intent/confidence/risk |
| **ReAct Loop 有边界** | PASS | maxReactRounds=3 限制循环次数 |
| **Response Composer 模板渲染** | PASS | critical 风险使用模板，低风险使用 LLM |
| **Tool Registry (schema + 风险标签)** | PASS | ToolDefinition 枚举 + RiskLevel |
| **Dispatcher 防火墙 (权限 + schema + 执行)** | PARTIAL | 有 schema 校验和风险检查，**缺少 RBAC 权限校验** |
| **Evidence-first (工具输出/KB 片段)** | PARTIAL | 低风险回复基于工具结果，但**未强制引用来源** |
| **高风险模板化 (LLM 不生成承诺文本)** | PASS | DATA_DELETION 使用固定模板 |
| **写操作 idempotency_key** | PARTIAL | UserDataDeletionService 生成 requestId，但**未强制幂等** |
| **不可逆操作二次确认** | FAIL | ToolDispatcher 要求确认，但**确认流程无法完成** (BUG-2) |
| **低置信度处理** | PARTIAL | 低置信度发送澄清问题，但**未限制最多 1 次澄清** |
| **工具 schema 校验失败重试** | PASS | schemaError 标记为 retryable，ReAct 循环重试 |
| **工具超时/熔断** | FAIL | KimiClient 有超时配置但**无熔断器**，RestTemplate 无重试 |
| **PII 检测与脱敏** | FAIL | **未实现** |
| **Prompt Injection 防护** | FAIL | **未实现**输入检测或结构化分隔 |
| **RBAC/ACL 数据隔离** | FAIL | **未实现** |
| **结构化审计日志 (不记 CoT)** | PARTIAL | 有结构化日志 (intent/confidence/tool/duration)，但**未写入独立审计库** |
| **SLO: p95 < 2.5s (工具直查)** | UNKNOWN | 未进行性能测试 |
| **SLO: p95 < 4s (RAG 路径)** | UNKNOWN | 未进行性能测试 |
| **Session Store (Redis)** | FAIL | Session 状态存储在 PostgreSQL，**未使用 Redis** |
| **RAG: Hybrid Retrieval (Vector + BM25)** | PARTIAL | FaqService 仅实现 Vector 搜索，**缺少 BM25** |
| **RAG: Reranker** | FAIL | **未实现** |

### 合规性总结

| 分类 | PASS | PARTIAL | FAIL | 总计 |
|------|------|---------|------|------|
| 核心架构 | 4 | 2 | 1 | 7 |
| 安全护栏 | 1 | 0 | 3 | 4 |
| 降级策略 | 2 | 1 | 1 | 4 |
| 知识引擎 | 0 | 1 | 2 | 3 |
| 可观测性 | 0 | 1 | 0 | 1 |
| **合计** | **7** | **5** | **7** | **19** |

---

## 五、测试覆盖度分析

### 已覆盖的 AI Service 测试场景

| 场景分类 | 测试内容 | 覆盖状态 |
|---------|---------|---------|
| **Happy Path** | GENERAL_CHAT → LLM 回复 | COVERED |
| **Happy Path** | POST_QUERY → 工具调用 → LLM 格式化 | COVERED |
| **Happy Path** | DATA_DELETION → 模板回复 | COVERED |
| **降级** | 低置信度 → 澄清提示 | COVERED |
| **降级** | GetStream 发送失败 → 不抛异常 | COVERED |
| **工具系统** | 未知工具 → 错误 | COVERED |
| **工具系统** | 参数缺失 → schema 错误 | COVERED |
| **工具系统** | IRREVERSIBLE 工具未确认 → 需确认 | COVERED |
| **工具系统** | FAQ 搜索 → embedding → pgvector | COVERED |
| **工具系统** | 帖子查询 → 返回帖子列表 | COVERED |
| **工具系统** | 数据删除 → 生成 requestId | COVERED |
| **路由** | AI_HANDLING → AgentCore | COVERED |
| **路由** | HUMAN_HANDLING → HumanAgentService | COVERED |
| **路由** | 转人工关键字 → 状态更新 + 分配客服 | COVERED |

### 未覆盖的 AI Service 测试场景

| 场景分类 | 缺失测试 | 优先级 |
|---------|---------|--------|
| **IntentRouter** | JSON 解析：标准 JSON | P0 |
| **IntentRouter** | JSON 解析：带 markdown 代码围栏 | P0 |
| **IntentRouter** | JSON 解析：畸形 JSON → 回退 GENERAL_CHAT | P0 |
| **IntentRouter** | 空响应 → 回退 GENERAL_CHAT | P0 |
| **IntentRouter** | LLM 调用异常 → 回退 GENERAL_CHAT | P0 |
| **IntentRouter** | 未知 intent 值 → 回退 GENERAL_CHAT | P1 |
| **IntentRouter** | confidence 边界值 (0.0, 1.0) | P1 |
| **ReactPlanner** | POST_QUERY 缺少 username → 返回 null | P0 |
| **ReactPlanner** | DATA_DELETION 缺少 username → 返回 null | P0 |
| **ReactPlanner** | previousResult 成功 → 停止规划 | P0 |
| **ReactPlanner** | GENERAL_CHAT → 返回 null | P1 |
| **ReactPlanner** | KB_QUESTION → faq_search 工具调用 | P1 |
| **ResponseComposer** | DATA_DELETION + needsConfirmation → 确认提示 | P0 |
| **ResponseComposer** | DATA_DELETION + success → 成功模板 | P0 |
| **ResponseComposer** | DATA_DELETION + error → 失败模板 | P0 |
| **ResponseComposer** | POST_QUERY + toolResult → LLM 格式化 | P1 |
| **ResponseComposer** | LLM 回复为空 → fallback | P1 |
| **ResponseComposer** | LLM 调用异常 → fallback | P1 |
| **AgentCore** | ReAct 循环重试 (retryable error) | P0 |
| **AgentCore** | ReAct 循环达到 maxRounds 上限 | P1 |
| **AgentCore** | KB_QUESTION 完整路径 | P1 |
| **AgentCore** | handleMessage 异常 → fallback 回复 | P1 |
| **KimiClient** | chatCompletion 成功 | P0 |
| **KimiClient** | chatCompletion 超时 | P0 |
| **KimiClient** | embedding 成功 | P1 |
| **KimiClient** | embedding 返回空数据 | P1 |

---

## 六、性能与并发分析

### 异步处理

| 检查项 | 状态 | 说明 |
|-------|------|------|
| AI 处理异步执行 | PASS | `@Async("aiTaskExecutor")` + 独立线程池 |
| 线程池配置合理 | PASS | core=5, max=20, queue=50 |
| API 请求不阻塞 | PASS | `POST /api/messages/inbound` 立即返回 |
| AsyncConfig 启用 | PASS | `@EnableAsync` 已配置 |

### 潜在性能问题

| 问题 | 严重程度 | 说明 |
|------|---------|------|
| 每次 AI 处理都查询完整 session 历史 | MEDIUM | `buildSessionHistory()` 每次调用 `findBySessionId`，长对话时查询量大 |
| 无 LLM 响应缓存 | LOW | 相同意图重复查询时每次都调 LLM |
| 无并发消息排序保证 | MEDIUM | 同一 session 快速发送多条消息时，异步处理可能乱序 |
| AsyncConfig 未配置 RejectedExecutionHandler | LOW | 队列满时默认 AbortPolicy 会抛异常 |

---

## 七、安全分析

| 安全项 | 状态 | 说明 |
|-------|------|------|
| **Prompt Injection** | NOT IMPLEMENTED | 用户输入未经检测直接拼入 LLM prompt |
| **API Key 保护** | PASS | 通过环境变量注入，不硬编码 |
| **日志脱敏** | PARTIAL | IntentRouter 日志记录了消息长度而非原文，但 AgentCore 未脱敏 |
| **参数注入** | LOW RISK | 工具参数来自 LLM 提取，非直接用户输入 |
| **PII 防泄露** | NOT IMPLEMENTED | 用户消息中的个人信息直接传递给 LLM |
| **Rate Limiting** | NOT IMPLEMENTED | 无 LLM 调用频率限制 |

---

## 八、问题优先级排序

### P0 - 必须修复 (影响核心功能)

| 序号 | 问题 | 影响 |
|------|------|------|
| 1 | **用户消息 LLM 上下文重复** (BUG-1) | 影响意图识别准确性，浪费 token |
| 2 | **DATA_DELETION 确认流程不可完成** (BUG-2) | 用户永远无法完成数据删除操作 |
| 3 | **IntentRouter/ReactPlanner/ResponseComposer 缺少独立测试** (BUG-3) | 三个核心组件行为无法独立验证 |

### P1 - 应尽快修复 (影响代码质量和可维护性)

| 序号 | 问题 | 影响 |
|------|------|------|
| 4 | ReactPlanner 未使用的 KimiClient 依赖 (BUG-4) | 冗余依赖 |
| 5 | KimiClient 异常类型不符合规范 (BUG-5) | 错误处理不统一 |
| 6 | KimiClient 错误处理策略不一致 (BUG-6) | 增加调用方复杂度 |
| 7 | @Value 字段注入 (BUG-7, BUG-12, BUG-18) | 违反编码规范 |
| 8 | 输入长度校验缺失 (BUG-8) | 超长消息可能导致 API 失败 |

### P2 - 建议修复 (提升健壮性)

| 序号 | 问题 | 影响 |
|------|------|------|
| 9 | 工具结果伪装为 assistant 消息 (BUG-9) | 可能影响 LLM 上下文理解 |
| 10 | KimiClient 缺少 HTTP 429 处理 (BUG-10) | 无法应对限流 |
| 11 | KimiClient 缺少重试机制 (BUG-11) | 临时故障直接失败 |
| 12 | ToolDispatcher 不检查空字符串 (BUG-13) | 空 username 可通过校验 |
| 13 | ReAct 循环耗尽无专门处理 (BUG-14) | 静默使用失败结果 |
| 14 | 参数直接强转无防御 (BUG-15) | 可能抛 ClassCastException |
| 15 | IntentResult 字段使用 String 非枚举 (BUG-16) | 失去类型安全 |

---

## 九、建议补充的测试用例

### IntentRouterTest (新建, P0)

```java
@ExtendWith(MockitoExtension.class)
class IntentRouterTest {
    // 1. 标准 JSON 响应 → 正确解析 intent/confidence/risk/params
    // 2. 带 ```json 围栏的响应 → 正确剥离后解析
    // 3. 畸形 JSON → 回退 GENERAL_CHAT (confidence=0.3)
    // 4. 空/null 响应 → 回退 GENERAL_CHAT
    // 5. LLM 调用抛异常 → 回退 GENERAL_CHAT
    // 6. 未知 intent 值 → 回退 GENERAL_CHAT
    // 7. confidence=0.0 和 1.0 边界值
    // 8. extracted_params 为 null → 空 Map
    // 9. 历史消息为 null → 不抛异常
}
```

### ReactPlannerTest (新建, P0)

```java
@ExtendWith(MockitoExtension.class)
class ReactPlannerTest {
    // 1. POST_QUERY + 有 username → 返回 post_query ToolCall
    // 2. POST_QUERY + 无 username → 返回 null
    // 3. DATA_DELETION + 有 username → 返回 user_data_delete ToolCall (userConfirmed=false)
    // 4. DATA_DELETION + 无 username → 返回 null
    // 5. KB_QUESTION → 返回 faq_search ToolCall
    // 6. GENERAL_CHAT → 返回 null
    // 7. previousResult 成功 → 返回 null (停止规划)
    // 8. previousResult 失败 → 继续规划
}
```

### ResponseComposerTest (新建, P0)

```java
@ExtendWith(MockitoExtension.class)
class ResponseComposerTest {
    // 1. composeFromTemplate: DATA_DELETION + null toolResult → 请求用户名
    // 2. composeFromTemplate: DATA_DELETION + needsConfirmation → 确认提示 (含用户名)
    // 3. composeFromTemplate: DATA_DELETION + success → 成功模板
    // 4. composeFromTemplate: DATA_DELETION + error → 失败模板
    // 5. composeWithEvidence: 正常 LLM 回复 → 返回 LLM 内容
    // 6. composeWithEvidence: LLM 返回空 → fallback
    // 7. composeWithEvidence: LLM 抛异常 → fallback
    // 8. composeWithEvidence: toolResult 为 null → "未执行任何工具查询"
    // 9. composeWithEvidence: toolResult 失败 → 显示错误信息
}
```

### KimiClientTest (新建, P1, 使用 MockServer/WireMock)

```java
class KimiClientTest {
    // 1. chatCompletion: 正常响应 → 解析 content
    // 2. chatCompletion: 超时 → 抛异常
    // 3. chatCompletion: HTTP 429 → 抛异常 (应包含限流信息)
    // 4. chatCompletion: HTTP 500 → 抛异常
    // 5. embedding: 正常响应 → 返回 float[]
    // 6. embedding: 空 body → 返回 float[0]
    // 7. embedding: 网络异常 → 抛异常
}
```

---

## 十、总结

### 整体评估

AI Chatbot Service 的架构设计合理，实现了 Bounded Agent 的核心模式（IntentRouter → ReactPlanner → ToolDispatcher → ResponseComposer），异步处理和降级策略基本到位。但存在 **2 个关键 bug** 和 **多处规范不符合项** 需要优先修复。

### 统计

| 分类 | 数量 |
|------|------|
| CRITICAL 问题 | 2 |
| HIGH 问题 | 5 |
| MEDIUM 问题 | 7 |
| LOW 问题 | 4 |
| **总计** | **18** |

| 测试统计 | 数量 |
|---------|------|
| AI 相关测试用例总数 | 50 |
| 全部通过 | 50 |
| 缺少独立测试的核心组件 | 4 (IntentRouter, ReactPlanner, ResponseComposer, KimiClient) |
| 建议新增测试用例数 | ~33 |

| 设计规范合规性 | 数量 |
|--------------|------|
| 完全符合 | 7/19 (37%) |
| 部分符合 | 5/19 (26%) |
| 未符合 | 7/19 (37%) |

### 下一步建议

1. **立即修复** BUG-1 (消息重复) 和 BUG-2 (确认流程) — 这两个问题会直接影响用户使用
2. **补充测试** IntentRouter、ReactPlanner、ResponseComposer 的独立单元测试
3. **统一错误处理** KimiClient 改用自定义 LlmCallException
4. **实现确认状态管理** 完成 DATA_DELETION 的完整确认流程
5. **安全加固** 实现输入长度校验和基本的 Prompt Injection 检测
