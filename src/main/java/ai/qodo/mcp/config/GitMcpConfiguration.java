package ai.qodo.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Git MCP Server.
 * <p>
 * This configuration provides a facade to the centralized McpToolsConfiguration,
 * insulating GitService from direct dependencies on the shared configuration.
 */
@Configuration
public class GitMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GitMcpConfiguration.class);
    private final McpToolsConfiguration mcpToolsConfiguration;

    public GitMcpConfiguration(McpToolsConfiguration mcpToolsConfiguration) {
        this.mcpToolsConfiguration = mcpToolsConfiguration;
    }

    /**
     * Returns the default local path from centralized configuration.
     */
    public String getDefaultLocalPath() {
        return mcpToolsConfiguration.getDefaultLocalPath();
    }

    /**
     * Ensures roots are initialized before proceeding with operations.
     * Delegates to centralized configuration.
     *
     * @param toolContext The tool context containing the MCP exchange
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void ensureRootsInitialized(ToolContext toolContext) throws InterruptedException {
        mcpToolsConfiguration.ensureRootsInitialized(toolContext);
    }

}
