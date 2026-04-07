package com.vivek.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AuthController}.
 *
 * <p>Uses a full Spring context with H2 (from application.yml) and an empty
 * DatabaseConnection.xml (from src/test/resources) so no real DB is needed.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthControllerTest {

  @Autowired MockMvc mvc;

  // ── GET /api/me ────────────────────────────────────────────────────────────

  @Test
  @WithMockUser(username = "alice", roles = "READ_ONLY")
  void me_whenAuthenticated_returnsUsernameAndRole() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.role").value("READ_ONLY"))
        .andExpect(jsonPath("$.permissions").isArray());
  }

  @Test
  @WithMockUser(username = "admin", roles = "ADMIN")
  void me_asAdmin_returnsAdminRole() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void me_whenNotAuthenticated_returns401() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/auth/config ───────────────────────────────────────────────────

  @Test
  void authConfig_isPublicEndpoint_returns200WithoutAuth() throws Exception {
    mvc.perform(get("/api/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.oauth2Enabled").exists());
  }

  @Test
  void authConfig_defaultConfig_oauth2DisabledIsFalse() throws Exception {
    mvc.perform(get("/api/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.oauth2Enabled").value(false));
  }
}
