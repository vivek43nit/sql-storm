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

class DatabaseConfigXmlParserTest {

  private DatabaseConfigXmlParser parser;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    parser = new DatabaseConfigXmlParser();
  }

  @Test
  void getApplicationName_returnsFkblitz() {
    assertThat(parser.getApplicationName()).isEqualTo("fkblitz");
  }

  @Test
  void getSupportedExtension_returnsXml() {
    assertThat(parser.getSupportedExtension()).isEqualTo("xml");
  }

  @Test
  void parse_withValidXml_returnsConnectionConfig() throws IOException {
    File f = writeXml("<CONNECTIONS>" +
        "<CONNECTION ID=\"1\" DRIVER_CLASS_NAME=\"org.h2.Driver\" " +
        "DATABASE_URL=\"jdbc:h2:mem:test\" USER_NAME=\"sa\" PASSWORD=\"\" " +
        "GROUP=\"mygroup\" DB_NAME=\"mydb\"/>" +
        "</CONNECTIONS>");
    ConnectionConfig result = parser.parse(f);
    assertThat(result).isNotNull();
    assertThat(result.getConnections()).hasSize(1);
    assertThat(result.getConnections().get(0).getGroup()).isEqualTo("mygroup");
    assertThat(result.getConnections().get(0).getDbName()).isEqualTo("mydb");
  }

  @Test
  void parse_withUpdatableAndDeletable_setsFlags() throws IOException {
    File f = writeXml("<CONNECTIONS>" +
        "<CONNECTION ID=\"1\" DRIVER_CLASS_NAME=\"org.h2.Driver\" " +
        "DATABASE_URL=\"jdbc:h2:mem:test\" USER_NAME=\"sa\" PASSWORD=\"\" " +
        "GROUP=\"g\" DB_NAME=\"d\" UPDATABLE=\"true\" DELETABLE=\"true\"/>" +
        "</CONNECTIONS>");
    ConnectionConfig result = parser.parse(f);
    assertThat(result.getConnections().get(0).isUpdatable()).isTrue();
    assertThat(result.getConnections().get(0).isDeletable()).isTrue();
  }

  @Test
  void parse_withConnectionExpiryAndRetryCount_setsValues() throws IOException {
    File f = writeXml("<CONNECTIONS CONNECTION_EXPIRY_TIME=\"7200000\" MAX_RETRY_COUNT=\"3\">" +
        "</CONNECTIONS>");
    ConnectionConfig result = parser.parse(f);
    assertThat(result).isNotNull();
    assertThat(result.getConnectionExpiryTime()).isEqualTo(7200000L);
    assertThat(result.getMaxRetryCount()).isEqualTo(3);
  }

  @Test
  void parse_withSearchableRowLimit_setsLimit() throws IOException {
    File f = writeXml("<CONNECTIONS>" +
        "<CONNECTION ID=\"1\" DRIVER_CLASS_NAME=\"org.h2.Driver\" " +
        "DATABASE_URL=\"jdbc:h2:mem:test\" USER_NAME=\"sa\" PASSWORD=\"\" " +
        "GROUP=\"g\" DB_NAME=\"d\" NON_INDEXED_SEARCHABLE_ROW_LIMIT=\"500\"/>" +
        "</CONNECTIONS>");
    ConnectionConfig result = parser.parse(f);
    assertThat(result.getConnections().get(0).getSearchableRowLimit()).isEqualTo(500);
  }

  @Test
  void parse_withEmptyConnections_returnsEmptyList() throws IOException {
    File f = writeXml("<CONNECTIONS CONNECTION_EXPIRY_TIME=\"3600000\" MAX_RETRY_COUNT=\"1\">" +
        "</CONNECTIONS>");
    ConnectionConfig result = parser.parse(f);
    assertThat(result).isNotNull();
    assertThat(result.getConnections()).isEmpty();
  }

  @Test
  void parse_withInvalidFile_returnsNull() {
    File nonExistent = new File(tempDir.toFile(), "missing.xml");
    ConnectionConfig result = parser.parse(nonExistent);
    assertThat(result).isNull();
  }

  private File writeXml(String content) throws IOException {
    Path p = tempDir.resolve("conn_" + System.nanoTime() + ".xml");
    Files.writeString(p, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + content);
    return p.toFile();
  }
}
