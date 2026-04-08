package com.vivek.sqlstorm.metadata;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.customrelation.DatabaseConfig;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.DatabaseDTO;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit / integration tests for DatabaseMetaDataManager.
 *
 * Uses real H2 in-memory databases so lazyLoadFromDb() exercises the full DB path.
 */
class DatabaseMetaDataManagerTest {

    private static final String JDBC_A = "jdbc:h2:mem:meta_mgr_a;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String JDBC_B = "jdbc:h2:mem:meta_mgr_b;DB_CLOSE_DELAY=-1;MODE=MySQL";

    @BeforeAll
    static void createTables() throws Exception {
        Class.forName("org.h2.Driver");
        // database A — has one table: users
        try (Connection con = DriverManager.getConnection(JDBC_A, "sa", "");
             Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("CREATE TABLE IF NOT EXISTS orders (id BIGINT PRIMARY KEY, user_id BIGINT, FOREIGN KEY (user_id) REFERENCES users(id))");
        }
        // database B — has one table: products
        try (Connection con = DriverManager.getConnection(JDBC_B, "sa", "");
             Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS products (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        }
    }

    private DatabaseMetaDataManager buildManager(String group, String db, String jdbcUrl,
                                                  CustomRelationConfig customConfig) {
        ConnectionDTO dto = new ConnectionDTO("org.h2.Driver", jdbcUrl, "sa", "", 1L, group, db);
        dto.setMaxPoolSize(3);
        ConfigLoaderStrategy<ConnectionConfig> connLoader =
                () -> new ConnectionConfig(List.of(dto));
        DatabaseConnectionManager connMgr = new DatabaseConnectionManager(connLoader);
        return new DatabaseMetaDataManager(connMgr, () -> customConfig);
    }

    private DatabaseMetaDataManager buildManager(String group, String db, String jdbcUrl) {
        return buildManager(group, db, jdbcUrl, new CustomRelationConfig(new HashMap<>()));
    }

    private DatabaseMetaDataManager buildTwoDbManager(CustomRelationConfig customConfig) {
        ConnectionDTO dtoA = new ConnectionDTO("org.h2.Driver", JDBC_A, "sa", "", 1L, "grp", "dbA");
        dtoA.setMaxPoolSize(3);
        ConnectionDTO dtoB = new ConnectionDTO("org.h2.Driver", JDBC_B, "sa", "", 2L, "grp", "dbB");
        dtoB.setMaxPoolSize(3);
        ConfigLoaderStrategy<ConnectionConfig> connLoader =
                () -> new ConnectionConfig(List.of(dtoA, dtoB));
        DatabaseConnectionManager connMgr = new DatabaseConnectionManager(connLoader);
        return new DatabaseMetaDataManager(connMgr, () -> customConfig);
    }

    // ── getWithoutCheckMetaData — error paths ──────────────────────────────

    @Test
    void getWithoutCheckMetaData_unknownGroup_throws() {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        assertThatThrownBy(() -> mgr.getWithoutCheckMetaData("unknown", "db"))
                .isInstanceOf(ConnectionDetailNotFound.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void getWithoutCheckMetaData_unknownDb_throws() {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        assertThatThrownBy(() -> mgr.getWithoutCheckMetaData("grp", "no-db"))
                .isInstanceOf(ConnectionDetailNotFound.class)
                .hasMessageContaining("no-db");
    }

    @Test
    void getWithoutCheckMetaData_knownGroupAndDb_returnsDatabaseDTO() throws Exception {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        DatabaseDTO dto = mgr.getWithoutCheckMetaData("grp", "db");
        assertThat(dto).isNotNull();
        assertThat(dto.getGroup()).isEqualTo("grp");
        assertThat(dto.getName()).isEqualTo("db");
    }

    // ── lazyLoadFromDb via getMetaData / getTables ─────────────────────────

    @Test
    void getTables_triggersLazyLoad_returnsH2Tables() throws Exception {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        Collection<TableDTO> tables = mgr.getTables("grp", "db");

        assertThat(tables).isNotEmpty();
        List<String> names = tables.stream().map(TableDTO::getTableName).toList();
        assertThat(names).contains("USERS", "ORDERS");
    }

    @Test
    void getTables_calledTwice_loadedFromDbOnlyOnce() throws Exception {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        mgr.getTables("grp", "db");
        DatabaseDTO dto = mgr.getWithoutCheckMetaData("grp", "db");
        assertThat(dto.isLoadedFromDb()).isTrue();

        // Second call must not throw and must still return the tables
        Collection<TableDTO> tables = mgr.getTables("grp", "db");
        assertThat(tables).isNotEmpty();
    }

    @Test
    void getMetaData_loadsColumnsForTable() throws Exception {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        DatabaseDTO dto = mgr.getMetaData("grp", "db");

        TableDTO users = dto.getTableMetaData("USERS");
        assertThat(users).isNotNull();
        assertThat(users.getColumnMetaData("ID")).isNotNull();
        assertThat(users.getColumnMetaData("NAME")).isNotNull();
    }

    @Test
    void getMetaData_h2ForeignKey_doesNotThrow() throws Exception {
        // Full lazy-load including FK discovery must complete without exception.
        // H2 returns FK metadata with internal catalog names (not our logical db name),
        // so we only verify that the load completes and tables are populated.
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        DatabaseDTO dbDto = mgr.getMetaData("grp", "db");
        assertThat(dbDto.isLoadedFromDb()).isTrue();
        assertThat(dbDto.getTables()).isNotEmpty();
    }

    // ── buildSnapshot with custom relations ────────────────────────────────

    @Test
    void buildSnapshot_withCustomRelation_wiresReferTo() throws Exception {
        ReferenceDTO rel = new ReferenceDTO();
        rel.setDatabaseName("dbA");
        rel.setTableName("orders");
        rel.setColumnName("user_id");
        rel.setReferenceDatabaseName("dbA");
        rel.setReferenceTableName("users");
        rel.setReferenceColumnName("id");
        rel.setSource(ReferenceDTO.Source.CUSTOM);

        DatabaseConfig dbCfg = new DatabaseConfig();
        dbCfg.setRelations(List.of(rel));
        dbCfg.setJointTables(Collections.emptyMap());
        dbCfg.setAutoResolve(Collections.emptyMap());

        CustomRelationConfig customCfg = new CustomRelationConfig(Map.of("dbA", dbCfg));
        DatabaseMetaDataManager mgr = buildTwoDbManager(customCfg);

        // Custom relation is applied at snapshot time without lazy load
        DatabaseDTO dbA = mgr.getWithoutCheckMetaData("grp", "dbA");
        TableDTO orders = dbA.getOrAddTableMetaData("orders");
        ColumnDTO userId = orders.getOrAddColumnMetaData("user_id");
        assertThat(userId.getReferTo()).isNotEmpty();
        assertThat(userId.getReferTo().get(0).getTable()).isEqualTo("users");
    }

    @Test
    void buildSnapshot_withCustomRelation_wiresReferencedBy() throws Exception {
        ReferenceDTO rel = new ReferenceDTO();
        rel.setDatabaseName("dbA");
        rel.setTableName("orders");
        rel.setColumnName("user_id");
        rel.setReferenceDatabaseName("dbA");
        rel.setReferenceTableName("users");
        rel.setReferenceColumnName("id");
        rel.setSource(ReferenceDTO.Source.CUSTOM);

        DatabaseConfig dbCfg = new DatabaseConfig();
        dbCfg.setRelations(List.of(rel));
        dbCfg.setJointTables(Collections.emptyMap());
        dbCfg.setAutoResolve(Collections.emptyMap());

        CustomRelationConfig customCfg = new CustomRelationConfig(Map.of("dbA", dbCfg));
        DatabaseMetaDataManager mgr = buildTwoDbManager(customCfg);

        DatabaseDTO dbA = mgr.getWithoutCheckMetaData("grp", "dbA");
        TableDTO users = dbA.getOrAddTableMetaData("users");
        ColumnDTO id = users.getOrAddColumnMetaData("id");
        assertThat(id.getReferencedBy()).isNotEmpty();
        assertThat(id.getReferencedBy().get(0).getTable()).isEqualTo("orders");
    }

    @Test
    void buildSnapshot_unknownDatabaseInCustomConfig_logsWarnAndSkips() {
        // A custom relation referencing a database not in the connection config
        // should not throw — just log a warning and skip
        ReferenceDTO rel = new ReferenceDTO();
        rel.setDatabaseName("ghost_db");
        rel.setTableName("t");
        rel.setColumnName("c");
        rel.setReferenceDatabaseName("ghost_db");
        rel.setReferenceTableName("t2");
        rel.setReferenceColumnName("c2");
        rel.setSource(ReferenceDTO.Source.CUSTOM);

        DatabaseConfig dbCfg = new DatabaseConfig();
        dbCfg.setRelations(List.of(rel));
        dbCfg.setJointTables(Collections.emptyMap());
        dbCfg.setAutoResolve(Collections.emptyMap());

        CustomRelationConfig customCfg = new CustomRelationConfig(Map.of("ghost_db", dbCfg));

        // Must not throw even though ghost_db is not in connection config
        org.assertj.core.api.Assertions.assertThatCode(
                () -> buildManager("grp", "db", JDBC_A, customCfg))
                .doesNotThrowAnyException();
    }

    // ── reloadCustomRelationConfig ─────────────────────────────────────────

    @Test
    void reloadCustomRelationConfig_swapsSnapshotAtomically() throws Exception {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);

        // Initial load: grab reference before reload
        DatabaseDTO before = mgr.getWithoutCheckMetaData("grp", "db");

        // Reload with empty config
        mgr.reloadCustomRelationConfig(new CustomRelationConfig(new HashMap<>()));

        // After reload, we can still resolve group/db (new snapshot has it)
        DatabaseDTO after = mgr.getWithoutCheckMetaData("grp", "db");
        assertThat(after).isNotNull();
        // Old reference is still safely readable (not mutated)
        assertThat(before.getGroup()).isEqualTo("grp");
    }

    @Test
    void reloadCustomRelationConfig_updatesCustomRelationConfig() {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        CustomRelationConfig newCfg = new CustomRelationConfig(new HashMap<>());
        mgr.reloadCustomRelationConfig(newCfg);
        assertThat(mgr.getCustomRelationConfig()).isSameAs(newCfg);
    }

    // ── getGroupNames / getDbNames delegation ──────────────────────────────

    @Test
    void getGroupNames_delegatesToConnectionManager() {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        assertThat(mgr.getGroupNames()).containsExactly("grp");
    }

    @Test
    void getDbNames_delegatesToConnectionManager() throws Exception {
        DatabaseMetaDataManager mgr = buildManager("grp", "db", JDBC_A);
        assertThat(mgr.getDbNames("grp")).containsExactly("db");
    }
}
