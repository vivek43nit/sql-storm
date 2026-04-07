package com.vivek.sqlstorm.config.connection.parsers;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConfigJsonParserTest {

  private DatabaseConfigJsonParser parser;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    parser = new DatabaseConfigJsonParser();
  }

  @Test
  void getApplicationName_returnsFkblitz() {
    assertThat(parser.getApplicationName()).isEqualTo("fkblitz");
  }

  @Test
  void getSupportedExtension_returnsJson() {
    assertThat(parser.getSupportedExtension()).isEqualTo("json");
  }

  @Test
  void parse_withValidJson_returnsConnectionConfig() throws IOException {
    String json = "{\"connections\":[{" +
        "\"ID\":1,\"DRIVER_CLASS_NAME\":\"org.h2.Driver\"," +
        "\"DATABASE_URL\":\"jdbc:h2:mem:test\"," +
        "\"USER_NAME\":\"sa\",\"PASSWORD\":\"\"," +
        "\"GROUP\":\"test\",\"DB_NAME\":\"testdb\"}]}";
    File f = writeJson(json);
    ConnectionConfig result = parser.parse(f);
    assertThat(result).isNotNull();
    assertThat(result.getConnections()).hasSize(1);
    assertThat(result.getConnections().get(0).getGroup()).isEqualTo("test");
    assertThat(result.getConnections().get(0).getDbName()).isEqualTo("testdb");
  }

  @Test
  void parse_withOptionalFields_usesDefaults() throws IOException {
    String json = "{\"connections\":[{" +
        "\"ID\":1,\"DRIVER_CLASS_NAME\":\"org.h2.Driver\"," +
        "\"DATABASE_URL\":\"jdbc:h2:mem:test\"," +
        "\"USER_NAME\":\"sa\",\"PASSWORD\":\"\"," +
        "\"GROUP\":\"test\",\"DB_NAME\":\"testdb\"}]," +
        "\"connection_expiry_time\":7200000,\"max_retry_count\":3}";
    File f = writeJson(json);
    ConnectionConfig result = parser.parse(f);
    assertThat(result).isNotNull();
    assertThat(result.getConnectionExpiryTime()).isEqualTo(7200000L);
    assertThat(result.getMaxRetryCount()).isEqualTo(3);
  }

  @Test
  void parse_withUpdatableAndDeletable_setsFlags() throws IOException {
    String json = "{\"connections\":[{" +
        "\"ID\":1,\"DRIVER_CLASS_NAME\":\"org.h2.Driver\"," +
        "\"DATABASE_URL\":\"jdbc:h2:mem:test\"," +
        "\"USER_NAME\":\"sa\",\"PASSWORD\":\"\"," +
        "\"GROUP\":\"test\",\"DB_NAME\":\"testdb\"," +
        "\"UPDATABLE\":true,\"DELETABLE\":true}]}";
    File f = writeJson(json);
    ConnectionConfig result = parser.parse(f);
    assertThat(result.getConnections().get(0).isUpdatable()).isTrue();
    assertThat(result.getConnections().get(0).isDeletable()).isTrue();
  }

  @Test
  void parse_withInvalidFile_returnsNull() {
    File nonExistent = new File(tempDir.toFile(), "missing.json");
    ConnectionConfig result = parser.parse(nonExistent);
    assertThat(result).isNull();
  }

  private File writeJson(String content) throws IOException {
    Path p = tempDir.resolve("conn_" + System.nanoTime() + ".json");
    Files.writeString(p, content);
    return p.toFile();
  }
}
