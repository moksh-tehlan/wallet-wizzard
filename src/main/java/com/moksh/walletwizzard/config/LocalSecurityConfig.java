package com.moksh.walletwizzard.config;

import com.moksh.walletwizzard.dto.UserRegistrationRequest;
import com.moksh.walletwizzard.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Local-only security config — active when SPRING_PROFILES_ACTIVE=local.
 *
 * Permits all requests without JWT validation and injects a hardcoded
 * test user into TenantContext so RLS and service logic still work end-to-end.
 * Never loaded in staging or production.
 */
@Slf4j
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalSecurityConfig {

    // Fixed UUID used as the local dev user — stable across restarts
    public static final UUID LOCAL_USER_ID =
            UUID.fromString("00000000-0000-7000-8000-000000000001");

    private final UserService userService;

    @Bean
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        log.warn("Running with LOCAL security profile — all requests permitted, no JWT required");
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new LocalTenantFilter(userService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static class LocalTenantFilter extends OncePerRequestFilter {

        private final UserService userService;

        LocalTenantFilter(UserService userService) {
            this.userService = userService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            try {
                TenantContext.setCurrentUser(LOCAL_USER_ID);
                userService.findOrCreate(new UserRegistrationRequest(
                        LOCAL_USER_ID, "dev@local.test", "Local Dev User"));
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
