# Capacity Benchmarking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register every fkblitz HikariCP pool with Micrometer under a distinct, meaningful name; expose Tomcat thread metrics; then run a VU-ladder k6 benchmark that produces a config recommendation table.

**Architecture:** HikariCP 5.x ships `MicrometerMetricsTrackerFactory` — set it on `HikariConfig` before pool creation in `DatabaseConnectionManager`, `DbConfigLoader`, and `RelationRowDbLoader`. Pool names follow a single convention: `fkblitz-{role}-{qualifier}`. When a pool is removed via hot-reload, deregister its Micrometer meters before closing to prevent stale metrics and duplicate-registration bugs on re-creation.

**Tech Stack:** Spring Boot / HikariCP 5.1.0 / Micrometer 1.14 / Prometheus / k6 / bash

---

## Current broken state (pre-conditions)

| File | Problem |
|---|---|
| `FkBlitzMetrics.java` | References `getActiveConnectionCount()` and `getMaxConnectionCount()` — both deleted → **compile error** |
| 5 test files | `new DatabaseConnectionManager(loader, 5)` — constructor now has 3 params → **latent compile error** |
| `DatabaseConnectionManager.java` | `MeterRegistry` field present, import added, but `createDataSource()` never calls `setMetricsTrackerFactory()` → Micrometer not wired |
| `application.yml` | Missing `server.tomcat.mbeanregistry.enabled`, missing H2 pool name |

---

## Pool Naming Convention

All HikariCP pools must follow: `fkblitz-{role}-{qualifier}`

| Pool | Role | Qualifier | Final name | Max size |
|---|---|---|---|---|
| Spring Boot H2 auth | `auth` | — | `fkblitz-auth` | 10 (Spring default) |
| DbConfigLoader (connection config) | `config` | `db-{table}` | `fkblitz-config-db-{table}` | 2 |
| DbConfigLoader (custom-mapping) | `config` | `db-{table}` | `fkblitz-config-db-{table}` | 2 |
| RelationRowDbLoader | `config` | `relation` | `fkblitz-config-relation` | 2 |
| User data connections | `data` | `{group}-{dbName}` | `fkblitz-data-{group}-{dbName}` | global default (100 for benchmark) |

---

## File Map

| File | Action | What changes |
|---|---|---|
| `backend/src/main/java/com/vivek/sqlstorm/config/connection/ConnectionDTO.java` | Modify | `maxPoolSize` default `-1` (sentinel for "use global") |
| `backend/src/main/java/com/vivek/metrics/FkBlitzMetrics.java` | Modify | Remove two custom connection gauges + `DatabaseConnectionManager` param |
| `backend/src/main/java/com/vivek/sqlstorm/connection/DatabaseConnectionManager.java` | Modify | Wire `MicrometerMetricsTrackerFactory`; rename pool; add `deregisterPoolMetrics()`; call it on remove/replace |
| `backend/src/main/java/com/vivek/sqlstorm/config/loader/DbConfigLoader.java` | Modify | Add `@Nullable MeterRegistry` param; rename pool; wire tracker factory |
| `backend/src/main/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoader.java` | Modify | Add `@Nullable MeterRegistry` param; rename pool; wire tracker factory |
| `backend/src/main/java/com/vivek/config/ConfigLoaderConfig.java` | Modify | Add `MeterRegistry` param to both `@Bean` methods; pass to loader constructors |
| `backend/src/main/resources/application.yml` | Modify | H2 pool name `fkblitz-auth`; Tomcat MBean registry; pool/thread env vars (already done) |
| `backend/src/test/java/com/vivek/sqlstorm/connection/DatabaseConnectionManagerTest.java` | Modify | Add `null` as 3rd constructor arg |
| `backend/src/test/java/com/vivek/sqlstorm/integration/AbstractMariaDbContainerTest.java` | Modify | Add `null` as 3rd constructor arg |
| `backend/src/test/java/com/vivek/sqlstorm/metadata/DatabaseMetaDataManagerMariaDbTest.java` | Modify | Add `null` as 3rd constructor arg |
| `backend/src/test/java/com/vivek/sqlstorm/metadata/DatabaseMetaDataManagerConcurrencyTest.java` | Modify | Add `null` as 3rd constructor arg |
| `backend/src/test/java/com/vivek/sqlstorm/metadata/DatabaseMetaDataManagerTest.java` | Modify | Add `null` as 3rd constructor arg (2 callsites) |
| `backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderTest.java` | Modify | Add `null` as last constructor arg (2 callsites) |
| `backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderMariaDbTest.java` | Modify | Add `null` as last constructor arg |
| `backend/src/test/java/com/vivek/sqlstorm/config/loader/DbConfigLoaderTest.java` | Modify | Add `null` as last constructor arg (3 callsites) |
| `tests/performance/capacity-poll.sh` | Modify | Correct Prometheus metric names |
| `tests/performance/capacity-report.sh` | No change | Already correct |

