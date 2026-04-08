# FkBlitz Performance Tests (k6)

Three k6 scripts covering the most latency-sensitive paths.

## Prerequisites

- Docker (k6 runs as a container — no local install needed)
- A running FkBlitz stack: `docker compose up -d`

## Scripts

| Script | VUs | Duration | What it tests | JDBC pool exercised? |
|---|---|---|---|---|
| `k6-metadata.js` | 50 | 6.5 min | Metadata read throughput (groups + tables endpoints) | ❌ in-memory only |
| `k6-auth.js` | 10→100 | 2 min | Auth latency + rate-limit enforcement | ❌ auth pool only |
| `k6-reload-concurrent.js` | 50 | 5 min | CME safety: concurrent metadata reads + relation traversal | ❌ in-memory only |
| `k6-query.js` | 10→100 | 7 min | Real SQL execution — SELECT, JOIN, FK references | ✅ data pool |
| `k6-capacity.js` | 10→200 | ~16 min | VU ladder for resource sizing (metadata path) | ❌ in-memory only |
| `k6-verify.js` | 200 | 5 min | Steady-state validation at recommended config | ❌ in-memory only |

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
- Metadata endpoint is in-memory (snapshotRef reads) — sub-3ms p95 at 50 VUs is expected. **These scripts do not exercise the JDBC data pool.**
- Auth p95 at 100 VUs is high (~5s) because no sleep between iterations; real users don't hammer login 100× concurrently. Zero 5xx confirms auth is stable under extreme load.
- Concurrent test (130k requests, 50 VUs, 435 req/s) produced zero CME/NPE — thread-safe snapshot swap validated.

### SQL Query Baselines (`k6-query.js`)

The only script that actually borrows JDBC connections from the data pool (40% simple SELECT, 40% JOIN, 20% FK references navigation).

> Measured on Apple M-series (local Docker stack, MariaDB). Captured: 2026-04-08.
> Config: `FKBLITZ_MAX_POOL_SIZE=100`, `FKBLITZ_TOMCAT_THREADS_MAX=400`, `-Xmx1g`. Ramp 10→100 VUs, `sleep(0.5)`.

| Endpoint | p50 | p95 | Errors | JDBC active max | JDBC pending max |
|---|---|---|---|---|---|
| Simple SELECT (`users LIMIT 20`) | 27ms | **553ms** | 0.00% | 7 | 0 |
| JOIN SELECT (orders + users) | 28ms | **586ms** | 0.00% | 7 | 0 |
| FK references (`/api/references`) | 28ms | **653ms** | 0.00% | 7 | 0 |

**Resource usage at 100 VUs with real SQL:**

| Metric | Max | Avg | Ceiling |
|---|---|---|---|
| JDBC active (data pool) | 7 | 0.8 | 100 |
| JDBC pending | 0 | 0 | — |
| JDBC acquire latency | 14.5ms | — | — |
| Tomcat threads busy | 22 | 3.1 | 400 |
| JVM heap | 196 MB | 115 MB | 1024 MB |

**Confirmed production config for SQL-heavy workloads at 100 VUs:**

| Setting | Value | Basis |
|---|---|---|
| `FKBLITZ_MAX_POOL_SIZE` | 10 | Max 7 active, 0 pending — no contention |
| `FKBLITZ_TOMCAT_THREADS_MAX` | 50 | Max 22 busy (6% utilization) |
| `-Xmx` | 256m | 178 MB peak, 256 MB gives safe headroom |

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

> Measured on Apple M-series (local Docker stack). Captured: 2026-04-08.

**Run 1 — Discovery (high ceiling config):**
Config: `FKBLITZ_MAX_POOL_SIZE=100`, `FKBLITZ_TOMCAT_THREADS_MAX=400`, `-Xmx1g`.
Script: `k6-capacity.js` (ramp 10→200 VUs, `sleep(0.5)` between requests).

| Metric | Peak (200 VUs) | Ceiling |
|---|---|---|
| JDBC connections active | ~0 (sub-ms borrows, missed by 5s poll) | 100 |
| Tomcat threads busy | 15 | 400 |
| JVM heap | 198 MB | 1024 MB |
| latency p95 | 41ms | — |
| http_req_failed | 0.00% | — |

> Note: the 15-thread peak reflects burst behavior during the ramp, not steady-state at 200 VUs.
> With `sleep(0.5)` and ~5ms requests, only ≈ 2 threads are active at any instant under steady load.

**Run 2 — Verification (recommended config, steady 200 VUs):**
Config: `FKBLITZ_MAX_POOL_SIZE=10`, `FKBLITZ_TOMCAT_THREADS_MAX=50`, `-Xmx256m`.
Script: `k6-verify.js` (200 VUs constant for 5 min, `sleep(0.1)` between requests).

| Metric | Peak | Ceiling | Result |
|---|---|---|---|
| JDBC connections active | ~0 | 10 | ✅ Pool correct |
| Tomcat threads busy | **50/50** | 50 | ❌ Saturated — p95 degraded to 1.53s |
| JVM heap | 102 MB | 256 MB | ✅ Heap correct |
| http_req_failed | 0.00% | — | ✅ No errors |

**Finding:** Tomcat thread count is the bottleneck at sustained 200 VUs. At `sleep(0.1)` per iteration,
each VU issues ~10 req/s; 200 VUs = ~2,000 req/s. With 50 threads and ~630ms avg latency,
the thread pool saturates (Little's Law: L = λW → 200 × 0.63s = 126 concurrent → needs ≥ 130 threads).

**Corrected production config:**

| Setting | Value | Rationale |
|---|---|---|
| `FKBLITZ_MAX_POOL_SIZE` | 10 | Confirmed — metadata reads are sub-ms; no pool exhaustion |
| `FKBLITZ_TOMCAT_THREADS_MAX` | 200 | Match your expected peak concurrent users |
| `-Xmx` | 256m | Confirmed — 102 MB peak at 200 VUs, 256 MB gives 2.5× headroom |

> Rule of thumb: set `FKBLITZ_TOMCAT_THREADS_MAX` ≥ your peak concurrent active users.
> Re-run `k6-verify.js` with the final config to validate before deploying to production.

## Auth Note

FkBlitz supports 4 auth backends. These scripts use **form-based login**
(`POST /api/login`) which works for `h2`, `mysql`, `config`, and `external-api` modes.

OAuth2/OIDC flows involve browser redirects and cannot be scripted with k6 HTTP API.
OAuth2 performance is covered by Playwright E2E browser tests instead.
