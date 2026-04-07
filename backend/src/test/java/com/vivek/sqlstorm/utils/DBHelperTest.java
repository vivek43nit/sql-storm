package com.vivek.sqlstorm.utils;

import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.ColumnPath;
import com.vivek.sqlstorm.dto.DatabaseDTO;
import com.vivek.sqlstorm.dto.IndexInfo;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.dto.request.ExecuteRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DBHelper} using an in-process H2 database.
 * No Spring context is required — these are pure JDBC tests.
 */
class DBHelperTest {

  private static Connection conn;

  @BeforeAll
  static void setUp() throws Exception {
    Class.forName("org.h2.Driver");
    conn = DriverManager.getConnection(
        "jdbc:h2:mem:dbhelper_test;DB_CLOSE_DELAY=-1", "sa", "");
    try (Statement st = conn.createStatement()) {
      st.execute("CREATE TABLE IF NOT EXISTS parent_table (" +
          "id INT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
      st.execute("CREATE INDEX IF NOT EXISTS idx_name ON parent_table(name)");
      st.execute("CREATE TABLE IF NOT EXISTS child_table (" +
          "id INT PRIMARY KEY, parent_id INT, " +
          "FOREIGN KEY (parent_id) REFERENCES parent_table(id))");
    }
  }

  @AfterAll
  static void tearDown() throws SQLException {
    if (conn != null) conn.close();
  }

  @Test
  void getTables_returnsCreatedTables() throws SQLException {
    List<TableDTO> tables = DBHelper.getTables(conn);
    assertThat(tables).isNotNull();
    boolean hasParent = tables.stream()
        .anyMatch(t -> "PARENT_TABLE".equalsIgnoreCase(t.getTableName()));
    assertThat(hasParent).isTrue();
  }

  @Test
  void getColumns_returnsColumnsForTable() throws SQLException {
    var columns = DBHelper.getColumns(conn, "PARENT_TABLE");
    assertThat(columns).isNotNull().isNotEmpty();
    boolean hasId = columns.stream().anyMatch(c -> "ID".equalsIgnoreCase(c.getName()));
    assertThat(hasId).isTrue();
  }

  @Test
  void getAllIndexedColumns_returnsIndexInfo() throws SQLException {
    List<IndexInfo> indexes = DBHelper.getAllIndexedColumns(conn, "parent_table");
    assertThat(indexes).isNotNull();
  }

  @Test
  void getAllForeignKeys_returnsFK() throws SQLException {
    List<ReferenceDTO> fks = DBHelper.getAllForeignKeys(conn, "CHILD_TABLE");
    assertThat(fks).isNotNull();
    boolean hasParentRef = fks.stream()
        .anyMatch(r -> "PARENT_TABLE".equalsIgnoreCase(r.getReferenceTableName()));
    assertThat(hasParentRef).isTrue();
  }

  @Test
  void getAllForeignKeys_whenNoFK_returnsEmpty() throws SQLException {
    List<ReferenceDTO> fks = DBHelper.getAllForeignKeys(conn, "parent_table");
    assertThat(fks).isEmpty();
  }

  @Test
  void isReferToConditionMatch_withEmptyConditions_returnsTrue() {
    // empty conditions object means no constraints — always matches
    assertThat(DBHelper.isReferToConditionMatch(
        new org.json.JSONObject(), new org.json.JSONObject())).isTrue();
  }

  @Test
  void isReferToConditionMatch_withMatchingStringCondition_returnsTrue() {
    org.json.JSONObject conditions = new org.json.JSONObject().put("type", "admin");
    org.json.JSONObject data = new org.json.JSONObject().put("type", "admin");
    assertThat(DBHelper.isReferToConditionMatch(conditions, data)).isTrue();
  }

  @Test
  void isReferToConditionMatch_withNonMatchingCondition_returnsFalse() {
    org.json.JSONObject conditions = new org.json.JSONObject().put("type", "admin");
    org.json.JSONObject data = new org.json.JSONObject().put("type", "user");
    assertThat(DBHelper.isReferToConditionMatch(conditions, data)).isFalse();
  }

  @Test
  void isReferToConditionMatch_withArrayCondition_matchingValue_returnsTrue() {
    org.json.JSONArray roles = new org.json.JSONArray().put("admin").put("superadmin");
    org.json.JSONObject conditions = new org.json.JSONObject().put("role", roles);
    org.json.JSONObject data = new org.json.JSONObject().put("role", "admin");
    assertThat(DBHelper.isReferToConditionMatch(conditions, data)).isTrue();
  }

  @Test
  void isReferToConditionMatch_withArrayCondition_nonMatchingValue_returnsFalse() {
    org.json.JSONArray roles = new org.json.JSONArray().put("admin").put("superadmin");
    org.json.JSONObject conditions = new org.json.JSONObject().put("role", roles);
    org.json.JSONObject data = new org.json.JSONObject().put("role", "guest");
    assertThat(DBHelper.isReferToConditionMatch(conditions, data)).isFalse();
  }

  @Test
  void contains_withMatchingValue_returnsTrue() {
    org.json.JSONArray arr = new org.json.JSONArray().put("foo").put("bar");
    assertThat(DBHelper.contains(arr, "foo")).isTrue();
  }

  @Test
  void contains_withNonMatchingValue_returnsFalse() {
    org.json.JSONArray arr = new org.json.JSONArray().put("foo").put("bar");
    assertThat(DBHelper.contains(arr, "baz")).isFalse();
  }

  @Test
  void getWhereQueryFromConditions_withStringValue_buildsClause() {
    org.json.JSONObject conditions = new org.json.JSONObject().put("status", "active");
    String where = DBHelper.getWhereQueryFromConditions(conditions);
    assertThat(where).contains("status").contains("active");
  }

  @Test
  void getWhereQueryFromConditions_withArrayValue_buildsInClause() {
    org.json.JSONArray vals = new org.json.JSONArray().put("a").put("b");
    org.json.JSONObject conditions = new org.json.JSONObject().put("col", vals);
    String where = DBHelper.getWhereQueryFromConditions(conditions);
    assertThat(where).contains("col").contains(" in ");
  }

  // ── getExecuteRequestsForReferedByReq ────────────────────────────────────

  @Test
  void getExecuteRequestsForReferedByReq_whenNullManager_throwsIllegalArgument() {
    ColumnPath self = new ColumnPath("db1", "t1", "id");
    ColumnPath ref  = new ColumnPath("db1", "t2", "parent_id");
    assertThatThrownBy(() ->
        DBHelper.getExecuteRequestsForReferedByReq(null, "g1", self, ref, "42", false, 100))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getExecuteRequestsForReferedByReq_simpleCase_returnsOneRequest() throws Exception {
    DatabaseManager dm  = mock(DatabaseManager.class);
    DatabaseDTO     db  = mock(DatabaseDTO.class);
    TableDTO        tbl = mock(TableDTO.class);

    when(dm.getMetaData("g1", "db1")).thenReturn(db);
    when(db.getTableMetaData("t2")).thenReturn(tbl);
    when(tbl.getPrimaryKey()).thenReturn(null);
    when(tbl.getJointTableMapping()).thenReturn(null);

    ColumnPath self = new ColumnPath("db1", "t1", "id");
    ColumnPath ref  = new ColumnPath("db1", "t2", "parent_id");

    List<ExecuteRequest> reqs = DBHelper.getExecuteRequestsForReferedByReq(
        dm, "g1", self, ref, "42", false, 100);

    assertThat(reqs).hasSize(1);
    assertThat(reqs.get(0).getQuery()).contains("t2").contains("parent_id").contains("42");
  }

  @Test
  void getExecuteRequestsForReferedByReq_withPrimaryKey_addsOrderBy() throws Exception {
    DatabaseManager dm  = mock(DatabaseManager.class);
    DatabaseDTO     db  = mock(DatabaseDTO.class);
    TableDTO        tbl = mock(TableDTO.class);

    when(dm.getMetaData("g1", "db1")).thenReturn(db);
    when(db.getTableMetaData("orders")).thenReturn(tbl);
    when(tbl.getPrimaryKey()).thenReturn("order_id");
    when(tbl.getJointTableMapping()).thenReturn(null);

    ColumnPath self = new ColumnPath("db1", "users", "id");
    ColumnPath ref  = new ColumnPath("db1", "orders", "user_id");

    List<ExecuteRequest> reqs = DBHelper.getExecuteRequestsForReferedByReq(
        dm, "g1", self, ref, "5", false, 50);

    assertThat(reqs).hasSize(1);
    assertThat(reqs.get(0).getQuery()).contains("order_id");
  }

  @Test
  void getExecuteRequestsForReferedByReq_selfReference_marksAsSelf() throws Exception {
    DatabaseManager dm  = mock(DatabaseManager.class);
    DatabaseDTO     db  = mock(DatabaseDTO.class);
    TableDTO        tbl = mock(TableDTO.class);

    when(dm.getMetaData("g1", "db1")).thenReturn(db);
    when(db.getTableMetaData("employees")).thenReturn(tbl);
    when(tbl.getPrimaryKey()).thenReturn(null);
    when(tbl.getJointTableMapping()).thenReturn(null);

    ColumnPath self = new ColumnPath("db1", "employees", "id");
    ColumnPath ref  = new ColumnPath("db1", "employees", "id"); // same path

    List<ExecuteRequest> reqs = DBHelper.getExecuteRequestsForReferedByReq(
        dm, "g1", self, ref, "7", false, 30);

    assertThat(reqs).hasSize(1);
    assertThat(reqs.get(0).getRelation()).isEqualTo(ExecuteRequest.SELF);
  }

  @Test
  void getExecuteRequestsForReferedByReq_withConditions_addsWhereClause() throws Exception {
    DatabaseManager dm  = mock(DatabaseManager.class);
    DatabaseDTO     db  = mock(DatabaseDTO.class);
    TableDTO        tbl = mock(TableDTO.class);

    when(dm.getMetaData("g1", "db1")).thenReturn(db);
    when(db.getTableMetaData("items")).thenReturn(tbl);
    when(tbl.getPrimaryKey()).thenReturn(null);
    when(tbl.getJointTableMapping()).thenReturn(null);

    ColumnPath self = new ColumnPath("db1", "orders", "id");
    ColumnPath ref  = new ColumnPath("db1", "items", "order_id");
    ref.setConditions(new org.json.JSONObject().put("status", "active"));

    List<ExecuteRequest> reqs = DBHelper.getExecuteRequestsForReferedByReq(
        dm, "g1", self, ref, "10", false, 100);

    assertThat(reqs).hasSize(1);
    assertThat(reqs.get(0).getQuery()).contains("status").contains("active");
  }
}
