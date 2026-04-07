package com.vivek.sqlstorm.connection;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
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

    public DatabaseConnectionManager(ConfigLoaderStrategy<ConnectionConfig> connectionConfigLoader) {
        this.connectionMap = new HashMap<>();
        this.configs = connectionConfigLoader.load();
        for (ConnectionDTO config : configs.getConnections()) {
            log.debug("Registering connection: {}", config);
            addConnectionInfo(new ConnectionInfo(config));
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

    public Connection getConnection(String groupName, String dbName)
            throws SQLException, ConnectionDetailNotFound, ClassNotFoundException {
        lock.writeLock().lock();
        try {
            ConnectionInfo connInfo = getConnectionInfo(groupName, dbName);
            if (!isValidConnection(connInfo)) {
                connInfo.setConnection(createConnection(connInfo.getConfig()));
            }
            return connInfo.getConnection();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ConnectionDTO getConnectionConfig(String group, String database) throws ConnectionDetailNotFound {
        return getConnectionInfo(group, database).getConfig();
    }

    /**
     * Returns the count of connections that are currently open (non-null, not closed).
     * Used by {@link com.vivek.metrics.FkBlitzMetrics} to expose a gauge.
     */
    public int getActiveConnectionCount() {
        lock.readLock().lock();
        try {
            int count = 0;
            for (Map<String, ConnectionInfo> group : connectionMap.values()) {
                for (ConnectionInfo info : group.values()) {
                    Connection c = info.getConnection();
                    try {
                        if (c != null && !c.isClosed()) count++;
                    } catch (SQLException ignored) { }
                }
            }
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void closeAllConnections() {
        lock.writeLock().lock();
        try {
            connectionMap.values().forEach(dbs ->
                    dbs.values().forEach(ConnectionInfo::closeConnection));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Hot-reload: called by DbConfigLoader's change listener when a new config is detected.
     * Diffs the old and new connection lists:
     *   - Removed entries: close and discard
     *   - New entries: add with lazy connection
     *   - Changed entries (URL/credentials): close existing, replace config
     *   - Unchanged entries: update flags (UPDATABLE, DELETABLE) only
     */
    public void reloadConnections(ConnectionConfig newConfig) {
        log.info("Reloading connection config — {} connections in new config", newConfig.getConnections().size());
        lock.writeLock().lock();
        try {
            Set<String> newKeys = newConfig.getConnections().stream()
                    .map(c -> c.getGroup() + "::" + c.getDbName())
                    .collect(Collectors.toSet());

            // Close and remove entries that are no longer in the new config
            connectionMap.forEach((group, dbs) ->
                    dbs.entrySet().removeIf(e -> {
                        if (!newKeys.contains(group + "::" + e.getKey())) {
                            e.getValue().closeConnection();
                            log.info("Removed connection group={} db={}", group, e.getKey());
                            return true;
                        }
                        return false;
                    }));
            connectionMap.entrySet().removeIf(e -> e.getValue().isEmpty());

            // Add new or update existing
            for (ConnectionDTO dto : newConfig.getConnections()) {
                Map<String, ConnectionInfo> groupMap = connectionMap.computeIfAbsent(
                        dto.getGroup(), k -> new ConcurrentHashMap<>());
                ConnectionInfo existing = groupMap.get(dto.getDbName());

                if (existing == null) {
                    groupMap.put(dto.getDbName(), new ConnectionInfo(dto));
                    log.info("Added new connection group={} db={}", dto.getGroup(), dto.getDbName());
                } else if (!sameConnectionDetails(existing.getConfig(), dto)) {
                    existing.closeConnection();
                    existing.setConfig(dto);
                    log.info("Updated connection details group={} db={}", dto.getGroup(), dto.getDbName());
                } else {
                    // Same connection — only update flags
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

    private boolean isValidConnection(ConnectionInfo connection) throws SQLException {
        return connection.getConnection() != null
                && System.currentTimeMillis() - connection.getConnectTime() < configs.getConnectionExpiryTime()
                && !connection.getConnection().isClosed();
    }

    private Connection createConnection(ConnectionDTO config) throws ClassNotFoundException, SQLException {
        Class.forName(config.getDriverClassName());
        DriverManager.setLoginTimeout(10);
        SQLException last = null;
        for (int i = 0; i < configs.getMaxRetryCount(); i++) {
            try {
                return DriverManager.getConnection(config.getDatabaseURL(), config.getUser(), config.getPassword());
            } catch (SQLException ex) {
                last = ex;
            }
        }
        throw last;
    }

    private static boolean sameConnectionDetails(ConnectionDTO a, ConnectionDTO b) {
        return Objects.equals(a.getDatabaseURL(), b.getDatabaseURL())
                && Objects.equals(a.getUser(), b.getUser())
                && Objects.equals(a.getPassword(), b.getPassword())
                && Objects.equals(a.getDriverClassName(), b.getDriverClassName());
    }

    // ── Inner class ────────────────────────────────────────────────────────

    private static class ConnectionInfo {
        private long connectTime;
        private ConnectionDTO config;
        private Connection connection;

        ConnectionInfo(ConnectionDTO config) {
            this.config = config;
        }

        long getConnectTime() { return connectTime; }
        ConnectionDTO getConfig() { return config; }
        void setConfig(ConnectionDTO config) { this.config = config; }
        Connection getConnection() { return connection; }

        void setConnection(Connection connection) {
            closeConnection();
            this.connection = connection;
            this.connectTime = System.currentTimeMillis();
        }

        void closeConnection() {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ex) {
                    log.error("Error closing connection: {}", ex.getMessage());
                }
                connection = null;
            }
        }
    }
}
