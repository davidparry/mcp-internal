package ai.qodo.mcp.service;

import ai.qodo.mcp.config.JiraConfiguration;
import ai.qodo.mcp.config.JiraMcpConfiguration;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service()
@ConditionalOnProperty(name = "mcp.jira.enabled", havingValue = "true", matchIfMissing = true)
public class JiraService {
    private static final Logger logger = LoggerFactory.getLogger(JiraService.class);
    private final JiraMcpConfiguration mcpConfiguration;
    private JiraRestClient jiraRestClient;

    public JiraService(JiraMcpConfiguration jiraMcpConfiguration) {
        this.mcpConfiguration = jiraMcpConfiguration;
    }

    @PostConstruct
    public void init() {
        JiraConfiguration jiraConfiguration = mcpConfiguration.getJiraConfiguration();
        if (mcpConfiguration.isConfigurationValid()) {
            try {
                URI jiraServerUri = new URI(jiraConfiguration.getSiteUrl());
                AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();

                // Create authentication handler with email and API token
                BasicHttpAuthenticationHandler authHandler =
                        new BasicHttpAuthenticationHandler(jiraConfiguration.getEmail(),
                                                           jiraConfiguration.getApiToken());

                this.jiraRestClient = factory.create(jiraServerUri, authHandler);
                logger.info("Jira REST client initialized successfully for: {}", jiraConfiguration);
            } catch (URISyntaxException e) {
                logger.error("Invalid Jira site URL: {}", jiraConfiguration.getSiteUrl(), e);
            } catch (Exception er) {
                logger.error("");
            }
        } else {
            logger.warn("Jira configuration is invalid. Service will not be initialized. {}", jiraConfiguration);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (jiraRestClient != null) {
            try {
                jiraRestClient.close();
                logger.debug("Jira REST client closed successfully");
            } catch (Exception e) {
                logger.debug("Error closing Jira REST client", e);
            }
        }
    }

    /**
     * Ensures the Jira client is initialized before executing operations.
     */
    private void ensureClientInitialized() {
        if (jiraRestClient == null) {
            throw new IllegalStateException("Jira REST client is not initialized. Please check your configuration.");
        }
    }

    @Tool(name = "jira_get_issue", description = "Retrieves detailed information about a specific Jira issue. " +
            "Returns issue details including summary, description, status, assignee, reporter, priority, " + "issue " +
            "type, and all custom fields. Use this to get comprehensive information about a single issue.")
    public String getIssue(
            @ToolParam(description = "The issue key (e.g., 'PROJ-123')") String issueKey) throws ExecutionException,
            InterruptedException {
        ensureClientInitialized();

        Issue issue = jiraRestClient.getIssueClient().getIssue(issueKey).get();

        return formatIssueDetails(issue);
    }

    @Tool(name = "jira_search_issues", description = "Searches for Jira issues using JQL (Jira Query Language). " +
            "Returns a list of issues matching the search criteria. Use this to find issues based on various " +
            "filters like project, status, assignee, labels, etc. Example JQL: 'project = PROJ AND status = Open'")
    public String searchIssues(@ToolParam(description = "JQL query string for searching issues") String jql,
                               @ToolParam(description = "Maximum number of results to return (default: 50)") Integer maxResults) throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        int limit = maxResults != null ? maxResults : 50;

        SearchResult searchResult = jiraRestClient.getSearchClient().searchJql(jql, limit, 0, null).get();

        StringBuilder result = new StringBuilder();
        result.append("Search Results (").append(searchResult.getTotal()).append(" total issues found):\n\n");

        for (Issue issue : searchResult.getIssues()) {
            result.append(formatIssueSummary(issue)).append("\n");
        }

        return result.toString();
    }

    @Tool(name = "jira_create_issue", description = "Creates a new Jira issue in the specified project. " + "Requires" +
            " project key, issue type, and summary at minimum. Can optionally include description, " + "priority, " +
            "labels, and other fields. Use this to create new tasks, bugs, stories, or other issue types.")
    public String createIssue(@ToolParam(description = "Project key where the issue will be created") String projectKey,
                              @ToolParam(description = "Issue type (e.g., 'Bug', 'Task', 'Story')") String issueType,
                              @ToolParam(description = "Issue summary/title") String summary,
                              @ToolParam(description = "Issue description (optional)") String description,
                              @ToolParam(description = "Priority (e.g., 'High', 'Medium', 'Low') (optional)") String priority,
                              @ToolParam(description = "Comma-separated list of labels (optional)") String labels) throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        // Get project
        Project project = jiraRestClient.getProjectClient().getProject(projectKey).get();

