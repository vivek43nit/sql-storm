package com.vivek.sqlstorm.connection;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class DatabaseConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionManager.class);

    private volatile ConnectionConfig configs;
    private final Map<String, Map<String, ConnectionInfo>> connectionMap;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Global default pool size — overrides ConnectionDTO.maxPoolSize (5) when set via env/property. */
    private final int defaultMaxPoolSize;

    /** Nullable — tests pass null; production Spring context always injects. */
    @Nullable
    private final MeterRegistry meterRegistry;

    public DatabaseConnectionManager(
            ConfigLoaderStrategy<ConnectionConfig> connectionConfigLoader,
            @Value("${fkblitz.connection.default-max-pool-size:5}") int defaultMaxPoolSize,
            @Nullable MeterRegistry meterRegistry) {
        this.defaultMaxPoolSize = defaultMaxPoolSize;
        this.meterRegistry = meterRegistry;
        this.connectionMap = new HashMap<>();
        this.configs = connectionConfigLoader.load();
        for (ConnectionDTO config : configs.getConnections()) {
            log.debug("Registering connection: {}", config);
            addConnectionInfo(new ConnectionInfo(config, createDataSource(config)));
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public Set<String> getGroupNames() {
        lock.readLock().lock();
        try {
            return connectionMap.keySet();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> getDbNames(String group) throws ConnectionDetailNotFound {
        lock.readLock().lock();
        try {
            if (!connectionMap.containsKey(group)) {
                throw new ConnectionDetailNotFound("Invalid group name: " + group);
            }
            return connectionMap.get(group).keySet();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isUpdatableConnection(String groupName, String dbName) throws ConnectionDetailNotFound {
        return getConnectionInfo(groupName, dbName).getConfig().isUpdatable();
    }

    public boolean isDeletableConnection(String groupName, String dbName) throws ConnectionDetailNotFound {
        return getConnectionInfo(groupName, dbName).getConfig().isDeletable();
    }

    /**
     * Returns a connection from the per-database HikariCP pool.
     * Uses the read lock — multiple callers can borrow connections concurrently.
     * HikariCP manages pool health and validation internally.
     */
    public Connection getConnection(String groupName, String dbName)
            throws SQLException, ConnectionDetailNotFound {
        lock.readLock().lock();
        try {
            return getConnectionInfo(groupName, dbName).getPool().getConnection();
        } finally {
            lock.readLock().unlock();
        }
    }

    public ConnectionDTO getConnectionConfig(String group, String database) throws ConnectionDetailNotFound {
        return getConnectionInfo(group, database).getConfig();
    }

    public void closeAllConnections() {
        lock.writeLock().lock();
        try {
            connectionMap.values().forEach(dbs ->
                    dbs.values().forEach(ConnectionInfo::closePool));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Hot-reload: diffs the old and new connection lists.
     *   - Removed entries: close pool and discard
     *   - New entries: create pool and add
     *   - Changed entries (URL/credentials): close old pool, create new pool
     *   - Unchanged entries: update flags (UPDATABLE, DELETABLE) and pool size only
     */
    public void reloadConnections(ConnectionConfig newConfig) {
        log.info("Reloading connection config — {} connections in new config",
                newConfig.getConnections().size());
        lock.writeLock().lock();
        try {
            Set<String> newKeys = newConfig.getConnections().stream()
                    .map(c -> c.getGroup() + "::" + c.getDbName())
                    .collect(Collectors.toSet());

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
            connectionMap.entrySet().removeIf(e -> e.getValue().isEmpty());

            for (ConnectionDTO dto : newConfig.getConnections()) {
                Map<String, ConnectionInfo> groupMap = connectionMap.computeIfAbsent(
                        dto.getGroup(), k -> new ConcurrentHashMap<>());
                ConnectionInfo existing = groupMap.get(dto.getDbName());

                if (existing == null) {
                    groupMap.put(dto.getDbName(), new ConnectionInfo(dto, createDataSource(dto)));
                    log.info("Added new connection group={} db={}", dto.getGroup(), dto.getDbName());
                } else if (!sameConnectionDetails(existing.getConfig(), dto)) {
                    deregisterPoolMetrics("fkblitz-data-" + dto.getGroup() + "-" + dto.getDbName());
                    existing.closePool();
                    groupMap.put(dto.getDbName(), new ConnectionInfo(dto, createDataSource(dto)));
                    log.info("Updated connection details group={} db={}", dto.getGroup(), dto.getDbName());
                } else {
                    // Same connection — update config flags only (no pool recreation)
                    existing.setConfig(dto);
                }
            }

            this.configs = newConfig;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private ConnectionInfo getConnectionInfo(String group, String database) throws ConnectionDetailNotFound {
        lock.readLock().lock();
        try {
            Map<String, ConnectionInfo> dbs = connectionMap.get(group);
            if (dbs == null) throw new ConnectionDetailNotFound("Invalid group name: " + group);
            ConnectionInfo info = dbs.get(database);
            if (info == null) throw new ConnectionDetailNotFound("No database " + database + " in group: " + group);
            return info;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void addConnectionInfo(ConnectionInfo info) {
        connectionMap
                .computeIfAbsent(info.getConfig().getGroup(), k -> new ConcurrentHashMap<>())
                .put(info.getConfig().getDbName(), info);
    }

    private HikariDataSource createDataSource(ConnectionDTO config) {
        HikariConfig hk = new HikariConfig();
        hk.setJdbcUrl(config.getDatabaseURL());
        hk.setUsername(config.getUser());
        hk.setPassword(config.getPassword());
        hk.setDriverClassName(config.getDriverClassName());
        // Use the global default unless the connection config explicitly overrides it
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

    private static boolean sameConnectionDetails(ConnectionDTO a, ConnectionDTO b) {
        return Objects.equals(a.getDatabaseURL(), b.getDatabaseURL())
                && Objects.equals(a.getUser(), b.getUser())
                && Objects.equals(a.getPassword(), b.getPassword())
                && Objects.equals(a.getDriverClassName(), b.getDriverClassName());
    }

    // ── Inner class ────────────────────────────────────────────────────────

    private static class ConnectionInfo {
        private ConnectionDTO config;
        private final HikariDataSource pool;

        ConnectionInfo(ConnectionDTO config, HikariDataSource pool) {
            this.config = config;
            this.pool = pool;
        }

        ConnectionDTO getConfig() { return config; }
        void setConfig(ConnectionDTO config) { this.config = config; }
        HikariDataSource getPool() { return pool; }

        void closePool() {
            if (pool != null && !pool.isClosed()) {
                pool.close();
            }
        }
    }
}
