package com.vivek.sqlstorm.config.loader;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.customrelation.DatabaseConfig;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Loads custom FK relations from a dedicated {@code relation_mapping} table where
 * each row represents exactly one relation — no JSON blob.
 *
 * <p><b>Change detection:</b> {@code SELECT MAX(updated_at) FROM relation_mapping} is
 * issued on every {@link #refresh()} call. This is an O(1) index-only scan. Only when
 * the timestamp advances (or on first load) is the full row set re-fetched. Soft-deletes
 * ({@code is_active = 0}) bump {@code updated_at} and are therefore detected automatically.
 *
 * <p><b>Multi-node propagation:</b> After detecting a change, this loader publishes to
 * the Redis channel {@code fkblitz:config-changed} (if a {@link StringRedisTemplate} is
 * injected). All other cluster nodes subscribe and trigger an immediate {@code refresh()},
 * collapsing the inter-node staleness window from {@code refreshIntervalSeconds} to
 * sub-second.
 *
 * <p><b>Thread-safety:</b> {@code cachedConfig} and {@code lastMaxUpdatedAt} are
 * {@code AtomicReference} / {@code AtomicLong}. {@link #refresh()} is safe to call
 * concurrently (worst case: two threads both detect a change and both reload — harmless).
 */
public class RelationRowDbLoader implements RefreshableConfigLoader<CustomRelationConfig>, Closeable {

    public static final String REDIS_CHANNEL = "fkblitz:config-changed";

    private static final Logger log = LoggerFactory.getLogger(RelationRowDbLoader.class);

    private static final String SQL_MAX_UPDATED_AT =
            "SELECT MAX(updated_at) FROM %s";

    private static final String SQL_LOAD_RELATIONS =
            "SELECT database_name, table_name, column_name, " +
            "       ref_database_name, ref_table_name, ref_column_name, conditions_json " +
            "FROM %s " +
            "WHERE is_active = 1 " +
            "ORDER BY database_name";

    private final String table;
    private final HikariDataSource dataSource;

    private final AtomicReference<CustomRelationConfig> cachedConfig = new AtomicReference<>();
    /** Epoch millis of the MAX(updated_at) seen on last successful load. 0 = never loaded. */
    private final AtomicLong lastMaxUpdatedAt = new AtomicLong(0);

    private volatile Consumer<CustomRelationConfig> changeListener;
    /** Optional — injected by ConfigPropagationConfig for cross-node invalidation. */
    private volatile StringRedisTemplate redisTemplate;

    public RelationRowDbLoader(String jdbcUrl, String username, String password, String table) {
        this.table = table;
        HikariConfig hk = new HikariConfig();
        hk.setJdbcUrl(jdbcUrl);
        hk.setUsername(username);
        hk.setPassword(password);
        hk.setMaximumPoolSize(2);
        hk.setMinimumIdle(1);
        hk.setConnectionTimeout(30_000);
        hk.setPoolName("fkblitz-relation-cfg");
        this.dataSource = new HikariDataSource(hk);
    }

    @Override
    public void setChangeListener(Consumer<CustomRelationConfig> listener) {
        this.changeListener = listener;
    }

    /** Optionally inject a Redis template for cross-node pub/sub invalidation. */
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Initial load at startup — fails fast on error. */
    @Override
    public CustomRelationConfig load() throws ConfigLoadException {
        long maxTs = fetchMaxUpdatedAt();
        CustomRelationConfig config = fetchAndAssemble();
        cachedConfig.set(config);
        lastMaxUpdatedAt.set(maxTs);
        log.info("Loaded {} relation(s) from table '{}'",
                config.getDatabases().values().stream()
                      .mapToInt(db -> db.getRelations().size()).sum(),
                table);
        return config;
    }

    /**
     * Polls for changes. O(1) when nothing changed — only {@code MAX(updated_at)} is queried.
     * On change: re-fetches all active rows, rebuilds config, notifies listener, publishes to Redis.
     * Failures are logged as WARN; previous config is retained (fail-open).
     */
    @Override
    public void refresh() {
        try {
            long newMaxTs = fetchMaxUpdatedAt();
            if (newMaxTs <= lastMaxUpdatedAt.get()) {
                return; // no change
            }
            CustomRelationConfig newConfig = fetchAndAssemble();
            cachedConfig.set(newConfig);
            lastMaxUpdatedAt.set(newMaxTs);
            log.info("Relation mapping change detected in '{}' — reloading", table);

            Consumer<CustomRelationConfig> listener = changeListener;
            if (listener != null) {
                listener.accept(newConfig);
            }
            publishToRedis();
        } catch (Exception e) {
            log.warn("RelationRowDbLoader refresh from '{}' failed — retaining previous config: {}",
                    table, e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private long fetchMaxUpdatedAt() throws ConfigLoadException {
        String sql = String.format(SQL_MAX_UPDATED_AT, table);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                return ts != null ? ts.getTime() : 0L;
            }
            return 0L;
        } catch (Exception e) {
            throw new ConfigLoadException("Failed to query MAX(updated_at) from '" + table + "'", e);
        }
    }

    private CustomRelationConfig fetchAndAssemble() throws ConfigLoadException {
        String sql = String.format(SQL_LOAD_RELATIONS, table);
        Map<String, List<ReferenceDTO>> byDatabase = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String dbName       = rs.getString("database_name");
                String tableName    = rs.getString("table_name");
                String columnName   = rs.getString("column_name");
                String refDbName    = rs.getString("ref_database_name");
                String refTableName = rs.getString("ref_table_name");
                String refColName   = rs.getString("ref_column_name");
                String condJson     = rs.getString("conditions_json");

                ReferenceDTO ref = new ReferenceDTO();
                ref.setDatabaseName(dbName);
                ref.setTableName(tableName);
                ref.setColumnName(columnName);
                ref.setReferenceDatabaseName(refDbName);
                ref.setReferenceTableName(refTableName);
                ref.setReferenceColumnName(refColName);
                ref.setSource(ReferenceDTO.Source.CUSTOM);
                if (condJson != null && !condJson.isBlank()) {
                    ref.setConditions(new JSONObject(condJson));
                }

                byDatabase.computeIfAbsent(dbName, k -> new ArrayList<>()).add(ref);
            }
        } catch (Exception e) {
            throw new ConfigLoadException("Failed to load relations from '" + table + "'", e);
        }

        Map<String, DatabaseConfig> databases = new HashMap<>();
        for (Map.Entry<String, List<ReferenceDTO>> entry : byDatabase.entrySet()) {
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setRelations(entry.getValue());
            dbConfig.setJointTables(Collections.emptyMap());
            dbConfig.setAutoResolve(Collections.emptyMap());
            databases.put(entry.getKey(), dbConfig);
        }

        return new CustomRelationConfig(databases);
    }

    private void publishToRedis() {
        StringRedisTemplate rt = redisTemplate;
        if (rt == null) return;
        try {
            rt.convertAndSend(REDIS_CHANNEL, Instant.now().toString());
        } catch (Exception e) {
            log.warn("Failed to publish config-changed event to Redis: {}", e.getMessage());
        }
    }
}
