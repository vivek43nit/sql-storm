package com.vivek.sqlstorm.config.loader;

import com.sun.net.httpserver.HttpServer;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.customrelation.parsers.CustomRelationConfigJsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ApiConfigLoader} backed by an in-process HTTP server.
 */
class ApiConfigLoaderTest {

  private static final String VALID_JSON = "{\"databases\":{}}";

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

  private ApiConfigLoader<CustomRelationConfig> buildLoader(String path) {
    return new ApiConfigLoader<>(
        "http://localhost:" + port + path,
        null,
        5,
        "json",
        new CustomRelationConfigJsonParser());
  }

  @Test
  void load_whenServerReturns200_parsesAndReturnsConfig() throws Exception {
    server.createContext("/cfg", ex -> {
      byte[] bytes = VALID_JSON.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.getResponseBody().close();
    });
    server.start();

    ApiConfigLoader<CustomRelationConfig> loader = buildLoader("/cfg");
    CustomRelationConfig config = loader.load();
    assertThat(config).isNotNull();
  }

  @Test
  void load_withBearerToken_sendsAuthorizationHeader() throws Exception {
    server.createContext("/cfg-token", ex -> {
      String auth = ex.getRequestHeaders().getFirst("Authorization");
      // Serve valid JSON only when auth header is present
      String body = (auth != null && auth.startsWith("Bearer ")) ? VALID_JSON : "{}";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.getResponseBody().close();
    });
    server.start();

    ApiConfigLoader<CustomRelationConfig> loader = new ApiConfigLoader<>(
        "http://localhost:" + port + "/cfg-token",
        "my-token",
        5,
        "json",
        new CustomRelationConfigJsonParser());

    CustomRelationConfig config = loader.load();
    assertThat(config).isNotNull();
  }

  @Test
  void load_whenServerReturnsNon2xx_throwsConfigLoadException() throws Exception {
    server.createContext("/cfg-error", ex -> {
      ex.sendResponseHeaders(503, -1);
      ex.getResponseBody().close();
    });
    server.start();

    ApiConfigLoader<CustomRelationConfig> loader = buildLoader("/cfg-error");
    assertThatThrownBy(loader::load)
        .isInstanceOf(ConfigLoadException.class)
        .hasMessageContaining("503");
  }

  @Test
  void load_whenServerUnreachable_throwsConfigLoadException() {
    ApiConfigLoader<CustomRelationConfig> loader = new ApiConfigLoader<>(
        "http://localhost:1/cfg-unreachable",
        null,
        1,
        "json",
        new CustomRelationConfigJsonParser());

    assertThatThrownBy(loader::load)
        .isInstanceOf(ConfigLoadException.class);
  }
}
