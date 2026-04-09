# FkBlitz — Enterprise Configuration Guide

This guide covers everything needed to run FkBlitz in a production team environment: authentication, access control, observability, Kubernetes deployment, and release management.

For getting started locally, see the [README](../README.md).

---

## Contents

1. [Authentication & Access Control](#1-authentication--access-control)
2. [Sensitive Columns](#2-sensitive-columns)
3. [Custom Relations (custom_mapping.json)](#3-custom-relations-custom_mappingjson)
4. [Database Connection Reference](#4-database-connection-reference)
5. [Remote Config Loading](#5-remote-config-loading)
6. [Redis](#6-redis)
7. [Observability](#7-observability)
8. [Kubernetes & Helm](#8-kubernetes--helm)
9. [API Documentation](#9-api-documentation)
10. [Security Hardening](#10-security-hardening)
11. [Releasing](#11-releasing)
12. [Scaling](#12-scaling)

---

## 1. Authentication & Access Control

### Roles

| Role | Can do |
|------|--------|
| `ADMIN` | Everything: queries, CRUD, user management, actuator, Swagger UI |
| `READ_WRITE` | Execute queries + add/edit/delete rows |
| `READ_ONLY` | Execute queries only — no mutations |

Additional permission groups (assigned alongside a base role):

| Permission | Effect |
|------------|--------|
| `SENSITIVE_DATA_RO` | Can view columns marked as sensitive (value shown) |
| `SENSITIVE_DATA_RW` | Can view **and** write to sensitive columns |
| *(neither)* | Sensitive column values replaced with `••••••` |

### User Store Backends

Configured via `fkblitz.auth.user-store` in `application.yml`:

#### `h2` (default — embedded, zero config)

```yaml
fkblitz:
  auth:
    user-store: h2
    admin-user: ${FKBLITZ_ADMIN_USER:admin}
    admin-password: ${FKBLITZ_ADMIN_PASSWORD:changeme}
```

In-memory H2 database. Data is lost on restart unless you configure a file URL. Good for single-node dev or demo deployments.

#### `mysql` (recommended for teams)

```yaml
fkblitz:
  auth:
    user-store: mysql
    admin-user: ${FKBLITZ_ADMIN_USER:admin}
    admin-password: ${FKBLITZ_ADMIN_PASSWORD:changeme}
spring:
  datasource:
    url: jdbc:mysql://auth-db:3306/fkblitz_auth
    username: ${FKBLITZ_AUTH_DB_USER}
    password: ${FKBLITZ_AUTH_DB_PASSWORD}
```

Schema is auto-created on first start. Uses an isolated datasource — does not conflict with monitored database connections.

#### `config` (static users, no management API)

```yaml
fkblitz:
  auth:
    user-store: config
    users:
      - username: alice
        password: "{bcrypt}$2a$10$..."
        role: READ_WRITE
        permissions: SENSITIVE_DATA_RO
      - username: readonly
        password: "{noop}secret"
        role: READ_ONLY
        permissions: ""
```

Users are read from config at startup. Adding a user requires a restart. The user management API (`/api/admin/users`) is disabled in this mode.

#### `external-api` (delegate to a corporate directory)

```yaml
fkblitz:
  auth:
    user-store: external-api
    external-api:
      url: https://auth.internal/verify
      token: ${FKBLITZ_AUTH_API_TOKEN:}   # Bearer token for the external service
      role-claim: role                    # JSON field in the response carrying the role
      timeout-seconds: 5
```

On login, FkBlitz POSTs `{"username":"...","password":"..."}` to the configured URL and expects:

```json
{ "authenticated": true, "role": "READ_WRITE" }
```

On timeout or non-2xx, login fails closed.

### User Management API

Available when `user-store` is `h2` or `mysql`. Requires `ADMIN` role.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/admin/users` | List all users |
| `POST` | `/api/admin/users` | Create a user |
| `PUT` | `/api/admin/users/{id}` | Update role, permissions, or password |
| `DELETE` | `/api/admin/users/{id}` | Delete a user |

Example — create a user:

```sh
curl -u admin:changeme -X POST http://localhost:9044/fkblitz/api/admin/users \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"s3cret","role":"READ_WRITE","permissions":"SENSITIVE_DATA_RO"}'
```

### OAuth2 / OIDC

Works alongside local auth. Users sign in via their provider and are assigned a role from a configurable claim.

```yaml
fkblitz:
  auth:
    oauth2:
      enabled: true
      role-claim: fkblitz_role   # claim name in the provider's user-info response
      default-role: READ_ONLY    # fallback when claim is absent
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
```

When `oauth2.enabled: true`, a "Sign in with Google / GitHub" button appears on the login page. Local form login remains active in parallel.

---

## 2. Sensitive Columns

Mark columns as sensitive in `custom_mapping.json`. Users without `SENSITIVE_DATA_RO` see `••••••`; users without `SENSITIVE_DATA_RW` cannot write to those columns.

```json
{
  "sensitiveColumns": [
    { "database": "prod",  "table": "users",         "column": "password_hash" },
    { "database": "prod",  "table": "users",         "column": "ssn" },
    { "database": "*",     "table": "payment_cards", "column": "cvv" }
  ]
}
```

- `"database": "*"` applies the rule across all databases.
- Masking is enforced server-side — values never leave the backend for unauthorised users.
- Write guard: `POST /api/row/add` and `PUT /api/row/edit` return `403` if the payload contains a sensitive column and the user lacks `SENSITIVE_DATA_RW`.

---

## 3. Custom Relations (custom_mapping.json)

`custom_mapping.json` extends the schema FkBlitz reads from `INFORMATION_SCHEMA` with soft/virtual FK relationships that are not enforced at the database level. It also configures many-to-many junction tables, column auto-expansion, and sensitive column masking.

### File location

FkBlitz searches for the file in this order:

1. `/etc/fkblitz/custom_mapping.json`
2. `~/.fkblitz/custom_mapping.json`
3. `~/custom_mapping.json`
4. Classpath fallback (bundled empty file)

### Top-level structure

```json
{
  "databases": {
    "<database-name>": {
      "relations":      [...],
      "mapping_tables": {...},
      "auto_resolve":   {...}
    }
  },
  "sensitiveColumns": [...]
}
```

---

### `relations` — custom foreign key definitions

Each entry declares one virtual FK link. FkBlitz shows it alongside real FK relationships in the UI.

| Field | Required | Description |
|---|---|---|
| `table_name` | Yes | Source table |
| `table_column` | Yes | Source column holding the reference value |
| `referenced_table_name` | Yes | Target table |
| `referenced_column_name` | Yes | Target column being referenced |
| `referenced_database_name` | No | Target database (defaults to the enclosing `databases` key) |
| `conditions` | No | Extra filter — see [Conditions](#conditions) below |

**Same-database example:**

```json
{
  "databases": {
    "mydb": {
      "relations": [
        {
          "table_name":             "audit_log",
          "table_column":           "entity_id",
          "referenced_table_name":  "orders",
          "referenced_column_name": "id"
        }
      ]
    }
  }
}
```

**Cross-database example** (orders in `ops_db` referencing users in `auth_db`):

```json
{
  "databases": {
    "ops_db": {
      "relations": [
        {
          "table_name":               "orders",
          "table_column":             "created_by",
          "referenced_database_name": "auth_db",
          "referenced_table_name":    "users",
          "referenced_column_name":   "id"
        }
      ]
    }
  }
}
```

---

### Conditions

`conditions` is an optional JSON object on a relation that adds column filters when navigating back-references. Keys are column names on the **source** table; values are the required values.

**Exact match** — only show `audit_log` rows where `entity_type = 'ORDER'`:

```json
{
  "table_name":             "audit_log",
  "table_column":           "entity_id",
  "referenced_table_name":  "orders",
  "referenced_column_name": "id",
  "conditions": {
    "entity_type": "ORDER"
  }
}
```

This appends `AND entity_type='ORDER'` to the back-reference query.

**IN list** — match any value from a set:

```json
{
  "conditions": {
    "status": ["pending", "processing"]
  }
}
```

This appends `AND status IN ('pending','processing')`.

Multiple keys are ANDed together. String values are exact-match; array values become `IN (...)`.

---

### `mapping_tables` — many-to-many junction tables

Declares junction tables so FkBlitz can navigate through them transparently.

| Field | Required | Description |
|---|---|---|
| `type` | Yes | `ONE_TO_ONE`, `ONE_TO_MANY`, or `MANY_TO_MANY` |
| `from` | Yes | Column referencing the left-hand entity |
| `to` | Yes | Column referencing the right-hand entity |
| `include-self` | No | If `true`, include the junction row itself in results (default: `false`) |

```json
{
  "databases": {
    "mydb": {
      "mapping_tables": {
        "order_tags": {
          "type":         "MANY_TO_MANY",
          "from":         "order_id",
          "to":           "tag_id",
          "include-self": false
        }
      }
    }
  }
}
```

Navigating `orders.id` will now jump through `order_tags` to `tags` automatically.

---

### `auto_resolve` — automatic column expansion

Lists columns that FkBlitz should auto-expand when tracing a row, without requiring a click.

```json
{
  "databases": {
    "mydb": {
      "auto_resolve": {
        "orders": ["user_id", "product_id"]
      }
    }
  }
}
```

When a `Trace` is triggered on a row in `orders`, `user_id` and `product_id` are followed automatically.

---

### Full example

```json
{
  "databases": {
    "mydb": {
      "relations": [
        {
          "table_name":             "audit_log",
          "table_column":           "entity_id",
          "referenced_table_name":  "orders",
          "referenced_column_name": "id",
          "conditions": { "entity_type": "ORDER" }
        },
        {
          "table_name":               "orders",
          "table_column":             "created_by",
          "referenced_database_name": "auth_db",
          "referenced_table_name":    "users",
          "referenced_column_name":   "id"
        }
      ],
      "mapping_tables": {
        "order_tags": {
          "type": "MANY_TO_MANY",
          "from": "order_id",
          "to":   "tag_id"
        }
      },
      "auto_resolve": {
        "orders": ["user_id"]
      }
    }
  },
  "sensitiveColumns": [
    { "database": "mydb", "table": "users", "column": "password_hash" },
    { "database": "*",    "table": "payment_cards", "column": "cvv" }
  ]
}
```

---

## 4. Database Connection Reference

Full attribute reference for `DatabaseConnection.xml`:

| Attribute | Required | Description |
|-----------|----------|-------------|
| `ID` | Yes | Unique integer identifier |
| `GROUP` | Yes | Environment label in the UI (e.g. `production`) |
| `DB_NAME` | Yes | Display name for the database |
| `DRIVER_CLASS_NAME` | Yes | `com.mysql.cj.jdbc.Driver` or `org.mariadb.jdbc.Driver` |
| `DATABASE_URL` | Yes | JDBC URL — include `?useInformationSchema=true` for MySQL |
| `USER_NAME` | Yes | Database user |
| `PASSWORD` | Yes | Database password |
| `UPDATABLE` | No | `"true"` enables Add Row and Edit Row buttons |
| `DELETABLE` | No | `"true"` enables Delete Row button |
| `NON_INDEXED_SEARCHABLE_ROW_LIMIT` | No | Row cap for searches on non-indexed columns |
| `CONNECTION_EXPIRY_TIME` | No | Connection TTL in ms (on `<CONNECTIONS>` element) |
| `MAX_RETRY_COUNT` | No | Reconnect attempts on failure (on `<CONNECTIONS>` element) |

FkBlitz searches for the file in this order:
1. `/etc/fkblitz/DatabaseConnection.xml`
2. `~/.fkblitz/DatabaseConnection.xml`
3. `~/DatabaseConnection.xml`
4. Classpath fallback (bundled sample)

---

## 5. Remote Config Loading

Instead of reading config from disk, FkBlitz can pull `DatabaseConnection.xml` and `custom_mapping.json` from an HTTP endpoint or a database table. A restart is required for `file` and `api` sources; the `db` source supports auto-refresh.

```yaml
fkblitz:
  config:
    connection:
      source: file          # file | api | db

      api:
        url: https://config.internal/fkblitz/connections
        token: ${FKBLITZ_CONFIG_API_TOKEN:}
        format: xml         # xml | json
        timeout-seconds: 10

      db:
        url: jdbc:mysql://config-db:3306/configdb
        username: ${FKBLITZ_CFGDB_USER}
        password: ${FKBLITZ_CFGDB_PASSWORD}
        table: fkblitz_connection_config
        column: config_content
        format: xml
        refresh-interval-seconds: 60   # 0 = load once at boot

    custom-mapping:
      source: file
      # same api/db sub-keys as above
```

**Behaviour by source:**

| Source | Initial load | Updates |
|--------|-------------|---------|
| `file` | From disk at boot | Restart required |
| `api` | HTTP GET at boot | Restart required |
| `db` | DB query at boot | Auto-refresh at `refresh-interval-seconds` (0 = disabled) |

On initial load failure, FkBlitz fails fast. On refresh failure, the previous config is retained and a `WARN` is logged.

---

## 6. Redis

Redis enables distributed session storage and metadata caching — required when running more than one replica.

```yaml
fkblitz:
  redis:
    enabled: ${FKBLITZ_REDIS_ENABLED:false}
    host: ${FKBLITZ_REDIS_HOST:localhost}
    port: ${FKBLITZ_REDIS_PORT:6379}
    password: ${FKBLITZ_REDIS_PASSWORD:}
```

When `enabled: false` (default):
- Sessions are stored in-memory (single-node only)
- Metadata is cached with `ConcurrentMapCache`

When `enabled: true`:
- Sessions are stored in Redis with a 30-minute TTL (configurable via `spring.session.timeout`)
- Schema metadata is cached in Redis with a 5-minute TTL and evicted on config reload
- **Relation config changes are propagated to all nodes via pub/sub** (see below)

### Relation config invalidation (pub/sub)

When the `db` config source detects a change in the `relation_mapping` table, it publishes a message to the Redis channel `fkblitz:config-changed`. Every replica subscribes to this channel and triggers an immediate `refresh()` on receipt — collapsing the inter-node staleness window from `refresh-interval-seconds` down to sub-second.

```
Node 1 detects MAX(updated_at) changed
  → reloads relation config
  → publishes to fkblitz:config-changed

Node 2, Node 3 receive the message
  → each triggers refresh() immediately
  → all nodes converge within <1s
```

Without Redis, each replica polls independently at `refresh-interval-seconds`. With Redis, the first node to detect a change notifies all others instantly.

### Redis persistence

**FkBlitz does not require Redis persistence and does not configure it.**

Redis stores two things: sessions (temporary auth tickets that expire) and schema metadata (a cache over data that already lives in MySQL). If the Redis pod restarts:
- Cached metadata is rebuilt automatically on the next request
- Active sessions are lost — users are prompted to log in again

This is the expected and acceptable behaviour. The Helm chart uses `emptyDir` for Redis storage — no PVC is provisioned. Do not configure AOF or RDB persistence on your Redis instance for this workload; it adds operational cost with no benefit.

---

## 7. Observability

### Prometheus Metrics

Exposed at `GET /fkblitz/actuator/prometheus` (requires `ADMIN` role).

**Application metrics:**

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `fkblitz_query_duration_seconds` | Histogram | `group`, `database` | Query execution time |
| `fkblitz_query_requests_total` | Counter | `group`, `database`, `status` | Query count by outcome |
| `fkblitz_crud_operations_total` | Counter | `operation`, `table` | Add/edit/delete count |
| `fkblitz_auth_failures_total` | Counter | — | Failed login attempts |

**HikariCP connection pool metrics** (per pool, via Micrometer):

| Metric | Labels | Description |
|--------|--------|-------------|
| `hikaricp_connections_active` | `pool` | Connections currently executing a query |
| `hikaricp_connections_idle` | `pool` | Connections sitting idle in the pool |
| `hikaricp_connections_pending` | `pool` | Threads waiting for a connection — key pressure signal |
| `hikaricp_connections_max` | `pool` | Pool size ceiling |
| `hikaricp_connections_acquire_seconds` | `pool` | Time to borrow a connection from the pool |
| `hikaricp_connections_timeout_total` | `pool` | Cumulative connection timeout count |

**Pool naming convention:**

| Pool name pattern | Used by |
|---|---|
| `fkblitz-auth` | Spring Security H2/MySQL auth datasource |
| `fkblitz-config-db-{table}` | `DbConfigLoader` — connection/relation config from DB |
| `fkblitz-config-relation` | `RelationRowDbLoader` — dedicated relation mapping pool |
| `fkblitz-data-{group}-{dbName}` | User data connections (one pool per configured database) |

Example PromQL to monitor pool pressure across all data connections:

```promql
# Threads waiting for a connection (should be 0)
sum(hikaricp_connections_pending{pool=~"fkblitz-data-.*"})

# Max borrow latency over last 5 minutes
max(rate(hikaricp_connections_acquire_seconds_sum{pool=~"fkblitz-data-.*"}[5m])
  / rate(hikaricp_connections_acquire_seconds_count{pool=~"fkblitz-data-.*"}[5m]))
```

### Grafana Dashboard

When using `docker compose up`, a pre-built Grafana dashboard is provisioned automatically at **[http://localhost:3000](http://localhost:3000)** (default login: `admin` / `admin`).

To provision manually, copy `docker/grafana/` into your Grafana instance.

### Structured Logging

Enable JSON logging for production log aggregators (ELK, Loki, etc.):

```sh
SPRING_PROFILES_ACTIVE=prod
```

Every log line includes `timestamp`, `level`, `service`, `version`, `requestId`, `trace_id`, `span_id`, and `message`.

| Field | Description |
|-------|-------------|
| `requestId` | Per-request UUID, also returned in `X-Request-Id` response header |
| `trace_id` | 32-char hex trace ID. Extracted from incoming W3C `traceparent` header, or generated if absent |
| `span_id` | 16-char hex span ID, generated fresh per request |

The outbound `traceparent` response header (`00-{traceId}-{spanId}-00`) allows downstream services to continue the trace.

In dev/staging (no `prod` profile), logs are human-readable console output.

### Health Probes

| Endpoint | Auth | Use |
|----------|------|-----|
| `GET /fkblitz/actuator/health/liveness` | Public | Process alive — use for K8s liveness probe |
| `GET /fkblitz/actuator/health/readiness` | Public | DB connections healthy — use for K8s readiness probe |
| `GET /fkblitz/actuator/health` | Public | Combined status |

---

## 8. Kubernetes & Helm

### Install

```sh
helm install fkblitz ./helm/fkblitz \
  --namespace fkblitz --create-namespace \
  --set secret.adminPassword=yourpassword \
  --set configmap.connectionXml="$(cat /path/to/DatabaseConnection.xml)"
```

### Key Values

| Value | Default | Description |
|-------|---------|-------------|
| `replicaCount` | `2` | App replica count (ignored when `autoscaling.enabled=true`) |
| `image.repository` | `ghcr.io/vivek43nit/fkblitz` | Container image |
| `image.tag` | `latest` | Image tag |
| `redis.enabled` | `true` | Deploy bundled single-node Redis |
| `redis.host` | `""` | External Redis host (overrides bundled Redis when set) |
| `autoscaling.enabled` | `true` | HPA — CPU target 70%, 2–6 replicas |
| `ingress.enabled` | `false` | Ingress resource |
| `ingress.className` | `nginx` | Ingress class |
| `serviceMonitor.enabled` | `false` | Prometheus Operator `ServiceMonitor` |
| `secret.adminPassword` | `changeme` | Bootstrap admin password |
| `secret.redisPassword` | `""` | Redis auth password |
| `secret.oauth2ClientSecret` | `""` | OAuth2 client secret |
| `secret.existingSecret` | `""` | Use a pre-existing K8s Secret instead of chart-managed one |
| `configmap.connectionXml` | sample | Inline `DatabaseConnection.xml` content |
| `resources.requests` | `250m / 512Mi` | CPU / memory requests |
| `resources.limits` | `1 / 1Gi` | CPU / memory limits |
| `app.springProfile` | `prod` | Spring profile (enables JSON logging) |
| `auth.userStore` | `h2` | User store backend |

### Example — production values file

```yaml
# prod-values.yaml
replicaCount: 3

image:
  tag: v2.1.0

auth:
  userStore: mysql

redis:
  enabled: true
  host: redis.internal.example.com   # external Redis cluster

secret:
  existingSecret: fkblitz-secrets    # managed by External Secrets Operator

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: fkblitz.internal.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: fkblitz-tls
      hosts:
        - fkblitz.internal.example.com

serviceMonitor:
  enabled: true
```

```sh
helm upgrade --install fkblitz ./helm/fkblitz \
  --namespace fkblitz --create-namespace \
  -f prod-values.yaml
```

### Secrets Management

The chart creates a single `Secret` resource from `secret.*` values. For production, supply secrets via:

- **External Secrets Operator** — set `secret.existingSecret` to a pre-synced secret name
- **Helm secrets plugin** — encrypt values with SOPS or Vault
- **`--set` at deploy time** — pass from CI vault (avoid committing to `values.yaml`)

---

## 9. API Documentation

Swagger UI is available at `/fkblitz/swagger-ui.html` for users with the `ADMIN` role.

OpenAPI JSON spec: `/fkblitz/v3/api-docs`

To disable in environments where even ADMIN access is undesired:

```yaml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

---

## 10. Security Hardening

### Security Headers

Every response includes:

| Header | Value |
|--------|-------|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Content-Security-Policy` | `default-src 'self'; frame-ancestors 'none'; …` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |

### Rate Limiting

Per-user, in-memory rate limits (Bucket4j):

| Endpoint | Limit |
|----------|-------|
| `POST /api/execute` | 60 requests / minute |
| `POST/PUT/DELETE /api/row/**` | 30 requests / minute |

Returns `429 Too Many Requests` with a `Retry-After` header on breach.

Rate limits are per-user key. Unauthenticated requests share an `"anonymous"` bucket.

### Deployment Checklist

- [ ] Change default `adminPassword` from `changeme`
- [ ] Set `SPRING_PROFILES_ACTIVE=prod` for JSON logging
- [ ] Use `user-store: mysql` backed by a dedicated auth database
- [ ] Run behind HTTPS — HSTS header is included but TLS termination must happen at the load balancer or ingress
- [ ] Set `UPDATABLE="false"` / `DELETABLE="false"` on connections that should never be mutated
- [ ] Review `sensitiveColumns` in `custom_mapping.json` for any PII or credential columns
- [ ] Enable `serviceMonitor` and connect Grafana alerts for `fkblitz_auth_failures_total`

---

## 11. Releasing

Push a `release/vX.Y.Z` branch to trigger the automated CI/CD pipeline:

```sh
git checkout -b release/v2.1.0
git push origin release/v2.1.0
```

The pipeline runs six jobs in sequence:

| Job | What it does |
|-----|-------------|
| **version** | Parses `2.1.0` from the branch name |
| **bump** | Runs `mvn versions:set` and `npm version`, commits both back |
| **changelog** | Generates release notes from conventional commits since the last tag using `git-cliff` |
| **test** | Full `mvn verify` (JaCoCo) + `npm run test:coverage` (Vitest) |
| **docker** | Builds `linux/amd64` + `linux/arm64` image, pushes `v2.1.0` and `latest` to GHCR |
| **release** | Creates git tag `v2.1.0`, creates GitHub Release with generated changelog body |

### Commit message convention

The changelog groups commits by type. Use [conventional commits](https://www.conventionalcommits.org/):

| Prefix | Changelog section |
|--------|------------------|
| `feat:` | Added |
| `fix:` | Fixed |
| `perf:` | Performance |
| `docs:` | Docs |
| `security:` | Security |
| `feat!:` or `BREAKING CHANGE:` in body | Breaking Changes |
| `test:`, `ci:`, `chore:` | Skipped from changelog |

---

## 12. Scaling

### Single node

Default configuration — sessions in-memory, metadata cached locally. Works out of the box with `docker compose up`. Not suitable for high availability.

### Multi-replica

Requires Redis:

1. Set `fkblitz.redis.enabled: true` and point to a shared Redis instance
2. Deploy with `replicaCount ≥ 2` (or enable HPA)
3. Use a load balancer or ingress in front — any replica can serve any request

Sessions are stored in Redis and shared across all replicas. Schema metadata cache is invalidated on config reload events.

### Recommended production topology

```
                ┌─────────────────┐
  browser ───▶  │  Ingress / LB   │
                └────────┬────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
     fkblitz-1      fkblitz-2      fkblitz-3   (HPA: 2–6 pods)
          │              │              │
          └──────────────┼──────────────┘
                         │
               ┌─────────┴──────────┐
               ▼                    ▼
            Redis             MySQL (auth DB)
               │
        (sessions + cache)
```

Each fkblitz pod connects directly to your monitored MySQL/MariaDB databases — those connections are not pooled through Redis.

### Resource sizing

Based on capacity benchmarks (see [README performance baselines](../README.md#capacity-benchmark)):

| Setting | Recommended | Basis |
|---|---|---|
| `FKBLITZ_MAX_POOL_SIZE` | `10` | Max 7 JDBC connections active at 100 VUs with real SQL; 0 pending |
| `FKBLITZ_TOMCAT_THREADS_MAX` | `≥ peak concurrent users` | Little's Law: at 200 VUs + 100ms think time, need ~130+ threads; 50 threads saturated |
| `-Xmx` | `256m` | 196 MB heap peak at 200 VUs; 256 MB gives 30% headroom |

> The metadata endpoints (`/api/groups`, `/api/tables`) are pure in-memory reads — JDBC pool is not touched. Size `FKBLITZ_MAX_POOL_SIZE` based on concurrent SQL query users, not total browser users.

Re-run `tests/performance/k6-verify.js` at your expected peak VU count after adjusting config to validate before production rollout.
