package com.vivek.web;

import com.vivek.sqlstorm.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for {@link RateLimitFilter}.
 *
 * <p>The per-user Bucket4j buckets are keyed by principal name. By using
 * distinct {@code @WithMockUser} usernames, tests are isolated from each
 * other's token consumption.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RateLimitFilterTest {

  @Autowired MockMvc mvc;
  @MockBean DatabaseManager databaseManager;

  // ── Untracked endpoint (no limit applied) ─────────────────────────────────

  @Test
  @WithMockUser(username = "rl-user-1")
  void untrackedEndpoint_passesThrough() throws Exception {
    mvc.perform(get("/api/groups"))
        .andExpect(status().isOk());
  }

  // ── /api/execute — limit 60 req/min ───────────────────────────────────────

  @Test
  @WithMockUser(username = "rl-user-2", roles = "READ_ONLY")
  void executeEndpoint_withinLimit_isNotRateLimited() throws Exception {
    // First request should always pass
    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"SELECT 1\"}"))
        .andExpect(result ->
            org.assertj.core.api.Assertions.assertThat(
                result.getResponse().getStatus()).isNotEqualTo(429));
  }

  @Test
  @WithMockUser(username = "rl-exhaust-execute", roles = "READ_ONLY")
  void executeEndpoint_whenLimitExceeded_returns429() throws Exception {
    // Exhaust all 60 tokens in the bucket
    for (int i = 0; i < 60; i++) {
      mvc.perform(post("/api/execute").param("group", "g1")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"database\":\"db1\",\"query\":\"SELECT 1\"}"));
    }
    // 61st must be rate-limited
    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"SELECT 1\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  // ── /api/row/** — limit 30 req/min ────────────────────────────────────────

  @Test
  @WithMockUser(username = "rl-user-3", roles = "READ_WRITE")
  void rowEndpoint_withinLimit_isNotRateLimited() throws Exception {
    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1"))
        .andExpect(result ->
            org.assertj.core.api.Assertions.assertThat(
                result.getResponse().getStatus()).isNotEqualTo(429));
  }

  @Test
  @WithMockUser(username = "rl-exhaust-row", roles = "READ_WRITE")
  void rowEndpoint_whenLimitExceeded_returns429() throws Exception {
    // Exhaust all 30 tokens in the bucket
    for (int i = 0; i < 30; i++) {
      mvc.perform(delete("/api/row")
          .param("group", "g1").param("database", "db1")
          .param("table", "t1").param("pk", "id").param("pkValue", "1"));
    }
    // 31st must be rate-limited
    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1"))
        .andExpect(status().isTooManyRequests());
  }

  // ── Anonymous user is rate-limited independently ──────────────────────────

  @Test
  void anonymousEndpoint_notRateLimited() throws Exception {
    // /api/auth/config is public and untracked — never rate-limited
    mvc.perform(get("/api/auth/config"))
        .andExpect(status().isOk());
  }
}
