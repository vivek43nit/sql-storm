package com.vivek.sqlstorm.connection;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DatabaseConnectionManager using H2 in-memory databases.
 */
class DatabaseConnectionManagerTest {

    private static final String JDBC_A =
            "jdbc:h2:mem:conn_mgr_a;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String JDBC_B =
            "jdbc:h2:mem:conn_mgr_b;DB_CLOSE_DELAY=-1;MODE=MySQL";

    private DatabaseConnectionManager manager;

    private static ConnectionDTO dto(String group, String db, String url) {
        ConnectionDTO d = new ConnectionDTO("org.h2.Driver", url, "sa", "", 1L, group, db);
        d.setMaxPoolSize(3);
        return d;
    }

    @BeforeEach
    void setUp() {
        ConnectionDTO h2 = dto("grp", "db", JDBC_A);
        ConfigLoaderStrategy<ConnectionConfig> loader = () -> new ConnectionConfig(List.of(h2));
        manager = new DatabaseConnectionManager(loader, 5, null);
    }

    @AfterEach
    void tearDown() {
        manager.closeAllConnections();
    }

    // ── Basic connectivity ─────────────────────────────────────────────────

    @Test
    void getConnection_returnsWorkingConnection() throws Exception {
        try (Connection con = manager.getConnection("grp", "db");
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void getGroupNames_returnsConfiguredGroup() {
        Set<String> groups = manager.getGroupNames();
        assertThat(groups).containsExactly("grp");
    }

    @Test
    void getDbNames_returnsConfiguredDb() throws ConnectionDetailNotFound {
        Set<String> dbs = manager.getDbNames("grp");
        assertThat(dbs).containsExactly("db");
    }

    @Test
    void getDbNames_unknownGroup_throws() {
        assertThatThrownBy(() -> manager.getDbNames("unknown"))
                .isInstanceOf(ConnectionDetailNotFound.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void getConnection_unknownGroup_throws() {
        assertThatThrownBy(() -> manager.getConnection("no-group", "db"))
                .isInstanceOf(ConnectionDetailNotFound.class);
    }

    @Test
    void getConnection_unknownDb_throws() {
        assertThatThrownBy(() -> manager.getConnection("grp", "no-db"))
                .isInstanceOf(ConnectionDetailNotFound.class);
    }

    // ── closeAllConnections ────────────────────────────────────────────────

    @Test
    void closeAllConnections_preventsSubsequentGetConnection() {
        manager.closeAllConnections();
        assertThatThrownBy(() -> manager.getConnection("grp", "db"))
                .isInstanceOf(Exception.class); // pool closed → SQLException or HikariCP exception
    }

    // ── reloadConnections — add ────────────────────────────────────────────

    @Test
    void reloadConnections_addNewDb_makesItReachable() throws Exception {
        ConnectionDTO original = dto("grp", "db", JDBC_A);
        ConnectionDTO newDb    = dto("grp", "db2", JDBC_B);

        manager.reloadConnections(new ConnectionConfig(List.of(original, newDb)));

        assertThat(manager.getDbNames("grp")).contains("db", "db2");
        try (Connection con = manager.getConnection("grp", "db2");
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 42")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(42);
        }
    }

    // ── reloadConnections — remove ─────────────────────────────────────────

    @Test
    void reloadConnections_removeDb_makesItUnreachable() throws Exception {
        // Start with both, then reload with only the original
        ConnectionDTO original = dto("grp", "db", JDBC_A);
        ConnectionDTO extra    = dto("grp", "db2", JDBC_B);
        manager.reloadConnections(new ConnectionConfig(List.of(original, extra)));
        assertThat(manager.getDbNames("grp")).contains("db2");

        // Remove db2
        manager.reloadConnections(new ConnectionConfig(List.of(original)));
        assertThat(manager.getDbNames("grp")).doesNotContain("db2");
        assertThatThrownBy(() -> manager.getConnection("grp", "db2"))
                .isInstanceOf(ConnectionDetailNotFound.class);
    }

    // ── reloadConnections — credentials changed ────────────────────────────

    @Test
    void reloadConnections_urlChanged_newPoolCreated() throws Exception {
        ConnectionDTO updated = dto("grp", "db", JDBC_B); // same name, different URL
        manager.reloadConnections(new ConnectionConfig(List.of(updated)));

        try (Connection con = manager.getConnection("grp", "db");
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 99")) {
            assertThat(rs.next()).isTrue();
        }
    }

    // ── reloadConnections — flags only ─────────────────────────────────────

    @Test
    void reloadConnections_flagsOnly_updatesConfigWithoutPoolRecreation() throws Exception {
        ConnectionDTO updated = dto("grp", "db", JDBC_A);
        updated.setUpdatable(true);
        updated.setDeletable(true);

        manager.reloadConnections(new ConnectionConfig(List.of(updated)));

        assertThat(manager.isUpdatableConnection("grp", "db")).isTrue();
        assertThat(manager.isDeletableConnection("grp", "db")).isTrue();
        // Connection still works
        try (Connection con = manager.getConnection("grp", "db")) {
            assertThat(con).isNotNull();
        }
    }

    // ── reloadConnections — whole group removed ────────────────────────────

    @Test
    void reloadConnections_removeEntireGroup_groupNoLongerPresent() throws Exception {
        ConnectionDTO newGroup = dto("grp2", "db", JDBC_B);
        manager.reloadConnections(new ConnectionConfig(List.of(newGroup)));

        assertThat(manager.getGroupNames()).doesNotContain("grp");
        assertThat(manager.getGroupNames()).contains("grp2");
    }

    // ── isUpdatable / isDeletable defaults ────────────────────────────────

    @Test
    void defaults_notUpdatableNotDeletable() throws ConnectionDetailNotFound {
        assertThat(manager.isUpdatableConnection("grp", "db")).isFalse();
        assertThat(manager.isDeletableConnection("grp", "db")).isFalse();
    }
}
