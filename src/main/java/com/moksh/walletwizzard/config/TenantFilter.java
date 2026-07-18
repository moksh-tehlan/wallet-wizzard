package com.moksh.walletwizzard.config;

import com.moksh.walletwizzard.dto.UserRegistrationRequest;
import com.moksh.walletwizzard.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runs after BearerTokenAuthenticationFilter (JWT is already validated at this point).
 * Extracts user identity from the JWT, stores the user ID in TenantContext (for RLS),
 * and ensures the user record exists in the database (creating it on first login).
 *
 * TenantContext is cleared in the finally block — critical for ThreadLocal hygiene in
 * a pooled-thread environment.
 */
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            var authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();

            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                UUID userId = UUID.fromString(jwt.getSubject());
                TenantContext.setCurrentUser(userId);
                userService.findOrCreate(new UserRegistrationRequest(userId, extractEmail(jwt), extractName(jwt)));
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalStateException(
                    "JWT is missing the 'email' claim. Enable the email scope in your identity provider.");
        }
        return email;
    }

    private String extractName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) return name;

        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given != null && family != null) return (given + " " + family).trim();
        if (given != null) return given;
        if (family != null) return family;

        return jwt.getClaimAsString("email").split("@")[0];
    }
}
