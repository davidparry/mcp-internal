package ai.qodo.mcp.config;

import ai.qodo.mcp.service.GitService;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Configuration for Git MCP Server.
 * <p>
 * This configuration sets up the GitService which provides Git operations
 * that can be exposed through the MCP protocol.
 * <p>
 * The Spring AI MCP Server starter will automatically discover and expose
 * the GitService methods as MCP tools when properly annotated.
 */
@Configuration
public class GitMcpConfiguration  {

    private static final Logger logger = LoggerFactory.getLogger(GitMcpConfiguration.class);
    private final CountDownLatch rootsLatch = new CountDownLatch(1);
    private String defaultLocalPath;
    private boolean rootsInitialized = false;
    private McpSchema.ClientCapabilities clientCapabilities;
    private McpSyncServerExchange serverExchange;
    private final ApplicationContext context;

    public GitMcpConfiguration(ApplicationContext context) {
        this.context = context;
    }

    public String getDefaultLocalPath() {
        return this.defaultLocalPath;
    }

    public boolean isRootsInitialized() {
        return rootsInitialized;
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
        logger.info("Server exchange set, ready to communicate with client");
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
            logger.info("Requesting roots from client...");
            McpSchema.ListRootsResult roots = serverExchange.listRoots();
            
            if (roots != null && !roots.roots().isEmpty()) {
                logger.info("Received {} root(s) from client", roots.roots().size());
                initRootPath(roots.roots());
            } else {
                logger.warn("Client returned empty roots list");
            }
        } catch (Exception e) {
            logger.error("Error requesting roots from client", e);
        }
    }

    @Bean
    public List<ToolCallback> gitTools(GitService gitservice) {
        return List.of(ToolCallbacks.from(gitservice));
    }

    @Bean
    public BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> rootsChangeHandler(
            GitMcpConfiguration mcpConfiguration) {
        logger.info("!!! Registering rootsChangeHandler !!!");

        return (exchange, roots) -> {

            setServerExchange(exchange);
            logger.info("Server exchange stored");

            // If roots are provided, use them
            if (roots != null && !roots.isEmpty()) {
                logger.info("Using {} provided roots", roots.size());
                initRootPath(roots);
            }
            // If no roots provided, proactively request them from the client
            else {
                logger.warn("No roots provided, requesting from client...");
                requestRootsFromClient();
            }
        };
    }

    /**
     * Handler for MCP client initialization.
     * This bean is called when the MCP client sends its initialization request with capabilities.
     */
    @Bean
    public Consumer<McpSchema.ClientCapabilities> clientCapabilitiesHandler(GitMcpConfiguration mcpConfiguration) {
        return capabilities -> {
            logger.info("Received client capabilities during initialization");
            mcpConfiguration.clientCapabilities = capabilities;


        };
    }

    /**
     * Handler that receives the McpSyncServerExchange after initialization.
     * This bean is called by the Spring AI MCP framework when the server exchange is ready,
     * which happens after the client sends the initialized notification.
     * Use this to proactively request roots from the client.
     */
    @Bean(name = "mcpServerExchangeConsumer")
    public Consumer<McpSyncServerExchange> mcpServerExchangeConsumer(GitMcpConfiguration mcpConfiguration) {
        return exchange -> {
            logger.info("Received server exchange after initialization");
            mcpConfiguration.setServerExchange(exchange);
            mcpConfiguration.requestRootsFromClient();
        };
    }

    @Bean(name = "mcpServerInitializedConsumer")
    public Consumer<McpSyncServerExchange> mcpServerInitializedConsumer() {
        logger.info("!!! Registering mcpServerInitializedConsumer !!!");

        return exchange -> {
            logger.info("=================================================");
            logger.info("!!! INITIALIZED notification received !!!");
            logger.info("!!! Exchange is now available !!!");
            logger.info("=================================================");

            this.serverExchange = exchange;

            // Now proactively request roots
            logger.info("Proactively requesting roots from client...");
            requestRootsFromClient();
        };
    }
    @Bean(name = "serverExchangeConsumer")
    public Consumer<McpSyncServerExchange> serverExchangeConsumer() {
        logger.info("!!! Registering serverExchangeConsumer !!!");

        return exchange -> {
            logger.info("=================================================");
            logger.info("!!! serverExchangeConsumer called !!!");
            logger.info("=================================================");

            this.serverExchange = exchange;
            requestRootsFromClient();
        };
    }

}
