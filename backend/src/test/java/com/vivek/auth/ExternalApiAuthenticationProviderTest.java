package com.vivek.auth;

import com.sun.net.httpserver.HttpServer;
import com.vivek.config.FkBlitzAuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ExternalApiAuthenticationProvider} backed by an in-process HTTP server.
 */
class ExternalApiAuthenticationProviderTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  private ExternalApiAuthenticationProvider buildProvider(String path) {
    FkBlitzAuthProperties.ExternalApiConfig cfg = new FkBlitzAuthProperties.ExternalApiConfig();
    cfg.setUrl("http://localhost:" + port + path);
    cfg.setTimeoutSeconds(5);
    cfg.setRoleClaim("role");
    cfg.setPermissionsClaim("permissions");
    return new ExternalApiAuthenticationProvider(cfg);
  }

  private Authentication token(String user, String pass) {
    return new UsernamePasswordAuthenticationToken(user, pass);
  }

  @Test
  void authenticate_whenAuthenticatedTrue_returnsAuthentication() throws Exception {
    String resp = "{\"authenticated\":true,\"role\":\"READ_WRITE\"}";
    server.createContext("/auth", ex -> {
      byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.getResponseBody().close();
    });
    server.start();

    ExternalApiAuthenticationProvider provider = buildProvider("/auth");
    Authentication result = provider.authenticate(token("alice", "secret"));

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getName()).isEqualTo("alice");
    assertThat(result.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_READ_WRITE"));
  }

  @Test
  void authenticate_withPermissions_addsPermissionAuthorities() throws Exception {
    String resp = "{\"authenticated\":true,\"role\":\"READ_ONLY\",\"permissions\":\"AUDIT_VIEWER, DATA_EXPORT\"}";
    server.createContext("/auth2", ex -> {
      byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.getResponseBody().close();
    });
    server.start();

    ExternalApiAuthenticationProvider provider = buildProvider("/auth2");
    Authentication result = provider.authenticate(token("bob", "pass"));

    assertThat(result.getAuthorities()).anyMatch(a -> a.getAuthority().equals("AUDIT_VIEWER"));
    assertThat(result.getAuthorities()).anyMatch(a -> a.getAuthority().equals("DATA_EXPORT"));
  }

  @Test
  void authenticate_withBearerToken_sendsAuthorizationHeader() throws Exception {
    FkBlitzAuthProperties.ExternalApiConfig cfg = new FkBlitzAuthProperties.ExternalApiConfig();
    cfg.setUrl("http://localhost:" + port + "/auth3");
    cfg.setTimeoutSeconds(5);
    cfg.setToken("my-secret-token");
    cfg.setRoleClaim("role");
    cfg.setPermissionsClaim("permissions");

    server.createContext("/auth3", ex -> {
      String auth = ex.getRequestHeaders().getFirst("Authorization");
      String status = (auth != null && auth.startsWith("Bearer ")) ? "true" : "false";
      String resp = "{\"authenticated\":" + status + ",\"role\":\"READ_ONLY\"}";
      byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.getResponseBody().close();
    });
    server.start();

    ExternalApiAuthenticationProvider provider = new ExternalApiAuthenticationProvider(cfg);
    Authentication result = provider.authenticate(token("carol", "pass"));
    assertThat(result.isAuthenticated()).isTrue();
  }

  @Test
  void authenticate_whenAuthenticatedFalse_throwsBadCredentials() throws Exception {
    String resp = "{\"authenticated\":false}";
    server.createContext("/auth4", ex -> {
      byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.getResponseBody().close();
    });
    server.start();

    ExternalApiAuthenticationProvider provider = buildProvider("/auth4");
    assertThatThrownBy(() -> provider.authenticate(token("eve", "wrong")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void authenticate_whenNon2xxResponse_throwsBadCredentials() throws Exception {
    server.createContext("/auth5", ex -> {
      ex.sendResponseHeaders(401, -1);
      ex.getResponseBody().close();
    });
    server.start();

    ExternalApiAuthenticationProvider provider = buildProvider("/auth5");
    assertThatThrownBy(() -> provider.authenticate(token("frank", "pass")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void authenticate_withUnknownRole_defaultsToReadOnly() throws Exception {
    String resp = "{\"authenticated\":true,\"role\":\"SUPERADMIN_NONEXISTENT\"}";
    server.createContext("/auth6", ex -> {
      byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.getResponseBody().close();
    });
    server.start();

    ExternalApiAuthenticationProvider provider = buildProvider("/auth6");
    Authentication result = provider.authenticate(token("grace", "pass"));
    assertThat(result.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_READ_ONLY"));
  }

  @Test
  void authenticate_whenServerUnreachable_throwsBadCredentials() {
    // No server started on this port — connection should fail
    FkBlitzAuthProperties.ExternalApiConfig cfg = new FkBlitzAuthProperties.ExternalApiConfig();
    cfg.setUrl("http://localhost:1/auth-unreachable");
    cfg.setTimeoutSeconds(1);
    cfg.setRoleClaim("role");
    cfg.setPermissionsClaim("permissions");

    ExternalApiAuthenticationProvider provider = new ExternalApiAuthenticationProvider(cfg);
    assertThatThrownBy(() -> provider.authenticate(token("henry", "pass")))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("unavailable");
  }

  @Test
  void supports_usernamePasswordToken_returnsTrue() {
    ExternalApiAuthenticationProvider provider = buildProvider("/auth");
    assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
  }

  @Test
  void supports_otherTokenType_returnsFalse() {
    ExternalApiAuthenticationProvider provider = buildProvider("/auth");
    assertThat(provider.supports(Authentication.class)).isFalse();
  }
}
