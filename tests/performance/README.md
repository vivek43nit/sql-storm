# FkBlitz Performance Tests (k6)

Three k6 scripts covering the most latency-sensitive paths.

## Prerequisites

- Docker (k6 runs as a container — no local install needed)
- A running FkBlitz stack: `docker compose up -d`

## Scripts

| Script | VUs | Duration | What it tests |
|---|---|---|---|
| `k6-metadata.js` | 50 | 6.5 min | Metadata read throughput |
| `k6-auth.js` | 10→100 | 2 min | Auth latency + rate-limit enforcement |
| `k6-reload-concurrent.js` | 50 | 5 min | CME safety: concurrent read + reload |

## Running

```sh
# 1. Start the full stack
docker compose up -d

# 2. Run a script (replace k6-metadata.js with any script)
docker run --rm --network host \
  -v "$(pwd)/tests/performance:/tests" \
  -e BASE_URL=http://localhost:9044/fkblitz \
  -e USERNAME=admin \
  -e PASSWORD=changeme \
  -e GROUP=localhost \
  -e DATABASE=demo \
  grafana/k6 run /tests/k6-metadata.js
```

## Performance Baselines

> Measured on Apple M-series / GitHub Actions ubuntu-latest.
> Update this table after each major release run.

| Script | p50 | p95 | p99 | Error rate |
|---|---|---|---|---|
| k6-metadata | — | < 500ms | < 1s | < 1% |
| k6-auth | — | < 200ms | < 500ms | < 1% |
| k6-reload-concurrent | — | < 500ms | < 1s | < 0.5% |

Fill in actual measured values after the first CI run.

## Auth Note

FkBlitz supports 4 auth backends. These scripts use **form-based login**
(`POST /api/login`) which works for `h2`, `mysql`, `config`, and `external-api` modes.

OAuth2/OIDC flows involve browser redirects and cannot be scripted with k6 HTTP API.
OAuth2 performance is covered by Playwright E2E browser tests instead.
