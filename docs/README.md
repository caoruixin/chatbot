# Documentation Index

## Core System Documents

| Document | Purpose | Maps to Code |
|----------|---------|-------------|
| **PRD.md** | Product requirements defining Conversation/Session/Message lifecycle, routing rules, and system architecture | `GlobalOrchestrator`, `MessageRouter`, `SessionStatus` enum, `GetStreamService` — the entire message flow is built from this spec |
| **tech-design-spec.md** | Technical architecture: 3-layer pattern (Controller→Service→Mapper), MyBatis, Kimi LLM, DashScope embedding, tool system design | Every backend package follows this spec — `controller/`, `service/`, `mapper/`, `config/`, `dto/`, `model/` |
| **ai-service-agent.md** | Bounded Agent architecture: intent routing, ReAct planning, evidence-first responses, risk-aware tool firewall, SLO targets | `IntentRouter`, `ReactPlanner`, `ResponseComposer`, `ToolDispatcher`, `ToolDefinition` (risk levels) |
| **system-guide.md** | Operations manual: environment setup (JDK 21, PostgreSQL 16+, Node.js), configuration (.env.local), running locally, testing scenarios | `application.yml`, startup scripts in `scripts/`, frontend `config/env.ts` |

## Evaluation Framework Documents

| Document | Purpose | Maps to Code |
|----------|---------|-------------|
| **eval-spec.md** | 4-layer evaluation system: L1 Gate → L2 Outcome → L3 Trajectory → L4 Reply Quality | `GateEvaluator`, `LayeredOutcomeEvaluator`, `LayeredTrajectoryEvaluator`, `ReplyQualityEvaluator` in `backend/src/eval/` |
| **eval-phase1-tech-design.md** | Phase 1 refactoring plan: migrating 6 flat evaluators to 4 layered evaluators (completed) | Documents the deprecated/ → new evaluator transition; explains backward-compatible JSONL episode format in `DatasetLoader` |
| **eval-phase2-tech-design.md** | Phase 2 design: multi-turn conversation evaluation support (in design/partial implementation) | `SyncAgentAdapter`, `TurnResult`, `TurnExpectation` model classes |

## Development Guides (in project roots)

| Document | Purpose |
|----------|---------|
| **CLAUDE.md** (root) | High-level project overview and onboarding entry point |
| **backend/CLAUDE.md** | Backend coding standards: SOLID, 3-layer architecture, constructor injection, async LLM calls |
| **frontend/CLAUDE.md** | Frontend coding standards: React hooks, TypeScript strict mode, Context API, component hierarchy |
| **TESTING_GUIDE.md** (root) | Step-by-step end-to-end testing scenarios for User→AI→Human Agent flow |

## Deprecated (docs/deprecated/)

Archived documents superseded by current specs or no longer relevant. Kept for historical reference only.
