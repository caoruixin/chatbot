这是一份**更精简、可分版本迭代落地**的《AI 客服 Agent Evaluation Framework Mini-Spec》。重点是：**不依赖任何新增付费/商业化外部服务**；只定义少量“方法 + 属性 + 数据格式”作为契约，其余都接你现有 codebase 里的三方服务（CRM/OMS/工单/KB/网关等）。

---

## 0. 约束与目标

### 约束（硬）

* 不引入任何新增付费 SaaS / 商业化平台（含需要新开通的 API、托管控制台等）。
* 评估框架可本地/自托管运行（文件/SQLite/Postgres 均可）。
* 能适配你当前 **三子代理**（identity / execute / composer），未来换成更 agentic 的编排也不重写评估框架。

### 目标（核心）

* 每次迭代（换模型/改 prompt/改 workflow）能做**可重复的离线回归评估**并**对比 baseline vs candidate**。
* 失败可定位到：身份识别问题 / 工具调用问题 / 回复组织问题。

---

## 1. 最小“系统契约”（必须稳定）

### 1.1 AgentAdapter（唯一强制接口）

> 你内部怎么编排都行，但对评估框架必须暴露同一个入口。

```ts
run_episode(episode, run_config) -> run_result
```

**run_result 必须包含：**

* `final_reply: string`
* `actions: ToolAction[]`（工具调用/状态变更/升级人工等结构化动作）
* `artifacts: { identity?, plan?, execution?, composer? }`（可选；三子代理建议分别填）
* `metrics: { latency_ms, token_in?, token_out?, cost? }`（你现有系统能拿多少填多少）
* `trace: TraceLike`（先支持 JSON 事件列表即可；后续再升级）

### 1.2 ToolAction（动作统一格式）

* `name`（工具名/动作名）
* `args`（脱敏后参数）
* `status`（ok/failed）
* `result_summary`（可选）
* `ts`（时间戳）

---

## 2. Test Case（Episode）最小格式（JSONL）

每行一个 episode（便于增量维护 & diff）：

```json
{
  "id": "refund_001",
  "suite": "refund",
  "tags": ["identity_required", "high_risk"],
  "initial_state": {
    "user": {"is_logged_in": true, "tier": "vip"},
    "env": {"kb_snapshot": "kb_2026_02_01", "seed": 42}
  },
  "conversation": [
    {"role": "user", "content": "I want a refund for order #123, arrived damaged."}
  ],
  "expected": {
    "must_call": [{"name": "OMS.get_order", "min": 1}],
    "must_not": ["request_password", "claim_action_not_done"],
    "outcome": "refund_initiated_or_escalated",
    "side_effects": [{"type": "refund", "status": "initiated_or_ticket_created"}],
    "reply_constraints": {"language": "en", "must_mention": ["timeline"]},
    "golden_reply": "我们已经为您的订单 #123 发起了退款申请，预计3-5个工作日到账。",
    "tool_arg_constraints": [
      {"name": "OMS.get_order", "args": {"order_id": "123"}}
    ]
  }
}
```

> 备注：`expected` 允许”不完全真值”，用规则断言 + 过程约束来评估（避免必须写唯一标准答案）。`golden_reply` 和 `tool_arg_constraints` 为可选字段，用于语义质量评估和工具参数校验（Iter1 引入）。

---

## 3. Evaluators（只保留 4 类，先把80%价值做出来）

### E1. Contract（硬门槛，必须过）

* `must_not` 违规（比如：未执行却宣称已退款/已改地址；泄露内部提示；索要密码等）
* 回复/结构化输出 schema 校验（若你有 JSON 输出）
* 工具调用 schema 校验（name/参数类型/必填字段）
* 身份门禁：未满足身份要求时不得执行敏感工具（可用规则表达）

### E2. Trajectory（过程正确性）

* `must_call` 是否满足（某工具至少调用几次）
* 顺序约束（可选）：比如先 identity 再 execute，再 composer
* 工具调用次数上限（控制成本/乱调用）

### E3. Outcome（结果与副作用）

* `side_effects` 是否达成：比如创建工单/发起退款/生成正确升级人工动作

> 这部分不要求你造新服务：直接复用你 codebase 的“测试环境/沙箱/Mock 能力”，或让 episode.initial_state 指定 seed 走模拟分支。

