package ai.qodo.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Component
public class McpDebugger implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(McpDebugger.class);

    @Autowired
    private ApplicationContext context;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        logger.info("=== MCP Configuration Debug ===");

        // Check all BiConsumer beans
        Map<String, BiConsumer> biConsumers = context.getBeansOfType(BiConsumer.class);
        logger.info("BiConsumer beans found: {}", biConsumers.size());
        biConsumers.forEach((name, bean) -> {
            logger.info("  - Bean name: {}", name);
            logger.info("    Bean class: {}", bean.getClass().getName());
        });

        // Check all Consumer beans
        Map<String, Consumer> consumers = context.getBeansOfType(Consumer.class);
        logger.info("Consumer beans found: {}", consumers.size());
        consumers.forEach((name, bean) -> {
            logger.info("  - Bean name: {}", name);
            logger.info("    Bean class: {}", bean.getClass().getName());
        });

        // Check for any Spring AI MCP beans
        String[] allBeans = context.getBeanDefinitionNames();
        logger.info("Searching for MCP-related beans...");
        for (String beanName : allBeans) {
            if (beanName.toLowerCase().contains("mcp")) {
                Object bean = context.getBean(beanName);
                logger.info("  MCP bean: {} -> {}", beanName, bean.getClass().getName());
            }
        }

        logger.info("=== End Debug ===");
    }
}
