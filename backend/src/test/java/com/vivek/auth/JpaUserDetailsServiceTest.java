package com.vivek.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JpaUserDetailsService} and its static {@code buildAuthorities} helper.
 */
class JpaUserDetailsServiceTest {

  private UserRepository repo;
  private JpaUserDetailsService service;

  @BeforeEach
  void setUp() {
    repo = mock(UserRepository.class);
    service = new JpaUserDetailsService(repo);
  }

  private FkBlitzUser user(String username, String role, String permissions) {
    FkBlitzUser u = new FkBlitzUser();
    u.setUsername(username);
    u.setPasswordHash("{noop}pass");
    u.setRole(role);
    u.setPermissions(permissions);
    u.setEnabled(true);
    return u;
  }

  @Test
  void loadUserByUsername_whenFound_returnsUserDetails() {
    when(repo.findByUsername("alice")).thenReturn(Optional.of(user("alice", "READ_ONLY", null)));

    UserDetails ud = service.loadUserByUsername("alice");

    assertThat(ud.getUsername()).isEqualTo("alice");
    assertThat(ud.isEnabled()).isTrue();
    assertThat(ud.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_READ_ONLY"));
  }

  @Test
  void loadUserByUsername_whenNotFound_throwsUsernameNotFoundException() {
    when(repo.findByUsername("nobody")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("nobody");
  }

  @Test
  void buildAuthorities_withValidRole_addsRoleAuthority() {
    FkBlitzUser u = user("bob", "READ_WRITE", null);
    List<GrantedAuthority> authorities = JpaUserDetailsService.buildAuthorities(u);
    assertThat(authorities).anyMatch(a -> a.getAuthority().equals("ROLE_READ_WRITE"));
  }

  @Test
  void buildAuthorities_withInvalidRole_defaultsToReadOnly() {
    FkBlitzUser u = user("charlie", "NONEXISTENT_ROLE", null);
    List<GrantedAuthority> authorities = JpaUserDetailsService.buildAuthorities(u);
    assertThat(authorities).anyMatch(a -> a.getAuthority().equals("ROLE_READ_ONLY"));
  }

  @Test
  void buildAuthorities_withPermissions_addsPermissionAuthorities() {
    FkBlitzUser u = user("dave", "ADMIN", "AUDIT_VIEWER, DATA_EXPORT");
    List<GrantedAuthority> authorities = JpaUserDetailsService.buildAuthorities(u);
    assertThat(authorities).anyMatch(a -> a.getAuthority().equals("AUDIT_VIEWER"));
    assertThat(authorities).anyMatch(a -> a.getAuthority().equals("DATA_EXPORT"));
  }

  @Test
  void buildAuthorities_withBlankPermissions_onlyAddsRole() {
    FkBlitzUser u = user("eve", "READ_ONLY", "   ");
    List<GrantedAuthority> authorities = JpaUserDetailsService.buildAuthorities(u);
    assertThat(authorities).hasSize(1);
    assertThat(authorities).anyMatch(a -> a.getAuthority().equals("ROLE_READ_ONLY"));
  }

  @Test
  void buildAuthorities_withNullPermissions_onlyAddsRole() {
    FkBlitzUser u = user("frank", "ADMIN", null);
    List<GrantedAuthority> authorities = JpaUserDetailsService.buildAuthorities(u);
    assertThat(authorities).hasSize(1);
    assertThat(authorities).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
}
