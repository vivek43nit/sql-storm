package com.vivek.utils.resource;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResorceFinder}.
 */
class ResorceFinderTest {

  @Test
  void getFile_whenFileOnClasspath_returnsFile() {
    // custom_mapping.json is placed in src/test/resources and available on classpath
    File f = ResorceFinder.getFile("fkblitz", "custom_mapping.json");
    assertThat(f).isNotNull();
    assertThat(f.exists()).isTrue();
  }

  @Test
  void getFile_whenFileNotFound_returnsNull() {
    File f = ResorceFinder.getFile("fkblitz", "definitely-does-not-exist-xyz.json");
    assertThat(f).isNull();
  }
}