### E4. Efficiency（效率）

* latency p50/p95（离线可用每条 run 的 latency 聚合）
* token/cost（如果你现有链路已记录就用；否则先只统计 tool-call 次数）

### E5. Semantic（回复质量 — Iter1 引入）

评估 agent 回复的**内容正确性**，弥补 E1-E4 只做行为约束检查的不足。

* **Golden Reply 语义相似度**：当 episode 提供 `golden_reply` 时，用 embedding 模型（复用现有 DashScope text-embedding-v4）计算 `finalReply` 与 `golden_reply` 的余弦相似度。阈值（默认 0.75）可配置。
* **LLM-as-Judge 评分**：用现有 Kimi 模型对 `finalReply` 做结构化评分（满分 5 分），评分维度：
  * **correctness**（正确性）：回复内容是否准确、是否与 golden_reply 语义一致
  * **completeness**（完整性）：是否包含关键信息点
  * **tone**（语气）：是否符合客服场景（礼貌、专业）
* **Tool Argument 校验**：当 episode 提供 `tool_arg_constraints` 时，检查实际 tool call 的参数是否与预期匹配（精确匹配或包含匹配）。

> 不引入新的外部服务：embedding 复用 DashScope，LLM judge 复用 Kimi。评分 prompt 作为 eval 内部配置管理，不影响 production prompt。

**LLM Judge 模型独立性要求：**

生成和评估应避免使用同一模型（"不要用同一个模型评估自己"）。在不引入新付费服务的约束下，采用以下策略缓解：

* Judge 使用与生产不同的 Kimi 模型变体（如生产用 `moonshot-v1-8k`，Judge 用 `moonshot-v1-32k` 或更高参数版本）
* Judge prompt 中增加严格的评分校准指令（锚定示例 + 明确的扣分标准）
* 通过 `application-eval.yml` 单独配置 Judge 的 model_id、temperature 等参数，与生产配置解耦
* 未来如有条件，可替换为不同厂商的模型做 Judge（如 DeepSeek、Qwen）

### E6. RAG Retrieval Quality（检索质量 — Iter1 引入）

评估 FAQ 知识库检索管道的质量，弥补 E1-E5 未覆盖"检索到的上下文是否正确/充分"的不足。

* **Context Precision**：检索到的文档中，有多少与用户问题真正相关。当 episode 提供 `expected_contexts`（预期应检索到的 FAQ 条目 ID 列表）时，计算 precision = 命中数 / 实际检索数。
* **Context Recall**：预期的相关文档是否都被检索到。recall = 命中数 / 预期文档数。
* **Faithfulness（忠实度）**：agent 回复是否仅基于检索到的内容，而非编造。用 LLM Judge 判断回复中的每个事实声明是否能在检索到的上下文中找到依据。

**Episode 格式扩展（可选字段）：**

```json
{
  "expected": {
    "expected_contexts": ["faq_refund_policy", "faq_return_period"],
    "faithfulness_check": true
  }
}
```

**实现依赖：** SyncAgentAdapter 需在 RunResult 中记录 `retrievedContexts`（检索到的 FAQ 条目 ID + 内容摘要），供 E6 评估。Faithfulness 判断复用 Kimi（遵循 E5 的模型独立性策略）。

> 此评估器仅对涉及知识库检索的 episode（如 suite=faq）生效，其它 suite 自动跳过。

---

## 4. 版本与对比（最小可用）

### 4.1 Version Fingerprint（强制）

每次评估要生成一个 `agent_version` 字符串（建议 hash）包含：

* `model_id`
* `prompt_version(s)`（或 prompt 文本 hash）
* `workflow_version`
* `tools_schema_version`
* `kb_snapshot`
* `git_commit`

> 目的：任何回归都能明确“是哪次组合变化造成”。

### 4.2 基线对比（baseline vs candidate）

评估框架必须支持：

* 同一 dataset 跑两个版本
* 输出 delta：通过率变化、suite/tag 切片变化、Top regressions 列表

---

## 5. Runner（CLI）最小功能

必须提供 3 个命令：

1. `eval run`

* 输入：`--dataset path --agent-config candidate.yaml --out results_dir`
* 输出：每条 episode 的 run_result + scores + 汇总 report.json

