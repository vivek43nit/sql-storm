package com.vivek.sqlstorm.config.loader;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.customrelation.parsers.CustomRelationConfigJsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DbConfigLoader} using an in-process H2 database.
 */
class DbConfigLoaderTest {

  private static final String JDBC_URL =
      "jdbc:h2:mem:dbconfigloader_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
  private static final String USER = "sa";
  private static final String PASS = "";
  private static final String TABLE = "app_config";
  private static final String COLUMN = "config_json";

  private static final String VALID_JSON = "{\"databases\":{}}";
  private static final String UPDATED_JSON = "{\"databases\":{\"mydb\":{}}}";

  @BeforeAll
  static void setUp() throws Exception {
    Class.forName("org.h2.Driver");
    try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
         Statement st = conn.createStatement()) {
      st.execute("CREATE TABLE IF NOT EXISTS " + TABLE +
          " (" + COLUMN + " VARCHAR(4000) NOT NULL)");
      st.execute("DELETE FROM " + TABLE);
      st.execute("INSERT INTO " + TABLE + " VALUES ('" + VALID_JSON + "')");
    }
  }

  private DbConfigLoader<CustomRelationConfig> buildLoader(String json) {
    // Update DB row before building the loader
    try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
         Statement st = conn.createStatement()) {
      st.execute("DELETE FROM " + TABLE);
      st.execute("INSERT INTO " + TABLE + " VALUES ('" + json + "')");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new DbConfigLoader<>(JDBC_URL, USER, PASS, TABLE, COLUMN, "json",
        new CustomRelationConfigJsonParser());
  }

  @Test
  void load_fromH2_returnsConfig() throws Exception {
    DbConfigLoader<CustomRelationConfig> loader = buildLoader(VALID_JSON);
    CustomRelationConfig config = loader.load();
    assertThat(config).isNotNull();
  }

  @Test
  void setChangeListener_andRefresh_whenContentUnchanged_doesNotFireListener()
      throws Exception {
    DbConfigLoader<CustomRelationConfig> loader = buildLoader(VALID_JSON);
    loader.load(); // initialise hash

    AtomicReference<CustomRelationConfig> fired = new AtomicReference<>();
    loader.setChangeListener(fired::set);

    loader.refresh(); // same content → no change
    assertThat(fired.get()).isNull();
  }

  @Test
  void refresh_whenContentChanges_firesListener() throws Exception {
    DbConfigLoader<CustomRelationConfig> loader = buildLoader(VALID_JSON);
    loader.load(); // initialise hash

    AtomicReference<CustomRelationConfig> fired = new AtomicReference<>();
    loader.setChangeListener(fired::set);

    // Update DB to new content
    try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
         Statement st = conn.createStatement()) {
      st.execute("DELETE FROM " + TABLE);
      st.execute("INSERT INTO " + TABLE + " VALUES ('" + UPDATED_JSON + "')");
    }

    loader.refresh();
    assertThat(fired.get()).isNotNull();
  }

  @Test
  void load_whenTableEmpty_throwsConfigLoadException() throws Exception {
    try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
         Statement st = conn.createStatement()) {
      st.execute("DELETE FROM " + TABLE);
    }
    DbConfigLoader<CustomRelationConfig> loader =
        new DbConfigLoader<>(JDBC_URL, USER, PASS, TABLE, COLUMN, "json",
            new CustomRelationConfigJsonParser());
    assertThatThrownBy(loader::load).isInstanceOf(ConfigLoadException.class);
  }

  @Test
  void refresh_whenDbUnreachable_doesNotThrow() {
    // Loader with a bad JDBC URL — refresh must swallow the error
    DbConfigLoader<CustomRelationConfig> loader =
        new DbConfigLoader<>("jdbc:h2:mem:nonexistent_xxx", USER, PASS, TABLE, COLUMN, "json",
            new CustomRelationConfigJsonParser());
    // load() would fail; refresh() should be fail-open
    loader.refresh(); // must not throw
  }
}
