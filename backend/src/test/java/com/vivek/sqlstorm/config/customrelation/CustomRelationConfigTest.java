package com.vivek.sqlstorm.config.customrelation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRelationConfigTest {

  private CustomRelationConfig config;

  @BeforeEach
  void setUp() {
    CustomRelationConfig.SensitiveColumn exact = new CustomRelationConfig.SensitiveColumn();
    exact.setDatabase("prod");
    exact.setTable("users");
    exact.setColumn("password_hash");

    CustomRelationConfig.SensitiveColumn wildcard = new CustomRelationConfig.SensitiveColumn();
    wildcard.setDatabase("*");
    wildcard.setTable("payment_cards");
    wildcard.setColumn("cvv");

    config = new CustomRelationConfig(Map.of());
    config.setSensitiveColumns(List.of(exact, wildcard));
  }

  @Test
  void isSensitive_exactDatabaseMatch_returnsTrue() {
    assertThat(config.isSensitive("prod", "users", "password_hash")).isTrue();
  }

  @Test
  void isSensitive_wildcardDatabaseMatch_returnsTrueForAnyDatabase() {
    assertThat(config.isSensitive("staging", "payment_cards", "cvv")).isTrue();
    assertThat(config.isSensitive("prod", "payment_cards", "cvv")).isTrue();
    assertThat(config.isSensitive("dev", "payment_cards", "cvv")).isTrue();
  }

  @Test
  void isSensitive_wrongColumn_returnsFalse() {
    assertThat(config.isSensitive("prod", "users", "email")).isFalse();
  }

  @Test
  void isSensitive_wrongTable_returnsFalse() {
    assertThat(config.isSensitive("prod", "orders", "password_hash")).isFalse();
  }

  @Test
  void isSensitive_wrongDatabase_returnsFalse() {
    assertThat(config.isSensitive("dev", "users", "password_hash")).isFalse();
  }

  @Test
  void isSensitive_emptySensitiveColumns_returnsFalse() {
    CustomRelationConfig emptyConfig = new CustomRelationConfig(Map.of());
    assertThat(emptyConfig.isSensitive("prod", "users", "password_hash")).isFalse();
  }
}
