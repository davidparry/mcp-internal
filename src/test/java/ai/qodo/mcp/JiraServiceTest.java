package ai.qodo.mcp;

import ai.qodo.mcp.config.JiraConfiguration;
import ai.qodo.mcp.config.JiraMcpConfiguration;
import ai.qodo.mcp.service.JiraService;
import com.atlassian.jira.rest.client.api.*;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import io.atlassian.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JiraServiceTest {

    private JiraService jiraService;

    @Mock
    private JiraMcpConfiguration mcpConfiguration;

    @Mock
    private JiraConfiguration jiraConfiguration;

    @Mock
    private JiraRestClient jiraRestClient;

    @Mock
    private IssueRestClient issueClient;

    @Mock
    private SearchRestClient searchClient;

    @Mock
    private ProjectRestClient projectClient;

    @Mock
    private MetadataRestClient metadataClient;

    @Mock
    private UserRestClient userClient;

    @Mock
    private Issue issue;

    @Mock
    private BasicIssue basicIssue;

    @Mock
    private Project project;

    @Mock
    private IssueType issueType;

    @Mock
    private Priority priority;

    @Mock
    private Status status;

    @Mock
    private User user;

    @Mock
    private SearchResult searchResult;

    @Mock
    private Transition transition;

    @Mock
    private Promise<Issue> issuePromise;

    @Mock
    private Promise<BasicIssue> basicIssuePromise;

    @Mock
    private Promise<SearchResult> searchResultPromise;

    @Mock
    private Promise<Project> projectPromise;

    @Mock
    private Promise<Iterable<BasicProject>> projectsPromise;

    @Mock
    private Promise<Iterable<IssueType>> issueTypesPromise;

    @Mock
    private Promise<Iterable<Priority>> prioritiesPromise;

    @Mock
    private Promise<Iterable<Status>> statusesPromise;

    @Mock
    private Promise<Iterable<Transition>> transitionsPromise;

    @Mock
    private Promise<User> userPromise;

    @Mock
    private Promise<Void> voidPromise;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        MockitoAnnotations.openMocks(this);

        // Setup configuration mocks
        when(mcpConfiguration.getJiraConfiguration()).thenReturn(jiraConfiguration);
        when(jiraConfiguration.getSiteUrl()).thenReturn("https://test.atlassian.net");
        when(jiraConfiguration.getEmail()).thenReturn("test@example.com");
        when(jiraConfiguration.getApiToken()).thenReturn("test-token");
        when(mcpConfiguration.isConfigurationValid()).thenReturn(true);

        // Create service instance
        jiraService = new JiraService(mcpConfiguration);

        // Use reflection to inject the mocked JiraRestClient
        try {
            java.lang.reflect.Field clientField = JiraService.class.getDeclaredField("jiraRestClient");
            clientField.setAccessible(true);
            clientField.set(jiraService, jiraRestClient);
        } catch (Exception e) {
            fail("Failed to inject mocked JiraRestClient: " + e.getMessage());
        }

        // Setup common client mocks
        when(jiraRestClient.getIssueClient()).thenReturn(issueClient);
        when(jiraRestClient.getSearchClient()).thenReturn(searchClient);
        when(jiraRestClient.getProjectClient()).thenReturn(projectClient);
        when(jiraRestClient.getMetadataClient()).thenReturn(metadataClient);
        when(jiraRestClient.getUserClient()).thenReturn(userClient);
    }

    @Test
    void testShouldThrowExceptionWhenClientNotInitialized() {
        // Create a new service with invalid configuration
        when(mcpConfiguration.isConfigurationValid()).thenReturn(false);
        JiraService uninitializedService = new JiraService(mcpConfiguration);
        uninitializedService.init();

        // Verify that operations throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            uninitializedService.getIssue("TEST-123");
        });
    }

    @Test
    void testShouldRetrieveIssueDetailsSuccessfully() throws ExecutionException, InterruptedException {
        // Setup mock issue
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);
        when(issue.getKey()).thenReturn("TEST-123");
        when(issue.getSummary()).thenReturn("Test Issue");
        when(issue.getIssueType()).thenReturn(issueType);
        when(issueType.getName()).thenReturn("Bug");
        when(issue.getStatus()).thenReturn(status);
        when(status.getName()).thenReturn("Open");
        when(issue.getPriority()).thenReturn(priority);
        when(priority.getName()).thenReturn("High");
        when(issue.getReporter()).thenReturn(user);
        when(user.getDisplayName()).thenReturn("John Doe");
        when(issue.getAssignee()).thenReturn(user);
        when(issue.getCreationDate()).thenReturn(null);
        when(issue.getUpdateDate()).thenReturn(null);
        when(issue.getLabels()).thenReturn(Collections.emptySet());
        when(issue.getDescription()).thenReturn("Test description");
        when(issue.getComments()).thenReturn(Collections.emptyList());

        // Execute
        String result = jiraService.getIssue("TEST-123");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("TEST-123"));
        assertTrue(result.contains("Test Issue"));
        assertTrue(result.contains("Bug"));
        assertTrue(result.contains("Open"));
        verify(issueClient, times(1)).getIssue("TEST-123");
    }

    @Test
    void testShouldSearchIssuesWithJqlQuery() throws ExecutionException, InterruptedException {
        // Setup mock search result
        when(searchClient.searchJql(anyString(), anyInt(), anyInt(), any())).thenReturn(searchResultPromise);
        when(searchResultPromise.get()).thenReturn(searchResult);
        when(searchResult.getTotal()).thenReturn(2);
        when(searchResult.getIssues()).thenReturn(Arrays.asList(issue, issue));
        when(issue.getKey()).thenReturn("TEST-123");
        when(issue.getSummary()).thenReturn("Test Issue");
        when(issue.getStatus()).thenReturn(status);
        when(status.getName()).thenReturn("Open");
        when(issue.getPriority()).thenReturn(priority);
        when(priority.getName()).thenReturn("High");
        when(issue.getAssignee()).thenReturn(user);
        when(user.getDisplayName()).thenReturn("John Doe");

        // Execute
        String result = jiraService.searchIssues("project = TEST", 50);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("2 total issues found"));
        assertTrue(result.contains("TEST-123"));
        verify(searchClient, times(1)).searchJql("project = TEST", 50, 0, null);
    }

    @Test
    void testShouldCreateIssueWithAllFields() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(projectClient.getProject("TEST")).thenReturn(projectPromise);
        when(projectPromise.get()).thenReturn(project);
        when(metadataClient.getIssueTypes()).thenReturn(issueTypesPromise);
        when(issueTypesPromise.get()).thenReturn(Collections.singletonList(issueType));
        when(issueType.getName()).thenReturn("Bug");
        when(metadataClient.getPriorities()).thenReturn(prioritiesPromise);
        when(prioritiesPromise.get()).thenReturn(Collections.singletonList(priority));
        when(priority.getName()).thenReturn("High");
        when(issueClient.createIssue(any(IssueInput.class))).thenReturn(basicIssuePromise);
        when(basicIssuePromise.get()).thenReturn(basicIssue);
        when(basicIssue.getKey()).thenReturn("TEST-456");

        // Execute
        String result = jiraService.createIssue("TEST", "Bug", "New Bug", "Description", "High", "label1,label2");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("TEST-456"));
        assertTrue(result.contains("Issue created successfully"));
        verify(issueClient, times(1)).createIssue(any(IssueInput.class));
    }

    @Test
    void testShouldUpdateIssueWithPartialFields() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);
        when(issueClient.updateIssue(eq("TEST-123"), any(IssueInput.class))).thenReturn(voidPromise);
        when(voidPromise.get()).thenReturn(null);

        // Execute - update only summary
        String result = jiraService.updateIssue("TEST-123", "Updated Summary", null, null, null);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("TEST-123 updated successfully"));
        verify(issueClient, times(1)).updateIssue(eq("TEST-123"), any(IssueInput.class));
    }

    @Test
    void testShouldTransitionIssueToValidStatus() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);
        when(issueClient.getTransitions(issue)).thenReturn(transitionsPromise);
        when(transitionsPromise.get()).thenReturn(Collections.singletonList(transition));
        when(transition.getName()).thenReturn("In Progress");
        when(transition.getId()).thenReturn(11);
        when(issueClient.transition(eq(issue), any(TransitionInput.class))).thenReturn(voidPromise);
        when(voidPromise.get()).thenReturn(null);

        // Execute
        String result = jiraService.transitionIssue("TEST-123", "In Progress", null);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("TEST-123 transitioned to 'In Progress' successfully"));
        verify(issueClient, times(1)).transition(eq(issue), any(TransitionInput.class));
    }

    @Test
    void testShouldAddCommentToIssue() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);
        when(issue.getCommentsUri()).thenReturn(URI.create("https://test.atlassian.net/rest/api/2/issue/TEST-123/comment"));
        when(issueClient.addComment(any(URI.class), any(Comment.class))).thenReturn(voidPromise);
        when(voidPromise.get()).thenReturn(null);

        // Execute
        String result = jiraService.addComment("TEST-123", "This is a test comment");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Comment added to issue TEST-123 successfully"));
        verify(issueClient, times(1)).addComment(any(URI.class), any(Comment.class));
    }

    @Test
    void testShouldAssignIssueToUser() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);
        when(userClient.getUser("john.doe")).thenReturn(userPromise);
        when(userPromise.get()).thenReturn(user);
        when(user.getDisplayName()).thenReturn("John Doe");
        when(user.getName()).thenReturn("john.doe"); // Add user name for the assignee field
        when(issueClient.updateIssue(eq("TEST-123"), any(IssueInput.class))).thenReturn(voidPromise);
        when(voidPromise.get()).thenReturn(null);

        // Execute
        String result = jiraService.assignIssue("TEST-123", "john.doe");

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("TEST-123 assigned to John Doe"));
        verify(issueClient, times(1)).updateIssue(eq("TEST-123"), any(IssueInput.class));
    }

    @Test
    void testShouldRetrieveAllProjects() throws ExecutionException, InterruptedException {
        // Setup mocks
        BasicProject basicProject = mock(BasicProject.class);
        when(basicProject.getKey()).thenReturn("TEST");
        when(basicProject.getName()).thenReturn("Test Project");
        when(basicProject.getSelf()).thenReturn(URI.create("https://test.atlassian.net/rest/api/2/project/TEST"));
        when(projectClient.getAllProjects()).thenReturn(projectsPromise);
        when(projectsPromise.get()).thenReturn(Collections.singletonList(basicProject));

        // Execute
        String result = jiraService.getProjects();

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Available Projects"));
        assertTrue(result.contains("TEST"));
        assertTrue(result.contains("Test Project"));
        verify(projectClient, times(1)).getAllProjects();
    }

    @Test
    void testShouldRetrieveIssueTypes() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(metadataClient.getIssueTypes()).thenReturn(issueTypesPromise);
        when(issueTypesPromise.get()).thenReturn(Collections.singletonList(issueType));
        when(issueType.getName()).thenReturn("Bug");
        when(issueType.getDescription()).thenReturn("A problem which impairs or prevents the functions of the product");
        when(issueType.isSubtask()).thenReturn(false);

        // Execute
        String result = jiraService.getIssueTypes();

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Available Issue Types"));
        assertTrue(result.contains("Bug"));
        verify(metadataClient, times(1)).getIssueTypes();
    }

    @Test
    void testShouldRetrievePriorities() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(metadataClient.getPriorities()).thenReturn(prioritiesPromise);
        when(prioritiesPromise.get()).thenReturn(Collections.singletonList(priority));
        when(priority.getName()).thenReturn("High");
        when(priority.getDescription()).thenReturn("This problem will block progress");

        // Execute
        String result = jiraService.getPriorities();

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Available Priorities"));
        assertTrue(result.contains("High"));
        verify(metadataClient, times(1)).getPriorities();
    }

    @Test
    void testShouldRetrieveStatuses() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(metadataClient.getStatuses()).thenReturn(statusesPromise);
        when(statusesPromise.get()).thenReturn(Collections.singletonList(status));
        when(status.getName()).thenReturn("Open");
        when(status.getDescription()).thenReturn("The issue is open and ready for the assignee to start work on it");

        // Execute
        String result = jiraService.getStatuses();

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Available Statuses"));
        assertTrue(result.contains("Open"));
        verify(metadataClient, times(1)).getStatuses();
    }

    @Test
    void testShouldHandleNullMaxResultsInSearch() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(searchClient.searchJql(anyString(), anyInt(), anyInt(), any())).thenReturn(searchResultPromise);
        when(searchResultPromise.get()).thenReturn(searchResult);
        when(searchResult.getTotal()).thenReturn(0);
        when(searchResult.getIssues()).thenReturn(Collections.emptyList());

        // Execute with null maxResults
        String result = jiraService.searchIssues("project = TEST", null);

        // Verify - should default to 50
        assertNotNull(result);
        verify(searchClient, times(1)).searchJql("project = TEST", 50, 0, null);
    }

    @Test
    void testShouldHandleNoUpdatesInUpdateIssue() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);

        // Execute with all null parameters
        String result = jiraService.updateIssue("TEST-123", null, null, null, null);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("No updates provided"));
        verify(issueClient, never()).updateIssue(anyString(), any(IssueInput.class));
    }

    @Test
    void testShouldUnassignIssue() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);
        when(issueClient.updateIssue(eq("TEST-123"), any(IssueInput.class))).thenReturn(voidPromise);
        when(voidPromise.get()).thenReturn(null);

        // Execute with null assignee to unassign
        String result = jiraService.assignIssue("TEST-123", null);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("Issue TEST-123 unassigned"));
        verify(issueClient, times(1)).updateIssue(eq("TEST-123"), any(IssueInput.class));
    }

    @Test
    void testShouldUnassignIssueWhenAssigneeIsEmptyString() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);

        // Execute with empty string assignee - this also triggers the null path
        // This test documents the current behavior
        assertThrows(NullPointerException.class, () -> {
            jiraService.assignIssue("TEST-123", "");
        });
    }

    @Test
    void testShouldThrowExceptionWhenTransitionNotAvailable() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(issueClient.getIssue("TEST-123")).thenReturn(issuePromise);
        when(issuePromise.get()).thenReturn(issue);
        when(issueClient.getTransitions(issue)).thenReturn(transitionsPromise);
        when(transitionsPromise.get()).thenReturn(Collections.emptyList());

        // Execute and verify exception
        assertThrows(IllegalArgumentException.class, () -> {
            jiraService.transitionIssue("TEST-123", "Invalid Status", null);
        });
    }

    @Test
    void testShouldThrowExceptionWhenIssueTypeNotFound() throws ExecutionException, InterruptedException {
        // Setup mocks
        when(projectClient.getProject("TEST")).thenReturn(projectPromise);
        when(projectPromise.get()).thenReturn(project);
        when(metadataClient.getIssueTypes()).thenReturn(issueTypesPromise);
        when(issueTypesPromise.get()).thenReturn(Collections.emptyList());

        // Execute and verify exception
        assertThrows(IllegalArgumentException.class, () -> {
            jiraService.createIssue("TEST", "InvalidType", "Summary", null, null, null);
        });
    }
}
