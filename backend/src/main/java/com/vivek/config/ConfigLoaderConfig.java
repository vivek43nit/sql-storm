package com.vivek.config;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.parsers.DatabaseConfigJsonParser;
import com.vivek.sqlstorm.config.connection.parsers.DatabaseConfigXmlParser;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.customrelation.parsers.CustomRelationConfigJsonParser;
import com.vivek.sqlstorm.config.loader.ApiConfigLoader;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.config.loader.DbConfigLoader;
import com.vivek.sqlstorm.config.loader.FileConfigLoader;
import com.vivek.sqlstorm.config.loader.RefreshableConfigLoader;
import com.vivek.sqlstorm.config.loader.RelationRowDbLoader;
import com.vivek.sqlstorm.constants.Constants;
import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import com.vivek.sqlstorm.metadata.DatabaseMetaDataManager;
import com.vivek.utils.parser.ConfigParserInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
@EnableConfigurationProperties(FkBlitzConfigProperties.class)
public class ConfigLoaderConfig {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoaderConfig.class);

    // ── Connection config loader ────────────────────────────────────────────

    @Bean
    public ConfigLoaderStrategy<ConnectionConfig> connectionConfigLoader(FkBlitzConfigProperties props) {
        FkBlitzConfigProperties.ConfigSource src = props.getConfig().getConnection();
        return switch (src.getSource()) {
            case "api" -> {
                validateApiConfig(src.getApi(), "connection");
                yield new ApiConfigLoader<>(
                        src.getApi().getUrl(),
                        src.getApi().getToken(),
                        src.getApi().getTimeoutSeconds(),
                        src.getApi().getFormat(),
                        parserForConnectionFormat(src.getApi().getFormat()));
            }
            case "db" -> {
                validateDbConfig(src.getDb(), "connection");
                yield new DbConfigLoader<>(
                        src.getDb().getUrl(),
                        src.getDb().getUsername(),
                        src.getDb().getPassword(),
                        src.getDb().getTable(),
                        src.getDb().getColumn(),
                        src.getDb().getFormat(),
                        parserForConnectionFormat(src.getDb().getFormat()));
            }
            default -> new FileConfigLoader<>(
                    ConnectionConfig.class,
                    Constants.CONNECTION_CONFIGURATION_FILE_NAME,
                    List.of(new DatabaseConfigXmlParser(), new DatabaseConfigJsonParser()));
        };
    }

    // ── Custom mapping config loader ───────────────────────────────────────

    @Bean
    public ConfigLoaderStrategy<CustomRelationConfig> customMappingConfigLoader(FkBlitzConfigProperties props) {
        FkBlitzConfigProperties.ConfigSource src = props.getConfig().getCustomMapping();
        return switch (src.getSource()) {
            case "api" -> {
                validateApiConfig(src.getApi(), "custom-mapping");
                yield new ApiConfigLoader<>(
                        src.getApi().getUrl(),
                        src.getApi().getToken(),
                        src.getApi().getTimeoutSeconds(),
                        src.getApi().getFormat(),
                        new CustomRelationConfigJsonParser());
            }
            case "db" -> {
                validateDbConfig(src.getDb(), "custom-mapping");
                yield new DbConfigLoader<>(
                        src.getDb().getUrl(),
                        src.getDb().getUsername(),
                        src.getDb().getPassword(),
                        src.getDb().getTable(),
                        src.getDb().getColumn(),
                        src.getDb().getFormat(),
                        new CustomRelationConfigJsonParser());
            }
            case "relation-table" -> {
                validateDbConfig(src.getDb(), "custom-mapping");
                yield new RelationRowDbLoader(
                        src.getDb().getUrl(),
                        src.getDb().getUsername(),
                        src.getDb().getPassword(),
                        src.getDb().getTable());
            }
            default -> new FileConfigLoader<>(
                    CustomRelationConfig.class,
                    Constants.CUSTOM_RELATION_CONFIG_FILE_NAME,
                    List.of(new CustomRelationConfigJsonParser()));
        };
    }

    // ── Auto-refresh wiring (runs after all beans are ready) ───────────────

    /**
     * If either loader is a RefreshableConfigLoader (DbConfigLoader or RelationRowDbLoader)
     * and refreshIntervalSeconds > 0, schedule its refresh() via TaskScheduler with a random
     * startup jitter to prevent thundering-herd in multi-node deployments.
     */
    @Bean
    public ApplicationRunner configRefreshSetup(
            ConfigLoaderStrategy<ConnectionConfig> connectionConfigLoader,
            ConfigLoaderStrategy<CustomRelationConfig> customMappingConfigLoader,
            DatabaseConnectionManager connectionManager,
            DatabaseMetaDataManager metaDataManager,
            TaskScheduler taskScheduler,
            FkBlitzConfigProperties props) {

        return args -> {
            scheduleConnectionRefresh(connectionConfigLoader, connectionManager,
                    taskScheduler, props.getConfig().getConnection().getDb().getRefreshIntervalSeconds());

            scheduleCustomMappingRefresh(customMappingConfigLoader, metaDataManager,
                    taskScheduler, props.getConfig().getCustomMapping().getDb().getRefreshIntervalSeconds());
        };
    }

    @SuppressWarnings("unchecked")
    private void scheduleConnectionRefresh(ConfigLoaderStrategy<ConnectionConfig> loader,
                                           DatabaseConnectionManager manager,
                                           TaskScheduler scheduler,
                                           long intervalSeconds) {
        if (!(loader instanceof RefreshableConfigLoader<ConnectionConfig> refreshable)) return;
        refreshable.setChangeListener(manager::reloadConnections);
        if (intervalSeconds > 0) {
            Instant firstRun = Instant.now().plusMillis(
                    ThreadLocalRandom.current().nextLong(intervalSeconds * 1_000));
            scheduler.scheduleWithFixedDelay(refreshable::refresh, firstRun,
                    Duration.ofSeconds(intervalSeconds));
            log.info("Scheduled connection config refresh every {}s from DB (jitter applied)", intervalSeconds);
        }
    }

    @SuppressWarnings("unchecked")
    private void scheduleCustomMappingRefresh(ConfigLoaderStrategy<CustomRelationConfig> loader,
                                              DatabaseMetaDataManager manager,
                                              TaskScheduler scheduler,
                                              long intervalSeconds) {
        if (!(loader instanceof RefreshableConfigLoader<CustomRelationConfig> refreshable)) return;
        refreshable.setChangeListener(manager::reloadCustomRelationConfig);
        if (intervalSeconds > 0) {
            Instant firstRun = Instant.now().plusMillis(
                    ThreadLocalRandom.current().nextLong(intervalSeconds * 1_000));
            scheduler.scheduleWithFixedDelay(refreshable::refresh, firstRun,
                    Duration.ofSeconds(intervalSeconds));
            log.info("Scheduled custom-mapping config refresh every {}s from DB (jitter applied)", intervalSeconds);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static ConfigParserInterface<ConnectionConfig> parserForConnectionFormat(String format) {
        return "xml".equalsIgnoreCase(format) ? new DatabaseConfigXmlParser() : new DatabaseConfigJsonParser();
    }

    private static void validateApiConfig(FkBlitzConfigProperties.ApiSourceConfig api, String name) {
        if (api.getUrl() == null || api.getUrl().isBlank()) {
            throw new IllegalStateException("fkblitz.config." + name + ".api.url must be set when source=api");
        }
    }

    private static void validateDbConfig(FkBlitzConfigProperties.DbSourceConfig db, String name) {
        if (db.getUrl() == null || db.getUrl().isBlank()) {
            throw new IllegalStateException("fkblitz.config." + name + ".db.url must be set when source=db");
        }
        if (db.getTable() == null || db.getTable().isBlank()) {
            throw new IllegalStateException("fkblitz.config." + name + ".db.table must be set when source=db");
        }
    }
}
