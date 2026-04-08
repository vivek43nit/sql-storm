// k6-query.js — benchmark with real SQL execution (exercises JDBC pool)
//
// Unlike k6-metadata.js (in-memory reads), this script runs actual SELECT queries
// against the target database so the JDBC connection pool is genuinely exercised.
//
// Mix of workloads per iteration:
//   40% — simple SELECT (users table, small result)
//   40% — JOIN query (orders + users, medium result)
//   20% — references navigation (FK lookup, exercises metadata + SQL)
//
// Run:
//   docker run --rm --network host \
//     -v "$(pwd)/tests/performance:/tests" \
//     grafana/k6 run /tests/k6-query.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9071/fkblitz';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'changeme';
const GROUP    = __ENV.GROUP    || 'demo';
const DATABASE = __ENV.DATABASE || 'demo';

const latencySimple  = new Trend('latency_simple_select');
const latencyJoin    = new Trend('latency_join_select');
const latencyRefs    = new Trend('latency_references');
const errorRate      = new Rate('errors');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10  },
        { duration: '1m',  target: 10  },
        { duration: '30s', target: 50  },
        { duration: '2m',  target: 50  },
        { duration: '30s', target: 100 },
        { duration: '2m',  target: 100 },
        { duration: '30s', target: 0   },
      ],
    },
  },
  thresholds: {
    http_req_failed:    ['rate<0.01'],
    http_req_duration:  ['p(95)<2000'],
    latency_simple_select: ['p(95)<500'],
    latency_join_select:   ['p(95)<1000'],
    latency_references:    ['p(95)<2000'],
    errors:             ['rate<0.01'],
  },
};

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/login`,
    `username=${USERNAME}&password=${PASSWORD}`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  );
  const setCookie = loginRes.headers['Set-Cookie'] || '';
  const match = setCookie.match(/JSESSIONID=([^;]+)/);
  if (!match) throw new Error(`No JSESSIONID in Set-Cookie: ${setCookie}`);
  return { sessionId: match[1] };
}

export default function ({ sessionId }) {
  const headers = {
    Cookie: `JSESSIONID=${sessionId}`,
    'Content-Type': 'application/json',
  };
  const executeUrl = `${BASE_URL}/api/execute?group=${GROUP}`;

  const roll = Math.random();

  if (roll < 0.40) {
    // ── Simple SELECT ────────────────────────────────────────────────────────
    const body = JSON.stringify({
      database: DATABASE,
      query: 'SELECT * FROM users LIMIT 20',
      queryType: 'S',
      info: '',
      relation: 'self',
    });
    const t0 = Date.now();
    const res = http.post(executeUrl, body, { headers });
    latencySimple.add(Date.now() - t0);
    const ok = check(res, { 'simple select 200': (r) => r.status === 200 });
    errorRate.add(!ok);

  } else if (roll < 0.80) {
    // ── JOIN SELECT (orders + users) ─────────────────────────────────────────
    const body = JSON.stringify({
      database: DATABASE,
      query: 'SELECT o.id, o.status, u.name, u.email FROM orders o JOIN users u ON o.user_id = u.id LIMIT 20',
      queryType: 'S',
      info: '',
      relation: 'self',
    });
    const t0 = Date.now();
    const res = http.post(executeUrl, body, { headers });
    latencyJoin.add(Date.now() - t0);
    const ok = check(res, { 'join select 200': (r) => r.status === 200 });
    errorRate.add(!ok);

  } else {
    // ── FK references navigation (orders referencing user_id=1) ─────────────
    const row = encodeURIComponent(JSON.stringify({ id: 1 }));
    const refsUrl = `${BASE_URL}/api/references?group=${GROUP}&database=${DATABASE}&table=users&column=id&row=${row}&refRowLimit=20`;
    const t0 = Date.now();
    const res = http.get(refsUrl, { headers: { Cookie: `JSESSIONID=${sessionId}` } });
    latencyRefs.add(Date.now() - t0);
    const ok = check(res, { 'references 200': (r) => r.status === 200 });
    errorRate.add(!ok);
  }

  sleep(0.5);
}
