package com.vivek.auth;

import com.vivek.config.FkBlitzAuthProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only UserDetailsService backed by users declared in application.yml.
 * Supports {noop} and {bcrypt} password prefixes via Spring's
 * DelegatingPasswordEncoder.
 */
public class ConfigFileUserDetailsService implements UserDetailsService {

    private final List<FkBlitzAuthProperties.ConfigUser> users;

    public ConfigFileUserDetailsService(List<FkBlitzAuthProperties.ConfigUser> users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .map(this::toUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private UserDetails toUserDetails(FkBlitzAuthProperties.ConfigUser u) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        try {
            authorities.add(new SimpleGrantedAuthority(Role.valueOf(u.getRole()).toAuthority()));
        } catch (IllegalArgumentException ignored) {
            authorities.add(new SimpleGrantedAuthority(Role.READ_ONLY.toAuthority()));
        }
        if (u.getPermissions() != null && !u.getPermissions().isBlank()) {
            for (String perm : u.getPermissions().split(",")) {
                String t = perm.trim();
                if (!t.isEmpty()) authorities.add(new SimpleGrantedAuthority(t));
            }
        }
        return User.withUsername(u.getUsername())
                .password(u.getPassword())
                .authorities(authorities)
                .build();
    }
}
