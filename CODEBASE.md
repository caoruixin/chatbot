# Chatbot Codebase Description

> Auto-generated codebase overview. Last updated: 2026-04-06.

## Project Overview

A full-stack AI customer service system integrating a human agent IM platform with an AI Chatbot. The system routes user messages to either an AI agent (powered by Kimi/Moonshot LLM) or a human agent via GetStream real-time messaging, with seamless handoff between the two.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend Language | Java 21 |
| Backend Framework | Spring Boot 3.4.3 |
| Data Access | MyBatis 3.5 |
| Database | PostgreSQL 16+ with pgvector |
| LLM (Conversation) | Kimi / Moonshot AI |
| Embeddings | DashScope text-embedding-v4 (Alibaba Qwen) |
| Real-time Chat | GetStream SDK |
| Build (Backend) | Gradle 8.12 |
| Frontend Framework | React 19 + TypeScript 5.7 |
| Build (Frontend) | Vite 6 |
| CSS | Tailwind CSS 4 |
| Routing | React Router 7 |
| Testing | JUnit 5 (backend), Vitest + Testing Library (frontend) |

## Project Structure

```
chatbot/
├── backend/                         # Spring Boot application
│   ├── build.gradle                 # Gradle build config
│   └── src/
│       ├── main/java/com/chatbot/
│       │   ├── ChatbotApplication.java
│       │   ├── config/              # App configuration (Kimi, GetStream, Async, CORS, Prompts)
│       │   ├── controller/          # REST endpoints (5 controllers)
│       │   ├── service/             # Business logic
│       │   │   ├── orchestrator/    # GlobalOrchestrator — main message workflow
│       │   │   ├── router/          # MessageRouter — AI vs Human routing
│       │   │   ├── agent/           # AI Agent (ReAct pattern): IntentRouter, ReactPlanner, ResponseComposer
│       │   │   ├── tool/            # Tool system: FAQ search, Post query, Data deletion
│       │   │   ├── llm/             # Kimi/Moonshot HTTP client
│       │   │   ├── stream/          # GetStream SDK wrapper
│       │   │   └── human/           # Human agent service
│       │   ├── mapper/              # MyBatis data access (5 mappers)
│       │   ├── model/               # Entities: Message, Session, Conversation, FaqDoc, UserPost
│       │   ├── dto/                 # Request/Response DTOs
│       │   ├── enums/               # SessionStatus, SenderType, ConversationStatus, etc.
│       │   ├── exception/           # Custom exceptions + GlobalExceptionHandler
│       │   └── scheduler/           # SessionTimeoutScheduler
│       ├── main/resources/
│       │   ├── application.yml      # Main config
│       │   ├── db/migration/        # Flyway migrations (V1–V4)
│       │   ├── mapper/              # MyBatis XML SQL mappings
│       │   └── prompts/             # LLM prompt templates (intent-router, response-composer, faq-matcher)
│       ├── test/                    # 20 test files (controllers, services, agents, tools)
│       └── eval/                    # Evaluation framework (separate harness)
│           └── java/com/chatbot/eval/
│               ├── EvalApplication.java
│               ├── evaluator/       # Layered evaluators (Gate, Outcome, Trajectory, ReplyQuality)
│               ├── model/           # EvalEpisode, RunResult, etc.
│               ├── runner/          # EvalRunner
│               └── reporter/        # Markdown report generator
│
├── frontend/                        # React SPA
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── main.tsx                 # Entry point
│       ├── App.tsx                  # Router: /, /chat, /agent
│       ├── pages/
│       │   ├── UserChatPage.tsx     # User chat interface
│       │   └── AgentDashboardPage.tsx  # Human agent dashboard
│       ├── components/
│       │   ├── chat/                # Shared: MessageList, MessageBubble, MessageInput, TypingIndicator
│       │   ├── agent/               # Agent: SessionList, ToolPanel
│       │   ├── user/                # User: UserToolPanel
│       │   ├── help/                # Help widget: HelpPanel, HelpLauncherButton, HelpComposer, etc. (10+ components)
│       │   └── common/              # Layout
│       ├── hooks/                   # useChat, useSession, useAgentChat, useTools
│       ├── services/                # apiClient, streamClient
│       ├── types/                   # Shared TypeScript types
│       └── config/                  # Environment config
│
├── docs/                            # Documentation
│   ├── PRD.md                       # Product requirements
│   ├── tech-design-spec.md          # Technical architecture spec
│   ├── ai-service-agent.md          # AI bounded agent architecture
│   ├── system-guide.md              # System setup & maintenance guide
│   ├── eval-spec.md                 # Evaluation framework spec
│   ├── eval-phase1-tech-design.md   # Eval Phase 1 design (completed)
│   ├── eval-phase2-tech-design.md   # Eval Phase 2 design (in progress)
│   ├── deprecated/                  # Superseded documents
│   └── chats/                       # Development session logs
│
├── scripts/                         # start-backend.sh, start-frontend.sh
├── CLAUDE.md                        # Project overview for Claude Code
├── CODEBASE.md                      # This file
└── TESTING_GUIDE.md                 # End-to-end testing guide
```

