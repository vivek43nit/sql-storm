package com.vivek.controller;

import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("unchecked")

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MetaDataControllerTest {

  @Autowired MockMvc mvc;
  @MockBean DatabaseManager databaseManager;

  // ── /api/groups ──────────────────────────────────────────────────────────

  @Test
  @WithMockUser
  void getGroups_whenAuthenticated_returnsGroupList() throws Exception {
    given(databaseManager.getGroupNames()).willReturn(Set.of("prod", "staging"));

    mvc.perform(get("/api/groups"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void getGroups_whenNotAuthenticated_returns401() throws Exception {
    mvc.perform(get("/api/groups"))
        .andExpect(status().isUnauthorized());
  }

  // ── /api/databases ────────────────────────────────────────────────────────

  @Test
  @WithMockUser
  void getDatabases_whenAuthenticated_returnsDatabaseList() throws Exception {
    given(databaseManager.getDbNames("prod")).willReturn(Set.of("mydb"));

    mvc.perform(get("/api/databases").param("group", "prod"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void getDatabases_whenConnectionNotFound_returns400() throws Exception {
    given(databaseManager.getDbNames("bad-group"))
        .willThrow(new ConnectionDetailNotFound("Unknown group"));

    mvc.perform(get("/api/databases").param("group", "bad-group"))
        .andExpect(status().isBadRequest());
  }

  // ── /api/tables ───────────────────────────────────────────────────────────

  @Test
  @WithMockUser
  void getTables_whenAuthenticated_returnsTableList() throws Exception {
    TableDTO table = mock(TableDTO.class);
    given(table.getTableName()).willReturn("users");
    given(table.getRemark()).willReturn("");
    given(table.getPrimaryKey()).willReturn("id");
    given(databaseManager.getTables("g1", "mydb")).willReturn(List.of(table));

    mvc.perform(get("/api/tables").param("group", "g1").param("database", "mydb"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("users"));
  }

  @Test
  @WithMockUser
  void getTables_whenSQLException_returns400() throws Exception {
    given(databaseManager.getTables(anyString(), anyString()))
        .willThrow(new SQLException("DB error"));

    mvc.perform(get("/api/tables").param("group", "g1").param("database", "mydb"))
        .andExpect(status().isBadRequest());
  }

  // ── /api/admin/relations ─────────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "ADMIN")
  void getRelations_withoutTableFilter_returnsAllRelations() throws Exception {
    TableDTO table = mock(TableDTO.class);
    given(table.getTableName()).willReturn("orders");
    given(table.getColumns()).willReturn(Collections.emptyList());
    given(databaseManager.getTables("g1", "mydb")).willReturn(List.of(table));

    mvc.perform(get("/api/admin/relations").param("group", "g1").param("database", "mydb"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getRelations_withTableFilter_filtersResults() throws Exception {
    TableDTO table = mock(TableDTO.class);
    given(table.getTableName()).willReturn("orders");
    given(table.getColumns()).willReturn(Collections.emptyList());
    given(databaseManager.getTables("g1", "mydb")).willReturn(List.of(table));

    mvc.perform(get("/api/admin/relations")
            .param("group", "g1").param("database", "mydb").param("table", "other"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  // ── /api/admin/suggestions ────────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "ADMIN")
  void getSuggestions_whenAuthenticated_returnsSuggestions() throws Exception {
    given(databaseManager.getDbNames("g1")).willReturn(Set.of("mydb"));
    given(databaseManager.getTables("g1", "mydb")).willReturn(Collections.emptyList());

    mvc.perform(get("/api/admin/suggestions").param("group", "g1"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getSuggestions_whenConnectionNotFound_returns400() throws Exception {
    given(databaseManager.getDbNames("bad"))
        .willThrow(new ConnectionDetailNotFound("Unknown group"));

    mvc.perform(get("/api/admin/suggestions").param("group", "bad"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getRelations_withColumnsHavingReferTo_returnsRelationEntries() throws Exception {
    ColumnDTO col = mock(ColumnDTO.class);
    TableDTO table = mock(TableDTO.class);
    com.vivek.sqlstorm.dto.ColumnPath ref =
        new com.vivek.sqlstorm.dto.ColumnPath("db1", "orders", "user_id");

    given(col.getName()).willReturn("id");
    given(col.getReferTo()).willReturn(Collections.emptyList());
    given(col.getReferencedBy()).willReturn(List.of(ref));
    given(table.getTableName()).willReturn("users");
    given(table.getColumns()).willReturn(List.of(col));
    given(databaseManager.getTables("g1", "mydb")).willReturn(List.of(table));

    mvc.perform(get("/api/admin/relations").param("group", "g1").param("database", "mydb"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].table").value("users"))
        .andExpect(jsonPath("$[0].column").value("id"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getRelations_whenSQLException_returns400() throws Exception {
    given(databaseManager.getTables(anyString(), anyString()))
        .willThrow(new SQLException("DB error"));

    mvc.perform(get("/api/admin/relations").param("group", "g1").param("database", "mydb"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getSuggestions_withColumnsHavingReferTo_returnsSuggestions() throws Exception {
    ColumnDTO col = mock(ColumnDTO.class);
    TableDTO table = mock(TableDTO.class);
    com.vivek.sqlstorm.dto.ColumnPath ref =
        new com.vivek.sqlstorm.dto.ColumnPath("db1", "users", "id");

    given(col.getName()).willReturn("user_id");
    given(col.getReferTo()).willReturn(List.of(ref));
    given(table.getTableName()).willReturn("orders");
    given(table.getColumns()).willReturn(List.of(col));
    given(databaseManager.getDbNames("g1")).willReturn(Set.of("mydb"));
    given(databaseManager.getTables("g1", "mydb")).willReturn(List.of(table));

    mvc.perform(get("/api/admin/suggestions").param("group", "g1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mydb").isArray());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getSuggestions_whenTablesThrows_returns400() throws Exception {
    given(databaseManager.getDbNames("g1")).willReturn(Set.of("mydb"));
    given(databaseManager.getTables(anyString(), anyString()))
        .willThrow(new SQLException("DB error"));

    mvc.perform(get("/api/admin/suggestions").param("group", "g1"))
        .andExpect(status().isBadRequest());
  }
}
