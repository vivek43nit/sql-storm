package com.vivek.auth;

import com.vivek.config.FkBlitzAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFileUserDetailsServiceTest {

  private ConfigFileUserDetailsService service;

  @BeforeEach
  void setUp() {
    FkBlitzAuthProperties.ConfigUser admin = new FkBlitzAuthProperties.ConfigUser();
    admin.setUsername("admin");
    admin.setPassword("{noop}admin123");
    admin.setRole("ADMIN");
    admin.setPermissions("");

    FkBlitzAuthProperties.ConfigUser reader = new FkBlitzAuthProperties.ConfigUser();
    reader.setUsername("reader");
    reader.setPassword("{noop}reader123");
    reader.setRole("READ_ONLY");
    reader.setPermissions("SENSITIVE_DATA_RO, AUDIT_VIEWER");

    FkBlitzAuthProperties.ConfigUser badRole = new FkBlitzAuthProperties.ConfigUser();
    badRole.setUsername("badrole");
    badRole.setPassword("{noop}pass");
    badRole.setRole("NONEXISTENT_ROLE");
    badRole.setPermissions(null);

    service = new ConfigFileUserDetailsService(List.of(admin, reader, badRole));
  }

  @Test
  void loadUserByUsername_whenFound_returnsUserDetails() {
    UserDetails ud = service.loadUserByUsername("admin");
    assertThat(ud.getUsername()).isEqualTo("admin");
    assertThat(ud.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }

  @Test
  void loadUserByUsername_whenNotFound_throwsUsernameNotFound() {
    assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void loadUserByUsername_withPermissions_addsAuthorities() {
    UserDetails ud = service.loadUserByUsername("reader");
    assertThat(ud.getAuthorities()).anyMatch(a -> a.getAuthority().equals("SENSITIVE_DATA_RO"));
    assertThat(ud.getAuthorities()).anyMatch(a -> a.getAuthority().equals("AUDIT_VIEWER"));
  }

  @Test
  void loadUserByUsername_withInvalidRole_defaultsToReadOnly() {
    UserDetails ud = service.loadUserByUsername("badrole");
    assertThat(ud.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_READ_ONLY"));
  }
}
