/**
 * k6 load test — metadata endpoint
 *
 * Tests: GET /fkblitz/api/tables?group=<group>&database=<db>
 * Pattern: 50 VUs, steady-state for 5 minutes after 1-minute ramp-up.
 *
 * Auth: FkBlitz uses form-based session auth (POST /api/login → JSESSIONID cookie).
 *       setup() logs in once and passes the cookie to all VUs via __ENV.
 *       Note: OAuth2 flows require a browser redirect and cannot be scripted here.
 *
 * Thresholds:
 *   p(95) < 500ms  — 95th percentile response time
 *   p(99) < 1000ms — 99th percentile response time
 *   errors < 1%    — HTTP error rate
 *
 * Usage:
 *   docker run --rm --network host \
 *     -v $(pwd)/tests/performance:/tests \
 *     -e BASE_URL=http://localhost:9044/fkblitz \
 *     -e USERNAME=admin -e PASSWORD=changeme \
 *     -e GROUP=localhost -e DATABASE=demo \
 *     grafana/k6 run /tests/k6-metadata.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL  = __ENV.BASE_URL  || 'http://localhost:9044/fkblitz';
const USERNAME  = __ENV.USERNAME  || 'admin';
const PASSWORD  = __ENV.PASSWORD  || 'changeme';
const GROUP     = __ENV.GROUP     || 'localhost';
const DATABASE  = __ENV.DATABASE  || 'demo';

export const options = {
  stages: [
    { duration: '1m',  target: 50 },  // ramp up to 50 VUs
    { duration: '5m',  target: 50 },  // steady state
    { duration: '30s', target: 0  },  // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors:            ['rate<0.01'],
  },
};

export function setup() {
  const jar = http.cookieJar();
  const loginRes = http.post(
    `${BASE_URL}/api/login`,
    { username: USERNAME, password: PASSWORD },
    { jar }
  );
  if (loginRes.status !== 200) {
    throw new Error(`Login failed: HTTP ${loginRes.status} — ${loginRes.body}`);
  }
  // Extract JSESSIONID from the jar
  const cookies = jar.cookiesForURL(`${BASE_URL}/api/login`);
  const sessionId = cookies['JSESSIONID'] ? cookies['JSESSIONID'][0].value : null;
  if (!sessionId) {
    throw new Error('No JSESSIONID in response — check credentials and login URL');
  }
  return { sessionId };
}

export default function (data) {
  const params = {
    headers: { Cookie: `JSESSIONID=${data.sessionId}` },
    tags: { name: 'metadata-tables' },
  };

  // 1. List groups
  const groupsRes = http.get(`${BASE_URL}/api/groups`, params);
  const groupsOk = check(groupsRes, {
    'groups 200': (r) => r.status === 200,
    'groups is array': (r) => Array.isArray(JSON.parse(r.body)),
  });
  errorRate.add(!groupsOk);

  // 2. List tables for the configured group/database
  const tablesRes = http.get(
    `${BASE_URL}/api/tables?group=${GROUP}&database=${DATABASE}`,
    { ...params, tags: { name: 'metadata-tables' } }
  );
  const tablesOk = check(tablesRes, {
    'tables 200': (r) => r.status === 200,
  });
  errorRate.add(!tablesOk);

  sleep(1);
}
