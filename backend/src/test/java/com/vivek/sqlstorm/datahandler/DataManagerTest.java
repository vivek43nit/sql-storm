package com.vivek.sqlstorm.datahandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataManagerTest {

  @Test
  void get_withNullDataType_returnsOriginalData() {
    assertThat(DataManager.get(null, "hello")).isEqualTo("hello");
  }

  @Test
  void get_withEmptyDataType_returnsOriginalData() {
    assertThat(DataManager.get("", "hello")).isEqualTo("hello");
  }

  @Test
  void get_withUnknownDataType_returnsOriginalData() {
    assertThat(DataManager.get("unknown-type", "hello")).isEqualTo("hello");
  }

  @Test
  void set_withNullDataType_returnsOriginalData() {
    assertThat(DataManager.set(null, "value")).isEqualTo("value");
  }

  @Test
  void set_withEmptyDataType_returnsOriginalData() {
    assertThat(DataManager.set("", "value")).isEqualTo("value");
  }

  @Test
  void set_withUnknownDataType_returnsOriginalData() {
    assertThat(DataManager.set("bogus", "value")).isEqualTo("value");
  }

  @Test
  void get_withShortDate_doesNotThrow() {
    // ShortDateDataHandler converts millisecond epoch to date string
    String result = DataManager.get("short-date", "1000000000000");
    assertThat(result).isNotNull();
  }

  @Test
  void get_withLongDate_doesNotThrow() {
    String result = DataManager.get("long-date", "1000000000000");
    assertThat(result).isNotNull();
  }

  @Test
  void set_withShortDate_doesNotThrow() {
    // ShortDateDataHandler expects "dd MMM yyyy" format
    String result = DataManager.set("short-date", "01 Jan 2024");
    assertThat(result).isNotNull();
  }

  @Test
  void set_withLongDate_doesNotThrow() {
    // LongDateDataHandler expects "dd MMM yyyy HH:mm:ss" format
    String result = DataManager.set("long-date", "01 Jan 2024 00:00:00");
    assertThat(result).isNotNull();
  }

  @Test
  void get_withIp_returnsFormattedAddress() {
    // 192.168.1.1 = 0xC0A80101 = 3232235777
    String result = DataManager.get("ip", "3232235777");
    assertThat(result).isEqualTo("192.168.1.1");
  }

  @Test
  void set_withIp_returnsPackedLong() {
    String result = DataManager.set("ip", "192.168.1.1");
    assertThat(result).isEqualTo("3232235777");
  }

  @Test
  void set_withEmptyIp_returnsZero() {
    String result = DataManager.set("ip", "");
    assertThat(result).isEqualTo("0");
  }
}
