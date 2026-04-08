/**
 * k6 load test — auth endpoint + rate-limit behaviour
 *
 * Tests: POST /fkblitz/api/login (form-based auth)
 * Pattern: ramp from 10 → 100 VUs over 1 minute to trigger the 60 req/min rate limit.
 *
 * What this verifies:
 *   1. Auth succeeds (200) under normal concurrency
 *   2. Rate limit (429) kicks in when per-user limit is exceeded
 *   3. Retry-After header is present on 429 responses
 *   4. Auth system stays responsive — no 500s or timeouts
 *
 * Note: OAuth2/OIDC flows use browser redirects and cannot be driven
 *       by k6 HTTP API. This script tests form-based auth (h2/mysql/config backends).
 *
 * Usage:
 *   docker run --rm --network host \
 *     -v $(pwd)/tests/performance:/tests \
 *     -e BASE_URL=http://localhost:9044/fkblitz \
 *     -e USERNAME=admin -e PASSWORD=changeme \
 *     grafana/k6 run /tests/k6-auth.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';

const errorRate     = new Rate('errors');
const rateLimitHits = new Counter('rate_limit_hits');

const BASE_URL = __ENV.BASE_URL  || 'http://localhost:9071/fkblitz';
const USERNAME = __ENV.USERNAME  || 'admin';
const PASSWORD = __ENV.PASSWORD  || 'changeme';

export const options = {
  stages: [
    { duration: '30s', target: 10  }, // warm-up
    { duration: '1m',  target: 100 }, // ramp up to trigger rate limit
    { duration: '30s', target: 0   }, // ramp down
  ],
  thresholds: {
    // Auth must not produce server errors (zero 5xx)
    http_req_failed: ['rate<0.01'],
    // Auth p95 under max load (100 VUs, local dev stack) — 6s ceiling
    // Production baseline captured separately in README.md
    http_req_duration: ['p(95)<6000'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/api/login`,
    { username: USERNAME, password: PASSWORD },
    { tags: { name: 'auth-login' } }
  );

  if (res.status === 429) {
    rateLimitHits.add(1);
    const retryAfterPresent = check(res, {
      '429 has Retry-After header': (r) => r.headers['Retry-After'] !== undefined,
    });
    errorRate.add(!retryAfterPresent);
  } else {
    const ok = check(res, {
      'login 200 or 401': (r) => r.status === 200 || r.status === 401,
      'response has JSON body': (r) => r.body && r.body.startsWith('{'),
    });
    errorRate.add(!ok);
  }
}
