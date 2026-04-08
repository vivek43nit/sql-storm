package com.vivek.config;

import com.vivek.auth.*;
import com.vivek.metrics.FkBlitzMetrics;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({FkBlitzAuthProperties.class, FkBlitzConfigProperties.class})
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final FkBlitzAuthProperties authProps;
    private final FkBlitzMetrics metrics;

    public SecurityConfig(FkBlitzAuthProperties authProps, FkBlitzMetrics metrics) {
        this.authProps = authProps;
        this.metrics = metrics;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationManager authManager) throws Exception {
        http
            .cors(cors -> {})
            .csrf(csrf -> csrf.disable())
            .authenticationManager(authManager)
            .authorizeHttpRequests(auth -> auth
                // Public
                .requestMatchers("/api/login", "/api/auth/config",
                        "/oauth2/**", "/login/oauth2/**",
                        "/assets/**", "/index.html", "/favicon.ico", "/vite.svg", "/").permitAll()
                // Actuator health + Prometheus metrics — public (Prometheus scraper has no auth)
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Swagger UI and OpenAPI spec — ADMIN only
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                // User management — ADMIN only
                .requestMatchers("/api/admin/users/**").hasRole("ADMIN")
                // Admin views — ADMIN only
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Mutations — READ_WRITE or ADMIN
                .requestMatchers(
                        org.springframework.http.HttpMethod.POST,   "/api/row/**").hasAnyRole("READ_WRITE", "ADMIN")
                .requestMatchers(
                        org.springframework.http.HttpMethod.PUT,    "/api/row/**").hasAnyRole("READ_WRITE", "ADMIN")
                .requestMatchers(
                        org.springframework.http.HttpMethod.DELETE, "/api/row/**").hasAnyRole("READ_WRITE", "ADMIN")
                // All other API calls — any authenticated user
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Forbidden\"}");
                })
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/login")
                .successHandler((req, res, auth) -> {
                    log.info("AUTH_LOGIN user='{}' ip='{}'", auth.getName(), req.getRemoteAddr());
                    String role = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(a -> a.startsWith("ROLE_"))
                            .map(a -> a.substring(5))
                            .findFirst().orElse("UNKNOWN");
                    res.setStatus(HttpServletResponse.SC_OK);
                    res.setContentType("application/json");
                    res.getWriter().write(
                        "{\"status\":\"ok\",\"user\":\"" + auth.getName() + "\",\"role\":\"" + role + "\"}"
                    );
                })
                .failureHandler((req, res, ex) -> {
                    String username = req.getParameter("username");
                    log.warn("AUTH_LOGIN_FAIL user='{}' ip='{}'", username, req.getRemoteAddr());
                    metrics.recordAuthFailure();
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Invalid credentials\"}");
                })
            )
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessHandler((req, res, auth) -> {
                    if (auth != null) log.info("AUTH_LOGOUT user='{}' ip='{}'", auth.getName(), req.getRemoteAddr());
                    res.setStatus(HttpServletResponse.SC_OK);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":\"logged out\"}");
                })
            );

        // OAuth2 login — only wired if enabled
        if (authProps.getOauth2().isEnabled()) {
            http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(ui -> ui.userService(oauth2UserService()))
                .successHandler((req, res, auth) -> {
                    log.info("AUTH_OAUTH2_LOGIN user='{}' ip='{}'", auth.getName(), req.getRemoteAddr());
                    // Redirect back to the SPA after OAuth2 success
                    res.sendRedirect("/");
                })
            );
        }

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserRepository userRepository) {
        String mode = authProps.getUserStore();

        if ("external-api".equals(mode)) {
            ExternalApiAuthenticationProvider provider =
                    new ExternalApiAuthenticationProvider(authProps.getExternalApi());
            return new ProviderManager(provider);
        }

        DaoAuthenticationProvider dao = new DaoAuthenticationProvider();
        dao.setPasswordEncoder(passwordEncoder());

        if ("config".equals(mode)) {
            dao.setUserDetailsService(new ConfigFileUserDetailsService(authProps.getUsers()));
        } else {
            // h2 or mysql — backed by JPA
            dao.setUserDetailsService(new JpaUserDetailsService(userRepository));
        }

        return new ProviderManager(dao);
    }

    /**
     * OAuth2 user service: maps the provider's user-info claims to FkBlitz roles.
     * The role claim name is configurable via fkblitz.auth.oauth2.role-claim.
     */
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        String roleClaim = authProps.getOauth2().getRoleClaim();
        String defaultRole = authProps.getOauth2().getDefaultRole();

        return request -> {
            OAuth2User oauthUser = delegate.loadUser(request);
            String roleName = oauthUser.getAttribute(roleClaim);
            if (roleName == null) roleName = defaultRole;

            List<GrantedAuthority> authorities = new ArrayList<>(oauthUser.getAuthorities());
            try {
                authorities.add(new SimpleGrantedAuthority(Role.valueOf(roleName.toUpperCase()).toAuthority()));
            } catch (IllegalArgumentException e) {
                authorities.add(new SimpleGrantedAuthority(Role.READ_ONLY.toAuthority()));
            }

            String nameAttr = request.getClientRegistration().getProviderDetails()
                    .getUserInfoEndpoint().getUserNameAttributeName();
            return new DefaultOAuth2User(authorities, oauthUser.getAttributes(), nameAttr);
        };
    }
}
