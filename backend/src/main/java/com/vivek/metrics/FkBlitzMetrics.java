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
