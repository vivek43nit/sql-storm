package com.vivek.sqlstorm.connection;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.vivek.sqlstorm.integration.AbstractMariaDbContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DatabaseConnectionManager} against a real MariaDB container.
 *
 * <p>Validates HikariCP pool lifecycle (add, remove, credential-change, flags-only reload)
 * against a live MariaDB — behaviour that in-memory H2 cannot reproduce (real TCP handshakes,
 * connection validation, pool eviction).</p>
 *
 * <p>Requires Docker. Runs only under the {@code integration-tests} Maven profile.</p>
 */
@Tag("integration")
@Testcontainers
class DatabaseConnectionManagerMariaDbTest extends AbstractMariaDbContainerTest {

  private DatabaseConnectionManager manager;

  @BeforeEach
  void setUp() {
    manager = singleConnectionManager("grp", "db");
  }

  @AfterEach
  void tearDown() {
    manager.closeAllConnections();
  }

  // ── Basic connectivity ─────────────────────────────────────────────────────

  @Test
  void getConnection_returnsWorkingMariaDbConnection() throws Exception {
    try (Connection con = manager.getConnection("grp", "db");
         Statement st = con.createStatement();
         ResultSet rs = st.executeQuery("SELECT 1")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }
  }

  @Test
  void getConnection_returnsMariaDbServerVersion() throws Exception {
    try (Connection con = manager.getConnection("grp", "db")) {
      String version = con.getMetaData().getDatabaseProductVersion();
      assertThat(version).isNotBlank();
    }
  }

  // ── Group / DB lookups ─────────────────────────────────────────────────────

  @Test
  void getGroupNames_returnsConfiguredGroup() {
    assertThat(manager.getGroupNames()).containsExactly("grp");
  }

  @Test
  void getDbNames_returnsConfiguredDb() throws ConnectionDetailNotFound {
    assertThat(manager.getDbNames("grp")).containsExactly("db");
  }

  @Test
  void getDbNames_unknownGroup_throws() {
    assertThatThrownBy(() -> manager.getDbNames("ghost"))
        .isInstanceOf(ConnectionDetailNotFound.class)
        .hasMessageContaining("ghost");
  }

  // ── Pool lifecycle: add ────────────────────────────────────────────────────

  @Test
  void reloadConnections_addNewDb_newConnectionIsReachable() throws Exception {
    ConnectionDTO original = mariaDbDto("grp", "db", 1L);
    ConnectionDTO added    = mariaDbDto("grp", "db2", 2L);

    manager.reloadConnections(new ConnectionConfig(List.of(original, added)));

    assertThat(manager.getDbNames("grp")).contains("db", "db2");
    try (Connection con = manager.getConnection("grp", "db2");
         Statement st = con.createStatement();
         ResultSet rs = st.executeQuery("SELECT 42")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(42);
    }
  }

  // ── Pool lifecycle: remove ─────────────────────────────────────────────────

  @Test
  void reloadConnections_removeDb_removedDbIsUnreachable() throws Exception {
    ConnectionDTO original = mariaDbDto("grp", "db", 1L);
    ConnectionDTO extra    = mariaDbDto("grp", "db2", 2L);
    manager.reloadConnections(new ConnectionConfig(List.of(original, extra)));
    assertThat(manager.getDbNames("grp")).contains("db2");

    manager.reloadConnections(new ConnectionConfig(List.of(original)));

    assertThat(manager.getDbNames("grp")).doesNotContain("db2");
    assertThatThrownBy(() -> manager.getConnection("grp", "db2"))
        .isInstanceOf(ConnectionDetailNotFound.class);
  }

  // ── Pool lifecycle: credential/URL change ──────────────────────────────────

  @Test
  void reloadConnections_urlChanged_newPoolServesConnection() throws Exception {
    // Point "db" at a different database (still valid on same MariaDB server)
    String altUrl = MARIADB.getJdbcUrl().replace("/testdb", "/testdb?connectTimeout=5000");
    ConnectionDTO updated = new ConnectionDTO(
        "org.mariadb.jdbc.Driver", altUrl,
        MARIADB.getUsername(), MARIADB.getPassword(), 1L, "grp", "db");
    updated.setMaxPoolSize(3);

    manager.reloadConnections(new ConnectionConfig(List.of(updated)));

    try (Connection con = manager.getConnection("grp", "db");
         Statement st = con.createStatement();
         ResultSet rs = st.executeQuery("SELECT 99")) {
      assertThat(rs.next()).isTrue();
    }
  }

  // ── Pool lifecycle: flags-only ─────────────────────────────────────────────

  @Test
  void reloadConnections_flagsOnly_updatesWithoutPoolRecreation() throws Exception {
    ConnectionDTO updated = mariaDbDto("grp", "db", 1L);
    updated.setUpdatable(true);
    updated.setDeletable(true);

    manager.reloadConnections(new ConnectionConfig(List.of(updated)));

    assertThat(manager.isUpdatableConnection("grp", "db")).isTrue();
    assertThat(manager.isDeletableConnection("grp", "db")).isTrue();
    try (Connection con = manager.getConnection("grp", "db")) {
      assertThat(con).isNotNull();
    }
  }

  // ── Pool lifecycle: remove entire group ────────────────────────────────────

  @Test
  void reloadConnections_removeEntireGroup_oldGroupGone() throws Exception {
    ConnectionDTO newGroup = mariaDbDto("grp2", "db", 2L);
    manager.reloadConnections(new ConnectionConfig(List.of(newGroup)));

    assertThat(manager.getGroupNames()).doesNotContain("grp");
    assertThat(manager.getGroupNames()).contains("grp2");
  }

  // ── closeAllConnections ────────────────────────────────────────────────────

  @Test
  void closeAllConnections_preventsSubsequentGetConnection() {
    manager.closeAllConnections();
    assertThatThrownBy(() -> manager.getConnection("grp", "db"))
        .isInstanceOf(Exception.class);
  }

  // ── Defaults ──────────────────────────────────────────────────────────────

  @Test
  void defaults_notUpdatableNotDeletable() throws ConnectionDetailNotFound {
    assertThat(manager.isUpdatableConnection("grp", "db")).isFalse();
    assertThat(manager.isDeletableConnection("grp", "db")).isFalse();
  }
}
