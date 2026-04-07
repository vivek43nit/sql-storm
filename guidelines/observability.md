# Observability Guidelines

Good observability means you can answer "what is the system doing right now and why?" without SSH-ing into a server.

## Structured Logging

Always emit logs as JSON (or your platform's structured format). Never plain-text strings in production.

**Required fields on every log line:**

```json
{
  "timestamp": "2026-04-06T10:23:01.123Z",
  "level": "info",
  "service": "user-service",
  "version": "1.4.2",
  "trace_id": "abc123",
  "span_id": "def456",
  "message": "User login succeeded",
  "user_id": "u_789"
}
```

**Log levels — use precisely:**

| Level | When to use | Action required |
|-------|-------------|-----------------|
| `ERROR` | Operation failed, data may be lost or corrupt | Page on-call immediately |
| `WARN` | Unexpected state but operation succeeded | Investigate within 24h |
| `INFO` | Key business events (login, payment, order) | No action — for audit trail |
| `DEBUG` | Detailed execution flow | Dev/staging only, never prod |

**Never log:**
- Passwords, tokens, API keys, session IDs
- Full credit card numbers or CVVs
- Government IDs, SSNs, passport numbers
- Full request/response bodies (log shape only, not values)
- Stack traces at INFO level (ERROR only)

**Do log:**
- User ID (not email/name) for traceability
- Request ID / trace ID on every log line
- Timing information for slow operations
- External service call outcomes (success/fail + duration)

## Metrics — RED Pattern

For every service endpoint, track:

- **R**ate: requests per second
- **E**rrors: error rate (%)
- **D**uration: latency percentiles (p50, p95, p99)

For background jobs / queues, also track:
- Queue depth
- Processing lag (time between enqueue and start)
- Dead-letter queue size

**Naming convention:** `<service>_<operation>_<unit>`
Examples: `user_login_duration_ms`, `payment_requests_total`, `order_errors_total`

## Distributed Tracing

- Propagate trace context on every outbound call (HTTP headers: `traceparent`, `X-Trace-ID`)
- Every service entry point must extract and continue the trace
- Every external DB/cache/queue call must be a child span
- Instrument using OpenTelemetry — it's vendor-neutral

**Minimum span attributes:**
- `service.name`
- `http.method` + `http.url` (for HTTP spans)
- `db.system` + `db.statement` (for DB spans, redact values)
- `error` = true + `error.message` (on failure spans)

## Health Endpoints

Every service must expose:

```
GET /health/live   → 200 if process is running (liveness)
GET /health/ready  → 200 if service can handle traffic (readiness: DB connected, cache reachable)
```

Never return 500 from `/health/live`. It will restart your pod.

## Per-Language Tooling

| Language | Logger | Metrics | Tracing |
|----------|--------|---------|---------|
| Python | `structlog` | `prometheus-client` | `opentelemetry-sdk` |
| TypeScript | `pino` | `prom-client` | `@opentelemetry/sdk-node` |
| Go | `zerolog` or `zap` | `prometheus/client_golang` | `go.opentelemetry.io/otel` |
| Java | `logback` + `SLF4J` | `micrometer` | `opentelemetry-java` |
| Kotlin | `logback` + `SLF4J` | `micrometer` | `opentelemetry-java` |
| Rust | `tracing` crate | `metrics` crate | `opentelemetry` crate |
