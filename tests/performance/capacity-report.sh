#!/usr/bin/env bash
# capacity-report.sh — capacity benchmark summary using Prometheus range queries
#
# Usage:
#   bash tests/performance/capacity-report.sh /tmp/capacity-metrics.csv /tmp/capacity-k6.txt
#
# The CSV (from capacity-poll.sh) provides the run window timestamps.
# Prometheus is queried for max_over_time + avg_over_time over that window —
# much more accurate than reading point-in-time poll snapshots.
# The k6 txt provides latency percentiles.
#
# Why range queries instead of CSV max:
#   The 5s poll is a sample lottery. JDBC connections borrowed for < 5ms never appear.
#   Tomcat threads that spike briefly between polls are missed.
#   Prometheus already stores all scrape intervals (15s default); max_over_time()
#   finds the true peak across all stored data points in the window.

set -euo pipefail

CSV="${1:-/tmp/capacity-metrics.csv}"
K6_OUT="${2:-/tmp/capacity-k6.txt}"
PROM="${PROMETHEUS_URL:-http://localhost:9090}"

if [[ ! -f "$CSV" ]]; then
  echo "ERROR: metrics CSV not found: $CSV" >&2; exit 1
fi

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║              FkBlitz Capacity Benchmark Report                      ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""

python3 - "$CSV" "$K6_OUT" "$PROM" <<'PYEOF'
import sys, csv, json, re, urllib.request, urllib.parse
from datetime import datetime, timezone

csv_file  = sys.argv[1]
k6_file   = sys.argv[2]
prom      = sys.argv[3]

# ── Derive run window from CSV timestamps ─────────────────────────────────────
rows = []
with open(csv_file) as f:
    reader = csv.DictReader(f)
    for row in reader:
        try:
            rows.append(row)
        except Exception:
            pass

if not rows:
    print("  [!] No data in CSV — was capacity-poll.sh running during the k6 test?")
    sys.exit(0)

def parse_ts(ts_str):
    return datetime.fromisoformat(ts_str.replace('Z', '+00:00')).timestamp()

start_ts = parse_ts(rows[0]['timestamp'])
end_ts   = parse_ts(rows[-1]['timestamp'])
duration_s = max(int(end_ts - start_ts), 60)

print(f"Run window: {rows[0]['timestamp']} → {rows[-1]['timestamp']}  ({duration_s}s)")
print()

# ── Query Prometheus with max_over_time + avg_over_time ───────────────────────
def prom_query(expr, at_time=None):
    """Instant query at end of window (for range functions like max_over_time)."""
    params = {'query': expr}
    if at_time:
        params['time'] = str(at_time)
    url = f"{prom}/api/v1/query?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(url, timeout=5) as resp:
            d = json.load(resp)
        result = d.get('data', {}).get('result', [])
        if not result:
            return None
        # sum() returns single result; otherwise take first
        return float(result[0]['value'][1])
    except Exception as e:
        return None

# Range window string for PromQL (e.g. "300s")
window = f"{duration_s}s"
# Step for subquery (Prometheus scrape interval is 15s by default)
step = "15s"

def q_max(expr):
    return prom_query(f"max_over_time(({expr})[{window}:{step}])", at_time=end_ts)

def q_avg(expr):
    return prom_query(f"avg_over_time(({expr})[{window}:{step}])", at_time=end_ts)

# ── Data pool metrics ─────────────────────────────────────────────────────────
DATA_POOL   = 'pool=~"fkblitz-data-.*"'
AUTH_POOL   = 'pool="fkblitz-auth"'
CONFIG_POOL = 'pool=~"fkblitz-config-.*"'