---

## Task 1: Fix FkBlitzMetrics (compile error — must go first)

**Files:**
- Modify: `backend/src/main/java/com/vivek/metrics/FkBlitzMetrics.java`

Remove the two custom connection gauges and `DatabaseConnectionManager` dependency. HikariCP's native Micrometer integration now provides per-pool metrics labeled by `pool` tag.

- [ ] **Step 1: Rewrite FkBlitzMetrics.java**

```java
package com.vivek.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Central point for recording FkBlitz-specific metrics.
 *
 * <p>Connection pool metrics are now exported natively by HikariCP's Micrometer
 * integration, labeled by pool name:
 *   hikaricp_connections_active{pool="fkblitz-data-{group}-{db}"}
 *   hikaricp_connections_max{pool="fkblitz-data-{group}-{db}"}
 *   hikaricp_connections_pending{pool="fkblitz-data-{group}-{db}"}
 *   hikaricp_connections_active{pool="fkblitz-auth"}
 *   hikaricp_connections_active{pool="fkblitz-config-relation"}
 *   hikaricp_connections_active{pool="fkblitz-config-db-{table}"}
 * </p>
 */
@Component
public class FkBlitzMetrics {

  private final MeterRegistry registry;

  public FkBlitzMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordQuerySuccess(String group, String database, long durationMs) {
    queryTimer(group, database).record(durationMs, TimeUnit.MILLISECONDS);
    queryCounter(group, database, "success").increment();
  }

  public void recordQueryError(String group, String database) {
    queryCounter(group, database, "error").increment();
  }

  public void recordAuthFailure() {
    Counter.builder("fkblitz.auth.failures")
        .description("Number of failed authentication attempts")
        .register(registry)
        .increment();
  }

  public void recordCrudOperation(String operation, String table) {
    Counter.builder("fkblitz.crud.operations")
        .description("Row mutation operations (add/edit/delete)")
        .tag("operation", operation)
        .tag("table", table)
        .register(registry)
        .increment();
  }

  private Timer queryTimer(String group, String database) {
    return Timer.builder("fkblitz.query.duration")
        .description("Query execution duration")
        .tag("group", group)
        .tag("database", database)
        .publishPercentileHistogram(true)
        .register(registry);
  }

  private Counter queryCounter(String group, String database, String status) {
    return Counter.builder("fkblitz.query.requests")
        .description("Total query requests")
        .tag("group", group)
        .tag("database", database)
        .tag("status", status)
        .register(registry);
  }
}
```

- [ ] **Step 2: Verify compile**

```bash
cd backend && mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

---

## Task 2: Fix ConnectionDTO pool size sentinel

**Files:**
- Modify: `backend/src/main/java/com/vivek/sqlstorm/config/connection/ConnectionDTO.java`

Change the default from `5` (a magic number also used as the hardcoded DTO value) to `-1` (unambiguous sentinel for "not set — use global default"). Positive value always means explicit per-connection override.

- [ ] **Step 1: Change default in ConnectionDTO**

Find:
```java
private int maxPoolSize = 5;
```
Replace with:
```java
private int maxPoolSize = -1;
```

- [ ] **Step 2: Update the condition in DatabaseConnectionManager.createDataSource()**

Find:
```java
int poolSize = (config.getMaxPoolSize() != 5) ? config.getMaxPoolSize() : defaultMaxPoolSize;
```
Replace with:
```java
int poolSize = (config.getMaxPoolSize() > 0) ? config.getMaxPoolSize() : defaultMaxPoolSize;
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

