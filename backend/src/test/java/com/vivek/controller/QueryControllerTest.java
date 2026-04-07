package com.vivek.controller;

import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.ColumnPath;
import com.vivek.sqlstorm.dto.DatabaseDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class QueryControllerTest {

  @Autowired MockMvc mvc;
  @MockBean DatabaseManager databaseManager;

  // ── POST /api/execute ─────────────────────────────────────────────────────

  @Test
  void execute_whenNotAuthenticated_returns401() throws Exception {
    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"SELECT 1\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void execute_whenInvalidRequest_returns400() throws Exception {
    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void execute_whenConnectionNotFound_returns500() throws Exception {
    given(databaseManager.isUpdatableConnection("g1", "db1"))
        .willThrow(new ConnectionDetailNotFound("Unknown"));

    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"SELECT 1\"}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void execute_selectQuery_whenSQLException_returns500() throws Exception {
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.getConnection("g1", "db1")).willThrow(new SQLException("DB error"));

    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"SELECT 1\"}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  @WithMockUser(roles = "READ_ONLY")
  void execute_selectQuery_returnsResultSet() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData meta = mock(ResultSetMetaData.class);

    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeQuery()).willReturn(rs);
    given(rs.getMetaData()).willReturn(meta);
    given(meta.getColumnCount()).willReturn(0);
    given(rs.next()).willReturn(false);
    given(databaseManager.getMetaData("g1", "db1")).willReturn(mock(DatabaseDTO.class));
    given(databaseManager.getCustomRelationConfig())
        .willReturn(new CustomRelationConfig(Map.of()));

    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"SELECT 1\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void execute_dmlQuery_whenNotUpdatable_returns403() throws Exception {
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);

    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"INSERT INTO t VALUES(1)\",\"queryType\":\"I\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "READ_WRITE")
  void execute_dmlQuery_whenUpdatable_returnsAffectedRows() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);

    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(true);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeUpdate()).willReturn(1);

    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"INSERT INTO t VALUES(1)\",\"queryType\":\"I\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affectedRows").value(1));
  }

  @Test
  @WithMockUser
  void execute_selectWithColumn_returnsResultSet() throws Exception {
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData meta = mock(ResultSetMetaData.class);
    DatabaseDTO mockDb = mock(DatabaseDTO.class);
    TableDTO mockTable = mock(TableDTO.class);

    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeQuery()).willReturn(rs);
    given(rs.getMetaData()).willReturn(meta);
    given(meta.getColumnCount()).willReturn(1);
    given(meta.getColumnLabel(1)).willReturn("id");
    given(meta.getTableName(1)).willReturn("users");
    given(rs.next()).willReturn(true, false);
    given(rs.getObject(1)).willReturn(42);
    given(databaseManager.getMetaData("g1", "db1")).willReturn(mockDb);
    given(mockDb.getTableMetaData("users")).willReturn(mockTable);
    given(mockTable.getColumnMetaData(anyString())).willReturn(null);
    given(databaseManager.getCustomRelationConfig())
        .willReturn(new CustomRelationConfig(Map.of()));

    mvc.perform(post("/api/execute").param("group", "g1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"database\":\"db1\",\"query\":\"SELECT id FROM users\"}"))
        .andExpect(status().isOk());
  }

  // ── GET /api/references ───────────────────────────────────────────────────

  @Test
  @WithMockUser
  void getReferences_whenNoReferencedBy_returnsEmptyList() throws Exception {
    DatabaseDTO mockDb = mock(DatabaseDTO.class);
    TableDTO mockTable = mock(TableDTO.class);
    ColumnDTO mockColumn = mock(ColumnDTO.class);

    given(databaseManager.getMetaData("g1", "db1")).willReturn(mockDb);
    given(mockDb.getTableMetaData("t1")).willReturn(mockTable);
    given(mockTable.getColumnMetaData("id")).willReturn(mockColumn);
    given(mockColumn.getReferencedBy()).willReturn(Collections.emptyList());

    mvc.perform(get("/api/references")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("column", "id")
            .param("row", "{\"id\":1}"))
        .andExpect(status().isOk())
        .andExpect(content().string("[]"));
  }

  // ── GET /api/dereferences ─────────────────────────────────────────────────

  @Test
  @WithMockUser
  void getDeReferences_whenNoReferTo_returnsEmptyList() throws Exception {
    DatabaseDTO mockDb = mock(DatabaseDTO.class);
    TableDTO mockTable = mock(TableDTO.class);
    ColumnDTO mockColumn = mock(ColumnDTO.class);

    given(databaseManager.getMetaData("g1", "db1")).willReturn(mockDb);
    given(mockDb.getTableMetaData("t1")).willReturn(mockTable);
    given(mockTable.getColumnMetaData("user_id")).willReturn(mockColumn);
    given(mockColumn.getReferTo()).willReturn(Collections.emptyList());

    mvc.perform(get("/api/dereferences")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("column", "user_id")
            .param("row", "{\"user_id\":42}"))
        .andExpect(status().isOk())
        .andExpect(content().string("[]"));
  }

  @Test
  @WithMockUser
  void getDeReferences_whenReferToExists_returnsResults() throws Exception {
    DatabaseDTO mockDb = mock(DatabaseDTO.class);
    TableDTO mockTable = mock(TableDTO.class);
    TableDTO refTable = mock(TableDTO.class);
    ColumnDTO mockColumn = mock(ColumnDTO.class);
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData meta = mock(ResultSetMetaData.class);

    ColumnPath referTo = new ColumnPath("db1", "users", "id");

    given(databaseManager.getMetaData("g1", "db1")).willReturn(mockDb);
    given(mockDb.getTableMetaData("orders")).willReturn(mockTable);
    given(mockTable.getColumnMetaData("user_id")).willReturn(mockColumn);
    given(mockColumn.getReferTo()).willReturn(List.of(referTo));
    given(mockDb.getTableMetaData("users")).willReturn(refTable);
    given(refTable.getPrimaryKey()).willReturn(null);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeQuery()).willReturn(rs);
    given(rs.getMetaData()).willReturn(meta);
    given(meta.getColumnCount()).willReturn(0);
    given(rs.next()).willReturn(false);
    given(databaseManager.getCustomRelationConfig()).willReturn(new CustomRelationConfig(Map.of()));

    mvc.perform(get("/api/dereferences")
            .param("group", "g1").param("database", "db1")
            .param("table", "orders").param("column", "user_id")
            .param("row", "{\"user_id\":42}"))
        .andExpect(status().isOk());
  }

  // ── GET /api/traceRow ─────────────────────────────────────────────────────

  @Test
  @WithMockUser
  void traceRow_whenNoRelations_returnsEmptyList() throws Exception {
    DatabaseDTO mockDb = mock(DatabaseDTO.class);
    TableDTO mockTable = mock(TableDTO.class);

    given(databaseManager.getMetaData("g1", "db1")).willReturn(mockDb);
    given(mockDb.getTableMetaData("t1")).willReturn(mockTable);
    given(mockTable.getColumns()).willReturn(Collections.emptyList());

    mvc.perform(get("/api/trace")
            .param("group", "g1").param("database", "db1")
            .param("table", "t1").param("row", "{\"id\":1}"))
        .andExpect(status().isOk())
        .andExpect(content().string("[]"));
  }

  @Test
  @WithMockUser
  void traceRow_whenColumnHasReferTo_callsDeReferences() throws Exception {
    DatabaseDTO mockDb = mock(DatabaseDTO.class);
    TableDTO mockTable = mock(TableDTO.class);
    TableDTO refTable = mock(TableDTO.class);
    ColumnDTO col = mock(ColumnDTO.class);
    ColumnDTO col2 = mock(ColumnDTO.class);
    ColumnDTO refCol = mock(ColumnDTO.class);
    Connection conn = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData meta = mock(ResultSetMetaData.class);

    ColumnPath referTo = new ColumnPath("db1", "users", "id");

    // col has referTo, col2 has no relations
    given(col.getName()).willReturn("user_id");
    given(col.getReferTo()).willReturn(List.of(referTo));
    given(col.getReferencedBy()).willReturn(Collections.emptyList());
    given(col2.getReferTo()).willReturn(Collections.emptyList());
    given(col2.getReferencedBy()).willReturn(Collections.emptyList());

    given(databaseManager.getMetaData("g1", "db1")).willReturn(mockDb);
    given(mockDb.getTableMetaData("orders")).willReturn(mockTable);
    given(mockTable.getColumns()).willReturn(List.of(col, col2));
    given(mockTable.getColumnMetaData("user_id")).willReturn(refCol);
    given(refCol.getReferTo()).willReturn(List.of(referTo));
    given(mockDb.getTableMetaData("users")).willReturn(refTable);
    given(refTable.getPrimaryKey()).willReturn(null);
    given(databaseManager.getConnection("g1", "db1")).willReturn(conn);
    given(databaseManager.isUpdatableConnection("g1", "db1")).willReturn(false);
    given(databaseManager.isDeletableConnection("g1", "db1")).willReturn(false);
    given(conn.prepareStatement(anyString())).willReturn(ps);
    given(ps.executeQuery()).willReturn(rs);
    given(rs.getMetaData()).willReturn(meta);
    given(meta.getColumnCount()).willReturn(0);
    given(rs.next()).willReturn(false);
    given(databaseManager.getCustomRelationConfig()).willReturn(new CustomRelationConfig(Map.of()));

    mvc.perform(get("/api/trace")
            .param("group", "g1").param("database", "db1")
            .param("table", "orders").param("row", "{\"user_id\":42}"))
        .andExpect(status().isOk());
  }
}
