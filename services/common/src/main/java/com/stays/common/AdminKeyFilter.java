package com.stays.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class AdminKeyFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-Admin-Key";
    private final String expectedKey;

    public AdminKeyFilter(@Value("${app.admin.api-key:}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String suppliedKey = request.getHeader(HEADER_NAME);
        if (expectedKey.isBlank() || suppliedKey == null || !matches(suppliedKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Staff key required");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String suppliedKey) {
        return MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8));
    }
}
