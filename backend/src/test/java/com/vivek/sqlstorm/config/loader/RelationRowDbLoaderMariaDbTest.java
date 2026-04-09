package com.vivek.sqlstorm.config.loader;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.integration.AbstractMariaDbContainerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RelationRowDbLoader} against a real MariaDB container.
 *
 * <p>Validates that {@code MAX(updated_at)} change detection, soft-delete handling, and
 * cross-DB relation loading work correctly against MariaDB's actual timestamp precision
 * and {@code ON UPDATE CURRENT_TIMESTAMP} behaviour — not H2's approximation.</p>
 *
 * <p>Requires Docker. Runs only under the {@code integration-tests} Maven profile.</p>
 */
@Tag("integration")
@Testcontainers
class RelationRowDbLoaderMariaDbTest extends AbstractMariaDbContainerTest {

  private static final String TABLE = "relation_mapping";

  private RelationRowDbLoader loader;

  @BeforeAll
  static void createTable() throws Exception {
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      st.execute("""
          CREATE TABLE IF NOT EXISTS relation_mapping (
              id                BIGINT NOT NULL AUTO_INCREMENT,
              database_name     VARCHAR(128) NOT NULL,
              table_name        VARCHAR(128) NOT NULL,
              column_name       VARCHAR(128) NOT NULL,
              ref_database_name VARCHAR(128) NOT NULL,
              ref_table_name    VARCHAR(128) NOT NULL,
              ref_column_name   VARCHAR(128) NOT NULL,
              conditions_json   TEXT NULL,
              is_active         TINYINT(1) NOT NULL DEFAULT 1,
              created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              UNIQUE KEY uq_relation (
                  database_name, table_name, column_name,
                  ref_database_name, ref_table_name, ref_column_name
              ),
              INDEX idx_updated_at (updated_at),
              INDEX idx_db_active  (database_name, is_active)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
    }
  }

  @AfterAll
  static void dropTable() throws Exception {
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      st.execute("DROP TABLE IF EXISTS " + TABLE);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      st.execute("DELETE FROM " + TABLE);
    }
    loader = new RelationRowDbLoader(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword(), TABLE, null);
  }

  /**
   * Inserts a row with updated_at = NOW() - INTERVAL 2 SECOND so it is
   * outside the 1-second refresh buffer and is immediately visible on load/refresh.
   */
  private void insert(String db, String tbl, String col,
                      String refDb, String refTbl, String refCol,
                      String condJson, boolean active) throws Exception {
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      String cond = condJson == null ? "NULL" : "'" + condJson + "'";
      st.execute(String.format(
          "INSERT INTO %s (database_name,table_name,column_name," +
          "ref_database_name,ref_table_name,ref_column_name,conditions_json,is_active," +
          "created_at,updated_at) " +
          "VALUES ('%s','%s','%s','%s','%s','%s',%s,%d," +
          "NOW() - INTERVAL 2 SECOND, NOW() - INTERVAL 2 SECOND)",
          TABLE, db, tbl, col, refDb, refTbl, refCol, cond, active ? 1 : 0));
    }
  }

  /** Inserts a row with updated_at = NOW() — within the 1-second buffer. */
  private void insertNow(String db, String tbl, String col,
                         String refDb, String refTbl, String refCol,
                         String condJson, boolean active) throws Exception {
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      String cond = condJson == null ? "NULL" : "'" + condJson + "'";
      st.execute(String.format(
          "INSERT INTO %s (database_name,table_name,column_name," +
          "ref_database_name,ref_table_name,ref_column_name,conditions_json,is_active) " +
          "VALUES ('%s','%s','%s','%s','%s','%s',%s,%d)",
          TABLE, db, tbl, col, refDb, refTbl, refCol, cond, active ? 1 : 0));
    }
  }

  /**
   * Forces updated_at to advance by 1 second so MariaDB's DATETIME precision
   * (1-second resolution) triggers a change detection on the next {@code refresh()}.
   */
  private void advanceUpdatedAt() throws Exception {
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      st.execute("UPDATE " + TABLE +
          " SET updated_at = DATE_ADD(updated_at, INTERVAL 1 SECOND)");
    }
    // Give MariaDB a moment to reflect the update
    Thread.sleep(100);
  }

  // ── Load ──────────────────────────────────────────────────────────────────

  @Test
  void load_groupsRelationsByDatabase() throws Exception {
    insert("db1", "orders",   "user_id",     "db1", "users",    "id", null, true);
    insert("db1", "payments", "order_id",    "db1", "orders",   "id", null, true);
    insert("db2", "products", "category_id", "db2", "category", "id", null, true);

    CustomRelationConfig cfg = loader.load();

    assertThat(cfg.getDatabases()).containsKeys("db1", "db2");
    assertThat(cfg.getDatabases().get("db1").getRelations()).hasSize(2);
    assertThat(cfg.getDatabases().get("db2").getRelations()).hasSize(1);
  }

  @Test
  void load_setsSourceToCustom() throws Exception {
    insert("db1", "orders", "user_id", "db1", "users", "id", null, true);

    CustomRelationConfig cfg = loader.load();
    List<ReferenceDTO> refs = cfg.getDatabases().get("db1").getRelations();

    assertThat(refs).allMatch(r -> r.getSource() == ReferenceDTO.Source.CUSTOM);
  }

  @Test
  void load_parsesConditionsJson() throws Exception {
    insert("db1", "orders", "user_id", "db1", "users", "id", "{\"type\":\"inner\"}", true);

    CustomRelationConfig cfg = loader.load();
    ReferenceDTO ref = cfg.getDatabases().get("db1").getRelations().get(0);

    assertThat(ref.getConditions()).isNotNull();
    assertThat(ref.getConditions().getString("type")).isEqualTo("inner");
  }

  @Test
  void load_emptyTable_returnsEmptyConfig() throws Exception {
    CustomRelationConfig cfg = loader.load();
    assertThat(cfg.getDatabases()).isEmpty();
  }

  // ── Change detection ──────────────────────────────────────────────────────

  @Test
  void refresh_noChange_doesNotCallListener() throws Exception {
    insert("db1", "orders", "user_id", "db1", "users", "id", null, true);
    loader.load();

    AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
    loader.setChangeListener(received::set);
    loader.refresh(); // same MAX(updated_at) — no change

    assertThat(received.get()).isNull();
  }

  @Test
  void refresh_afterInsertWithAdvancedTimestamp_callsListener() throws Exception {
    insert("db1", "orders", "user_id", "db1", "users", "id", null, true);
    loader.load();

    insert("db1", "payments", "order_id", "db1", "orders", "id", null, true);
    advanceUpdatedAt(); // ensures MAX(updated_at) advances

    AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
    loader.setChangeListener(received::set);
    loader.refresh();

    assertThat(received.get()).isNotNull();
    assertThat(received.get().getDatabases().get("db1").getRelations()).hasSize(2);
  }

  // ── Soft-delete ───────────────────────────────────────────────────────────

  @Test
  void refresh_afterSoftDelete_excludesInactiveRow() throws Exception {
    insert("db1", "orders",   "user_id",  "db1", "users",  "id", null, true);
    insert("db1", "payments", "order_id", "db1", "orders", "id", null, true);
    loader.load();

    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      // ON UPDATE CURRENT_TIMESTAMP bumps updated_at to NOW(). We then sleep so
      // that timestamp falls outside the 1-second refresh buffer before calling refresh().
      st.execute("UPDATE " + TABLE + " SET is_active = 0 WHERE table_name = 'payments'");
    }
    Thread.sleep(1200); // wait for payments row's new updated_at to clear the buffer

    AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
    loader.setChangeListener(received::set);
    loader.refresh();

    assertThat(received.get()).isNotNull();
    assertThat(received.get().getDatabases().get("db1").getRelations()).hasSize(1);
  }

  // ── Buffer boundary ───────────────────────────────────────────────────────

  @Test
  void refresh_rowInsertedWithinBuffer_notVisibleUntilBufferExpires() throws Exception {
    // Establish baseline with a settled (past) row so lastMaxUpdatedAt > 0
    insert("db1", "orders", "user_id", "db1", "users", "id", null, true);
    loader.load();

    // Insert a row with current timestamp — within the 1-second buffer
    insertNow("db1", "payments", "order_id", "db1", "orders", "id", null, true);

    AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
    loader.setChangeListener(received::set);

    // Immediately refresh — new row must NOT be visible (within buffer)
    loader.refresh();
    assertThat(received.get())
        .as("row inserted within 1-second buffer must not trigger a reload")
        .isNull();

    // Wait for buffer to expire
    Thread.sleep(1200);

    // Refresh again — row must now be visible
    loader.refresh();
    assertThat(received.get())
        .as("row must be visible after buffer expires")
        .isNotNull();
    assertThat(received.get().getDatabases().get("db1").getRelations()).hasSize(2);
  }

  // ── Cross-database ────────────────────────────────────────────────────────

  @Test
  void load_crossDatabaseRelation_preservesRefDatabase() throws Exception {
    insert("db1", "orders", "user_id", "db2", "users", "id", null, true);

    CustomRelationConfig cfg = loader.load();
    ReferenceDTO ref = cfg.getDatabases().get("db1").getRelations().get(0);

    assertThat(ref.getReferenceDatabaseName()).isEqualTo("db2");
    assertThat(ref.getReferenceTableName()).isEqualTo("users");
  }
}
