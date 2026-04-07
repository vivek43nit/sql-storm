package com.vivek.auth;

import com.vivek.config.FkBlitzAuthProperties;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Delegates credential verification to an external HTTP service.
 *
 * Expected request:  POST <url>  body: {"username":"...","password":"..."}
 * Expected response: {"authenticated":true,"role":"READ_WRITE","permissions":"SENSITIVE_DATA_RO"}
 *
 * On timeout or non-2xx: login fails (fail-closed).
 */
public class ExternalApiAuthenticationProvider implements AuthenticationProvider {
    private static final Logger log = LoggerFactory.getLogger(ExternalApiAuthenticationProvider.class);

    private final FkBlitzAuthProperties.ExternalApiConfig config;
    private final HttpClient http;

    public ExternalApiAuthenticationProvider(FkBlitzAuthProperties.ExternalApiConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

            if (config.getToken() != null && !config.getToken().isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + config.getToken());
            }

            HttpResponse<String> response = http.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("External auth API returned HTTP {} for user '{}'", response.statusCode(), username);
                throw new BadCredentialsException("Authentication failed");
            }

            JSONObject resp = new JSONObject(response.body());
            if (!resp.optBoolean("authenticated", false)) {
                throw new BadCredentialsException("Invalid credentials");
            }

            List<GrantedAuthority> authorities = buildAuthorities(resp);
            return new UsernamePasswordAuthenticationToken(username, null, authorities);

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("External auth API error for user '{}': {}", username, e.getMessage());
            throw new BadCredentialsException("Authentication service unavailable");
        }
    }

    private List<GrantedAuthority> buildAuthorities(JSONObject resp) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        String roleName = resp.optString(config.getRoleClaim(), Role.READ_ONLY.name()).toUpperCase();
        try {
            authorities.add(new SimpleGrantedAuthority(Role.valueOf(roleName).toAuthority()));
        } catch (IllegalArgumentException e) {
            authorities.add(new SimpleGrantedAuthority(Role.READ_ONLY.toAuthority()));
        }

        String permsRaw = resp.optString(config.getPermissionsClaim(), "");
        if (!permsRaw.isBlank()) {
            for (String perm : permsRaw.split(",")) {
                String t = perm.trim().toUpperCase();
                if (!t.isEmpty()) authorities.add(new SimpleGrantedAuthority(t));
            }
        }

        return authorities;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
