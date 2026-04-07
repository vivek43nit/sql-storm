package com.vivek.auth;

import com.vivek.config.FkBlitzAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final FkBlitzAuthProperties authProps;

    public UserService(UserRepository repo, PasswordEncoder encoder, FkBlitzAuthProperties authProps) {
        this.repo = repo;
        this.encoder = encoder;
        this.authProps = authProps;
    }

    /** Seeds the bootstrap admin user if no users exist yet. */
    @Bean
    public ApplicationRunner seedAdminUser() {
        return args -> {
            String mode = authProps.getUserStore();
            if (!"h2".equals(mode) && !"mysql".equals(mode)) return; // only for JPA stores
            if (repo.count() == 0) {
                String adminUser = authProps.getAdminUser();
                String rawPwd = authProps.getAdminPassword();
                FkBlitzUser admin = new FkBlitzUser(adminUser, encoder.encode(rawPwd), Role.ADMIN);
                admin.setPermissions(UserPermission.SENSITIVE_DATA_RO.name()
                        + "," + UserPermission.SENSITIVE_DATA_RW.name());
                repo.save(admin);
                log.info("Seeded bootstrap admin user '{}'", adminUser);
            }
        };
    }

    public List<FkBlitzUser> listAll() {
        return repo.findAll();
    }

    public Optional<FkBlitzUser> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public FkBlitzUser create(String username, String password, String role, String permissions) {
        if (repo.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        FkBlitzUser user = new FkBlitzUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setPermissions(permissions != null ? permissions : "");
        return repo.save(user);
    }

    @Transactional
    public FkBlitzUser update(Long id, String role, String permissions, Boolean enabled, String newPassword) {
        FkBlitzUser user = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (role != null) user.setRole(role);
        if (permissions != null) user.setPermissions(permissions);
        if (enabled != null) user.setEnabled(enabled);
        if (newPassword != null && !newPassword.isBlank()) user.setPasswordHash(encoder.encode(newPassword));
        return repo.save(user);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