metrics = {
    # (label, max_expr, avg_expr, ceiling_expr)
    'hikari_active_data':   (
        'JDBC active (data pools)',
        f'sum(hikaricp_connections_active{{{DATA_POOL}}})',
        f'sum(hikaricp_connections_active{{{DATA_POOL}}})',
        f'sum(hikaricp_connections_max{{{DATA_POOL}}})',
    ),
    'hikari_pending_data':  (
        'JDBC pending (waiting for connection)',
        f'sum(hikaricp_connections_pending{{{DATA_POOL}}})',
        f'sum(hikaricp_connections_pending{{{DATA_POOL}}})',
        None,
    ),
    'hikari_acquire_ms':    (
        'JDBC acquire time (avg wait per borrow)',
        f'1000 * sum(rate(hikaricp_connections_acquire_seconds_sum{{{DATA_POOL}}}[1m])) / sum(rate(hikaricp_connections_acquire_seconds_count{{{DATA_POOL}}}[1m]))',
        f'1000 * sum(rate(hikaricp_connections_acquire_seconds_sum{{{DATA_POOL}}}[1m])) / sum(rate(hikaricp_connections_acquire_seconds_count{{{DATA_POOL}}}[1m]))',
        None,
    ),
    'hikari_timeout':       (
        'JDBC pool timeouts (total)',
        f'sum(hikaricp_connections_timeout_total{{{DATA_POOL}}})',
        None,
        None,
    ),
    'tomcat_busy':          (
        'Tomcat threads busy',
        'tomcat_threads_busy_threads',
        'tomcat_threads_busy_threads',
        'tomcat_threads_config_max_threads',
    ),
    'heap_mb':              (
        'JVM heap used (MB)',
        'sum(jvm_memory_used_bytes{area="heap"}) / 1048576',
        'sum(jvm_memory_used_bytes{area="heap"}) / 1048576',
        'sum(jvm_memory_max_bytes{area="heap",id="G1 Old Gen"}) / 1048576',
    ),
}

print("Resource Usage  (max_over_time / avg_over_time across full run window)")
print(f"{'Metric':<40} {'Max':>8} {'Avg':>8} {'Ceiling':>8}")
print("-" * 68)

results = {}
for key, (label, max_expr, avg_expr, ceil_expr) in metrics.items():
    max_val  = q_max(max_expr)  if max_expr  else None
    avg_val  = q_avg(avg_expr)  if avg_expr  else None
    ceil_val = prom_query(ceil_expr, at_time=end_ts) if ceil_expr else None

    def fmt(v, unit=''):
        if v is None: return '  n/a'
        return f"{v:>7.1f}{unit}"

    unit = 'ms' if 'ms' in key else ('MB' if 'mb' in key else '')
    print(f"  {label:<38} {fmt(max_val):>9} {fmt(avg_val):>9} {fmt(ceil_val):>9}")
    results[key] = {'max': max_val, 'avg': avg_val, 'ceil': ceil_val}

print()

# ── k6 latency summary ────────────────────────────────────────────────────────
k6_p95 = k6_p99 = k6_errors = k6_rps = None
try:
    with open(k6_file) as f:
        for line in f:
            if 'http_req_duration' in line and 'p(95)' in line:
                m = re.search(r'p\(95\)=(\S+)', line)
                if m: k6_p95 = m.group(1)
                m = re.search(r'p\(99\)=(\S+)', line)
                if m: k6_p99 = m.group(1)
            if 'http_req_failed' in line and '%' in line:
                m = re.search(r'(\d+\.\d+)%', line)
                if m: k6_errors = m.group(1)
            if 'checks_total' in line:
                m = re.search(r'(\d+\.\d+)/s', line)
                if m: k6_rps = m.group(1)
except FileNotFoundError:
    pass

if k6_p95:
    print(f"k6 Latency  (http_req_duration)")
    print(f"  p95: {k6_p95:<12}  p99: {k6_p99 or 'n/a':<12}  errors: {k6_errors or '?'}%  throughput: {k6_rps or '?'} req/s")
    print()

# ── Pool pressure diagnosis ───────────────────────────────────────────────────
print("Pool Pressure Diagnosis")
pending_max = results.get('hikari_pending_data', {}).get('max')
acquire_max = results.get('hikari_acquire_ms', {}).get('max')
timeout_max = results.get('hikari_timeout', {}).get('max')
tomcat_max_val = results.get('tomcat_busy', {}).get('max')
tomcat_ceil = results.get('tomcat_busy', {}).get('ceil')

