package com.vivek.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "fkblitz")
public class FkBlitzConfigProperties {

    private Cors cors = new Cors();
    private Config config = new Config();
    private Redis redis = new Redis();

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";
    }

    @Data
    public static class Config {
        private ConfigSource connection = new ConfigSource();
        private ConfigSource customMapping = new ConfigSource();
    }

    @Data
    public static class ConfigSource {
        /** file | api | db */
        private String source = "file";
        private ApiSourceConfig api = new ApiSourceConfig();
        private DbSourceConfig db = new DbSourceConfig();
    }

    @Data
    public static class ApiSourceConfig {
        private String url;
        private String token;
        private String format = "json";
        private int timeoutSeconds = 10;
    }

    @Data
    public static class Redis {
        /** Set true to use Redis for sessions and metadata caching. */
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 6379;
        /** Optional Redis password. */
        private String password;
        private int database = 0;
    }

    @Data
    public static class DbSourceConfig {
        private String url;
        private String username;
        private String password;
        private String table;
        private String column = "config_content";
        private String format = "json";
        /** 0 means load once at boot (no auto-refresh). */
        private long refreshIntervalSeconds = 0;
    }
}
