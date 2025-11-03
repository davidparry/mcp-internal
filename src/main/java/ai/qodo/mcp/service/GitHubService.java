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
            @ToolParam(description = "The target-branch for the Pull Request if you are unsure do not guess instead the default branch will be used.", required = false) String targetBranch,
            ToolContext toolContext) {
        String params = ParameterStringBuilder.buildParameterString("body", body, "title", title, "sourceBranch",
                                                                    sourceBranch, "targetBranch", targetBranch);
        logger.info("Attempting Pull Request for parameters: {}", params);
        try {
            this.githubMcpConfiguration.ensureRootsInitialized(toolContext);
        } catch (InterruptedException e) {
            logger.error("Could not get of git repo to create the PR, failed to create PR for Title {} sourceBranch " + ":" + " {}, targetBranch {} description/body {} ", title, sourceBranch, targetBranch, body, e);
            return new ToolOutputResult("Error in creating PR for parameters " + params,
                                        "Failed to create PR: " + e.getMessage(), -1, true, "InterruptedException");
        }
        String mergeToBranch = "main";
        ToolOutputResult result;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder
                .setGitDir(findGitDirectory(this.githubMcpConfiguration.getDefaultLocalPath()))
                .readEnvironment()
                .findGitDir()
                .build()) {
            RemoteConfig remoteConfig = new RemoteConfig(repository.getConfig(), "origin");
            URIish uri = remoteConfig.getURIs().getFirst();

            String url = uri.toString();
            String ownerRepo = parseGitHubUrl(url);
            GitHub github = new GitHubBuilder()
                    .withOAuthToken(githubMcpConfiguration.getGithubConfiguration().getApiToken())
                    .build();
            GHRepository ghRepository = github.getRepository(ownerRepo);
            logger.debug("Trying to create PR for git repository {} gitHub Repo {} ", repository, ghRepository);

            if(targetBranch != null && !targetBranch.isBlank()) {
                // Use the provided target branch
                mergeToBranch = targetBranch;
                logger.debug("Using provided target branch: {}", mergeToBranch);
            } else {
                // Get the default branch from GitHub
                mergeToBranch = ghRepository.getDefaultBranch();
                if (mergeToBranch == null || mergeToBranch.isBlank()) {
                    // Fallback to 'main' if default branch is not available
                    mergeToBranch = "main";
                    logger.warn("Could not determine default branch from GitHub, using fallback: {}", mergeToBranch);
                } else {
                    logger.debug("Using repository default branch: {}", mergeToBranch);
                }
            }
            logger.info("Creating PR from '{}' to '{}'", sourceBranch, mergeToBranch);

            GHPullRequest pr = ghRepository.createPullRequest(title, sourceBranch, mergeToBranch, body);
            long prId = pr.getId();
            result = new ToolOutputResult("Pull Request created with ID:" + prId + " for parameters: " + params, "",
                                          0, false, "");

        } catch (IOException ioe) {
            logger.error("Failed to create PR for for Title {} sourceBranch : {}, targetBranch {} description/body " + "{}" + " ", title, sourceBranch, mergeToBranch, body, ioe);
            return new ToolOutputResult("", "Failed to create PR: " + ioe.getMessage(), -1, true, "IOException");
        } catch (URISyntaxException urie) {
            logger.error("Failed to create PR for for Title {} sourceBranch : {}, targetBranch {} description/body " + "{}" + " ", title, sourceBranch, mergeToBranch, body, urie);
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

    /**
     * Searches downward from the given directory to find the .git directory.
     * This method recursively traverses the directory tree from the starting path,
     * looking for a .git directory in the current directory and all subdirectories.
     *
     * @param startPath The starting directory path to begin the search from
     * @return File object pointing to the .git directory, or null if not found
     */
    private File findGitDirectory(String startPath) {
        if (startPath == null || startPath.isEmpty()) {
            logger.warn("Start path is null or empty, cannot search for .git directory");
            return null;
        }

        File startDir = new File(startPath);
        
        // Ensure the starting path exists and is a directory
        if (!startDir.exists()) {
            logger.warn("Start path does not exist: {}", startPath);
            return null;
        }
        
        if (!startDir.isDirectory()) {
            // If it's a file, start from its parent directory
            startDir = startDir.getParentFile();
        }

        // Search recursively downward
        return searchGitDirectoryRecursive(startDir);
    }

    /**
     * Recursively searches for .git directory in the given directory and its subdirectories.
     *
     * @param directory The directory to search in
     * @return File object pointing to the .git directory, or null if not found
     */
    private File searchGitDirectoryRecursive(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }

        // Check if .git exists in current directory
        File gitDir = new File(directory, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            logger.debug("Found .git directory at: {}", gitDir.getAbsolutePath());
            return gitDir;
        }

        // Search in subdirectories
        File[] subdirs = directory.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                // Skip hidden directories except .git (which we're looking for)
                if (subdir.getName().startsWith(".") && !subdir.getName().equals(".git")) {
                    continue;
                }
                
                File found = searchGitDirectoryRecursive(subdir);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Searches downward from the given directory to find the .git directory,
     * returning its fully qualified path as a String.
     *
     * @param startPath The starting directory path to begin the search from
     * @return Fully qualified path to the .git directory, or null if not found
     */
    private String findGitDirectoryPath(String startPath) {
        File gitDir = findGitDirectory(startPath);
        return gitDir != null ? gitDir.getAbsolutePath() : null;
    }


}
