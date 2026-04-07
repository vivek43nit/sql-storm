package com.vivek.sqlstorm.config.customrelation.parsers;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRelationConfigJsonParserTest {

  private CustomRelationConfigJsonParser parser;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    parser = new CustomRelationConfigJsonParser();
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
  void parse_withNullDatabases_returnsEmptyConfig() throws IOException {
    File f = createJson("{\"other\":\"field\"}");
    CustomRelationConfig result = parser.parse(f);
    assertThat(result).isNotNull();
    assertThat(result.getDatabases()).isEmpty();
  }

  @Test
  void parse_withEmptyDatabases_returnsEmptyConfig() throws IOException {
    File f = createJson("{\"databases\":{}}");
    CustomRelationConfig result = parser.parse(f);
    assertThat(result).isNotNull();
    assertThat(result.getDatabases()).isEmpty();
  }

  @Test
  void parse_withSensitiveColumns_parsesColumnList() throws IOException {
    File f = createJson("{\"databases\":{}," +
        "\"sensitiveColumns\":[" +
        "{\"database\":\"prod\",\"table\":\"users\",\"column\":\"password_hash\"}]}");
    CustomRelationConfig result = parser.parse(f);
    assertThat(result.getSensitiveColumns()).hasSize(1);
    assertThat(result.getSensitiveColumns().get(0).getColumn()).isEqualTo("password_hash");
  }

  @Test
  void parse_withRelations_parsesRelationList() throws IOException {
    String json = "{\"databases\":{\"mydb\":{\"relations\":[" +
        "{\"table_name\":\"orders\",\"table_column\":\"user_id\"," +
        "\"referenced_table_name\":\"users\",\"referenced_column_name\":\"id\"}]}}}";
    File f = createJson(json);
    CustomRelationConfig result = parser.parse(f);
    assertThat(result.getDatabases()).containsKey("mydb");
    assertThat(result.getDatabases().get("mydb").getRelations()).hasSize(1);
  }

  @Test
  void parse_withRelations_defaultsReferencedDatabaseToSameDatabase() throws IOException {
    String json = "{\"databases\":{\"mydb\":{\"relations\":[" +
        "{\"table_name\":\"orders\",\"table_column\":\"user_id\"," +
        "\"referenced_table_name\":\"users\",\"referenced_column_name\":\"id\"}]}}}";
    File f = createJson(json);
    CustomRelationConfig result = parser.parse(f);
    assertThat(result.getDatabases().get("mydb").getRelations().get(0)
        .getReferenceDatabaseName()).isEqualTo("mydb");
  }

  @Test
  void parse_withJointTables_parsesJointTables() throws IOException {
    String json = "{\"databases\":{\"mydb\":{\"mapping_tables\":{" +
        "\"user_roles\":{\"type\":\"MANY_TO_MANY\",\"from\":\"users\",\"to\":\"roles\"}}}}}";
    File f = createJson(json);
    CustomRelationConfig result = parser.parse(f);
    assertThat(result.getDatabases().get("mydb").getJointTables()).containsKey("user_roles");
  }

  @Test
  void parse_withInvalidFile_returnsNull() {
    File nonExistent = new File(tempDir.toFile(), "nonexistent.json");
    CustomRelationConfig result = parser.parse(nonExistent);
    assertThat(result).isNull();
  }

  private File createJson(String content) throws IOException {
    Path p = tempDir.resolve("test_" + System.nanoTime() + ".json");
    Files.writeString(p, content);
    return p.toFile();
  }
}
