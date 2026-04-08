package com.vivek.sqlstorm.integration;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/**
 * Base class for Testcontainers-based integration tests that require a real MariaDB instance.
 *
 * <p>The container is shared across all subclasses (static) and reused between test classes
 * via {@code withReuse(true)} to avoid repeated Docker image pulls in CI.</p>
 *
 * <p>All subclasses must be annotated with {@code @Tag("integration")} so they are excluded
 * from the default fast test cycle and only run under the {@code integration-tests} Maven
 * profile or explicitly via {@code -Dgroups=integration}.</p>
 */
@Testcontainers
public abstract class AbstractMariaDbContainerTest {

  @Container
  protected static final MariaDBContainer<?> MARIADB =
      new MariaDBContainer<>("mariadb:11")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass")
          .withReuse(true);

  /**
   * Builds a {@link ConnectionDTO} pointing at the shared MariaDB container.
   *
   * @param group logical group name (e.g. "grp")
   * @param db    logical database name (e.g. "db")
   * @param id    unique numeric id for the connection
   */
  protected static ConnectionDTO mariaDbDto(String group, String db, long id) {
    ConnectionDTO dto = new ConnectionDTO(
        "org.mariadb.jdbc.Driver",
        MARIADB.getJdbcUrl(),
        MARIADB.getUsername(),
        MARIADB.getPassword(),
        id,
        group,
        db
    );
    dto.setMaxPoolSize(5);
    return dto;
  }

  /** Convenience factory: single-connection {@link DatabaseConnectionManager}. */
  protected static DatabaseConnectionManager singleConnectionManager(String group, String db) {
    ConnectionDTO dto = mariaDbDto(group, db, 1L);
    ConfigLoaderStrategy<ConnectionConfig> loader = () -> new ConnectionConfig(List.of(dto));
    return new DatabaseConnectionManager(loader, 5, null);
  }
}
