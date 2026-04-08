package com.vivek.sqlstorm.config.loader;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests RelationRowDbLoader against a real H2 in-memory relation_mapping table.
 */
class RelationRowDbLoaderTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:relation_loader_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String USER = "sa";
    private static final String PASS = "";
    private static final String TABLE = "relation_mapping";

    private RelationRowDbLoader loader;

    @BeforeAll
    static void createTable() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS relation_mapping (
                    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                    database_name     VARCHAR(128) NOT NULL,
                    table_name        VARCHAR(128) NOT NULL,
                    column_name       VARCHAR(128) NOT NULL,
                    ref_database_name VARCHAR(128) NOT NULL,
                    ref_table_name    VARCHAR(128) NOT NULL,
                    ref_column_name   VARCHAR(128) NOT NULL,
                    conditions_json   TEXT NULL,
                    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
                    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        }
    }

    @AfterAll
    static void dropTable() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + TABLE);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Reset table before each test
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement st = conn.createStatement()) {
            st.execute("DELETE FROM " + TABLE);
        }
        loader = new RelationRowDbLoader(JDBC_URL, USER, PASS, TABLE);
    }

    private void insert(String db, String tbl, String col, String refDb, String refTbl, String refCol,
                        String condJson, boolean active) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement st = conn.createStatement()) {
            String cond = condJson == null ? "NULL" : "'" + condJson + "'";
            st.execute(String.format(
                    "INSERT INTO %s (database_name,table_name,column_name," +
                    "ref_database_name,ref_table_name,ref_column_name,conditions_json,is_active) " +
                    "VALUES ('%s','%s','%s','%s','%s','%s',%s,%d)",
                    TABLE, db, tbl, col, refDb, refTbl, refCol, cond, active ? 1 : 0));
        }
    }

    private void bumpUpdatedAt() throws Exception {
        // H2 doesn't auto-bump updated_at; do it manually to simulate a change
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement st = conn.createStatement()) {
            st.execute("UPDATE " + TABLE + " SET updated_at = TIMESTAMPADD(SECOND, 1, updated_at)");
        }
    }

    @Test
    void load_assemblesRelationsGroupedByDatabase() throws Exception {
        insert("db1", "orders",   "user_id",    "db1", "users",    "id",   null, true);
        insert("db1", "payments", "order_id",   "db1", "orders",   "id",   null, true);
        insert("db2", "products", "category_id","db2", "category", "id",   null, true);

        CustomRelationConfig config = loader.load();

        assertThat(config.getDatabases()).containsKeys("db1", "db2");
        assertThat(config.getDatabases().get("db1").getRelations()).hasSize(2);
        assertThat(config.getDatabases().get("db2").getRelations()).hasSize(1);
    }

    @Test
    void load_setsSourceToCustom() throws Exception {
        insert("db1", "orders", "user_id", "db1", "users", "id", null, true);

        CustomRelationConfig config = loader.load();
        List<ReferenceDTO> refs = config.getDatabases().get("db1").getRelations();
        assertThat(refs).allMatch(r -> r.getSource() == ReferenceDTO.Source.CUSTOM);
    }

    @Test
    void load_parsesConditionsJson() throws Exception {
        insert("db1", "orders", "user_id", "db1", "users", "id", "{\"type\":\"inner\"}", true);

        CustomRelationConfig config = loader.load();
        ReferenceDTO ref = config.getDatabases().get("db1").getRelations().get(0);
        assertThat(ref.getConditions()).isNotNull();
        assertThat(ref.getConditions().getString("type")).isEqualTo("inner");
    }

    @Test
    void refresh_noChange_doesNotCallListener() throws Exception {
        insert("db1", "orders", "user_id", "db1", "users", "id", null, true);
        loader.load();

        AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
        loader.setChangeListener(received::set);
        loader.refresh(); // nothing changed

        assertThat(received.get()).isNull();
    }

    @Test
    void refresh_afterInsert_callsListenerWithNewConfig() throws Exception {
        insert("db1", "orders", "user_id", "db1", "users", "id", null, true);
        loader.load();

        // Insert a new row and bump updated_at so MAX changes
        insert("db1", "payments", "order_id", "db1", "orders", "id", null, true);
        bumpUpdatedAt();

        AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
        loader.setChangeListener(received::set);
        loader.refresh();

        assertThat(received.get()).isNotNull();
        assertThat(received.get().getDatabases().get("db1").getRelations()).hasSize(2);
    }

    @Test
    void refresh_afterSoftDelete_excludesInactiveRow() throws Exception {
        insert("db1", "orders",   "user_id",  "db1", "users",  "id", null, true);
        insert("db1", "payments", "order_id", "db1", "orders", "id", null, true);
        loader.load();

        // Soft-delete one row
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement st = conn.createStatement()) {
            st.execute("UPDATE " + TABLE + " SET is_active = 0 WHERE table_name = 'payments'");
        }
        bumpUpdatedAt();

        AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
        loader.setChangeListener(received::set);
        loader.refresh();

        assertThat(received.get()).isNotNull();
        assertThat(received.get().getDatabases().get("db1").getRelations()).hasSize(1);
    }

    @Test
    void load_emptyTable_returnsEmptyConfig() throws Exception {
        CustomRelationConfig config = loader.load();
        assertThat(config.getDatabases()).isEmpty();
    }

    @Test
    void refresh_crossDatabaseRelation_preservesRefDatabase() throws Exception {
        insert("db1", "orders", "user_id", "db2", "users", "id", null, true);
        CustomRelationConfig config = loader.load();

        ReferenceDTO ref = config.getDatabases().get("db1").getRelations().get(0);
        assertThat(ref.getReferenceDatabaseName()).isEqualTo("db2");
        assertThat(ref.getReferenceTableName()).isEqualTo("users");
    }
}
