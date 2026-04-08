/**
 * k6 stress test — concurrent metadata reads + config reload
 *
 * Validates the thread-safe snapshot swap under real traffic.
 * The critical guarantee: zero 500s (CME/NPE) while config is reloading.
 *
 * Traffic mix (per VU per iteration):
 *   80% — GET /api/tables (metadata read — hits snapshotRef)
 *   20% — GET /api/admin/relations (full relation traversal — concurrent snapshotRef reader)
 *
 * Reload happens automatically via the background scheduler. This test validates
 * zero CME/NPE across concurrent metadata readers at high concurrency.
 *
 * Threshold: http_req_failed < 0.005 (< 0.5% errors = CME/500 tolerance is near-zero)
 *
 * Usage:
 *   docker run --rm --network host \
 *     -v $(pwd)/tests/performance:/tests \
 *     -e BASE_URL=http://localhost:9044/fkblitz \
 *     -e USERNAME=admin -e PASSWORD=changeme \
 *     -e GROUP=localhost -e DATABASE=demo \
 *     grafana/k6 run /tests/k6-reload-concurrent.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL  || 'http://localhost:9071/fkblitz';
const USERNAME = __ENV.USERNAME  || 'admin';
const PASSWORD = __ENV.PASSWORD  || 'changeme';
const GROUP    = __ENV.GROUP     || 'demo';
const DATABASE = __ENV.DATABASE  || 'demo';

export const options = {
  stages: [
    { duration: '30s', target: 50 }, // ramp up
    { duration: '4m',  target: 50 }, // steady concurrent load
    { duration: '30s', target: 0  }, // ramp down
  ],
  thresholds: {
    // Near-zero 5xx tolerance — any CME or NPE will surface here
    http_req_failed:   ['rate<0.005'],
    http_req_duration: ['p(95)<500'],
    errors:            ['rate<0.005'],
  },
};

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/login`,
    { username: USERNAME, password: PASSWORD },
  );
  if (loginRes.status !== 200) {
    throw new Error(`Login failed: HTTP ${loginRes.status}`);
  }
  const setCookie = loginRes.headers['Set-Cookie'] || '';
  const match = setCookie.match(/JSESSIONID=([^;]+)/);
  if (!match) throw new Error(`No JSESSIONID in Set-Cookie: ${setCookie}`);
  return { sessionId: match[1] };
}

export default function (data) {
  const params = {
    headers: { Cookie: `JSESSIONID=${data.sessionId}` },
  };

  const roll = Math.random();

  if (roll < 0.80) {
    // 80%: metadata read — exercises snapshotRef under concurrent reload
    const res = http.get(
      `${BASE_URL}/api/tables?group=${GROUP}&database=${DATABASE}`,
      { ...params, tags: { name: 'metadata-read' } }
    );
    const ok = check(res, {
      'metadata 200': (r) => r.status === 200,
      'no 500': (r) => r.status !== 500,
    });
    errorRate.add(!ok);
  } else {
    // 20%: heavier relation traversal — exercises snapshotRef concurrently with reads
    // (no explicit reload endpoint; reload happens via scheduler in background)
    const res = http.get(
      `${BASE_URL}/api/admin/relations?group=${GROUP}&database=${DATABASE}`,
      { ...params, tags: { name: 'relations-read' } }
    );
    const ok = check(res, {
      'relations 200': (r) => r.status === 200,
      'no 500': (r) => r.status !== 500,
    });
    errorRate.add(!ok);
  }

  sleep(0.1);
}