## Architecture

### Message Flow

```
User sends message
       │
       ▼
  API Gateway (MessageController)
       │
       ▼
  GlobalOrchestrator
  ├── Creates/updates Conversation
  ├── Creates/updates Session (10-min timeout)
  └── Routes via MessageRouter
       │
       ├─── AI path ──────────────────────┐
       │                                   ▼
       │                           IntentRouter (LLM classifies intent)
       │                                   │
       │                           ReactPlanner (ReAct loop, max 3 rounds)
       │                                   │
       │                           ToolDispatcher
       │                           ├── FaqService (vector similarity search)
       │                           ├── PostQueryService (user post lookup)
       │                           └── UserDataDeletionService (GDPR)
       │                                   │
       │                           ResponseComposer (LLM generates reply)
       │                                   │
       │                           GetStreamService (send to user)
       │
       └─── Human path ───────────────────┐
                                           ▼
                                   HumanAgentService
                                           │
                                   Agent Dashboard (WebSocket via GetStream)
                                           │
                                   Agent replies → GetStreamService → User
```

### Routing Rules

- Default: messages go to AI Chatbot
- Transfer keywords ("转人工", "人工客服", etc.) → switch to Human Agent
- Once transferred, all messages in that session stay with Human Agent
- New session (after 10-min inactivity) resets to AI

### Database Schema (PostgreSQL + pgvector)

| Table | Purpose |
|-------|---------|
| `conversation` | User ↔ system relationship (UUID PK, user_id, getstream_channel_id, status) |
| `session` | Chat session within conversation (status: AI_HANDLING / HUMAN_HANDLING / CLOSED) |
| `message` | All messages (sender_type: USER / AI_CHATBOT / HUMAN_AGENT / SYSTEM) |
| `faq_doc` | FAQ knowledge base with vector(1024) embeddings for similarity search |
| `user_post` | Mock user post data for tool demo |

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/messages/inbound` | User sends message (triggers async AI handling) |
| POST | `/api/messages/agent-reply` | Human agent sends reply |
| GET | `/api/messages` | Fetch message history by conversationId |
| GET | `/api/sessions` | List sessions by conversationId |
| POST | `/api/sessions` | Create new session |
| GET | `/api/sessions/:id` | Get session details |
| GET | `/api/conversations/:userId` | Get user's conversation |
| GET | `/api/tools/schema` | Get available tool definitions |
| GET | `/api/stream-token` | Get GetStream auth token |

### Frontend Architecture

- **State management**: Pure React hooks (no Redux/Zustand)
  - `useChat` — message history, conversation, session state
  - `useSession` — session CRUD operations
  - `useAgentChat` — agent-specific chat logic
  - `useTools` — tool execution state
- **Real-time**: GetStream SDK for WebSocket message push
- **Two interfaces**: User chat page (`/chat`) and Agent dashboard (`/agent`)

### AI Agent Design (Bounded Agent / ReAct)

The AI agent follows a bounded architecture with safety guardrails:
- **IntentRouter**: LLM classifies user intent into categories (FAQ, post query, data deletion, transfer, chitchat)
- **ReactPlanner**: Executes a ReAct (Reason-Act) loop with max 3 rounds, selecting tools based on intent
- **Tools**: FAQ vector search, post status query, user data deletion request
- **ResponseComposer**: Generates the final natural language response from tool results
- **Guardrails**: Evidence-first responses, high-risk operation templates, prompt injection protection

### Evaluation Framework

A 4-layer evaluation system for AI quality assurance:
- **L1 Gate**: Hard threshold checks (latency, error rates)
- **L2 Outcome**: Task success metrics
- **L3 Trajectory**: Process quality (tool selection, reasoning)
- **L4 Reply Quality**: Output naturalness and accuracy

Phase 1 (single-turn evaluation) is complete. Phase 2 (multi-turn) is in design.

## Running Locally

```bash
# Backend (requires PostgreSQL with pgvector, Java 21)
cd backend && ./gradlew bootRun

# Frontend (requires Node.js)
cd frontend && npm install && npm run dev
```

Environment variables needed in `.env.local`:
- `DB_USERNAME`, `DB_PASSWORD`
- `KIMI_API_KEY` (Moonshot AI)
- `DASHSCOPE_API_KEY` (Alibaba Qwen)
- `GETSTREAM_API_KEY`, `GETSTREAM_API_SECRET`
