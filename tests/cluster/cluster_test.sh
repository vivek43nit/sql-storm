#!/usr/bin/env bash
# cluster_test.sh — Multi-node Redis pub/sub propagation test
#
# Validates that a config change (new row in relation_mapping) detected by node1
# is propagated via Redis pub/sub to node2 within a tight window.
#
# Prerequisites: Docker, Docker Compose v2, jq, curl, mysql-client (or mariadb-client)
# Usage: bash tests/cluster/cluster_test.sh
# Exit 0 = all assertions pass. Non-zero = failure (message printed).

set -euo pipefail

COMPOSE_FILE="$(dirname "$0")/docker-compose.cluster-test.yml"
NODE1="http://localhost:9044/fkblitz"
NODE2="http://localhost:9045/fkblitz"
MARIADB_HOST="127.0.0.1"
MARIADB_PORT="3306"
MARIADB_USER="fkblitz"
MARIADB_PASS="fkblitz123"
MARIADB_DB="clustertest"

# ── Colours ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }
info() { echo -e "${YELLOW}▶ $1${NC}"; }

# ── Helpers ────────────────────────────────────────────────────────────────────
wait_for_health() {
  local url="$1/actuator/health/liveness"
  local name="$2"
  local max=24  # 24 × 5s = 2 min
  info "Waiting for $name to be healthy..."
  for i in $(seq 1 $max); do
    if curl -sf "$url" > /dev/null 2>&1; then
      pass "$name is healthy"
      return 0
    fi
    sleep 5
  done
  fail "$name did not become healthy in time"
}

poll_for_relation() {
  local node_url="$1"
  local rel_db="$2"
  local rel_table="$3"
  local label="$4"
  local max_seconds="$5"
  info "Polling $label for relation db=$rel_db table=$rel_table (timeout ${max_seconds}s)..."

  # Login first to get session cookie
  local cookie_jar
  cookie_jar="$(mktemp)"
  curl -sf -c "$cookie_jar" -X POST "$node_url/api/login" \
    -d "username=admin&password=changeme" > /dev/null

  local elapsed=0
  while [ "$elapsed" -lt "$max_seconds" ]; do
    local response
    response=$(curl -sf -b "$cookie_jar" \
      "$node_url/api/admin/relations?group=&database=$rel_db" 2>/dev/null || echo "[]")

    if echo "$response" | jq -e \
        --arg tbl "$rel_table" \
        'map(select(.table == $tbl)) | length > 0' > /dev/null 2>&1; then
      rm -f "$cookie_jar"
      pass "$label saw relation for $rel_db.$rel_table after ${elapsed}s"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  rm -f "$cookie_jar"
  fail "$label did NOT see relation for $rel_db.$rel_table within ${max_seconds}s"
}

# ── Main ───────────────────────────────────────────────────────────────────────
info "Starting cluster (2 nodes + Redis + MariaDB)..."
docker compose -f "$COMPOSE_FILE" up -d --build

# Wait for both nodes
wait_for_health "$NODE1" "node1"
wait_for_health "$NODE2" "node2"

# ── Scenario 1: Insert a relation, verify both nodes pick it up ────────────────
info "Inserting test relation into MariaDB..."
mysql -h "$MARIADB_HOST" -P "$MARIADB_PORT" -u "$MARIADB_USER" -p"$MARIADB_PASS" \
  "$MARIADB_DB" -e "
  INSERT IGNORE INTO relation_mapping
    (database_name, table_name, column_name, ref_database_name, ref_table_name, ref_column_name)
  VALUES
    ('clustertest', 'orders', 'user_id', 'clustertest', 'users', 'id');
"

# Node1 should detect the change within its poll interval (10s) + some margin
poll_for_relation "$NODE1" "clustertest" "orders" "node1" 30

# Node2 should see it almost immediately via Redis pub/sub (< 5s after node1 detects)
poll_for_relation "$NODE2" "clustertest" "orders" "node2" 15

# ── Scenario 2: Soft-delete, verify both nodes remove it ──────────────────────
info "Soft-deleting the relation..."
mysql -h "$MARIADB_HOST" -P "$MARIADB_PORT" -u "$MARIADB_USER" -p"$MARIADB_PASS" \
  "$MARIADB_DB" -e "
  UPDATE relation_mapping
  SET is_active = 0
  WHERE database_name = 'clustertest' AND table_name = 'orders';
"

# Wait for node1 to detect removal (next poll cycle)
info "Waiting for nodes to detect soft-delete..."
sleep 15

# Confirm neither node shows the relation anymore
for NODE_URL in "$NODE1" "$NODE2"; do
  cookie_jar="$(mktemp)"
  curl -sf -c "$cookie_jar" -X POST "$NODE_URL/api/login" \
    -d "username=admin&password=changeme" > /dev/null
  response=$(curl -sf -b "$cookie_jar" \
    "$NODE_URL/api/admin/relations?group=&database=clustertest" 2>/dev/null || echo "[]")
  rm -f "$cookie_jar"
  count=$(echo "$response" | jq '[map(select(.table == "orders"))] | length' 2>/dev/null || echo "0")
  if [ "$count" -eq 0 ]; then
    pass "$NODE_URL: soft-deleted relation is gone"
  else
    fail "$NODE_URL: soft-deleted relation still present"
  fi
done

# ── Teardown ──────────────────────────────────────────────────────────────────
info "Tearing down cluster..."
docker compose -f "$COMPOSE_FILE" down -v --remove-orphans

echo ""
pass "All cluster test assertions passed."
