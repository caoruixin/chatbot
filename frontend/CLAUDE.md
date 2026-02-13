# Frontend Development Guide

## 技术栈

- React 19.x
- TypeScript 5.7.x
- Vite 6.x
- React Router 7.x
- Tailwind CSS 4.x
- stream-chat-react 12.x / stream-chat 9.x

## 代码组织原则

### 目录结构与职责

```
src/
├── pages/           # 页面级组件（路由入口），组合子组件，不含复杂逻辑
├── components/      # 可复用 UI 组件
│   ├── chat/        # 消息展示相关（User Web + Agent Web 共用）
│   ├── agent/       # 人工客服专用组件
│   └── common/      # 通用布局组件
├── services/        # API 调用与外部服务封装
├── hooks/           # 自定义 React Hooks（业务逻辑封装）
├── types/           # TypeScript 类型定义
└── config/          # 环境变量与常量配置
```

### SOLID 原则

- **S - 单一职责**: 每个组件只做一件事。`MessageList` 只负责消息列表渲染，`MessageInput` 只负责输入交互，`ToolPanel` 只负责工具面板。页面组件 (Page) 只做组合编排。
- **O - 开闭原则**: 通过 props 扩展组件行为，不修改组件内部。新工具通过在 `ToolPanel` 添加配置项实现，不改核心渲染逻辑。
- **L - 里氏替换**: 共用组件 (MessageList, MessageBubble) 在 User Web 和 Agent Web 中行为一致，通过 props 控制差异（如是否显示工具面板）。
- **I - 接口隔离**: Props 接口精简，只声明组件实际需要的属性。不传递大对象让组件自己取子字段。
- **D - 依赖倒置**: 组件依赖 hooks 提供的抽象接口获取数据，不直接调用 API 或操作 GetStream SDK。

### YAGNI

- 不引入 Redux / Zustand / Jotai 等状态管理库。React hooks + Context 足够。
- 不封装通用 HTTP 客户端类，直接用 fetch 封装简单函数。
- 不提前做国际化 (i18n)、主题切换等功能。
- 不创建未使用的工具类或 helper 函数。

### KISS

- 样式用 Tailwind CSS 的 utility class 直接写在 JSX 中，不抽象 CSS-in-JS 或 CSS Module。
- 组件 props 用简单类型，避免深层嵌套对象。
- 路由配置集中在 `App.tsx`，不做多层嵌套路由。
- 环境变量通过 `import.meta.env.VITE_*` 直接访问，简单封装在 `config/env.ts`。

### DRY

- `MessageList` + `MessageBubble` + `MessageInput` 在 User Web 和 Agent Web 间共享。
- API 调用统一封装在 `services/apiClient.ts`，所有请求走同一入口。
- GetStream 连接管理统一在 `services/streamClient.ts`。
- 类型定义集中在 `types/index.ts`，前后端 DTO 类型对齐。

## TypeScript Best Practices

### 严格模式

tsconfig.json 开启 `strict: true`，包含：
- `strictNullChecks`: 强制处理 null/undefined
- `noImplicitAny`: 禁止隐式 any
- `strictFunctionTypes`: 函数参数类型严格检查

### 类型定义

```typescript
// types/index.ts - 与后端 DTO 对齐

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}

interface MessageResponse {
  messageId: string;
  conversationId: string;
  sessionId: string;
  senderType: 'USER' | 'AI_CHATBOT' | 'HUMAN_AGENT';
  senderId: string;
  content: string;
  createdAt: string;
}
```

### 类型优先于断言

- 优先使用类型注解，避免 `as` 类型断言。
- API 响应使用泛型 `ApiResponse<T>` 保证类型安全。
- 事件处理函数明确参数类型。

### 枚举用 union type

```typescript
type SenderType = 'USER' | 'AI_CHATBOT' | 'HUMAN_AGENT';
type SessionStatus = 'AI_HANDLING' | 'HUMAN_HANDLING' | 'CLOSED';
```

用字面量联合类型替代 TypeScript enum，与后端字符串枚举对齐，JSON 序列化更自然。

### 避免 any

- 外部 SDK 类型不明确时，用 `unknown` 后做类型守卫 (type guard)，不用 `any`。
- 第三方库缺少类型时，写最小声明文件 `.d.ts`。

## React Best Practices

### 函数组件 + Hooks

- 全部使用函数组件，不使用 class 组件。
- 业务逻辑封装到自定义 hooks (`useChat`, `useSession`, `useTools`)，组件只负责渲染。

### 组件设计

```typescript
// 好：props 简洁，职责单一
interface MessageBubbleProps {
  content: string;
  senderType: SenderType;
  createdAt: string;
}

function MessageBubble({ content, senderType, createdAt }: MessageBubbleProps) {
  // 纯渲染逻辑
}
```

