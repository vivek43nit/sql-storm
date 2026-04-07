package com.vivek.controller;

import com.vivek.auth.FkBlitzUser;
import com.vivek.auth.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User management API — ADMIN role only.
 * Passwords are never returned in responses.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "CRUD operations for FkBlitz users. ADMIN role required.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return userService.listAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return userService.findById(id)
                .map(u -> ResponseEntity.ok(toDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("username and password are required");
        }
        try {
            FkBlitzUser user = userService.create(
                    username, password,
                    body.getOrDefault("role", "READ_ONLY"),
                    body.getOrDefault("permissions", ""));
            return ResponseEntity.status(201).body(toDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            FkBlitzUser user = userService.update(
                    id,
                    (String) body.get("role"),
                    (String) body.get("permissions"),
                    body.containsKey("enabled") ? (Boolean) body.get("enabled") : null,
                    (String) body.get("password"));
            return ResponseEntity.ok(toDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toDto(FkBlitzUser u) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", u.getId());
        dto.put("username", u.getUsername());
        dto.put("role", u.getRole());
        dto.put("permissions", u.getPermissions());
        dto.put("enabled", u.isEnabled());
        dto.put("createdAt", u.getCreatedAt());
        return dto;
    }
}
