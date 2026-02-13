Production Architecture Spec v1: Bounded Agentic Customer Service (Chat Only)
1. 目标与范围
目标
- 用一个“有边界的 Agent”在聊天场景中解决两类高优先级问题：
  1. 广告状态查询（读多、量大、低延迟）
  2. 账号/数据删除请求（DSR）（高风险、强合规、强身份校验、通常异步执行）
- 同时支持 外部用户 与 内部用户，并通过 RBAC/ACL 保证数据隔离
v1 不包含
- Ticket / Case 系统集成（先不做）
- 多智能体“辩论式”框架
- 自由生成高风险确认文本（退款/删除/法律条款等）

---
2. SLO / 非功能指标（建议）
- Chat p95：读路径（工具直查）< 2.5s；RAG 路径 < 4s
- 工具调用：超时 2–5s、有限重试、熔断
- 安全：“有证据才回答”；无证据/低置信度 → 追问或转人工入口（仅提示用户联系人工，不创建 ticket）

---
3. 核心组件（Brain / Body / Memory）
Channel
- Chat Adapter（Web/App/IM）
Edge & Security
- API Gateway：鉴权、限流、审计日志、WAF
- IdP 集成：获取 role/tenant/region/entitlements
Agent Core（单体服务，降低延迟）
- Router：意图识别 + 置信度 + 风险等级
- ReAct Loop（有边界）：规划 → 工具调用 → 观察 → 输出
- Response Composer：模板渲染、引用来源、用户可执行下一步
Tooling
- Tool Registry：工具清单、schema、版本、风险标签（read/write/irreversible）
- Dispatcher（防火墙）：权限校验 + schema 校验 + 执行 + 错误规范化
- Tool Microservices：广告系统、账号系统、删除工作流、知识检索等
Knowledge Engine（RAG）
- Hybrid Retrieval：Vector + BM25
- Reranker：交叉编码 rerank + 阈值过滤
- ACL Pre-filter：检索前按用户权限过滤可见文档
State & Stores
- Session Store（Redis）：会话状态、认证状态、短期上下文、速率计数
- KB 存储：文档库 + 向量库 + 关键词索引
- 审计日志库：结构化事件（不存思维链）

---
4. 关键护栏（Bounded Agent 的“边界”）
Evidence-first（强制）
- 最终回复必须可追溯到 工具输出 或 KB 片段（doc_id + last_updated）
- 失败策略：最多 1–2 次澄清/重试 → 拒答 + 引导人工
高风险 Intent-to-Template（强制）
- DSR/删除/安全/合规模板：由合规预审
- LLM 只负责：意图识别、信息抽取、对话引导
- 最终承诺文本：只能由模板/确定性函数输出（避免“误确认删除已完成”等）
RBAC
- 检索：pre-retrieval ACL filter
- 工具：Dispatcher 二次校验（最小权限）
Prompt Injection 防护
- 输入启发式检测 + XML/JSON 结构化分隔 + 末尾“约束再声明”
- 最重要：工具防火墙（就算模型被诱导也执行不了未授权工具）
PII
- 入口侧 PII 检测与脱敏（日志/追踪）
- 必要时可做 tokenization（避免把敏感数据写入日志/向量库）

---
5. Chat 处理流程（按意图分三条主路径）
A) 广告状态查询（Fast Path，优先走工具）
1. Router：INTENT=AD_STATUS 且置信度达标
2. Dispatcher：执行 get_ad_status(user_id, ad_id)（read-only，强 schema）
3. Response Composer：模板化输出
  - 状态、到期/余额/审核、下一步操作、数据时间戳
  - “Source: Ads API @ ”
4. 工具失败：有限重试 → 降级提示（系统繁忙/稍后再试/联系人工）
性能建议
- 对 (user_id, ad_id) 做短 TTL 缓存
- 熔断：后端异常时避免放大故障

---
B) 数据删除（DSR）（高风险、强校验、通常异步）
1. Router：INTENT=DATA_DELETION（risk=critical）
2. 强制身份校验：登录态/OTP/二次验证（取决于现状）
3. 信息收集（对话引导，结构化表单化）
  - 删除范围（账号/消息/广告数据等）、主体标识、司法辖区等
4. Dispatcher：执行 start_deletion_workflow(...)（write，idempotency_key 必须）
5. 回复：仅模板（不自由生成承诺）
  - “已收到请求/已开始处理/预计时间范围/如何查看进度（如果 v1 没有进度查询，就明确说明）”
6. 若无法完成（身份不明/信息不足/后端异常）：拒答 + 引导人工
写操作强制工程约束
- 所有写工具：idempotency_key + request_id
- 不可逆操作：二次确认（“我确认删除且无法撤销”）

---
C) 通用咨询/政策/操作指引（RAG Path）
1. Router：INTENT=KB_QUESTION
2. RAG：ACL 预过滤 → hybrid retrieve → rerank → 取 top N
3. 生成：基于片段回答 + 引用 doc_id/更新时间
4. 高风险类问题（合规/安全）：启用 critic 校验；失败则拒答

---
6. 失败模式与降级策略（Chat Only）
- 低置信度：最多 1 个澄清问题（避免无限追问）→ 仍不清楚则引导人工
- 检索无结果/无授权文档：拒答（“我无法在当前权限下找到依据”）+ 引导人工
- 工具 schema 校验失败：给模型一次修正机会 → 再失败就拒答
- 工具超时/熔断：降级提示 + 重试建议 + 引导人工

---
7. 可观测性（不记录思维链）
记录结构化“决策痕迹”，而非 chain-of-thought：
- intent、置信度、风险等级
- 检索 doc_id 列表、rerank 分数、阈值命中情况
- 工具名、参数校验结果、延迟、错误类型（输出脱敏）
- 回复模板 ID、引用来源、是否触发拒答/人工引导
核心指标：
- p95/p99 延迟、工具错误率、缓存命中率
- 拒答率、人工引导率
- groundedness（基于引用覆盖率/评测集）

---
8. v1 里程碑（Chat Only）
1. MVP-0：Gateway + Auth + Session + Dispatcher 骨架 + 结构化日志
2. MVP-1：Router + 基础 RAG（ACL 预过滤）+ 引用输出 + 人工引导
3. MVP-2：广告状态 Fast Path（工具 + 缓存 + 模板）
4. MVP-3：DSR 对话引导 + 身份校验 + 删除 workflow 工具（幂等 + 二次确认）
5. Scale：分领域 specialist（Ads/DSR/KB）+ 评测集回归门禁