2. `eval compare`

* 输入：`--baseline baseline_results --candidate candidate_results`
* 输出：compare.json + compare.html（静态 HTML 即可）

3. `eval list-failures`

* 输入：`--results ... --filter "contract_fail|outcome_fail|tag=refund"`
* 输出：失败样本列表（含 trace/artifacts 指针）

---

## 6. Dashboard（先做“零依赖”版本）

**迭代早期不做复杂 Web 服务**，先满足：

* `compare.html`：静态页面（本地打开即可）
* 3 个核心视图：

  1. 总览：pass rate、各 evaluator 通过率、latency
  2. 切片：按 suite/tag 的对比表
  3. 失败样本：点开能看到 episode、final_reply、actions、artifacts 摘要、trace（JSON）

> 后续再升级为你现有的内部 console（接你已有框架/鉴权/数据库）。

---

## 7. 分版本迭代路线图（推荐）

### Iteration 0（最小可跑：离线回归）

**交付：**

* Episode JSONL 格式 + loader
* AgentAdapter 接口（先用“调用你现有 chatbot endpoint/函数”的方式实现一个 adapter）
* E1/E2/E4（Contract/Trajectory/Efficiency）
* `eval run / compare` + 静态 HTML 报告

**验收：**

* 给 20 条核心 case，能跑完并输出 baseline vs candidate 对比

---

### Iteration 1（Golden Answer + 语义评估 + 检索质量 + 可观测）

**交付：**

* **E5 SemanticEvaluator**：
  * Golden reply 语义相似度（复用 DashScope embedding）
  * LLM-as-Judge 结构化评分（复用 Kimi，评 correctness / completeness / tone）
  * Tool argument 校验（验证工具调用参数是否正确）
* **LLM Judge 模型独立性**：
  * Judge 使用与生产不同的 Kimi 模型变体（通过 `application-eval.yml` 单独配置）
  * Judge prompt 增加评分校准指令（锚定示例 + 明确扣分标准）
* **E6 RAG Retrieval Quality Evaluator**：
  * Context Precision / Recall（需 episode 提供 `expected_contexts`）
  * Faithfulness 检查（LLM 判断回复是否基于检索内容）
  * SyncAgentAdapter 扩展：RunResult 中记录 `retrievedContexts`
* Episode 格式扩展：`golden_reply`、`tool_arg_constraints`、`expected_contexts`、`faithfulness_check` 可选字段
* 为现有 20 条 episode 补充 golden_reply 和 tool_arg_constraints；为 faq suite 的 episode 补充 expected_contexts
* artifacts 标准化：`identity/execution/composer`（从你现有链路采集）
* TraceLike：最简单 JSON spans（step、ts、duration、tool_call）
* HTML report 增加语义评分 + 检索质量展示

**验收：**

* 跑完 20 条 case，每条有语义相似度分数 + LLM judge 3 维度评分
* faq suite 的 case 有 context precision/recall 分数 + faithfulness 判定
* 任一失败 case 可以定位：是 identity 错、tool 参数错、检索质量差、还是 composer 回复质量差
* compare 报告中可以看到语义分数和检索质量的 baseline vs candidate 对比
* LLM Judge 使用独立模型配置，与生产模型解耦

---

### Iteration 2（Outcome + 副作用验证）

**交付：**

* side_effect 校验机制（复用你 codebase 的测试环境/沙箱/Mock）
* E3 Outcome evaluator

**验收：**

* 退款/改地址/建工单等”必须工具副作用”类场景可自动判定成败

---

### Iteration 3（CI Gate + 在线监控基础）

**交付：**

* 在你现有 CI 里加一条 job：跑核心 dataset
* Gate 规则：Contract 0 容忍；关键 suite 不得回退超过阈值；semantic 平均分不低于阈值；latency 不超阈值
* **在线监控基础设施**：
  * 生产链路埋点：每次对话记录 session_id、意图识别结果、检索结果、工具调用、最终回复、latency、token 消耗
  * 结构化日志落库（复用现有 PostgreSQL，新增 `eval_trace` 表）
  * 基础业务指标采集：人工转接率、AI 首次解决率（FCR）、平均处理时间（AHT）
  * 简单的指标看板（可复用 HTML report 模式，或接入已有的内部 console）

