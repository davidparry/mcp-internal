package ai.qodo.mcp.config;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

/**
 * Configuration for Terminal MCP Server.
 * <p>
 * This configuration sets up the TerminalService which provides terminal command execution
 * that can be exposed through the MCP protocol.
 * <p>
 * The Spring AI MCP Server starter will automatically discover and expose
 * the TerminalService methods as MCP tools when properly annotated.
 * <p>
 * This configuration is conditional and will only be loaded when the property
 * 'mcp.terminal.enabled' is set to true in application.properties.
 */
@Configuration
public class TerminalMcpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TerminalMcpConfiguration.class);
    private final CountDownLatch rootsLatch = new CountDownLatch(1);
    private final ApplicationContext context;
    private String defaultLocalPath;
    private boolean rootsInitialized = false;
    private McpSchema.ClientCapabilities clientCapabilities;
    private McpSyncServerExchange serverExchange;
    // Combined blocked commands (default + configured)
    private final Set<String> BLOCKED_COMMANDS = new HashSet<>();

    public TerminalMcpConfiguration(ApplicationContext context) {
        this.context = context;
    }

    public String getDefaultLocalPath() {
        return this.defaultLocalPath;
    }

    public boolean isRootsInitialized() {
        return rootsInitialized;
    }
    @Value("${mcp.terminal.blocked:}")
    private String blockedCommands;

    public Set<String> getBlockedCommands() {
        return Set.copyOf(BLOCKED_COMMANDS);
    }

    @PostConstruct
    public void init() {
        // Parse and add additional blocked commands from configuration
        if (blockedCommands != null && !blockedCommands.trim().isEmpty()) {
            String[] additionalCommands = blockedCommands.split(",");
            for (String cmd : additionalCommands) {
                String trimmedCmd = cmd.trim();
                if (!trimmedCmd.isEmpty()) {
                    BLOCKED_COMMANDS.add(trimmedCmd.toLowerCase());
                    logger.info("Added blocked command from configuration: {}", trimmedCmd);
                }
            }
        }

        logger.info("Initialized with {} blocked commands", BLOCKED_COMMANDS.size());
    }
    protected void initRootPath(List<McpSchema.Root> roots) {
        McpSchema.Root firstRoot = roots.getFirst();
        String uri = firstRoot.uri();
        if (uri.startsWith("file://")) {
            uri = uri.substring(7);
        }
        if (uri.startsWith("//") && !uri.startsWith("///")) {
            uri = uri.substring(1);
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        this.defaultLocalPath = new File(uri).getAbsolutePath();
        rootsInitialized = true;
        rootsLatch.countDown();
        logger.info("Terminal root path initialized: {}", this.defaultLocalPath);
    }

    public CountDownLatch getRootsLatch() {
        return this.rootsLatch;
    }

    /**
     * Sets the server exchange instance for communicating with the client.
     * This should be called when the exchange becomes available.
     */
    public void setServerExchange(McpSyncServerExchange exchange) {
        this.serverExchange = exchange;
        logger.info("Terminal server exchange set, ready to communicate with client");
    }

    /**
     * Requests roots from the MCP client.
     * This sends a roots/list request to the client and waits for the response.
     * The response will be handled by the rootsChangeHandler bean.
     */
    public void requestRootsFromClient() {
        if (serverExchange == null) {
            logger.warn("Cannot request roots: server exchange not available");
            return;
        }

        try {
            logger.info("Requesting roots from client for Terminal service...");
            McpSchema.ListRootsResult roots = serverExchange.listRoots();

            if (roots != null && !roots.roots().isEmpty()) {
                logger.info("Received {} root(s) from client for Terminal service", roots.roots().size());
                initRootPath(roots.roots());
            } else {
                logger.warn("Client returned empty roots list for Terminal service");
            }
        } catch (Exception e) {
            logger.error("Error requesting roots from client for Terminal service", e);
        }
    }

    @Bean
    public BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> terminalRootsChangeHandler(
            TerminalMcpConfiguration mcpConfiguration) {
        logger.info("Registering terminalRootsChangeHandler");

        return (exchange, roots) -> {

            setServerExchange(exchange);
            logger.info("Terminal server exchange stored");

            // If roots are provided, use them
            if (roots != null && !roots.isEmpty()) {
                logger.info("Using {} provided roots for Terminal service", roots.size());
                initRootPath(roots);
            }
            // If no roots provided, proactively request them from the client
            else {
                logger.warn("No roots provided for Terminal service, requesting from client...");
                requestRootsFromClient();
            }
        };
    }
}
