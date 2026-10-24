#!/usr/bin/env bash
# Starts Postgres (Docker) and the backend with JDK 25. Keys come from .env.
# Usage: ./run.sh            (from the food-scanner-api-server folder, in Git Bash)
set -euo pipefail
cd "$(dirname "$0")"

# Use the portable JDK 25 if the current Java is older.
JDK25="$HOME/.jdks/jdk-25.0.4.1+1"
if [ -d "$JDK25" ]; then
  export JAVA_HOME="$JDK25"
fi

if [ ! -f .env ]; then
  echo "No .env file yet: copy .env.example to .env and add your GEMINI_API_KEY." >&2
fi

docker compose up -d postgres
exec ./mvnw spring-boot:run "$@"