        // Find issue type
        IssueType type = StreamSupport
                .stream(jiraRestClient.getMetadataClient().getIssueTypes().get().spliterator(), false)
                .filter(it -> it.getName().equalsIgnoreCase(issueType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Issue type not found: " + issueType));

        // Build issue input
        IssueInputBuilder issueBuilder = new IssueInputBuilder()
                .setProjectKey(projectKey)
                .setIssueType(type)
                .setSummary(summary);

        if (description != null && !description.isEmpty()) {
            issueBuilder.setDescription(description);
        }

        if (priority != null && !priority.isEmpty()) {
            Priority prio = StreamSupport
                    .stream(jiraRestClient.getMetadataClient().getPriorities().get().spliterator(), false)
                    .filter(p -> p.getName().equalsIgnoreCase(priority))
                    .findFirst()
                    .orElse(null);
            if (prio != null) {
                issueBuilder.setPriority(prio);
            }
        }

        if (labels != null && !labels.isEmpty()) {
            Set<String> labelSet = new HashSet<>(Arrays.asList(labels.split(",")));
            issueBuilder.setFieldValue("labels", labelSet);
        }

        IssueInput issueInput = issueBuilder.build();
        BasicIssue createdIssue = jiraRestClient.getIssueClient().createIssue(issueInput).get();

        return "Issue created successfully: " + createdIssue.getKey() + "\nURL: " + mcpConfiguration.getJiraConfiguration().getSiteUrl() +
                "/browse/" + createdIssue.getKey();
    }

    @Tool(name = "jira_update_issue", description = "Updates an existing Jira issue. Can update summary, " +
            "description, priority, labels, and other fields. Use this to modify issue details without " + "changing " +
            "its status.")
    public String updateIssue(@ToolParam(description = "The issue key to update (e.g., 'PROJ-123')") String issueKey,
                              @ToolParam(description = "New summary/title (optional)") String summary,
                              @ToolParam(description = "New description (optional)") String description,
                              @ToolParam(description = "New priority (optional)") String priority,
                              @ToolParam(description = "Comma-separated list of labels to set (optional)") String labels) throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Issue issue = jiraRestClient.getIssueClient().getIssue(issueKey).get();

        IssueInputBuilder updateBuilder = new IssueInputBuilder();
        boolean hasUpdates = false;

        if (summary != null && !summary.isEmpty()) {
            updateBuilder.setSummary(summary);
            hasUpdates = true;
        }

        if (description != null && !description.isEmpty()) {
            updateBuilder.setDescription(description);
            hasUpdates = true;
        }

        if (priority != null && !priority.isEmpty()) {
            Priority prio = StreamSupport
                    .stream(jiraRestClient.getMetadataClient().getPriorities().get().spliterator(), false)
                    .filter(p -> p.getName().equalsIgnoreCase(priority))
                    .findFirst()
                    .orElse(null);
            if (prio != null) {
                updateBuilder.setPriority(prio);
                hasUpdates = true;
            }
        }

        if (labels != null && !labels.isEmpty()) {
            Set<String> labelSet = new HashSet<>(Arrays.asList(labels.split(",")));
            updateBuilder.setFieldValue("labels", labelSet);
            hasUpdates = true;
        }

        if (!hasUpdates) {
            return "No updates provided for issue: " + issueKey;
        }

        IssueInput updateInput = updateBuilder.build();
        jiraRestClient.getIssueClient().updateIssue(issueKey, updateInput).get();

        return "Issue " + issueKey + " updated successfully";
    }

