# Chatbot Project

## Overview

AI customer service system integrating a human agent IM platform with an AI Chatbot. Routes user messages to either AI (Kimi/Moonshot LLM) or human agents via GetStream real-time messaging, with seamless handoff between the two.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.4, MyBatis, PostgreSQL (with pgvector)
- **Frontend**: React 19, TypeScript 5.7, Vite 6, Tailwind CSS 4, React Router 7
- **IM**: GetStream (stream-chat-react 12 / stream-chat 8)
- **LLM**: Kimi (Moonshot AI) for conversation, DashScope text-embedding-v4 (Alibaba Qwen) for embeddings

## Running Locally

```bash
# Backend (requires PostgreSQL with pgvector, JDK 21)
cd backend && ./gradlew bootRun

# Frontend (requires Node.js)
cd frontend && npm install && npm run dev
```

Environment variables in `.env.local`: `DB_USERNAME`, `DB_PASSWORD`, `KIMI_API_KEY`, `DASHSCOPE_API_KEY`, `GETSTREAM_API_KEY`, `GETSTREAM_API_SECRET`

## Development Guidelines

- Read existing code before suggesting modifications
- Prefer editing existing files over creating new ones
- Avoid common vulnerabilities (XSS, SQL injection, command injection)
- Keep code simple — avoid over-engineering and unnecessary abstractions
- Backend follows strict 3-layer architecture: Controller → Service → Mapper (see `backend/CLAUDE.md`)
- Frontend follows hooks-based patterns with no Redux (see `frontend/CLAUDE.md`)

## Documentation

Detailed specs live in `docs/` — see `docs/README.md` for an index covering PRD, technical design, AI agent architecture, evaluation framework, and system guide.
