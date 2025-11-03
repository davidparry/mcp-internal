package ai.qodo.mcp.service;

import ai.qodo.mcp.config.GithubMcpConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import ai.qodo.mcp.util.ParameterStringBuilder;
import jakarta.annotation.PostConstruct;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

@Service()
@ConditionalOnProperty(name = "mcp.github.enabled", havingValue = "true", matchIfMissing = true)
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);
    private final GithubMcpConfiguration githubMcpConfiguration;

    public GitHubService(GithubMcpConfiguration githubMcpConfiguration) {
        this.githubMcpConfiguration = githubMcpConfiguration;
    }

    @Tool(name = "github_create_pr", description = "Creates a new Pull Request on GitHub for the current repository. "
            + "This tool opens a PR from a source branch (typically a feature branch) to a target branch (typically main/master). "
            + "Use this when you need to propose code changes for review and merging. The tool automatically detects the GitHub "
            + "repository from the local git configuration and requires a valid GitHub API token to be configured. "
            + "Ensure both source and target branches exist on the remote repository before creating the PR.")
    public ToolOutputResult createPullRequest(
            @ToolParam(description = "The body of the Pull Request which is the indepth description of what has been "
                    + "developed") String body,
            @ToolParam(description = "The title of the Pull Request") String title,
            @ToolParam(description = "The source-branch for the Pull Request") String sourceBranch,
            @ToolParam(description = "The target-branch for the Pull Request") String targetBranch,
            ToolContext toolContext) {
        String params = ParameterStringBuilder.buildParameterString("body", body, "title", title, "sourceBranch",
                                                                    sourceBranch, "targetBranch", targetBranch);

        try {
            this.githubMcpConfiguration.ensureRootsInitialized(toolContext);
        } catch (InterruptedException e) {
            logger.error("Could not get of git repo to create the PR, failed to create PR for Title {} sourceBranch " + ":" + " {}, targetBranch {} description/body {} ", title, sourceBranch, targetBranch, body, e);
            return new ToolOutputResult("Error in creating PR for parameters " + params,
                                        "Failed to create PR: " + e.getMessage(), -1, true, "InterruptedException");
        }

        ToolOutputResult result;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder
                .setGitDir(new File(this.githubMcpConfiguration.getDefaultLocalPath() + "/.git"))
                .readEnvironment()
                .findGitDir()
                .build()) {
            logger.debug("Trying to create PR for repository {} ", repository.getDirectory().getName());
            RemoteConfig remoteConfig = new RemoteConfig(repository.getConfig(), "origin");
            URIish uri = remoteConfig.getURIs().getFirst();

            String url = uri.toString();
            String ownerRepo = parseGitHubUrl(url);
            GitHub github = new GitHubBuilder()
                    .withOAuthToken(githubMcpConfiguration.getGithubConfiguration().getApiToken())
                    .build();

            GHRepository ghRepository = github.getRepository(ownerRepo);

            GHPullRequest pr = ghRepository.createPullRequest(title, sourceBranch, targetBranch, body);
            long prId = pr.getId();
            result = new ToolOutputResult("Pull Request created with ID:" + prId + " for parameters: " + params, "",
                                          0, false, "");

        } catch (IOException ioe) {
            logger.error("Failed to create PR for for Title {} sourceBranch : {}, targetBranch {} description/body " + "{}" + " ", title, sourceBranch, targetBranch, body, ioe);
            return new ToolOutputResult("", "Failed to create PR: " + ioe.getMessage(), -1, true, "IOException");
        } catch (URISyntaxException urie) {
            logger.error("Failed to create PR for for Title {} sourceBranch : {}, targetBranch {} description/body " + "{}" + " ", title, sourceBranch, targetBranch, body, urie);
            return new ToolOutputResult("", "Failed to create PR: " + urie.getMessage(), -1, true,
                                        "URISyntaxException");
        }

        return result;
    }


    private String parseGitHubUrl(String url) {
        // Handle SSH format: git@github.com:owner/repo.git
        if (url.startsWith("git@github.com:")) {
            url = url.replace("git@github.com:", "");
        }
        // Handle HTTPS format: https://github.com/owner/repo.git
        else if (url.contains("github.com/")) {
            url = url.substring(url.indexOf("github.com/") + 11);
        }

        // Remove .git suffix if present
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }

        return url; // Returns "owner/repo-name"
    }


}
