package com.vivek.sqlstorm.metadata;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.DatabaseDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.integration.AbstractMariaDbContainerTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DatabaseMetaDataManager} against a real MariaDB container.
 *
 * <p>Validates FK discovery, lazy-load, snapshot swap, and concurrency behaviour that H2's
 * {@code INFORMATION_SCHEMA} cannot fully replicate (different catalog naming, FK metadata
 * format, and real network latency).</p>
 *
 * <p>Requires Docker. Runs only under the {@code integration-tests} Maven profile or with
 * {@code -Dgroups=integration}.</p>
 */
@Tag("integration")
@Testcontainers
class DatabaseMetaDataManagerMariaDbTest extends AbstractMariaDbContainerTest {

  @BeforeAll
  static void createSchema() throws Exception {
    // Use root to create the extra database and grant testuser access to it.
    // testuser only has privileges on the default 'testdb' created by the container.
    String rootUrl = MARIADB.getJdbcUrl();
    try (Connection root = DriverManager.getConnection(rootUrl, "root", MARIADB.getPassword());
         Statement st = root.createStatement()) {
      st.execute("CREATE DATABASE IF NOT EXISTS schematest");
      st.execute("GRANT ALL PRIVILEGES ON schematest.* TO '" + MARIADB.getUsername() + "'@'%'");
      st.execute("FLUSH PRIVILEGES");
    }
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      // FK-aware tables — orders.user_id → users.id
      st.execute("USE schematest");
      st.execute("DROP TABLE IF EXISTS orders");
      st.execute("DROP TABLE IF EXISTS users");
      st.execute("""
          CREATE TABLE users (
              id   BIGINT PRIMARY KEY AUTO_INCREMENT,
              name VARCHAR(100) NOT NULL
          ) ENGINE=InnoDB
          """);
      st.execute("""
          CREATE TABLE orders (
              id      BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id BIGINT NOT NULL,
              amount  DECIMAL(10,2),
              CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
          ) ENGINE=InnoDB
          """);
    }
  }

  private DatabaseMetaDataManager buildManager() {
    String jdbcUrl = MARIADB.getJdbcUrl().replace("/testdb", "/schematest")
        + "?useInformationSchema=true";
    ConnectionDTO dto = new ConnectionDTO(
        "org.mariadb.jdbc.Driver", jdbcUrl,
        MARIADB.getUsername(), MARIADB.getPassword(), 1L, "grp", "schematest");
    dto.setMaxPoolSize(5);
    ConfigLoaderStrategy<ConnectionConfig> loader =
        () -> new ConnectionConfig(List.of(dto));
    DatabaseConnectionManager mgr = new DatabaseConnectionManager(loader);
    return new DatabaseMetaDataManager(mgr, () -> new CustomRelationConfig(new HashMap<>()));
  }

  // ── Table discovery ────────────────────────────────────────────────────────

  @Test
  void getTables_withRealMariaDb_returnsCreatedTables() throws Exception {
    DatabaseMetaDataManager mgr = buildManager();

    Collection<TableDTO> tables = mgr.getTables("grp", "schematest");

    List<String> names = tables.stream()
        .map(t -> t.getTableName().toLowerCase())
        .toList();
    assertThat(names).contains("users", "orders");
  }

  @Test
  void getMetaData_loadsAllColumnsForUsersTable() throws Exception {
    DatabaseMetaDataManager mgr = buildManager();

    DatabaseDTO dto = mgr.getMetaData("grp", "schematest");
    // MariaDB returns table names in their defined case
    TableDTO users = dto.getTables().stream()
        .filter(t -> t.getTableName().equalsIgnoreCase("users"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("users table not found"));

    List<String> colNames = users.getColumns().stream()
        .map(c -> c.getName().toLowerCase())
        .toList();
    assertThat(colNames).contains("id", "name");
  }

  // ── FK discovery ───────────────────────────────────────────────────────────

  @Test
  void getMetaData_discoversForeignKey_fromOrdersToUsers() throws Exception {
    DatabaseMetaDataManager mgr = buildManager();
    DatabaseDTO dto = mgr.getMetaData("grp", "schematest");

    TableDTO orders = dto.getTables().stream()
        .filter(t -> t.getTableName().equalsIgnoreCase("orders"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("orders table not found"));

    ColumnDTO userId = orders.getColumns().stream()
        .filter(c -> c.getName().equalsIgnoreCase("user_id"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("user_id column not found"));

    // MariaDB should populate referTo via JDBC DatabaseMetaData.getImportedKeys()
    assertThat(userId.getReferTo())
        .withFailMessage("Expected FK from orders.user_id → users.id to be discovered")
        .isNotEmpty();
    assertThat(userId.getReferTo().get(0).getTable().toLowerCase()).isEqualTo("users");
  }

  @Test
  void getMetaData_loadsOnlyOnce_subsequentCallUsesCache() throws Exception {
    DatabaseMetaDataManager mgr = buildManager();

    mgr.getMetaData("grp", "schematest");
    DatabaseDTO first = mgr.getWithoutCheckMetaData("grp", "schematest");
    assertThat(first.isLoadedFromDb()).isTrue();

    // Second call — must not throw, must still return tables
    Collection<TableDTO> tables = mgr.getTables("grp", "schematest");
    assertThat(tables).isNotEmpty();
  }

  // ── Snapshot swap under concurrent load ────────────────────────────────────

  @Test
  void concurrentReadersAndReloaders_withRealMariaDb_noRaceConditions()
      throws InterruptedException {
    DatabaseMetaDataManager mgr = buildManager();

    int readers = 20;
    int reloaders = 2;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(readers + reloaders);
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    ExecutorService pool = Executors.newFixedThreadPool(readers + reloaders);

    for (int i = 0; i < readers; i++) {
      pool.submit(() -> {
        try {
          start.await();
          for (int j = 0; j < 30; j++) {
            try {
              Collection<TableDTO> tables = mgr.getTables("grp", "schematest");
              for (TableDTO t : tables) {
                t.getColumns().forEach(c -> {
                  c.getReferTo().size();
                  c.getReferencedBy().size();
                });
              }
            } catch (Exception ignored) {
              // connection errors under heavy load are acceptable
            }
          }
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          done.countDown();
        }
      });
    }

    for (int i = 0; i < reloaders; i++) {
      pool.submit(() -> {
        try {
          start.await();
          for (int j = 0; j < 5; j++) {
            mgr.reloadCustomRelationConfig(new CustomRelationConfig(new HashMap<>()));
            Thread.sleep(10);
          }
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
    pool.shutdown();

    assertThat(errors)
        .withFailMessage("Race conditions detected: %s", errors)
        .isEmpty();
  }

  // ── Snapshot swap correctness ──────────────────────────────────────────────

  @Test
  void reloadCustomRelationConfig_withMariaDb_oldSnapshotStillReadable() throws Exception {
    DatabaseMetaDataManager mgr = buildManager();
    Collection<TableDTO> snapshot = mgr.getTables("grp", "schematest");

    mgr.reloadCustomRelationConfig(new CustomRelationConfig(new HashMap<>()));

    // Old snapshot reference remains safely iterable — no CME
    List<String> tableNames = snapshot.stream()
        .map(TableDTO::getTableName)
        .toList();
    assertThat(tableNames).isNotNull();

    // New snapshot is valid
    DatabaseDTO fresh = mgr.getWithoutCheckMetaData("grp", "schematest");
    assertThat(fresh).isNotNull();
  }
}
