package com.vivek.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiMapTest {

  @Test
  void put_andGet_returnsList() {
    MultiMap<String, Integer> map = new MultiMap<>();
    map.put("a", 1);
    map.put("a", 2);
    assertThat(map.get("a")).containsExactly(1, 2);
  }

  @Test
  void get_nonExistentKey_returnsNull() {
    MultiMap<String, String> map = new MultiMap<>();
    assertThat(map.get("missing")).isNull();
  }

  @Test
  void containsKey_whenPresent_returnsTrue() {
    MultiMap<String, String> map = new MultiMap<>();
    map.put("key", "val");
    assertThat(map.containsKey("key")).isTrue();
  }

  @Test
  void containsKey_whenAbsent_returnsFalse() {
    MultiMap<String, String> map = new MultiMap<>();
    assertThat(map.containsKey("absent")).isFalse();
  }

  @Test
  void clear_removesAllEntries() {
    MultiMap<String, String> map = new MultiMap<>();
    map.put("k", "v");
    map.clear();
    assertThat(map.containsKey("k")).isFalse();
  }

  @Test
  void keySet_returnsAllKeys() {
    MultiMap<String, Integer> map = new MultiMap<>();
    map.put("x", 1);
    map.put("y", 2);
    assertThat(map.keySet()).containsExactlyInAnyOrder("x", "y");
  }
}