> 在线监控不引入 Langfuse 等外部平台，先用自建埋点 + 数据库实现最小闭环。后续如需升级，可考虑自部署 Langfuse 或接入已有的 observability 平台。

---

### Iteration 4（多轮对话 + 人工标注校准 + 安全评估）

**交付：**

* **多轮对话支持**：
  * SyncAgentAdapter 支持处理 `conversation` 中的多轮消息（而非仅取第一条）
  * 新增多轮对话一致性评估：上下文是否保持连贯、指代消解是否正确
  * 为核心场景补充多轮对话 episode（如：先问帖子状态 → 再问如何申诉 → 最后要求转人工）
* **人工标注校准流程**：
  * 建立 30-50 条人工标注的验证集（3 人标注 + 计算标注者一致性 Cohen's Kappa）
  * 校准 LLM Judge 评分与人工评分的一致率（目标 ≥ 80%）
  * 如果一致率不达标，迭代调整 Judge prompt 的评分标准和锚定示例
  * 提供简单的标注工具（可以是 CSV/JSONL + 脚本，不需要复杂 UI）
* **安全评估 / 红队测试**：
  * 新增 `security` suite，覆盖：prompt injection、越狱尝试、PII 泄露诱导、角色扮演攻击
  * 用 E1 Contract 的 `must_not` 规则做基础拦截检查
  * 新增 E7 SafetyEvaluator：检测回复中是否包含 PII、是否泄露系统 prompt、是否被诱导偏离客服角色
  * 安全 episode 不少于 20 条，覆盖常见攻击模式

**验收：**

* 多轮对话 episode 可正常执行并评估上下文一致性
* LLM Judge 与人工标注一致率 ≥ 80%
* 安全 suite 通过率 100%（0 容忍）

---

## 8. “调研 codebase 配置”的落地方式（不需要我直接读仓库）

为了把“你现有三方服务/工具注册表/prompt/workflow”写进 spec 并自动生成 `agent_version`，评估框架需要提供一个**Discovery 命令**（让 coding agent 在你的 repo 里实现即可）：

* `eval discover` 输出一个 `discovery.json`，至少包含：

  * tool registry（工具名、参数 schema、权限/敏感级别标签）
  * prompt versions（或 prompt hash）
  * workflow version（3 子代理配置、路由策略）
  * KB snapshot/version 获取方式
  * 模型路由配置（model_id + temperature 等）

> 这样评估框架就能“依赖你 codebase 已有能力”，而不是引入任何新外部服务。

---

## 9. 你可以直接贴给 Claude Code 的“精简任务描述”

```text
Build a minimal, self-hosted evaluation framework for our customer-support AI agent.
No paid SaaS dependencies.

Deliver in iterations:
Iter0: [DONE] dataset(JSONL) + runner CLI (run/compare) + Contract/Trajectory/Efficiency evaluators + static HTML report.
Iter1: SemanticEvaluator (golden reply similarity via DashScope embedding + LLM-as-Judge via separate Kimi model variant + tool arg validation) + RAG Retrieval Quality evaluator (context precision/recall + faithfulness) + artifacts/trace for debugging + golden_reply/expected_contexts data for 20 episodes.
Iter2: add Outcome evaluator verifying side effects via our existing test sandbox/mocks.
Iter3: add CI gate config (including semantic score thresholds) + online monitoring infra (production tracing, business metrics: escalation rate, FCR, AHT).
Iter4: multi-turn conversation support + human annotation calibration (30-50 labeled samples, Cohen's Kappa, Judge-human agreement >= 80%) + security/red-team suite (prompt injection, PII leakage, jailbreak detection).

Key contracts:
- AgentAdapter.run_episode(episode, run_config)-> run_result {final_reply, actions[], artifacts{}, metrics{}, trace{}, retrievedContexts[]}
- Episode JSONL schema with expected.must_call/must_not/outcome/side_effects/reply_constraints/golden_reply/tool_arg_constraints/expected_contexts/faithfulness_check
- Version fingerprint from model_id, prompt hash, workflow version, tool schema, kb snapshot, git commit
- LLM Judge must use a different model variant from production (configured via application-eval.yml)
Also implement `eval discover` to export tool registry, prompts, workflow config into discovery.json.
```
