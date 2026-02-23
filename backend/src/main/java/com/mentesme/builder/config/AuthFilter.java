package com.mentesme.builder.config;

import com.mentesme.builder.service.TokenService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class AuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private final AuthProperties authProperties;
    private final TokenService tokenService;
    private final Environment environment;

    public AuthFilter(AuthProperties authProperties, TokenService tokenService, Environment environment) {
        this.authProperties = authProperties;
        this.tokenService = tokenService;
        this.environment = environment;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Always allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Skip auth for non-API paths
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // Skip auth for login and status endpoints
        if (path.equals("/api/auth/login") || path.equals("/api/auth/status") || path.equals("/api/health") || path.startsWith("/api/db-")) {
            chain.doFilter(request, response);
            return;
        }

        // If auth is disabled, only allow in local/dev/test environments
        if (!authProperties.isEnabled()) {
            if (!isLocalEnvironment()) {
                log.error("Auth disabled in non-local environment — rejecting request to {}", path);
                sendUnauthorized(httpResponse, "Authentication required");
                return;
            }
            log.warn("Auth disabled — granting anonymous ADMIN access (local/dev only)");
            httpRequest.setAttribute("userRole", "ADMIN");
            httpRequest.setAttribute("userName", "dev-anonymous");
            chain.doFilter(request, response);
            return;
        }

        // Check for Authorization header
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(httpResponse, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        if (!tokenService.validateToken(token)) {
            sendUnauthorized(httpResponse, "Invalid or expired token");
            return;
        }

        // Token is valid — extract role and username as request attributes
        String role = tokenService.extractRole(token);
        if (role != null) {
            httpRequest.setAttribute("userRole", role);
        }
        String username = tokenService.extractUsername(token);
        if (username != null) {
            httpRequest.setAttribute("userName", username);
        }
        chain.doFilter(request, response);
    }

    private boolean isLocalEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) return true; // no profile = local dev
        for (String profile : profiles) {
            if ("local".equals(profile) || "dev".equals(profile) || "test".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
