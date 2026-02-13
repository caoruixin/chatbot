# Backend Development Guide

## 技术栈

- Java 21 (LTS)
- Spring Boot 3.4.x
- MyBatis 3.5.x (mybatis-spring-boot-starter 3.0.x)
- PostgreSQL 16+ with pgvector
- Flyway 10.x
- GetStream Chat Java SDK 1.24.x
- Gradle Groovy DSL 8.12.x

## 代码组织原则

### 分层架构 (严格单向依赖)

```
Controller → Service → Mapper
```

- **Controller**: 仅处理 HTTP 请求/响应，参数校验，调用 Service，返回 DTO。不含业务逻辑。
- **Service**: 全部业务逻辑在此层。Service 间可相互调用。
- **Mapper**: MyBatis 数据访问接口 + XML SQL 映射。仅被 Service 调用。
- **Model**: 简单 POJO，对应数据库表。无业务方法。
- **DTO**: Request/Response 对象，与 Model 隔离。Controller 不暴露 Model。

禁止反向调用：Mapper 不依赖 Service，Service 不依赖 Controller。

### SOLID 原则

- **S - 单一职责**: 每个类只有一个变更理由。Controller 只做接口适配，Service 只做业务编排，Mapper 只做数据访问。Agent 子系统中 IntentRouter / ReactPlanner / ResponseComposer / ToolDispatcher 各司其职。
- **O - 开闭原则**: 新增工具通过在 ToolDefinition 枚举添加条目 + 实现 ToolExecutor 接口完成，不修改 ToolDispatcher 核心逻辑。
- **L - 里氏替换**: ToolExecutor 接口的所有实现 (FaqService, PostQueryService, UserDataDeletionService) 可互换使用。
- **I - 接口隔离**: 工具执行器只需实现 `ToolExecutor.execute(Map<String, Object> params): ToolResult`，不强制实现无关方法。
- **D - 依赖倒置**: AgentCore 依赖 ToolExecutor 接口，不依赖具体 Service 实现。KimiClient 通过 Spring 配置注入 RestTemplate。

### YAGNI (You Aren't Gonna Need It)

- 不提前建抽象层。v1 只有 3 个工具，ToolDefinition 用枚举硬编码即可，不搞动态注册。
- 不预留扩展点，除非 tech-design-spec 明确要求。
- 不引入未使用的依赖 (Spring AI, JPA, Kotlin 等)。

### KISS (Keep It Simple, Stupid)

- LLM 调用直接用 RestTemplate + Map 构建请求体，不封装通用 LLM 抽象层。
- MyBatis XML 写原生 SQL，pgvector 查询直接用 `<=>` 运算符。
- 配置集中在 application.yml，通过 `@Value` 或 `@ConfigurationProperties` 注入。

### DRY (Don't Repeat Yourself)

- 统一响应格式：所有 API 返回 `ApiResponse<T>`。
- 统一异常处理：`@RestControllerAdvice` 全局异常处理器。
- 工具系统通过 ToolExecutor 接口统一调用模式，避免 if-else 分支。
- GetStream 消息发送统一通过 GetStreamService，不在各处重复 SDK 调用。

## Java Best Practices

### 命名规范

- 包名: `com.chatbot.{layer}.{module}` (全小写)
- 类名: `PascalCase` (如 `AgentCore`, `IntentRouter`)
- 方法名: `camelCase` (如 `handleMessage`, `findOrCreate`)
- 常量: `UPPER_SNAKE_CASE` (如 `MAX_REACT_ROUNDS`)
- 数据库字段映射: MyBatis `map-underscore-to-camel-case: true` 自动转换

### 不可变性优先

- DTO 字段使用 `private` + getter/setter（或 Java record，视团队约定）。
- 配置类用 `final` 字段 + 构造器注入。
- 集合返回用 `List.of()` / `Map.of()` / `Collections.unmodifiableList()` 防止外部修改。

### Null 安全

- 方法参数非空校验用 `Objects.requireNonNull()` 或 Spring `@NonNull`。
- 返回值可能为空时用 `Optional<T>`（Mapper 查单条记录）。
- 避免返回 null 集合，返回空集合 `List.of()`。

### 类型安全

- 使用枚举 (enum) 替代字符串常量 (SessionStatus, SenderType, RiskLevel 等)。
- MyBatis 中通过 TypeHandler 处理枚举与数据库 VARCHAR 的映射。

### 构造器注入

```java
@Service
public class AgentCore {
    private final IntentRouter intentRouter;
    private final ToolDispatcher toolDispatcher;
    private final ResponseComposer responseComposer;

    public AgentCore(IntentRouter intentRouter,
                     ToolDispatcher toolDispatcher,
                     ResponseComposer responseComposer) {
        this.intentRouter = intentRouter;
        this.toolDispatcher = toolDispatcher;
        this.responseComposer = responseComposer;
    }
}
```

优先使用构造器注入而非 `@Autowired` 字段注入，保证依赖不可变且易于测试。

## 并发处理

### Spring Boot 默认线程模型

- Spring Boot 内嵌 Tomcat 使用线程池处理请求（默认 200 线程）。
- 每个 HTTP 请求占用一个线程，Controller → Service → Mapper 同步执行。
- v1 阶段不引入响应式编程 (WebFlux)，保持 Servlet 同步模型。

### LLM 调用阻塞问题

