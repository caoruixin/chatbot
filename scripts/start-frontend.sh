#!/bin/bash
# Start the frontend Vite dev server

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "Starting frontend dev server..."
cd "$PROJECT_ROOT/frontend" && npm run dev
