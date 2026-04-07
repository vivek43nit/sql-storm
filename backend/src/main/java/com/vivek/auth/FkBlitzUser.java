package com.vivek.auth;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fkblitz_users")
@Data
@NoArgsConstructor
public class FkBlitzUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    /** One of: ADMIN, READ_WRITE, READ_ONLY */
    @Column(nullable = false, length = 20)
    private String role = Role.READ_ONLY.name();

    /**
     * Comma-separated UserPermission names, e.g. "SENSITIVE_DATA_RO,SENSITIVE_DATA_RW".
     * Empty string = no extra permissions.
     */
    @Column(length = 200)
    private String permissions = "";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public FkBlitzUser(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role.name();
    }
}
