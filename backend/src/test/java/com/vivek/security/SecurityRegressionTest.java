package com.vivek.security;

import com.vivek.sqlstorm.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security regression tests — verifies that auth, role enforcement,
 * rate limiting, and session handling behave correctly.
 *
 * <p>These run on every PR to prevent regressions at security boundaries.</p>
 *
 * <p><b>Session-based auth note:</b> FkBlitz uses form-based session auth
 * (not JWT). "Tampered token" scenarios are expressed as unauthenticated
 * requests (no valid session = anonymous = 401/403), which is the correct
 * Spring Security behaviour for session-based systems.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityRegressionTest {

  @Autowired MockMvc mvc;
  @MockBean  DatabaseManager databaseManager;

  // ── No session → 401 ──────────────────────────────────────────────────────

  @Test
  void protectedEndpoint_withNoSession_returns401() throws Exception {
    mvc.perform(get("/api/groups"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tablesEndpoint_withNoSession_returns401() throws Exception {
    mvc.perform(get("/api/tables").param("group", "g").param("database", "db"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void executeEndpoint_withNoSession_returns401() throws Exception {
    mvc.perform(post("/api/execute").param("group", "g")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db\",\"query\":\"SELECT 1\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ── Wrong role → 403 ──────────────────────────────────────────────────────

  @Test
  @WithMockUser(username = "reader", roles = "READ_ONLY")
  void adminEndpoint_withReadOnlyRole_returns403() throws Exception {
    mvc.perform(get("/api/admin/relations").param("group", "g").param("database", "db"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "reader", roles = "READ_ONLY")
  void rowMutationPost_withReadOnlyRole_returns403() throws Exception {
    mvc.perform(post("/api/row")
            .param("group", "g").param("database", "db").param("table", "t")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "reader", roles = "READ_ONLY")
  void rowMutationDelete_withReadOnlyRole_returns403() throws Exception {
    mvc.perform(delete("/api/row")
            .param("group", "g").param("database", "db")
            .param("table", "t").param("pk", "id").param("pkValue", "1"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "reader", roles = "READ_ONLY")
  void userManagementEndpoint_withReadOnlyRole_returns403() throws Exception {
    mvc.perform(get("/api/admin/users"))
        .andExpect(status().isForbidden());
  }

  // ── DML from READ_ONLY via execute endpoint → 403 ─────────────────────────

  @Test
  @WithMockUser(username = "sec-dml", roles = "READ_ONLY")
  void executeEndpoint_dmlQueryFromReadOnly_returns403() throws Exception {
    // queryType=U signals DML intent; the controller/security layer rejects it for READ_ONLY
    mvc.perform(post("/api/execute").param("group", "g")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db\",\"query\":\"UPDATE t SET a=1\",\"queryType\":\"U\"}"))
        .andExpect(status().isForbidden());
  }

  // ── Rate limiting → 429 ───────────────────────────────────────────────────

  @Test
  @WithMockUser(username = "sec-rl-exhaust", roles = "READ_ONLY")
  void executeEndpoint_burstExceedsRateLimit_returns429WithRetryAfter() throws Exception {
    // Exhaust the 60 req/min bucket. Send 80 requests to guarantee exhaustion
    // even if a few tokens refill during the loop.
    String body = "{\"database\":\"db\",\"query\":\"UPDATE x SET a=1\",\"queryType\":\"U\"}";
    for (int i = 0; i < 80; i++) {
      mvc.perform(post("/api/execute").param("group", "g")
          .contentType(MediaType.APPLICATION_JSON)
          .content(body));
    }
    mvc.perform(post("/api/execute").param("group", "g")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  // ── Public endpoints remain accessible ────────────────────────────────────

  @Test
  void healthLiveness_withNoSession_returns200() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk());
  }

  @Test
  void authConfig_withNoSession_returns200() throws Exception {
    mvc.perform(get("/api/auth/config"))
        .andExpect(status().isOk());
  }

  // ── Actuator (non-health) requires ADMIN ──────────────────────────────────

  @Test
  void actuatorMetrics_withNoSession_returns401() throws Exception {
    mvc.perform(get("/actuator/metrics"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "viewer", roles = "READ_ONLY")
  void actuatorMetrics_withReadOnlyRole_returns403() throws Exception {
    mvc.perform(get("/actuator/metrics"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "superadmin", roles = "ADMIN")
  void actuatorMetrics_withAdminRole_passesSecurityCheck() throws Exception {
    // Security contract: ADMIN must not receive 401 or 403.
    // (The endpoint may 500 in unit-test context due to missing registry config — that
    // is a configuration concern, not a security regression.)
    mvc.perform(get("/actuator/metrics"))
        .andExpect(result -> {
          int status = result.getResponse().getStatus();
          org.assertj.core.api.Assertions.assertThat(status)
              .withFailMessage("Expected ADMIN to pass security check (not 401/403), got %d", status)
              .isNotEqualTo(401)
              .isNotEqualTo(403);
        });
  }
}