if pending_max is not None:
    status = "✅ no contention" if pending_max < 1 else f"⚠️  peak {pending_max:.0f} threads waiting"
    print(f"  JDBC pending threads:    {status}")
import math
if acquire_max is not None and not math.isnan(acquire_max):
    status = "✅ healthy" if acquire_max < 5 else f"⚠️  peak {acquire_max:.1f}ms wait (> 5ms threshold)"
    print(f"  JDBC acquire latency:    {status}")
elif acquire_max is None or math.isnan(acquire_max):
    print(f"  JDBC acquire latency:    — (no data pool SQL executed during run)")
if timeout_max is not None:
    status = "✅ no timeouts" if timeout_max < 1 else f"❌ {timeout_max:.0f} timeouts — pool too small"
    print(f"  JDBC pool timeouts:      {status}")
if tomcat_max_val is not None and tomcat_ceil is not None:
    pct = (tomcat_max_val / tomcat_ceil * 100) if tomcat_ceil > 0 else 0
    status = "✅ headroom" if pct < 85 else f"⚠️  {pct:.0f}% utilized — near ceiling"
    print(f"  Tomcat thread utilization: {tomcat_max_val:.0f}/{tomcat_ceil:.0f} ({pct:.0f}%)  {status}")
print()

# ── Recommendations ───────────────────────────────────────────────────────────
HEADROOM = 1.25

def recommend_pool():
    # Base recommendation on pending threads + timeouts, not active count (active is misleading)
    p = pending_max or 0
    t = timeout_max or 0
    active_max = results.get('hikari_active_data', {}).get('max') or 0
    current_ceil = results.get('hikari_active_data', {}).get('ceil') or 10
    if t > 0:
        # Timeouts observed — pool is definitely too small
        return int(current_ceil * 2)
    elif p > 0:
        # Pending threads observed — pool is under pressure
        return max(10, int(current_ceil * HEADROOM))
    elif active_max > 0:
        # No pressure but connections were used — right-size to active peak + headroom
        rec = max(5, int(active_max * HEADROOM))
        return ((rec + 4) // 5) * 5
    else:
        # No JDBC activity on data pools — keep a sensible minimum
        return 10

def recommend_tomcat():
    v = tomcat_max_val or 0
    c = tomcat_ceil or 200
    if v >= c * 0.85:
        # Near saturation — recommend current ceiling × 1.5
        return int(c * 1.5)
    rec = max(50, int(v * HEADROOM))
    return ((rec + 24) // 25) * 25  # round to nearest 25

def recommend_heap():
    v = results.get('heap_mb', {}).get('max') or 256
    rec = max(256, int(v * HEADROOM))
    return ((rec + 127) // 128) * 128  # round to nearest 128

rec_pool   = recommend_pool()
rec_tomcat = recommend_tomcat()
rec_heap   = recommend_heap()

print("╔══════════════════════════════════════════════════════════════════════╗")
print("║  Recommended Configuration                                          ║")
print("╠══════════════════════════════════════════════════════════════════════╣")
print(f"║  FKBLITZ_MAX_POOL_SIZE       = {rec_pool:<39}║")
print(f"║  FKBLITZ_TOMCAT_THREADS_MAX  = {rec_tomcat:<39}║")
print(f"║  JAVA_TOOL_OPTIONS           = -Xms128m -Xmx{rec_heap}m{'':<{34 - len(str(rec_heap))}}║")
print("╚══════════════════════════════════════════════════════════════════════╝")
print()
print("Basis:")
print(f"  Pool size  — from hikaricp_connections_pending (not active gauge)")
print(f"  Threads    — from max_over_time(tomcat_threads_busy) × 1.25")
print(f"  Heap       — from max_over_time(jvm_memory_used_bytes) × 1.25")
print()
print("Set in docker-compose.yml or production env vars. Re-run to validate.")
PYEOF
