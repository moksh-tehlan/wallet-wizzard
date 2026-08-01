package com.moksh.walletwizzard.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final IpAddressMatcher LOCALHOST_V4 = new IpAddressMatcher("127.0.0.1");
    private static final IpAddressMatcher LOCALHOST_V6 = new IpAddressMatcher("::1");

    private final TenantFilter tenantFilter;

    @Bean
    @Profile("!local")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // GET / is the nginx upstream health check — localhost only
                        .requestMatchers(HttpMethod.GET, "/")
                            .access((a, ctx) -> new AuthorizationDecision(
                                LOCALHOST_V4.matches(ctx.getRequest()) ||
                                LOCALHOST_V6.matches(ctx.getRequest())))
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/.well-known/oauth-authorization-server",
                                "/.well-known/oauth-protected-resource",
                                "/oauth2/register",
                                "/oauth2/authorize",
                                "/oauth2/callback",
                                "/oauth2/token"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {})
                )
                .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Prevents Spring Boot from auto-registering TenantFilter as a plain Servlet filter.
     * It is managed explicitly by the Security filter chain instead.
     */
    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        var registration = new FilterRegistrationBean<>(tenantFilter);
        registration.setEnabled(false);
        return registration;
    }
}
