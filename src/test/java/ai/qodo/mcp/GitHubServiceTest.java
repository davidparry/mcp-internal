/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.GithubConfiguration;
import ai.qodo.mcp.config.GithubMcpConfiguration;
import ai.qodo.mcp.config.McpToolsConfiguration;
import ai.qodo.mcp.service.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GitHubService focusing on URL parsing and error handling.
 */
@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private GithubMcpConfiguration githubMcpConfiguration;

    @Mock
    private GithubConfiguration githubConfiguration;

    @Mock
    private McpToolsConfiguration mcpToolsConfiguration;

    private GitHubService gitHubService;

    @BeforeEach
    void setUp() {
        gitHubService = new GitHubService(githubMcpConfiguration);
    }

    @Test
    void testParseGitHubUrl_SSH_Format() throws Exception {
        String sshUrl = "git@github.com:owner/repo-name.git";
        String result = invokeParseGitHubUrl(sshUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_HTTPS_Format() throws Exception {
        String httpsUrl = "https://github.com/owner/repo-name.git";
        String result = invokeParseGitHubUrl(httpsUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_HTTPS_Without_Git_Extension() throws Exception {
        String httpsUrl = "https://github.com/owner/repo-name";
        String result = invokeParseGitHubUrl(httpsUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_Git_Protocol() throws Exception {
        String gitUrl = "git://github.com/owner/repo-name.git";
        String result = invokeParseGitHubUrl(gitUrl);
        assertEquals("owner/repo-name", result);
    }

    @Test
    void testParseGitHubUrl_With_Hyphen_In_Name() throws Exception {
        String url = "git@github.com:davidparry/demo-spring-petclinic-graphql.git";
        String result = invokeParseGitHubUrl(url);
        assertEquals("davidparry/demo-spring-petclinic-graphql", result);
    }

    @Test
    void testParseGitHubUrl_Null_URL() {
        Exception exception = assertThrows(Exception.class, () -> {
            invokeParseGitHubUrl(null);
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testParseGitHubUrl_Empty_URL() {
        Exception exception = assertThrows(Exception.class, () -> {
            invokeParseGitHubUrl("");
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testParseGitHubUrl_Invalid_Format() {
        Exception exception = assertThrows(Exception.class, () -> {
            invokeParseGitHubUrl("invalid-url-without-slash");
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void testFindGitDirectory_Null_Path() throws Exception {
        File result = invokeFindGitDirectory(null);
        assertNull(result);
    }

    @Test
    void testFindGitDirectory_Empty_Path() throws Exception {
        File result = invokeFindGitDirectory("");
        assertNull(result);
    }

    @Test
    void testFindGitDirectory_NonExistent_Path() throws Exception {
        File result = invokeFindGitDirectory("/non/existent/path");
        assertNull(result);
    }

    /**
     * Helper method to invoke private parseGitHubUrl method via reflection.
     */
    private String invokeParseGitHubUrl(String url) throws Exception {
        Method method = GitHubService.class.getDeclaredMethod("parseGitHubUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(gitHubService, url);
    }

    /**
     * Helper method to invoke protected findGitDirectory method via reflection.
     */
    private File invokeFindGitDirectory(String path) throws Exception {
        Method method = GitHubService.class.getDeclaredMethod("findGitDirectory", String.class);
        method.setAccessible(true);
        return (File) method.invoke(gitHubService, path);
    }
}
