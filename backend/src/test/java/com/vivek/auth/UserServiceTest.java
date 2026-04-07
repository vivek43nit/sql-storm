package com.vivek.auth;

import com.vivek.config.FkBlitzAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

  @Mock UserRepository repo;
  @Mock PasswordEncoder encoder;
  @Mock FkBlitzAuthProperties authProps;

  @InjectMocks UserService userService;

  @BeforeEach
  void setUp() {
    given(encoder.encode(any())).willAnswer(inv -> "hashed:" + inv.getArgument(0));
  }

  // ── create ─────────────────────────────────────────────────────────────────

  @Test
  void create_whenUsernameAvailable_savesUser() {
    given(repo.existsByUsername("alice")).willReturn(false);
    FkBlitzUser saved = new FkBlitzUser("alice", "hashed:pass", Role.READ_WRITE);
    given(repo.save(any())).willReturn(saved);

    FkBlitzUser result = userService.create("alice", "pass", "READ_WRITE", "");

    assertThat(result.getUsername()).isEqualTo("alice");
    verify(repo).save(any(FkBlitzUser.class));
  }

  @Test
  void create_whenUsernameAlreadyExists_throwsIllegalArgumentException() {
    given(repo.existsByUsername("alice")).willReturn(true);

    assertThatThrownBy(() -> userService.create("alice", "pass", "READ_ONLY", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alice");

    verify(repo, never()).save(any());
  }

  @Test
  void create_encodesPassword() {
    given(repo.existsByUsername("bob")).willReturn(false);
    given(repo.save(any())).willAnswer(inv -> inv.getArgument(0));

    FkBlitzUser result = userService.create("bob", "secret", "READ_ONLY", "");

    assertThat(result.getPasswordHash()).isEqualTo("hashed:secret");
  }

  // ── update ─────────────────────────────────────────────────────────────────

  @Test
  void update_whenUserExists_updatesRole() {
    FkBlitzUser existing = new FkBlitzUser("alice", "hashed:old", Role.READ_ONLY);
    existing.setId(1L);
    given(repo.findById(1L)).willReturn(Optional.of(existing));
    given(repo.save(any())).willAnswer(inv -> inv.getArgument(0));

    FkBlitzUser result = userService.update(1L, "READ_WRITE", null, null, null);

    assertThat(result.getRole()).isEqualTo("READ_WRITE");
    verify(repo).save(existing);
  }

  @Test
  void update_withNewPassword_encodesPassword() {
    FkBlitzUser existing = new FkBlitzUser("alice", "hashed:old", Role.READ_ONLY);
    existing.setId(1L);
    given(repo.findById(1L)).willReturn(Optional.of(existing));
    given(repo.save(any())).willAnswer(inv -> inv.getArgument(0));

    FkBlitzUser result = userService.update(1L, null, null, null, "newpass");

    assertThat(result.getPasswordHash()).isEqualTo("hashed:newpass");
  }

  @Test
  void update_whenUserNotFound_throwsIllegalArgumentException() {
    given(repo.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.update(99L, "ADMIN", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("99");
  }

  // ── delete ─────────────────────────────────────────────────────────────────

  @Test
  void delete_delegatesToRepository() {
    userService.delete(42L);
    verify(repo).deleteById(42L);
  }

  // ── listAll / findById ────────────────────────────────────────────────────

  @Test
  void listAll_returnsAllUsers() {
    List<FkBlitzUser> users = List.of(
        new FkBlitzUser("a", "h", Role.ADMIN),
        new FkBlitzUser("b", "h", Role.READ_ONLY)
    );
    given(repo.findAll()).willReturn(users);

    assertThat(userService.listAll()).hasSize(2);
  }

  @Test
  void findById_whenExists_returnsUser() {
    FkBlitzUser user = new FkBlitzUser("alice", "h", Role.READ_ONLY);
    given(repo.findById(1L)).willReturn(Optional.of(user));

    assertThat(userService.findById(1L)).contains(user);
  }
}