---

## Task 3: Wire Micrometer into DatabaseConnectionManager

**Files:**
- Modify: `backend/src/main/java/com/vivek/sqlstorm/connection/DatabaseConnectionManager.java`

Three changes: (1) rename pool to `fkblitz-data-{group}-{db}`; (2) attach `MicrometerMetricsTrackerFactory` on creation; (3) deregister meters before closing a pool to prevent stale metrics and duplicate-registration bugs on re-creation.

- [ ] **Step 1: Update `createDataSource()` — rename pool and wire tracker**

```java
private HikariDataSource createDataSource(ConnectionDTO config) {
    HikariConfig hk = new HikariConfig();
    hk.setJdbcUrl(config.getDatabaseURL());
    hk.setUsername(config.getUser());
    hk.setPassword(config.getPassword());
    hk.setDriverClassName(config.getDriverClassName());
    int poolSize = (config.getMaxPoolSize() > 0) ? config.getMaxPoolSize() : defaultMaxPoolSize;
    hk.setMaximumPoolSize(poolSize);
    hk.setMinimumIdle(1);
    hk.setConnectionTimeout(30_000);
    hk.setMaxLifetime(configs.getConnectionExpiryTime());
    hk.setPoolName("fkblitz-data-" + config.getGroup() + "-" + config.getDbName());
    if (meterRegistry != null) {
        hk.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
    }
    return new HikariDataSource(hk);
}
```

- [ ] **Step 2: Add `deregisterPoolMetrics()` method**

Add after `createDataSource()`:

```java
/**
 * Removes all Micrometer meters for a named pool from the registry.
 * Must be called before closing a pool — Micrometer won't auto-deregister,
 * and re-creating a same-named pool would bind to the stale meters.
 */
private void deregisterPoolMetrics(String poolName) {
    if (meterRegistry == null) return;
    meterRegistry.getMeters().stream()
        .filter(m -> poolName.equals(m.getId().getTag("pool")))
        .forEach(meterRegistry::remove);
}
```

- [ ] **Step 3: Call `deregisterPoolMetrics` in `reloadConnections()` — removal case**

Find:
```java
connectionMap.forEach((group, dbs) ->
    dbs.entrySet().removeIf(e -> {
        if (!newKeys.contains(group + "::" + e.getKey())) {
            e.getValue().closePool();
            log.info("Removed connection group={} db={}", group, e.getKey());
            return true;
        }
        return false;
    }));
```
Replace with:
```java
connectionMap.forEach((group, dbs) ->
    dbs.entrySet().removeIf(e -> {
        if (!newKeys.contains(group + "::" + e.getKey())) {
            deregisterPoolMetrics("fkblitz-data-" + group + "-" + e.getKey());
            e.getValue().closePool();
            log.info("Removed connection group={} db={}", group, e.getKey());
            return true;
        }
        return false;
    }));
```

- [ ] **Step 4: Call `deregisterPoolMetrics` in `reloadConnections()` — credential-change case**

Find:
```java
} else if (!sameConnectionDetails(existing.getConfig(), dto)) {
    existing.closePool();
    groupMap.put(dto.getDbName(), new ConnectionInfo(dto, createDataSource(dto)));
    log.info("Updated connection details group={} db={}", dto.getGroup(), dto.getDbName());
```
Replace with:
```java
} else if (!sameConnectionDetails(existing.getConfig(), dto)) {
    deregisterPoolMetrics("fkblitz-data-" + dto.getGroup() + "-" + dto.getDbName());
    existing.closePool();
    groupMap.put(dto.getDbName(), new ConnectionInfo(dto, createDataSource(dto)));
    log.info("Updated connection details group={} db={}", dto.getGroup(), dto.getDbName());
```

- [ ] **Step 5: Verify compile**

