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
    "reply_constraints": {"language": "en", "must_mention": ["timeline"]}
  }
}
```

> 备注：`expected` 允许“不完全真值”，用规则断言 + 过程约束来评估（避免必须写唯一标准答案）。

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

### Iteration 1（打通三子代理可观测）

**交付：**

* artifacts 标准化：`identity/execution/composer`（从你现有链路采集）
* TraceLike：最简单 JSON spans（step、ts、duration、tool_call）

**验收：**

* 任一失败 case 可以定位：是 identity 错、tool 错、还是 composer 错

---

### Iteration 2（Outcome + 副作用验证）

**交付：**

* side_effect 校验机制（复用你 codebase 的测试环境/沙箱/Mock）
* E3 Outcome evaluator

**验收：**

* 退款/改地址/建工单等“必须工具副作用”类场景可自动判定成败

---

### Iteration 3（CI Gate）

**交付：**

* 在你现有 CI 里加一条 job：跑核心 dataset
* Gate 规则：Contract 0 容忍；关键 suite 不得回退超过阈值；latency 不超阈值

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
Iter0: dataset(JSONL) + runner CLI (run/compare) + Contract/Trajectory/Efficiency evaluators + static HTML report.
Iter1: standardize artifacts for identity/execution/composer + trace JSON spans for debugging.
Iter2: add Outcome evaluator verifying side effects via our existing test sandbox/mocks.
Iter3: add CI gate config.

Key contracts:
- AgentAdapter.run_episode(episode, run_config)-> run_result {final_reply, actions[], artifacts{}, metrics{}, trace{}}
- Episode JSONL schema with expected.must_call/must_not/outcome/side_effects/reply_constraints
- Version fingerprint from model_id, prompt hash, workflow version, tool schema, kb snapshot, git commit
Also implement `eval discover` to export tool registry, prompts, workflow config into discovery.json.
```
