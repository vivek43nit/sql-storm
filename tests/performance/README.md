# FkBlitz Performance Tests (k6)

Three k6 scripts covering the most latency-sensitive paths.

## Prerequisites

- Docker (k6 runs as a container — no local install needed)
- A running FkBlitz stack: `docker compose up -d`

## Scripts

| Script | VUs | Duration | What it tests |
|---|---|---|---|
| `k6-metadata.js` | 50 | 6.5 min | Metadata read throughput (groups + tables endpoints) |
| `k6-auth.js` | 10→100 | 2 min | Auth latency + rate-limit enforcement |
| `k6-reload-concurrent.js` | 50 | 5 min | CME safety: concurrent metadata reads + relation traversal |

## Running

```sh
# 1. Start the full stack
docker compose up -d

# 2. Run a script (defaults work against local stack)
docker run --rm --network host \
  -v "$(pwd)/tests/performance:/tests" \
  grafana/k6 run /tests/k6-metadata.js

# Override defaults via env vars
docker run --rm --network host \
  -v "$(pwd)/tests/performance:/tests" \
  -e BASE_URL=http://localhost:9071/fkblitz \
  -e USERNAME=admin \
  -e PASSWORD=changeme \
  -e GROUP=demo \
  -e DATABASE=demo \
  grafana/k6 run /tests/k6-metadata.js
```

## Performance Baselines

> Measured on Apple M-series (local dev stack, Docker).
> Captured: 2026-04-08.

| Script | VUs | Requests | p50 | p95 | p99 | Errors | Gate |
|---|---|---|---|---|---|---|---|
| `k6-metadata.js` | 50 | 34,409 | 1.75ms | **2.72ms** | **4.08ms** | 0.00% | ✓ PASS |
| `k6-auth.js` | 10→100 | 2,998 | 646ms | 5.09s | — | 0.00% (zero 5xx) | ✓ PASS |
| `k6-reload-concurrent.js` | 50 | 130,522 | 2.24ms | **5.48ms** | — | 0.00% | ✓ PASS |

**Notes:**
- Metadata endpoint is in-memory (snapshotRef reads) — sub-3ms p95 at 50 VUs is expected.
- Auth p95 at 100 VUs is high (~5s) because no sleep between iterations; real users don't hammer login 100× concurrently. Zero 5xx confirms auth is stable under extreme load.
- Concurrent test (130k requests, 50 VUs, 435 req/s) produced zero CME/NPE — thread-safe snapshot swap validated.

## Capacity Benchmarking (Resource vs. Concurrency)

A separate script `k6-capacity.js` steps VUs from 10 → 200 in stages and records latency alongside Prometheus metrics (JVM heap, JDBC pool active/idle, Tomcat thread pool).

```sh
# 1. Start polling Prometheus (writes CSV)
bash tests/performance/capacity-poll.sh > /tmp/capacity-metrics.csv &

# 2. Run VU ladder (~16 min)
docker run --rm --network host \
  -v "$(pwd)/tests/performance:/tests" \
  grafana/k6 run /tests/k6-capacity.js 2>&1 | tee /tmp/capacity-k6.txt

# 3. Stop poll (Ctrl-C or kill %1), then generate report
kill %1
bash tests/performance/capacity-report.sh /tmp/capacity-metrics.csv /tmp/capacity-k6.txt
```

### Capacity Baselines

> Measured on Apple M-series (local dev stack, Docker). Captured: 2026-04-08.
> Config: `FKBLITZ_MAX_POOL_SIZE=100`, `FKBLITZ_TOMCAT_THREADS_MAX=400`, `-Xmx1g`.

| Metric | Peak (200 VUs) | Ceiling |
|---|---|---|
| JDBC connections active | ~0 (returned sub-ms, poll missed) | 100 |
| Tomcat threads busy | 15 | 400 |
| JVM heap | 198 MB | 1024 MB |
| latency_groups p95 | 41ms | — |
| latency_tables p95 | 41ms | — |
| http_req_failed | 0.00% | — |

**Recommended production config** (peak × 1.25, rounded):

| Setting | Value |
|---|---|
| `FKBLITZ_MAX_POOL_SIZE` | 10 |
| `FKBLITZ_TOMCAT_THREADS_MAX` | 50 |
| `-Xmx` | 256m |

> Re-run after config change to validate the ceilings hold under your actual workload.

## Auth Note

FkBlitz supports 4 auth backends. These scripts use **form-based login**
(`POST /api/login`) which works for `h2`, `mysql`, `config`, and `external-api` modes.

OAuth2/OIDC flows involve browser redirects and cannot be scripted with k6 HTTP API.
OAuth2 performance is covered by Playwright E2E browser tests instead.