    @Tool(name = "jira_transition_issue", description = "Transitions a Jira issue to a different status. " + "Use " +
            "this to move issues through workflow states like 'To Do', 'In Progress', 'Done', etc. " + "The available" +
            " transitions depend on the issue's current status and workflow configuration.")
    public String transitionIssue(@ToolParam(description = "The issue key (e.g., 'PROJ-123')") String issueKey,
                                  @ToolParam(description = "Target status name (e.g., 'In Progress', 'Done')") String targetStatus,
                                  @ToolParam(description = "Optional comment to add with the transition") String comment) throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Issue issue = jiraRestClient.getIssueClient().getIssue(issueKey).get();

        Iterable<Transition> transitions = jiraRestClient.getIssueClient().getTransitions(issue).get();

        Transition targetTransition = StreamSupport
                .stream(transitions.spliterator(), false)
                .filter(t -> t.getName().equalsIgnoreCase(targetStatus))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Transition to '" + targetStatus + "' not available " +
                                                                        "from current status"));

        TransitionInput transitionInput = new TransitionInput(targetTransition.getId());

        if (comment != null && !comment.isEmpty()) {
            transitionInput = new TransitionInput(targetTransition.getId(), Comment.valueOf(comment));
        }

        jiraRestClient.getIssueClient().transition(issue, transitionInput).get();

        return "Issue " + issueKey + " transitioned to '" + targetStatus + "' successfully";
    }

    @Tool(name = "jira_add_comment", description = "Adds a comment to a Jira issue. Use this to provide " + "updates," +
            " ask questions, or document information related to an issue.")
    public String addComment(@ToolParam(description = "The issue key (e.g., 'PROJ-123')") String issueKey,
                             @ToolParam(description = "Comment text to add") String commentText) throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Issue issue = jiraRestClient.getIssueClient().getIssue(issueKey).get();

        Comment comment = Comment.valueOf(commentText);

        jiraRestClient.getIssueClient().addComment(issue.getCommentsUri(), comment).get();

        return "Comment added to issue " + issueKey + " successfully";
    }

    @Tool(name = "jira_assign_issue", description = "Assigns or reassigns a Jira issue to a specific user. " + "Use " +
            "this to change the assignee of an issue. Pass null or empty string to unassign.")
    public String assignIssue(@ToolParam(description = "The issue key (e.g., 'PROJ-123')") String issueKey,
                              @ToolParam(description = "Username or email of the assignee (null to unassign)") String assignee) throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Issue issue = jiraRestClient.getIssueClient().getIssue(issueKey).get();

        User user = null;
        if (assignee != null && !assignee.isEmpty()) {
            // Try to find the user
            user = jiraRestClient.getUserClient().getUser(assignee).get();
        }

        IssueInputBuilder updateBuilder = new IssueInputBuilder();
        updateBuilder.setAssignee(user);

        IssueInput updateInput = updateBuilder.build();
        jiraRestClient.getIssueClient().updateIssue(issueKey, updateInput).get();

        String message = user != null ? "Issue " + issueKey + " assigned to " + user.getDisplayName() :
                "Issue " + issueKey + " unassigned";

        return message;
    }

    @Tool(name = "jira_get_projects", description = "Lists all accessible Jira projects. Returns project keys, " +
            "names, and descriptions. Use this to discover available projects before creating or searching issues.")
    public String getProjects() throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Iterable<BasicProject> projects = jiraRestClient.getProjectClient().getAllProjects().get();

        StringBuilder result = new StringBuilder("Available Projects:\n\n");

        for (BasicProject project : projects) {
            result
                    .append("Key: ")
                    .append(project.getKey())
                    .append("\nName: ")
                    .append(project.getName())
                    .append("\nURL: ")
                    .append(project.getSelf())
                    .append("\n\n");
        }

        return result.toString();
    }

    @Tool(name = "jira_get_issue_types", description = "Lists all available issue types in the Jira instance. " +
            "Returns issue type names and descriptions. Use this to see what types of issues can be created.")
    public String getIssueTypes() throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Iterable<IssueType> issueTypes = jiraRestClient.getMetadataClient().getIssueTypes().get();

        StringBuilder result = new StringBuilder("Available Issue Types:\n\n");

        for (IssueType type : issueTypes) {
            result
                    .append("Name: ")
                    .append(type.getName())
                    .append("\nDescription: ")
                    .append(type.getDescription())
                    .append("\nSubtask: ")
                    .append(type.isSubtask())
                    .append("\n\n");
        }

        return result.toString();
    }

    @Tool(name = "jira_get_priorities", description = "Lists all available priority levels in the Jira instance. " +
            "Use this to see what priority values can be assigned to issues.")
    public String getPriorities() throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Iterable<Priority> priorities = jiraRestClient.getMetadataClient().getPriorities().get();

        StringBuilder result = new StringBuilder("Available Priorities:\n\n");

        for (Priority priority : priorities) {
            result
                    .append("Name: ")
                    .append(priority.getName())
                    .append("\nDescription: ")
                    .append(priority.getDescription())
                    .append("\n\n");
        }

        return result.toString();
    }

    @Tool(name = "jira_get_statuses", description = "Lists all available statuses in the Jira instance. " + "Use this" +
            " to see what status values issues can have.")
    public String getStatuses() throws ExecutionException, InterruptedException {
        ensureClientInitialized();

        Iterable<Status> statuses = jiraRestClient.getMetadataClient().getStatuses().get();

        StringBuilder result = new StringBuilder("Available Statuses:\n\n");

        for (Status status : statuses) {
            result
                    .append("Name: ")
                    .append(status.getName())
                    .append("\nDescription: ")
                    .append(status.getDescription())
                    .append("\n\n");
        }

        return result.toString();
    }

    /**
     * Formats detailed issue information for display.
     */
    private String formatIssueDetails(Issue issue) {
        StringBuilder result = new StringBuilder();

        result.append("Issue: ").append(issue.getKey()).append("\n");
        result.append("Summary: ").append(issue.getSummary()).append("\n");
        result.append("Type: ").append(issue.getIssueType().getName()).append("\n");
        result.append("Status: ").append(issue.getStatus().getName()).append("\n");
        result
                .append("Priority: ")
                .append(issue.getPriority() != null ? issue.getPriority().getName() : "None")
                .append("\n");
        result
                .append("Reporter: ")
                .append(issue.getReporter() != null ? issue.getReporter().getDisplayName() : "Unknown")
                .append("\n");
        result
                .append("Assignee: ")
                .append(issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "Unassigned")
                .append("\n");
        result.append("Created: ").append(formatDateTime(issue.getCreationDate())).append("\n");
        result.append("Updated: ").append(formatDateTime(issue.getUpdateDate())).append("\n");

        if (issue.getLabels() != null && !issue.getLabels().isEmpty()) {
            result.append("Labels: ").append(String.join(", ", issue.getLabels())).append("\n");
        }

        if (issue.getDescription() != null && !issue.getDescription().isEmpty()) {
            result.append("\nDescription:\n").append(issue.getDescription()).append("\n");
        }

        if (issue.getComments() != null) {
            List<Comment> comments = StreamSupport
                    .stream(issue.getComments().spliterator(), false)
                    .collect(Collectors.toList());
            if (!comments.isEmpty()) {
                result.append("\nComments (").append(comments.size()).append("):\n");
                for (Comment comment : comments) {
                    result
                            .append("  - ")
                            .append(comment.getAuthor().getDisplayName())
                            .append(" (")
                            .append(formatDateTime(comment.getCreationDate()))
                            .append("): ")
                            .append(comment.getBody())
                            .append("\n");
                }
            }
        }

        result.append("\nURL: ").append(mcpConfiguration.getJiraConfiguration().getSiteUrl()).append("/browse/").append(issue.getKey());

        return result.toString();
    }

    /**
     * Formats issue summary for list display.
     */
    private String formatIssueSummary(Issue issue) {
        return String.format("[%s] %s - %s (%s) - Assignee: %s", issue.getKey(), issue.getSummary(), issue
                .getStatus()
                .getName(), issue.getPriority() != null ? issue
                .getPriority()
                .getName() : "None", issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "Unassigned");
    }

    /**
     * Formats date/time for display.
     */
    private String formatDateTime(org.joda.time.DateTime dateTime) {
        if (dateTime == null) {
            return "N/A";
        }
        return dateTime.toString("yyyy-MM-dd HH:mm:ss");
    }
}
