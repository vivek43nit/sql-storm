package com.vivek.sqlstorm.config.loader;

import com.vivek.utils.parser.ConfigParserInterface;
import com.vivek.utils.parser.ConfigParsingError;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Loads config from a single-row DB table column (JSON/XML blob).
 * Supports optional auto-refresh: when refreshIntervalSeconds > 0, a scheduler
 * (wired externally by ConfigLoaderConfig) calls {@link #refresh()} periodically.
 *
 * On change detection (SHA-256 hash comparison), the registered changeListener
 * is invoked with the new parsed config so callers can hot-reload.
 *
 * Uses a small HikariCP pool (max 2) so config polls don't pay new TCP handshake
 * overhead on every call.
 *
 * Thread-safety: the latest parsed config is held in an AtomicReference.
 * Requests read from it without blocking; refresh replaces it atomically.
 */
public class DbConfigLoader<T> implements RefreshableConfigLoader<T>, Closeable {
    private static final Logger log = LoggerFactory.getLogger(DbConfigLoader.class);

    private final String table;
    private final String column;
    private final String fileExtension;   // "xml" or "json"
    private final ConfigParserInterface<T> parser;
    private final HikariDataSource dataSource;

    private final AtomicReference<T> cachedConfig = new AtomicReference<>();
    private final AtomicReference<String> lastHash = new AtomicReference<>("");
    private volatile Consumer<T> changeListener;
    /** Optional — injected by ConfigPropagationConfig for cross-node invalidation. */
    private volatile StringRedisTemplate redisTemplate;

    public DbConfigLoader(String jdbcUrl,
                          String username,
                          String password,
                          String table,
                          String column,
                          String format,
                          ConfigParserInterface<T> parser) {
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
        hk.setPoolName("fkblitz-cfg-" + table);
        this.dataSource = new HikariDataSource(hk);
    }

    @Override
    public void setChangeListener(Consumer<T> listener) {
        this.changeListener = listener;
    }

    /** Optionally inject a Redis template for cross-node pub/sub invalidation. */
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Initial load at startup — fails fast on error. */
    @Override
    public T load() throws ConfigLoadException {
        String content = fetchContent();
        T config = parse(content);
        cachedConfig.set(config);
        lastHash.set(hash(content));
        log.info("Loaded config from DB table '{}'", table);
        return config;
    }

    /**
     * Called periodically by the scheduler (ConfigLoaderConfig wires this via TaskScheduler).
     * On change, atomically updates the cached config and invokes the changeListener.
     * Failures are logged as WARN and the previous config is retained (fail-open for refresh).
     */
    @Override
    public void refresh() {
        try {
            String content = fetchContent();
            String newHash = hash(content);
            if (newHash.equals(lastHash.get())) {
                return; // no change
            }
            T newConfig = parse(content);
            cachedConfig.set(newConfig);
            lastHash.set(newHash);
            log.info("Config change detected in DB table '{}' — reloading", table);
            Consumer<T> listener = changeListener;
            if (listener != null) {
                listener.accept(newConfig);
            }
            publishToRedis();
        } catch (Exception e) {
            log.warn("Config refresh from DB table '{}' failed — retaining previous config: {}",
                    table, e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private String fetchContent() throws ConfigLoadException {
        String sql = "SELECT " + column + " FROM " + table + " LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                throw new ConfigLoadException("No rows found in config table: " + table);
            }
            String content = rs.getString(column);
            if (content == null || content.isBlank()) {
                throw new ConfigLoadException("Config column '" + column + "' is empty in table: " + table);
            }
            return content;
        } catch (ConfigLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigLoadException("Failed to fetch config from DB table: " + table, e);
        }
    }

    private T parse(String content) throws ConfigLoadException {
        try {
            File tmp = File.createTempFile("fkblitz-cfg-db-", "." + fileExtension);
            tmp.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            T result = parser.parse(tmp);
            if (result == null) {
                throw new ConfigLoadException("Parser returned null for content from DB table: " + table);
            }
            return result;
        } catch (ConfigLoadException e) {
            throw e;
        } catch (ConfigParsingError | IOException e) {
            throw new ConfigLoadException("Failed to parse config from DB table: " + table, e);
        }
    }

    private void publishToRedis() {
        StringRedisTemplate rt = redisTemplate;
        if (rt == null) return;
        try {
            rt.convertAndSend(RelationRowDbLoader.REDIS_CHANNEL, Instant.now().toString());
        } catch (Exception e) {
            log.warn("Failed to publish config-changed event to Redis: {}", e.getMessage());
        }
    }

    private static String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
