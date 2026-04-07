package com.vivek.controller;

import com.vivek.sqlstorm.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link RowMutationController}.
 *
 * <p>Security enforcement (403 for READ_ONLY, 401 for unauthenticated) is the
 * primary focus. Success-path tests mock {@link DatabaseManager} to avoid
 * needing a real database in CI.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RowMutationControllerTest {

  @Autowired MockMvc mvc;

  /** Mocking DatabaseManager avoids requiring a real DB connection for success paths. */
  @MockBean DatabaseManager databaseManager;

  // ── Unauthenticated → 401 ─────────────────────────────────────────────────

  @Test
  void addRow_withNoAuth_returns401() throws Exception {
    mvc.perform(post("/api/row/add")
            .param("group", "g1").param("database", "db1").param("table", "t1")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void editRow_withNoAuth_returns401() throws Exception {
    mvc.perform(put("/api/row/edit")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteRow_withNoAuth_returns401() throws Exception {
    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1"))
        .andExpect(status().isUnauthorized());
  }

  // ── READ_ONLY → 403 ───────────────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void addRow_asReadOnly_returns403() throws Exception {
    mvc.perform(post("/api/row/add")
            .param("group", "g1").param("database", "db1").param("table", "t1")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void editRow_asReadOnly_returns403() throws Exception {
    mvc.perform(put("/api/row/edit")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void deleteRow_asReadOnly_returns403() throws Exception {
    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1"))
        .andExpect(status().isForbidden());
  }

  // ── READ_WRITE → 200 ──────────────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void addRow_asReadWrite_returns200() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(1);

    mvc.perform(post("/api/row/add")
            .param("group", "g1").param("database", "db1").param("table", "t1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alice\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void deleteRow_asReadWrite_returns200() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(1);

    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void editRow_asReadWrite_returns200() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(1);

    mvc.perform(put("/api/row/edit")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Updated\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void addRow_whenNotUpdatable_returns403() throws Exception {
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);

    mvc.perform(post("/api/row/add")
            .param("group", "g1").param("database", "db1").param("table", "t1")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Alice\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void deleteRow_whenNotDeletable_returns403() throws Exception {
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);

    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void addRow_asAdmin_returns200() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(1);

    mvc.perform(post("/api/row/add")
            .param("group", "g1").param("database", "db1").param("table", "t1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bob\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void editRow_whenNoRowsAffected_returns400() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(0);

    mvc.perform(put("/api/row/edit")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "999")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ghost\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void deleteRow_whenNoRowsAffected_returns400() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(0);

    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "999"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void addRow_whenSQLException_returns500() throws Exception {
    Connection conn = mock(Connection.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willThrow(new java.sql.SQLException("DB error"));

    mvc.perform(post("/api/row/add")
            .param("group", "g1").param("database", "db1").param("table", "t1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alice\"}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void editRow_whenNotUpdatable_returns403() throws Exception {
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);

    mvc.perform(put("/api/row/edit")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void addRow_withCustomRelationConfig_andNoSensitiveColumn_returns200() throws Exception {
    // Exercises canWriteSensitive() and the sensitive-column-check loop
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getCustomRelationConfig())
        .willReturn(new CustomRelationConfig(Map.of())); // non-null cfg, no sensitive cols
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(1);

    mvc.perform(post("/api/row/add")
            .param("group", "g1").param("database", "db1").param("table", "t1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alice\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void editRow_whenSQLException_returns500() throws Exception {
    Connection conn = mock(Connection.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getCustomRelationConfig()).willReturn(null);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willThrow(new java.sql.SQLException("DB error"));

    mvc.perform(put("/api/row/edit")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void deleteRow_whenSQLException_returns500() throws Exception {
    Connection conn = mock(Connection.class);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willThrow(new java.sql.SQLException("DB error"));

    mvc.perform(delete("/api/row")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void editRow_withCustomRelationConfig_andNoSensitiveColumn_returns200() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.getCustomRelationConfig())
        .willReturn(new CustomRelationConfig(Map.of()));
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(1);

    mvc.perform(put("/api/row/edit")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("pk", "id").param("pkValue", "1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bob\"}"))
        .andExpect(status().isOk());
  }
}
