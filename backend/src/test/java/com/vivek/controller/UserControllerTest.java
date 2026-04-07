package com.vivek.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link UserController}.
 *
 * <p>Uses the real H2 user store so user CRUD operations are fully exercised.
 * {@code @DirtiesContext} ensures the user store is reset between test methods
 * that create/delete users, preventing state bleed.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserControllerTest {

  @Autowired MockMvc mvc;

  // ── GET /api/admin/users ──────────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "ADMIN")
  void listUsers_asAdmin_returns200() throws Exception {
    mvc.perform(get("/api/admin/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void listUsers_asReadOnly_returns403() throws Exception {
    mvc.perform(get("/api/admin/users"))
        .andExpect(status().isForbidden());
  }

  @Test
  void listUsers_whenNotAuthenticated_returns401() throws Exception {
    mvc.perform(get("/api/admin/users"))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/admin/users/{id} ─────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "ADMIN")
  void getUser_whenNotFound_returns404() throws Exception {
    mvc.perform(get("/api/admin/users/999999"))
        .andExpect(status().isNotFound());
  }

  // ── POST /api/admin/users ─────────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "ADMIN")
  @DirtiesContext
  void createUser_asAdmin_withValidBody_returns201() throws Exception {
    mvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser1\",\"password\":\"pass123\",\"role\":\"READ_ONLY\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("testuser1"))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void createUser_missingUsername_returns400() throws Exception {
    mvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"pass123\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void createUser_missingPassword_returns400() throws Exception {
    mvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser2\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DirtiesContext
  void createUser_duplicateUsername_returns400() throws Exception {
    String body = "{\"username\":\"dupuser\",\"password\":\"pass123\"}";
    mvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  // ── PUT /api/admin/users/{id} ─────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "ADMIN")
  void updateUser_whenNotFound_returns400OrError() throws Exception {
    mvc.perform(put("/api/admin/users/999999")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"READ_WRITE\"}"))
        .andExpect(result ->
            org.assertj.core.api.Assertions.assertThat(
                result.getResponse().getStatus()).isIn(400, 500));
  }

  // ── DELETE /api/admin/users/{id} ──────────────────────────────────────────

  @Test
  @WithMockUser(roles = "ADMIN")
  @DirtiesContext
  void deleteUser_asAdmin_returns204() throws Exception {
    // Create a user first
    String createResult = mvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"todelete\",\"password\":\"pass123\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    // Extract id from response
    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    Long id = mapper.readTree(createResult).get("id").asLong();

    mvc.perform(delete("/api/admin/users/" + id))
        .andExpect(status().isNoContent());
  }
}
