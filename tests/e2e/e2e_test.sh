#!/usr/bin/env bash
# e2e_test.sh — Start the dev docker stack and run Playwright E2E tests.
#
# Uses the main docker-compose.yml (same setup users run locally) so that
# E2E passing guarantees the dev docker setup works end-to-end.
#
# Port 9071 is used to avoid conflicts with local dev servers on 9044.
#
# Prerequisites: Docker, Docker Compose v2, Node.js, npx
# Usage: bash tests/e2e/e2e_test.sh
# Exit 0 = all tests pass. Non-zero = failure.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker-compose.yml"
E2E_DIR="$(dirname "$0")"
FKBLITZ_PORT=9071
BASE_URL="http://localhost:${FKBLITZ_PORT}"

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }
info() { echo -e "${YELLOW}▶ $1${NC}"; }

# ── Teardown trap ─────────────────────────────────────────────────────────────
teardown() {
  info "Tearing down stack..."
  FKBLITZ_PORT=$FKBLITZ_PORT docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
}
trap teardown EXIT

# ── Start stack ───────────────────────────────────────────────────────────────
info "Starting dev stack on port ${FKBLITZ_PORT} (MariaDB + Redis + FkBlitz)..."
FKBLITZ_PORT=$FKBLITZ_PORT docker compose -f "$COMPOSE_FILE" up -d --build

# ── Wait for fkblitz to be healthy ───────────────────────────────────────────
info "Waiting for FkBlitz to be ready (up to 3 min)..."
max=36  # 36 × 5s = 3 min
for i in $(seq 1 $max); do
  if curl -sf "${BASE_URL}/fkblitz/actuator/health/liveness" > /dev/null 2>&1; then
    pass "FkBlitz is healthy"
    break
  fi
  if [ "$i" -eq "$max" ]; then
    fail "FkBlitz did not become healthy in time"
  fi
  sleep 5
done

# ── Run Playwright tests ──────────────────────────────────────────────────────
info "Running Playwright E2E tests..."
cd "$E2E_DIR"

# Install deps if needed
if [ ! -d node_modules ]; then
  npm install --silent
fi

BASE_URL=$BASE_URL npx playwright test

pass "All E2E tests passed."
