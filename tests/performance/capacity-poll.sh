#!/usr/bin/env bash
# capacity-poll.sh — poll Prometheus every 5s during k6 capacity run
#
# Writes CSV to stdout:
#   timestamp,vus_approx,hikari_active,tomcat_busy,heap_mb
#
# Usage:
#   bash tests/performance/capacity-poll.sh > /tmp/capacity-metrics.csv
#   (run this before or alongside k6-capacity.js)
#   Kill with Ctrl-C when k6 finishes.

set -euo pipefail

PROM="${PROMETHEUS_URL:-http://localhost:9090}"
FKBLITZ="${FKBLITZ_URL:-http://localhost:9071/fkblitz}"
INTERVAL="${POLL_INTERVAL_SECONDS:-5}"

# Verify dependencies
for cmd in curl python3; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: $cmd required" >&2; exit 1; }
done

query_instant() {
  local metric="$1"
  curl -s --max-time 3 \
    "${PROM}/api/v1/query?query=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${metric}'))")" \
    | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    result = d.get('data', {}).get('result', [])
    if result:
        print(result[0]['value'][1])
    else:
        print('0')
except Exception:
    print('0')
"
}

echo "timestamp,hikari_active,hikari_max,tomcat_busy,tomcat_max,heap_mb,heap_max_mb"

while true; do
  TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # Data pools only — excludes auth (fkblitz-auth) and config pools (fkblitz-config-*)
  HIKARI_ACTIVE=$(query_instant 'sum(hikaricp_connections_active{pool=~"fkblitz-data-.*"})')
  HIKARI_MAX=$(query_instant 'sum(hikaricp_connections_max{pool=~"fkblitz-data-.*"})')
  TOMCAT_BUSY=$(query_instant 'tomcat_threads_busy_threads')
  TOMCAT_MAX=$(query_instant 'tomcat_threads_config_max_threads')
  HEAP_USED=$(query_instant 'sum(jvm_memory_used_bytes{area="heap"})' | python3 -c "import sys; v=sys.stdin.read().strip(); print(f'{float(v)/1048576:.1f}' if v else '0')" 2>/dev/null || echo "0")
  HEAP_MAX=$(query_instant 'sum(jvm_memory_max_bytes{area="heap"})' | python3 -c "import sys; v=sys.stdin.read().strip(); print(f'{float(v)/1048576:.1f}' if v else '0')" 2>/dev/null || echo "0")

  echo "${TS},${HIKARI_ACTIVE},${HIKARI_MAX},${TOMCAT_BUSY},${TOMCAT_MAX},${HEAP_USED},${HEAP_MAX}"

  sleep "$INTERVAL"
done
