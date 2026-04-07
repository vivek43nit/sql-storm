package com.vivek.metrics;

import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Central point for recording FkBlitz-specific metrics.
 *
 * <p>Metric naming follows the Micrometer convention (dots). Prometheus exporter
 * converts dots to underscores automatically.</p>
 *
 * <ul>
 *   <li>{@code fkblitz.query.duration} — Timer (histogram) per group+database</li>
 *   <li>{@code fkblitz.query.requests} — Counter per group+database+status</li>
 *   <li>{@code fkblitz.auth.failures} — Counter for failed logins</li>
 *   <li>{@code fkblitz.crud.operations} — Counter per operation+table</li>
 *   <li>{@code fkblitz.connections.active} — Gauge of open DB connections</li>
 * </ul>
 */
@Component
public class FkBlitzMetrics {

  private final MeterRegistry registry;

  public FkBlitzMetrics(MeterRegistry registry, DatabaseConnectionManager connectionManager) {
    this.registry = registry;

    Gauge.builder("fkblitz.connections.active", connectionManager,
            DatabaseConnectionManager::getActiveConnectionCount)
        .description("Number of currently open database connections")
        .register(registry);
  }

  // ── Query metrics ──────────────────────────────────────────────────────────

  public void recordQuerySuccess(String group, String database, long durationMs) {
    queryTimer(group, database).record(durationMs, TimeUnit.MILLISECONDS);
    queryCounter(group, database, "success").increment();
  }

  public void recordQueryError(String group, String database) {
    queryCounter(group, database, "error").increment();
  }

  // ── Auth metrics ───────────────────────────────────────────────────────────

  public void recordAuthFailure() {
    Counter.builder("fkblitz.auth.failures")
        .description("Number of failed authentication attempts")
        .register(registry)
        .increment();
  }

  // ── CRUD metrics ───────────────────────────────────────────────────────────

  public void recordCrudOperation(String operation, String table) {
    Counter.builder("fkblitz.crud.operations")
        .description("Row mutation operations (add/edit/delete)")
        .tag("operation", operation)
        .tag("table", table)
        .register(registry)
        .increment();
  }

  // ── Internals ──────────────────────────────────────────────────────────────

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
