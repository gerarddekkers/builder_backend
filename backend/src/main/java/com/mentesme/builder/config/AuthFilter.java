package com.mentesme.builder.config;

import com.mentesme.builder.service.TokenService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class AuthFilter implements Filter {

    private final AuthProperties authProperties;
    private final TokenService tokenService;

    public AuthFilter(AuthProperties authProperties, TokenService tokenService) {
        this.authProperties = authProperties;
        this.tokenService = tokenService;
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
        if (path.equals("/api/auth/login") || path.equals("/api/auth/status") || path.equals("/api/health")) {
            chain.doFilter(request, response);
            return;
        }

        // If auth is disabled, allow all requests
        if (!authProperties.isEnabled()) {
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

        // Token is valid, proceed
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
