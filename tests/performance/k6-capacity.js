/**
 * k6 capacity benchmark — VU ladder
 *
 * Steps VUs from 10 → 200 in 6 stages (2 min each).
 * Measures latency at each concurrency level against the metadata endpoint.
 *
 * All resource ceilings must be set high before running (see docker-compose.yml):
 *   FKBLITZ_MAX_POOL_SIZE=100
 *   FKBLITZ_TOMCAT_THREADS_MAX=400
 *   JAVA_TOOL_OPTIONS=-Xmx1g
 *
 * Run alongside capacity-poll.sh which scrapes Prometheus for:
 *   hikaricp_connections_active, tomcat_threads_busy, jvm_memory_used_bytes
 *
 * Usage:
 *   # Terminal 1: poll Prometheus
 *   bash tests/performance/capacity-poll.sh > /tmp/capacity-metrics.csv &
 *
 *   # Terminal 2: run k6
 *   docker run --rm --network host \
 *     -v $(pwd)/tests/performance:/tests \
 *     grafana/k6 run /tests/k6-capacity.js 2>&1 | tee /tmp/capacity-k6.txt
 *
 *   # After both finish:
 *   bash tests/performance/capacity-report.sh /tmp/capacity-metrics.csv /tmp/capacity-k6.txt
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate   = new Rate('errors');
const groupsLatency = new Trend('latency_groups', true);
const tablesLatency = new Trend('latency_tables', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9071/fkblitz';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'changeme';
const GROUP    = __ENV.GROUP    || 'demo';
const DATABASE = __ENV.DATABASE || 'demo';

export const options = {
  stages: [
    { duration: '30s', target: 10  }, // ramp to 10 VUs
    { duration: '2m',  target: 10  }, // hold — baseline
    { duration: '30s', target: 25  }, // ramp to 25
    { duration: '2m',  target: 25  }, // hold
    { duration: '30s', target: 50  }, // ramp to 50
    { duration: '2m',  target: 50  }, // hold
    { duration: '30s', target: 100 }, // ramp to 100
    { duration: '2m',  target: 100 }, // hold
    { duration: '30s', target: 150 }, // ramp to 150
    { duration: '2m',  target: 150 }, // hold
    { duration: '30s', target: 200 }, // ramp to 200
    { duration: '2m',  target: 200 }, // hold
    { duration: '30s', target: 0   }, // ramp down
  ],
  thresholds: {
    // Capacity test — no hard pass/fail gates, just measure
    // (thresholds here just prevent k6 exit-code 1 from blocking CI)
    'http_req_failed': ['rate<0.10'],
  },
};

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/login`,
    { username: USERNAME, password: PASSWORD },
  );
  if (loginRes.status !== 200) {
    throw new Error(`Login failed: HTTP ${loginRes.status} — ${loginRes.body}`);
  }
  const setCookie = loginRes.headers['Set-Cookie'] || '';
  const match = setCookie.match(/JSESSIONID=([^;]+)/);
  if (!match) throw new Error(`No JSESSIONID in Set-Cookie: ${setCookie}`);
  return { sessionId: match[1] };
}

export default function (data) {
  const params = { headers: { Cookie: `JSESSIONID=${data.sessionId}` } };

  // Groups endpoint (lightweight)
  const t0 = Date.now();
  const groupsRes = http.get(`${BASE_URL}/api/groups`, { ...params, tags: { name: 'groups' } });
  groupsLatency.add(Date.now() - t0);
  const groupsOk = check(groupsRes, { 'groups 200': (r) => r.status === 200 });
  errorRate.add(!groupsOk);

  // Tables endpoint (touches snapshotRef)
  const t1 = Date.now();
  const tablesRes = http.get(
    `${BASE_URL}/api/tables?group=${GROUP}&database=${DATABASE}`,
    { ...params, tags: { name: 'tables' } }
  );
  tablesLatency.add(Date.now() - t1);
  const tablesOk = check(tablesRes, {
    'tables 200': (r) => r.status === 200,
    'no 500':     (r) => r.status !== 500,
  });
  errorRate.add(!tablesOk);

  sleep(0.5);
}
