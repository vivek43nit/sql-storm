package com.vivek.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "fkblitz.auth")
public class FkBlitzAuthProperties {

    /**
     * User store backend: h2 | mysql | config | external-api
     * Default: h2 (embedded H2 in-memory, suitable for standalone/dev)
     */
    private String userStore = "h2";

    /** Bootstrap admin credentials (used if no users exist in store). */
    private String adminUser = "admin";
    private String adminPassword = "changeme";

    /** Static users list — only used when userStore=config */
    private List<ConfigUser> users = new ArrayList<>();

    /** External API auth settings — only used when userStore=external-api */
    private ExternalApiConfig externalApi = new ExternalApiConfig();

    /** OAuth2/OIDC settings (works alongside any userStore) */
    private OAuth2Config oauth2 = new OAuth2Config();

    @Data
    public static class ConfigUser {
        private String username;
        /** Supports {noop}password or {bcrypt}$2a$... prefixes */
        private String password;
        private String role = "READ_ONLY";
        private String permissions = "";
    }

    @Data
    public static class ExternalApiConfig {
        private String url;
        /** Optional Bearer token sent to the external auth API */
        private String token;
        /** JSON path in the response body to read the role value */
        private String roleClaim = "role";
        /** JSON path in the response body to read permissions (comma-separated) */
        private String permissionsClaim = "permissions";
        private int timeoutSeconds = 5;
    }

    @Data
    public static class OAuth2Config {
        private boolean enabled = false;
        /** JWT / OAuth2 user-info claim name that maps to FkBlitz role */
        private String roleClaim = "fkblitz_role";
        /** Default role assigned when the role claim is absent */
        private String defaultRole = "READ_ONLY";
    }
}
