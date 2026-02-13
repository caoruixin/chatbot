#!/bin/bash
# Start the backend Spring Boot application
# Loads environment variables from .env.local

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Load env vars from .env.local
ENV_FILE="$PROJECT_ROOT/.env.local"
if [ -f "$ENV_FILE" ]; then
    echo "Loading environment from $ENV_FILE"
    set -a
    source "$ENV_FILE"
    set +a
else
    echo "WARNING: $ENV_FILE not found. Set environment variables manually."
fi

# Set JAVA_HOME to JDK 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using Java: $(java -version 2>&1 | head -1)"
echo "Starting backend..."

cd "$PROJECT_ROOT/backend" && ./gradlew bootRun
