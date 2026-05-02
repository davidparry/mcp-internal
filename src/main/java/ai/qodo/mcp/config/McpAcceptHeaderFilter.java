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
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Ensures POST requests to the MCP endpoint include the required
 * Accept: application/json, text/event-stream header.
 * <p>
 * The MCP Streamable HTTP spec mandates this header, but some clients
 * (e.g. MCP Inspector) omit it. This filter adds it when missing so
 * the Spring AI transport provider does not reject the request with 400.
 */
@Component
@Order(0)
public class McpAcceptHeaderFilter implements Filter {

    private static final String MCP_ENDPOINT = "/mcp";
    private static final String REQUIRED_ACCEPT = "application/json, text/event-stream";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (isMcpPost(httpRequest) && needsAcceptFix(httpRequest)) {
            chain.doFilter(new AcceptHeaderRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isMcpPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith(MCP_ENDPOINT);
    }

    private boolean needsAcceptFix(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept == null
                || (!accept.contains("text/event-stream") && !accept.contains("*/*"));
    }

    private static class AcceptHeaderRequestWrapper extends HttpServletRequestWrapper {

        AcceptHeaderRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if ("Accept".equalsIgnoreCase(name)) {
                return REQUIRED_ACCEPT;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Accept".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(REQUIRED_ACCEPT));
            }
            return super.getHeaders(name);
        }
    }
}
