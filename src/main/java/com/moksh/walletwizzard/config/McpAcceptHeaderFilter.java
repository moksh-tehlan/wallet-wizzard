package com.moksh.walletwizzard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Spring AI's Streamable HTTP transport requires Accept: text/event-stream.
 * Claude Code's MCP HTTP client omits it, causing 400s on tools/list.
 * This filter injects the required value for all POST /mcp requests.
 */
@Slf4j
@Component
@Order(1)
public class McpAcceptHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false; // log everything to find what Claude Code sends
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String accept = request.getHeader("Accept");
        log.info(">>> {} {} | Accept: {}", method, uri, accept);

        if ("POST".equals(method) && uri != null && uri.startsWith("/mcp")
                && (accept == null || !accept.contains("text/event-stream"))) {
            log.info(">>> Injecting text/event-stream into Accept");
            request = new AcceptOverrideWrapper(request);
        }
        chain.doFilter(request, response);
    }

    private static class AcceptOverrideWrapper extends HttpServletRequestWrapper {
        private static final String ACCEPT_VALUE = "application/json, text/event-stream";

        AcceptOverrideWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if ("Accept".equalsIgnoreCase(name)) return ACCEPT_VALUE;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Accept".equalsIgnoreCase(name)) return Collections.enumeration(List.of(ACCEPT_VALUE));
            return super.getHeaders(name);
        }
    }
}