```bash
mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

---

## Task 4: Wire Micrometer into DbConfigLoader and RelationRowDbLoader

**Files:**
- Modify: `backend/src/main/java/com/vivek/sqlstorm/config/loader/DbConfigLoader.java`
- Modify: `backend/src/main/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoader.java`
- Modify: `backend/src/main/java/com/vivek/config/ConfigLoaderConfig.java`

Both loaders create their own HikariCP pools (max 2, for config polling). Add `@Nullable MeterRegistry` as the last constructor parameter, rename pools, wire tracker factory. Then pass the registry from `ConfigLoaderConfig`.

- [ ] **Step 1: Update DbConfigLoader constructor**

Add import at top of `DbConfigLoader.java`:
```java
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.lang.Nullable;
```

Replace constructor signature and pool block:
```java
public DbConfigLoader(String jdbcUrl,
                      String username,
                      String password,
                      String table,
                      String column,
                      String format,
                      ConfigParserInterface<T> parser,
                      @Nullable MeterRegistry meterRegistry) {
    this.table = table;
    this.column = column;
    this.fileExtension = format;
    this.parser = parser;

    HikariConfig hk = new HikariConfig();
    hk.setJdbcUrl(jdbcUrl);
    hk.setUsername(username);
    hk.setPassword(password);
    hk.setMaximumPoolSize(2);
    hk.setMinimumIdle(1);
    hk.setConnectionTimeout(30_000);
    hk.setPoolName("fkblitz-config-db-" + table);
    if (meterRegistry != null) {
        hk.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
    }
    this.dataSource = new HikariDataSource(hk);
}
```

- [ ] **Step 2: Update RelationRowDbLoader constructor**

Add same imports to `RelationRowDbLoader.java`, then:

```java
public RelationRowDbLoader(String jdbcUrl, String username, String password,
                           String table, @Nullable MeterRegistry meterRegistry) {
    this.table = table;
    HikariConfig hk = new HikariConfig();
    hk.setJdbcUrl(jdbcUrl);
    hk.setUsername(username);
    hk.setPassword(password);
    hk.setMaximumPoolSize(2);
    hk.setMinimumIdle(1);
    hk.setConnectionTimeout(30_000);
    hk.setPoolName("fkblitz-config-relation");
    if (meterRegistry != null) {
        hk.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
    }
    this.dataSource = new HikariDataSource(hk);
}
```

- [ ] **Step 3: Update ConfigLoaderConfig — pass MeterRegistry to both @Bean methods**

Add import:
```java
import io.micrometer.core.instrument.MeterRegistry;
```

Update `connectionConfigLoader`:
```java
@Bean
public ConfigLoaderStrategy<ConnectionConfig> connectionConfigLoader(
        FkBlitzConfigProperties props, MeterRegistry meterRegistry) {
    // ...
    case "db" -> {
        validateDbConfig(src.getDb(), "connection");
        yield new DbConfigLoader<>(
                src.getDb().getUrl(),
                src.getDb().getUsername(),
                src.getDb().getPassword(),
                src.getDb().getTable(),
                src.getDb().getColumn(),
                src.getDb().getFormat(),
                parserForConnectionFormat(src.getDb().getFormat()),
                meterRegistry);                               // ← added
    }
    // file/api cases unchanged — no HikariCP pool to instrument
```

Update `customMappingConfigLoader`:
```java
@Bean
public ConfigLoaderStrategy<CustomRelationConfig> customMappingConfigLoader(
        FkBlitzConfigProperties props, MeterRegistry meterRegistry) {
    // ...
    case "db" -> {
        validateDbConfig(src.getDb(), "custom-mapping");
        yield new DbConfigLoader<>(
                src.getDb().getUrl(),
                src.getDb().getUsername(),
                src.getDb().getPassword(),
                src.getDb().getTable(),
                src.getDb().getColumn(),
                src.getDb().getFormat(),
                new CustomRelationConfigJsonParser(),
                meterRegistry);                               // ← added
    }
    case "relation-table" -> {
        validateDbConfig(src.getDb(), "custom-mapping");
        yield new RelationRowDbLoader(
                src.getDb().getUrl(),
                src.getDb().getUsername(),
                src.getDb().getPassword(),
                src.getDb().getTable(),
                meterRegistry);                               // ← added
    }
```

- [ ] **Step 4: Verify compile**

```bash
mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

---

## Task 5: Fix all test callsites

**Files:** 8 test files (see file map)

- [ ] **Step 1: Fix DatabaseConnectionManager tests (5 files) — add null as 3rd arg**

```bash
sed -i '' 's/new DatabaseConnectionManager(loader, 5)/new DatabaseConnectionManager(loader, 5, null)/g' \
  backend/src/test/java/com/vivek/sqlstorm/connection/DatabaseConnectionManagerTest.java \
  backend/src/test/java/com/vivek/sqlstorm/integration/AbstractMariaDbContainerTest.java \
  backend/src/test/java/com/vivek/sqlstorm/metadata/DatabaseMetaDataManagerMariaDbTest.java

sed -i '' 's/new DatabaseConnectionManager(connLoader, 5)/new DatabaseConnectionManager(connLoader, 5, null)/g' \
  backend/src/test/java/com/vivek/sqlstorm/metadata/DatabaseMetaDataManagerConcurrencyTest.java \
  backend/src/test/java/com/vivek/sqlstorm/metadata/DatabaseMetaDataManagerTest.java
```

- [ ] **Step 2: Fix DbConfigLoader tests (3 callsites) — add null as last arg**

```bash
# DbConfigLoaderTest.java has 3 callsites — add null after the parser arg
sed -i '' 's/new DbConfigLoader<>(JDBC_URL, USER, PASS, TABLE, COLUMN, "json",\(.*\))/new DbConfigLoader<>(JDBC_URL, USER, PASS, TABLE, COLUMN, "json",\1, null)/g' \
  backend/src/test/java/com/vivek/sqlstorm/config/loader/DbConfigLoaderTest.java
```

If the sed pattern doesn't match (multiline), edit manually — each callsite appends `, null` before the closing `)`:
```java
// Before:
new DbConfigLoader<>(JDBC_URL, USER, PASS, TABLE, COLUMN, "json", parser)
// After:
new DbConfigLoader<>(JDBC_URL, USER, PASS, TABLE, COLUMN, "json", parser, null)
```

- [ ] **Step 3: Fix RelationRowDbLoader tests (2 callsites) — add null as last arg**

```bash
sed -i '' 's/new RelationRowDbLoader(JDBC_URL, USER, PASS, TABLE)/new RelationRowDbLoader(JDBC_URL, USER, PASS, TABLE, null)/g' \
  backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderTest.java \
  backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderMariaDbTest.java
```

- [ ] **Step 4: Clean compile of main + tests**

```bash
mvn clean test-compile 2>&1 | grep -E "BUILD|ERROR" | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Run unit tests (no Docker)**

```bash
mvn test -Dtest="!*MariaDb*,!*ContainerTest*" 2>&1 | grep -E "Tests run|BUILD|FAIL" | tail -10
```
Expected: all pass, `BUILD SUCCESS`

- [ ] **Step 6: Commit Tasks 1–5**

```bash
git add backend/src/main/java/com/vivek/metrics/FkBlitzMetrics.java \
        backend/src/main/java/com/vivek/sqlstorm/config/connection/ConnectionDTO.java \
        backend/src/main/java/com/vivek/sqlstorm/connection/DatabaseConnectionManager.java \
        backend/src/main/java/com/vivek/sqlstorm/config/loader/DbConfigLoader.java \
        backend/src/main/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoader.java \
        backend/src/main/java/com/vivek/config/ConfigLoaderConfig.java \
        backend/src/test/
git commit -m "feat(metrics): register all HikariCP pools with Micrometer; distinct names per flow"
```

---

## Task 6: application.yml — pool name + Tomcat MBean registry

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Add H2 auth pool name under `spring.datasource`**

Find:
```yaml
  datasource:
    url: jdbc:h2:mem:fkblitz_auth;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
```
Add below `password:`:
```yaml
    hikari:
      pool-name: fkblitz-auth
```

- [ ] **Step 2: Add `mbeanregistry.enabled` under `server.tomcat`**

Find:
```yaml
  tomcat:
    threads:
      max: ${FKBLITZ_TOMCAT_THREADS_MAX:200}
      min-spare: ${FKBLITZ_TOMCAT_THREADS_MIN:10}
```
Add:
```yaml
  tomcat:
    threads:
      max: ${FKBLITZ_TOMCAT_THREADS_MAX:200}
      min-spare: ${FKBLITZ_TOMCAT_THREADS_MIN:10}
    mbeanregistry:
      enabled: true
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "feat(metrics): name H2 auth pool fkblitz-auth; enable Tomcat MBean registry"
```

---

## Task 7: Build Docker image and verify all metrics

**Files:** none

- [ ] **Step 1: Build and restart on port 9071**

```bash
docker compose build fkblitz 2>&1 | tail -5
FKBLITZ_PORT=9071 docker compose up -d --force-recreate fkblitz
```

- [ ] **Step 2: Wait for healthy**

```bash
for i in $(seq 1 18); do
  curl -s http://localhost:9071/fkblitz/actuator/health/liveness 2>/dev/null | grep -q "UP" && echo "UP" && break
  echo "waiting... ($i)"; sleep 5
done
```
Expected: `UP` within 90s

- [ ] **Step 3: Trigger a query to activate the data pool**

```bash
curl -s -X POST http://localhost:9071/fkblitz/api/login \
  -d "username=admin&password=changeme" -c /tmp/cap.txt -o /dev/null
curl -s "http://localhost:9071/fkblitz/api/tables?group=demo&database=demo" \
  -b /tmp/cap.txt -o /dev/null
```

- [ ] **Step 4: Verify all 5 pools appear in Prometheus**

```bash
curl -s http://localhost:9071/fkblitz/actuator/prometheus | \
  grep "hikaricp_connections_max" | grep "fkblitz"
```
Expected (only data + auth pools active by default; config pools only appear when `source: db`):
```
hikaricp_connections_max{...,pool="fkblitz-auth",...}              10.0
hikaricp_connections_max{...,pool="fkblitz-data-demo-demo",...}   100.0
```

- [ ] **Step 5: Verify Tomcat thread metrics**

```bash
curl -s http://localhost:9071/fkblitz/actuator/prometheus | \
  grep -E "tomcat_threads_(busy|config_max)"
```
Expected:
```
tomcat_threads_busy_threads{...}           N.0
tomcat_threads_config_max_threads{...}   400.0
```

- [ ] **Step 6: Verify Prometheus scrape is healthy**

```bash
curl -s 'http://localhost:9090/api/v1/targets' | \
  python3 -c "import sys,json; d=json.load(sys.stdin); \
  [print(t['scrapeUrl'], t['health']) for t in d['data']['activeTargets'] if 'fkblitz' in t['scrapeUrl']]"
```
Expected: `http://fkblitz:9044/fkblitz/actuator/prometheus up`

---

## Task 8: Update capacity-poll.sh metric names

**Files:**
- Modify: `tests/performance/capacity-poll.sh`

- [ ] **Step 1: Replace the polling block with correct metric names**

```bash
  # Data pools only — excludes auth (fkblitz-auth) and config pools (fkblitz-config-*)
  HIKARI_ACTIVE=$(query_instant 'sum(hikaricp_connections_active{pool=~"fkblitz-data-.*"})')
  HIKARI_MAX=$(query_instant 'sum(hikaricp_connections_max{pool=~"fkblitz-data-.*"})')
  TOMCAT_BUSY=$(query_instant 'tomcat_threads_busy_threads')
  TOMCAT_MAX=$(query_instant 'tomcat_threads_config_max_threads')
  HEAP_USED=$(query_instant 'sum(jvm_memory_used_bytes{area="heap"})' | \
    python3 -c "import sys; v=sys.stdin.read().strip(); print(f'{float(v)/1048576:.1f}' if v else '0')" 2>/dev/null || echo "0")
  HEAP_MAX=$(query_instant 'sum(jvm_memory_max_bytes{area="heap"})' | \
    python3 -c "import sys; v=sys.stdin.read().strip(); print(f'{float(v)/1048576:.1f}' if v else '0')" 2>/dev/null || echo "0")
```

- [ ] **Step 2: Smoke-test (3 polls, then Ctrl-C)**

```bash
bash tests/performance/capacity-poll.sh 2>/dev/null | head -4
```
Expected:
```
timestamp,hikari_active,hikari_max,tomcat_busy,tomcat_max,heap_mb,heap_max_mb
2026-...,0,100,5,400,280.1,1024.0
```

- [ ] **Step 3: Commit**

```bash
git add tests/performance/capacity-poll.sh
git commit -m "feat(perf): fix capacity-poll.sh metric names for fkblitz pool convention"
```

---

## Task 9: Run the capacity benchmark

**Files:** none

- [ ] **Step 1: Start Prometheus polling in background**

```bash
bash tests/performance/capacity-poll.sh > /tmp/capacity-metrics.csv &
POLL_PID=$!
echo "Polling PID: $POLL_PID"
```

- [ ] **Step 2: Run k6 VU ladder (~16 min)**

```bash
docker run --rm --network host \
  -v $(pwd)/tests/performance:/tests \
  grafana/k6 run /tests/k6-capacity.js 2>&1 | tee /tmp/capacity-k6.txt
```

- [ ] **Step 3: Stop polling**

```bash
kill $POLL_PID
```

- [ ] **Step 4: Generate report**

```bash
bash tests/performance/capacity-report.sh /tmp/capacity-metrics.csv /tmp/capacity-k6.txt
```
Expected: watermark table + recommended `FKBLITZ_MAX_POOL_SIZE`, `FKBLITZ_TOMCAT_THREADS_MAX`, `-Xmx`.

- [ ] **Step 5: Update README baselines**

Fill in `tests/performance/README.md` with measured p50/p95/p99 values from `/tmp/capacity-k6.txt`.

- [ ] **Step 6: Commit**

```bash
git add tests/performance/README.md
git commit -m "docs(perf): add capacity benchmark baselines and config recommendations"
```

---

## Self-Review

**Spec coverage:**
- ✅ Register all HikariCP pools with Micrometer natively → Tasks 3, 4
- ✅ Distinct meaningful pool name per flow → Pool naming convention + Tasks 3, 4, 6
- ✅ Deregister meters before pool close (stale/duplicate-registration bug) → Task 3
- ✅ ConnectionDTO sentinel `-1` (cleaner than magic `5`) → Task 2
- ✅ Tomcat thread metrics → Task 6
- ✅ Fix broken compile state → Tasks 1, 5
- ✅ Fix all test callsites (8 files) → Task 5
- ✅ capacity-poll.sh correct metric names → Task 8
- ✅ Run benchmark and produce recommendation → Task 9

**Placeholder scan:** None. All code blocks are complete. All `sed` commands include exact strings. All expected outputs are specified.

**Type consistency:**
- Pool name prefix `fkblitz-data-` used in `createDataSource()` (Task 3 Step 1) and `deregisterPoolMetrics()` calls (Task 3 Steps 3–4) — consistent.
- `MicrometerMetricsTrackerFactory` from `com.zaxxer.hikari.metrics.micrometer` — verified present in HikariCP 5.1.0 jar.
- `@Nullable` from `org.springframework.lang` — already imported in `DatabaseConnectionManager`.
- Constructor arg order: `DbConfigLoader(..., parser, meterRegistry)` — `meterRegistry` last in Task 4 Step 1, `null` appended last in Task 5 Step 2 — consistent.
- `RelationRowDbLoader(url, user, pass, table, meterRegistry)` — 5 args in Task 4 Step 2, `null` appended as 5th in Task 5 Step 3 — consistent.

**Pre-condition ordering:** Tasks 1–2 unblock compilation. Tasks 3–4 add features. Task 5 fixes tests and verifies. Task 6 is config-only. Task 7 gates on Docker build. Tasks 8–9 run the benchmark.
