/**
 * k6 stress test — concurrent metadata reads + config reload
 *
 * Validates the thread-safe snapshot swap under real traffic.
 * The critical guarantee: zero 500s (CME/NPE) while config is reloading.
 *
 * Traffic mix (per VU per iteration):
 *   80% — GET /api/tables (metadata read — hits snapshotRef)
 *   20% — POST /api/admin/reload (config reload — swaps snapshotRef atomically)
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

const BASE_URL = __ENV.BASE_URL  || 'http://localhost:9044/fkblitz';
const USERNAME = __ENV.USERNAME  || 'admin';
const PASSWORD = __ENV.PASSWORD  || 'changeme';
const GROUP    = __ENV.GROUP     || 'localhost';
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
  const jar = http.cookieJar();
  const loginRes = http.post(
    `${BASE_URL}/api/login`,
    { username: USERNAME, password: PASSWORD },
    { jar }
  );
  if (loginRes.status !== 200) {
    throw new Error(`Login failed: HTTP ${loginRes.status}`);
  }
  const cookies = jar.cookiesForURL(`${BASE_URL}/api/login`);
  const sessionId = cookies['JSESSIONID'] ? cookies['JSESSIONID'][0].value : null;
  if (!sessionId) throw new Error('No JSESSIONID');
  return { sessionId };
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
    // 20%: admin reload — triggers snapshotRef swap
    const res = http.post(
      `${BASE_URL}/api/admin/reload`,
      null,
      { ...params, tags: { name: 'config-reload' } }
    );
    // 200 or 403 (if READ_ONLY role) are acceptable; 500 is not
    const ok = check(res, {
      'reload not 500': (r) => r.status !== 500,
    });
    errorRate.add(!ok);
  }

  sleep(0.1);
}
