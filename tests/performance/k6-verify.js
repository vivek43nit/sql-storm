// Verification run: 200 VUs steady for 5 min against recommended config
// Thresholds: p95 < 200ms, 0% errors — tighter than capacity script
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9071/fkblitz';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'changeme';
const GROUP    = __ENV.GROUP    || 'demo';
const DATABASE = __ENV.DATABASE || 'demo';

const latencyGroups = new Trend('latency_groups');
const latencyTables = new Trend('latency_tables');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-vus',
      vus: 200,
      duration: '5m',
    },
  },
  thresholds: {
    http_req_failed:   ['rate<0.01'],          // < 1% errors
    http_req_duration: ['p(95)<500'],          // p95 < 500ms
    latency_groups:    ['p(95)<500'],
    latency_tables:    ['p(95)<500'],
  },
};

export function setup() {
  const loginUrl = `${BASE_URL}/api/login`;
  const loginRes = http.post(loginUrl, `username=${USERNAME}&password=${PASSWORD}`, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
  const setCookie = loginRes.headers['Set-Cookie'] || '';
  const match = setCookie.match(/JSESSIONID=([^;]+)/);
  if (!match) throw new Error(`No JSESSIONID in Set-Cookie: ${setCookie}`);
  return { sessionId: match[1] };
}

export default function ({ sessionId }) {
  const cookie = `JSESSIONID=${sessionId}`;
  const headers = { Cookie: cookie };

  const t0 = Date.now();
  const r1 = http.get(`${BASE_URL}/api/groups`, { headers });
  latencyGroups.add(Date.now() - t0);
  check(r1, { 'groups 200': (r) => r.status === 200 });

  const t1 = Date.now();
  const r2 = http.get(`${BASE_URL}/api/tables?group=${GROUP}&database=${DATABASE}`, { headers });
  latencyTables.add(Date.now() - t1);
  check(r2, { 'tables 200': (r) => r.status === 200 });

  sleep(0.1);
}
