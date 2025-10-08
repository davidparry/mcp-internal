package ai.qodo.mcp.config;

import ai.qodo.mcp.service.JiraService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class JiraServiceIntegrationTest {


    @Autowired
    private JiraService jiraService;

    @Autowired
    private JiraMcpConfiguration jiraMcpConfiguration;


    @BeforeEach
    void setUp() {
        jiraService.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        jiraService.cleanup();
    }

    @Test
    void testJiraIssue() throws Exception {
        String issue = jiraService.getIssue("SCRUM-150");
        assertNotNull(issue);
        System.out.println(issue);
    }


}
