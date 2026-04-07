package com.vivek.controller;

import com.vivek.config.FkBlitzAuthProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Tag(name = "Auth", description = "Authentication state and OAuth2 configuration")
public class AuthController {

    private final FkBlitzAuthProperties authProps;

    public AuthController(FkBlitzAuthProperties authProps) {
        this.authProps = authProps;
    }

    /**
     * Returns the current user's identity, role and permissions.
     * Frontend calls this after login to enable role-aware rendering.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        List<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String role = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("READ_ONLY");

        List<String> permissions = authorities.stream()
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "username", auth.getName(),
                "role", role,
                "permissions", permissions
        ));
    }

    /**
     * Returns auth configuration the frontend needs to render the login page.
     * This endpoint is public (no auth required).
     */
    @GetMapping("/auth/config")
    public ResponseEntity<?> authConfig() {
        FkBlitzAuthProperties.OAuth2Config oauth2 = authProps.getOauth2();
        return ResponseEntity.ok(Map.of(
                "oauth2Enabled", oauth2.isEnabled()
        ));
    }
}