### 状态管理策略

| 状态类型 | 管理方式 |
|---------|---------|
| 组件局部状态 | `useState` |
| 跨组件共享 (当前会话/用户信息) | React Context |
| 服务端数据 (消息列表/session 列表) | 自定义 hook 内 `useState` + fetch |
| 实时消息 | GetStream SDK 事件监听 → `useState` 更新 |

不引入全局状态管理库。如果 Context 层级过深导致性能问题，再考虑优化。

### Key 规范

- 列表渲染使用唯一且稳定的 `key`（messageId, sessionId），不用数组 index。

### 避免过度渲染

- 大列表考虑 `React.memo` 包裹子组件。
- 回调函数用 `useCallback` 防止子组件不必要重渲染。
- 计算量大的衍生数据用 `useMemo`。
- 但不要过早优化：先写正确的代码，有性能问题时再加 memo。

## 并发处理

### API 请求并发

- 避免用户快速点击发送按钮导致重复请求。发送按钮在请求进行中禁用 (loading state)。
- 页面切换时取消进行中的请求 (AbortController)：
  ```typescript
  useEffect(() => {
    const controller = new AbortController();
    fetchMessages(controller.signal);
    return () => controller.abort();
  }, [sessionId]);
  ```

### GetStream 事件处理

- GetStream WebSocket 事件在主线程触发，setState 更新是异步的。
- 消息列表更新使用函数式 setState 避免闭包中的过期状态：
  ```typescript
  setMessages(prev => [...prev, newMessage]);
  ```
- 组件卸载时取消事件监听，防止内存泄漏：
  ```typescript
  useEffect(() => {
    const handler = channel.on('message.new', onNewMessage);
    return () => handler.unsubscribe();
  }, [channel]);
  ```

### 竞态条件防护

- 快速切换 session 时，旧请求的响应可能晚于新请求到达。
- 使用 AbortController 或 flag 变量丢弃过期响应：
  ```typescript
  useEffect(() => {
    let cancelled = false;
    fetchMessages(sessionId).then(data => {
      if (!cancelled) setMessages(data);
    });
    return () => { cancelled = true; };
  }, [sessionId]);
  ```

## 错误处理

### API 错误处理

```typescript
// services/apiClient.ts

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new ApiError(response.status, await response.text());
  }
  const result: ApiResponse<T> = await response.json();
  if (!result.success) {
    throw new ApiError(400, result.error ?? 'Unknown error');
  }
  return result.data;
}
```

### 错误边界

- 在页面级组件外包裹 React Error Boundary，捕获渲染异常，显示 fallback UI。
- Error Boundary 不捕获事件处理函数和异步代码中的异常，这些需要在 hook/handler 中 try-catch。

### 用户反馈

| 场景 | 处理 |
|------|------|
| 消息发送失败 | 输入框上方显示错误提示，消息内容保留不清空 |
| API 请求超时 | 显示 "请求超时，请重试" |
| GetStream 连接断开 | 显示连接状态指示器，自动重连 (SDK 内置) |
| 工具调用失败 (Agent Web) | 工具面板内显示错误信息 |

### 不吞没错误

- 所有 catch 块必须有实际处理（显示错误 UI 或 console.error），不写空 catch。
- Promise 链末尾加 `.catch()`，async 函数用 try-catch。

## 日志处理

### 开发环境

- 使用 `console.error` 记录异常。
- 使用 `console.warn` 记录可恢复的异常（如 GetStream 重连）。
- `console.log` 仅用于开发调试，提交前清理或用条件保护：
  ```typescript
  if (import.meta.env.DEV) {
    console.log('Debug info:', data);
  }
  ```

### 生产环境

- v1 不集成外部日志服务 (Sentry 等)。
- 关键错误通过 Error Boundary 的 `componentDidCatch` 记录。
- 后续可考虑接入前端错误监控。

### 不记录敏感信息

- 不在 console 中输出 GetStream Token、用户消息原文。
- 不在 console 中输出完整 API 请求/响应体。

## 样式规范

### Tailwind CSS

- 直接在 JSX 中使用 utility class。
- 不使用 `@apply` 抽象（除非类名确实过长且重复 5 次以上）。
- 响应式设计使用 Tailwind 断点前缀 (`sm:`, `md:`, `lg:`)。

### 布局

- 主布局用 Flexbox (`flex`, `flex-col`)。
- 消息列表用 `overflow-y-auto` + `flex-col-reverse`（底部对齐）。

## 构建与运行

```bash
# 安装依赖
npm install

# 开发
npm run dev

# 构建
npm run build

# 预览生产构建
npm run preview
```

开发服务器运行在 `http://localhost:3000`，API 请求代理到 `http://localhost:8080`。
