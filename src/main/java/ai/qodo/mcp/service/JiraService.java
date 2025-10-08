package ai.qodo.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service()
@ConditionalOnProperty(
        name = "mcp.jira.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class JiraService {
    private static final Logger logger = LoggerFactory.getLogger(JiraService.class);


}