- Kimi API 调用是阻塞 I/O（HTTP 请求等待响应），单次调用可能耗时 2-10 秒。
- AI Agent 处理一条消息可能涉及多次 LLM 调用（意图识别 + 回复生成），总耗时可达 5-15 秒。
- **应对策略**:
  - `POST /api/messages/inbound` 接口立即返回 messageId（异步处理 AI 回复）。
  - AI 处理在独立线程池中执行，不阻塞 Tomcat 工作线程。
  - 使用 `@Async` + 自定义 `TaskExecutor`：
    ```java
    @Bean
    public TaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-agent-");
        return executor;
    }
    ```

### Session 并发安全

- 同一用户短时间内可能发送多条消息，需防止 Session 状态竞态。
- Session 状态更新使用数据库乐观锁或 `WHERE status = 'AI_HANDLING'` 条件更新。
- 避免内存锁（不用 synchronized/ReentrantLock），状态一致性依赖数据库。

### 数据库连接池

- 使用 Spring Boot 默认 HikariCP。
- 连接池大小建议：`maximumPoolSize = 10`（本地开发足够）。
- MyBatis 默认每次请求获取/释放连接，无需手动管理。

### 定时任务线程安全

- `SessionTimeoutScheduler` 使用 `@Scheduled(fixedRate = 60000)`。
- 超时关闭通过单条 SQL `UPDATE ... WHERE last_activity_at < threshold` 实现，天然幂等。
- 不在 Java 层遍历 session 列表做逐条更新。

## 错误处理

### 分层错误策略

| 层 | 策略 |
|---|------|
| Controller | 不处理业务异常，由全局异常处理器统一处理。参数校验失败返回 400。 |
| Service | 业务异常抛自定义异常。外部调用 (Kimi/GetStream) 捕获后包装为业务异常或降级。 |
| Mapper | SQL 异常由 Spring/MyBatis 自动转为 DataAccessException，Service 层捕获处理。 |
| Tool | 工具执行异常返回 `ToolResult.error()`，不抛异常。AgentCore 根据结果决定降级。 |

### 自定义异常体系

```java
// 业务异常基类
public class ChatbotException extends RuntimeException {
    private final String errorCode;
    public ChatbotException(String errorCode, String message) { ... }
}

// 具体异常
public class SessionNotFoundException extends ChatbotException { ... }
public class LlmCallException extends ChatbotException { ... }
public class ToolExecutionException extends ChatbotException { ... }
```

### 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChatbotException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(ChatbotException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
            .body(ApiResponse.error("内部错误"));
    }
}
```

### LLM 调用错误处理

- 设置 RestTemplate 超时: `connectTimeout=5s`, `readTimeout=10s`。
- 超时/网络异常 → 捕获 `ResourceAccessException` → 最多重试 1 次 → 降级回复。
- HTTP 429 (限流) → 不重试，直接降级。
- 响应 JSON 解析失败 → 记录原始响应 → 降级。

### GetStream 调用错误处理

- GetStream SDK 异常不应阻断主流程。
- 消息发送失败 → 记录错误日志 → 不影响数据库中消息记录的写入。
- 降级策略：消息已存 DB 但未推送时，前端可通过轮询 `/api/messages` 兜底获取。

## 日志处理

### 日志框架

- 使用 SLF4J + Logback（Spring Boot 默认）。
- 不引入 Log4j 或其他日志框架。

### 日志级别规范

| 级别 | 用途 |
|------|------|
| ERROR | 需要立即关注的异常：数据库连接失败、LLM 服务不可用、数据不一致 |
| WARN | 可恢复的异常：LLM 超时后重试成功、工具调用失败但降级处理、Session 未找到 |
| INFO | 关键业务事件：消息接收、意图识别结果、工具调用、Session 状态变更、转人工 |
| DEBUG | 开发调试：LLM 请求/响应体、SQL 参数、工具调用详细参数 |

### 结构化日志 (关键字段)

每条业务日志应包含可追踪字段：

```java
log.info("Intent recognized: conversationId={}, sessionId={}, intent={}, confidence={}, risk={}",
    conversationId, sessionId, intent.getIntent(), intent.getConfidence(), intent.getRisk());

log.info("Tool dispatched: sessionId={}, tool={}, success={}, duration={}ms",
    sessionId, toolCall.getToolName(), result.isSuccess(), duration);
```

### 敏感信息脱敏

- 日志中不输出用户消息原文（或做截断/脱敏）。
- 不输出 Kimi API Key、GetStream Secret。
- LLM 请求/响应体仅在 DEBUG 级别输出，生产环境关闭。

### 不记录 Chain-of-Thought

参考 ai-service-agent.md：记录结构化"决策痕迹"，不记录 LLM 思维链。
- 记录：intent / confidence / risk / tool name / duration / error type / template ID
- 不记录：LLM 内部推理过程、完整 prompt

## 测试规范

### 测试目录

```
src/test/java/com/chatbot/
├── controller/    # API 集成测试 (@WebMvcTest)
├── service/       # Service 单元测试 (Mockito)
└── mapper/        # Mapper 测试 (@MybatisTest + 内嵌 PG / H2)
```

### 测试原则

- Service 层测试 mock 外部依赖 (Mapper, KimiClient, GetStream)。
- AgentCore 测试覆盖三条路径 (Fast Path / DSR / RAG) + 失败降级场景。
- 工具测试验证 schema 校验 + 风险检查逻辑。

## 构建与运行

```bash
# 构建
./gradlew build

# 运行
./gradlew bootRun

# 测试
./gradlew test
```
