/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(1)
public class BearerTokenAuthFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(BearerTokenAuthFilter.class);

    private static final List<String> AUTH_EXEMPT_PATHS = List.of(
            "/actuator",
            "/health",
            "/ready",
            "/live"
    );

    @Value("${mcp.auth.bearer-token}")
    private String expectedToken;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isAuthExempt(httpRequest) || isCorsPreflightRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(httpRequest);

        if (token == null || !token.equals(expectedToken)) {
            logRejection(httpRequest);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Unauthorized\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Authorization: Bearer <token> header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // 2. ?token=<token> query parameter (for clients that cannot set headers)
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }
        return null;
    }

    private boolean isCorsPreflightRequest(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean isAuthExempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return AUTH_EXEMPT_PATHS.stream().anyMatch(exempt ->
                path.equals(exempt) || path.startsWith(exempt + "/"));
    }

    private void logRejection(HttpServletRequest request) {
        String traceId = extractTraceId(request);
        if (traceId != null) {
            logger.warn("Rejected unauthenticated request to {} (trace: {})", request.getRequestURI(), traceId);
        } else {
            logger.warn("Rejected unauthenticated request to {}", request.getRequestURI());
        }
    }

    private String extractTraceId(HttpServletRequest request) {
        // W3C Trace Context (OpenTelemetry, ADOT)
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null) {
            return traceparent;
        }
        // AWS X-Ray
        String xrayTrace = request.getHeader("X-Amzn-Trace-Id");
        if (xrayTrace != null) {
            return xrayTrace;
        }
        // Datadog
        String ddTrace = request.getHeader("x-datadog-trace-id");
        if (ddTrace != null) {
            return ddTrace;
        }
        // Jaeger
        String jaegerTrace = request.getHeader("uber-trace-id");
        if (jaegerTrace != null) {
            return jaegerTrace;
        }
        // Zipkin B3
        String b3Trace = request.getHeader("X-B3-TraceId");
        if (b3Trace != null) {
            return b3Trace;
        }
        // B3 single header
        String b3 = request.getHeader("b3");
        if (b3 != null) {
            return b3;
        }
        return null;
    }
}
