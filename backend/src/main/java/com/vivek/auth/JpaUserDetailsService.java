package com.vivek.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * UserDetailsService backed by the fkblitz_users JPA table (H2 or MySQL).
 */
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public JpaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        FkBlitzUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(buildAuthorities(user))
                .disabled(!user.isEnabled())
                .build();
    }

    static List<GrantedAuthority> buildAuthorities(FkBlitzUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Base role
        try {
            authorities.add(new SimpleGrantedAuthority(Role.valueOf(user.getRole()).toAuthority()));
        } catch (IllegalArgumentException ignored) {
            authorities.add(new SimpleGrantedAuthority(Role.READ_ONLY.toAuthority()));
        }
        // Extra permissions
        if (user.getPermissions() != null && !user.getPermissions().isBlank()) {
            for (String perm : user.getPermissions().split(",")) {
                String trimmed = perm.trim();
                if (!trimmed.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(trimmed));
                }
            }
        }
        return authorities;
    }
}
