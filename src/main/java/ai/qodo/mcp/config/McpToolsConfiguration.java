package ai.qodo.mcp.config;

import ai.qodo.mcp.service.GitService;
import ai.qodo.mcp.service.JiraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unified MCP Tools Configuration.
 * <p>
 * This configuration combines all available MCP tools (Git, Jira, etc.) into a single
 * List<ToolCallback> bean that Spring AI MCP Server can consume.
 * <p>
 * Each service's tools are conditionally included based on their respective
 * configuration properties.
 */
@Configuration
public class McpToolsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpToolsConfiguration.class);

    /**
     * Creates a unified list of all available MCP tools.
     * This bean is marked as @Primary to resolve the ambiguity when multiple
     * List<ToolCallback> beans exist.
     * 
     * Services are injected as Optional to handle cases where they're disabled
     * via configuration properties.
     * 
     * @param gitService Optional GitService (only present if mcp.git.enabled=true)
     * @param jiraService Optional JiraService (only present if mcp.jira.enabled=true)
     * @param jiraMcpConfiguration JiraMcpConfiguration for validation
     * @return Combined list of all available tool callbacks
     */
    @Bean
    @Primary
    public List<ToolCallback> allMcpTools(
            java.util.Optional<GitService> gitService,
            java.util.Optional<JiraService> jiraService,
            JiraMcpConfiguration jiraMcpConfiguration) {
        
        List<ToolCallback> allTools = new ArrayList<>();
        
        // Add Git tools if GitService is available
        gitService.ifPresent(service -> {
            logger.info("Adding Git tools to MCP server");
            allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));
        });
        
        // Add Jira tools if JiraService is available and properly configured
        jiraService.ifPresent(service -> {
            if (jiraMcpConfiguration.isConfigurationValid()) {
                logger.info("Adding Jira tools to MCP server");
                allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));
            } else {
                logger.warn("Jira service is available but configuration is invalid. Skipping Jira tools.");
            }
        });
        
        logger.info("Total MCP tools registered: {}", allTools.size());
        return allTools;
    }
}
