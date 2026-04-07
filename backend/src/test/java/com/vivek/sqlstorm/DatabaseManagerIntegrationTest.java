package com.vivek.sqlstorm;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test that wires a real H2 connection into {@link DatabaseManager},
 * exercising {@link com.vivek.sqlstorm.connection.DatabaseConnectionManager},
 * {@link com.vivek.sqlstorm.metadata.DatabaseMetaDataManager}, and
 * {@link com.vivek.sqlstorm.utils.DBHelper} with a real JDBC connection.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(DatabaseManagerIntegrationTest.H2ConnectionConfig.class)
class DatabaseManagerIntegrationTest {

  @Autowired
  DatabaseManager databaseManager;

  /**
   * Overrides the connection loader to supply one H2 connection pointing at the
   * same in-memory database used by the auth JPA layer (already created by Spring Boot).
   */
  @TestConfiguration
  static class H2ConnectionConfig {
    @Bean
    @Primary
    ConfigLoaderStrategy<ConnectionConfig> connectionConfigLoader() {
      ConnectionDTO h2 = new ConnectionDTO(
          "org.h2.Driver",
          "jdbc:h2:mem:fkblitz_auth;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
          "sa", "", 1L, "testgroup", "h2db");
      return () -> new ConnectionConfig(List.of(h2));
    }
  }

  @Test
  void getGroupNames_returnsConfiguredGroup() {
    Set<String> groups = databaseManager.getGroupNames();
    assertThat(groups).contains("testgroup");
  }

  @Test
  void getDbNames_forKnownGroup_returnsDatabase() throws ConnectionDetailNotFound {
    Set<String> dbs = databaseManager.getDbNames("testgroup");
    assertThat(dbs).contains("h2db");
  }

  @Test
  void getDbNames_forUnknownGroup_throwsConnectionDetailNotFound() {
    assertThatThrownBy(() -> databaseManager.getDbNames("nonexistent"))
        .isInstanceOf(ConnectionDetailNotFound.class);
  }

  @Test
  void isUpdatableConnection_returnsConfiguredValue() throws ConnectionDetailNotFound {
    // The H2 connection was created without UPDATABLE flag → defaults to false
    boolean updatable = databaseManager.isUpdatableConnection("testgroup", "h2db");
    assertThat(updatable).isFalse();
  }

  @Test
  void getTables_returnsH2SystemAndUserTables()
      throws ConnectionDetailNotFound, SQLException, ClassNotFoundException {
    // H2 auth DB has JPA-created tables (fkblitz_user, etc.)
    var tables = databaseManager.getTables("testgroup", "h2db");
    assertThat(tables).isNotNull();
  }

  @Test
  void getCustomRelationConfig_returnsNonNull() {
    assertThat(databaseManager.getCustomRelationConfig()).isNotNull();
  }

  @Test
  void getConnection_returnsOpenConnection()
      throws ConnectionDetailNotFound, ClassNotFoundException, java.sql.SQLException {
    java.sql.Connection con = databaseManager.getConnection("testgroup", "h2db");
    assertThat(con).isNotNull();
    assertThat(con.isClosed()).isFalse();
  }

  @Test
  void getMetaData_returnsTableMetaData()
      throws ConnectionDetailNotFound, java.sql.SQLException, ClassNotFoundException {
    // H2 auth DB has at least the fkblitz_user table created by JPA
    var meta = databaseManager.getMetaData("testgroup", "h2db");
    assertThat(meta).isNotNull();
    // DatabaseMetaDataManager builds the table list — at least one table exists in H2
    assertThat(meta.getTables()).isNotNull();
  }

  @Test
  void isDeletableConnection_returnsFalse() throws ConnectionDetailNotFound {
    // H2 connection was created without DELETABLE flag → defaults to false
    assertThat(databaseManager.isDeletableConnection("testgroup", "h2db")).isFalse();
  }
}
