package ai.qodo.mcp.service;

import ai.qodo.mcp.config.GitMcpConfiguration;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service()
@Scope("prototype")
public class GitService {

    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
    private final GitMcpConfiguration mcpConfiguration;

    public GitService(GitMcpConfiguration gitMcpConfiguration) {
        this.mcpConfiguration = gitMcpConfiguration;
    }

    /**
     * Checks if the client has root capabilities and waits for roots to be initialized.
     * This method blocks until roots are received or timeout occurs.
     */
    private void ensureRootsInitialized(ToolContext toolContext) throws InterruptedException {
        if (!this.mcpConfiguration.isRootsInitialized()) {
            Optional<McpSyncServerExchange> exchange = McpToolUtils.getMcpExchange(toolContext);
            if (exchange.isPresent()) {
                logger.info("Got exchange from tool context, requesting roots");
                mcpConfiguration.setServerExchange(exchange.get());
                mcpConfiguration.requestRootsFromClient();
            }


            logger.info("Waiting for roots to be initialized...");
            boolean received = this.mcpConfiguration.getRootsLatch().await(90, TimeUnit.SECONDS);
            if (!received) {
                logger.warn("Timeout waiting for roots. Proceeding without default path.");
            }
        }
    }


    @Tool(name = "git_clone_repository", description = "Clones a remote Git repository to the local filesystem. " +
            "Accepts both HTTPS and SSH URLs (e.g., 'https://github.com/user/repo.git' or 'git@github.com:user/repo" +
            ".git'). The repository will be cloned to a directory named after the repository in the configured " +
            "default path. Supports SSH authentication using standard SSH keys from ~/.ssh/. Use this to create a " +
            "local copy of a remote repository.")
    public String cloneRepository(
            @ToolParam(description = "The remote url for the repository to clone") String remoteUrl,
            ToolContext toolContext) throws GitAPIException, InterruptedException {
        // Ensure roots are initialized before proceeding
        ensureRootsInitialized(toolContext);

        String repoName = extractRepoName(remoteUrl);
        String targetPath = this.mcpConfiguration.getDefaultLocalPath() + File.separator + repoName;
        logger.info("Using path from roots: {}", targetPath);

        // Configure SSH transport with specific key file
        TransportConfigCallback transportConfigCallback = createSshTransportConfig();

        Git git = Git
                .cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(new File(targetPath))
                .setTransportConfigCallback(transportConfigCallback)
                .call();

        git.close();
        return "Repository cloned successfully to: " + targetPath;
    }

    /**
     * Creates a transport configuration callback that configures SSH to use a specific private key.
     * By default, it looks for id_ed25519 in ~/.ssh/, but can be configured to use other keys.
     */
    private TransportConfigCallback createSshTransportConfig() {
        // Get the SSH directory and home directory
        File homeDir = FS.DETECTED.userHome();
        File sshDir = new File(homeDir, ".ssh");

        logger.info("Configuring SSH transport with SSH directory: {}", sshDir.getAbsolutePath());

        return transport -> {
            if (transport instanceof SshTransport sshTransport) {

                // Create a custom SSH session factory with the SSH directory
                // This will automatically look for standard key files (id_rsa, id_ed25519, etc.)
                SshdSessionFactory sshSessionFactory = new SshdSessionFactoryBuilder()
                        .setPreferredAuthentications("publickey")
                        .setSshDirectory(sshDir)
                        .setHomeDirectory(homeDir)
                        .build(null);

                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        };
    }

    @Tool(name = "get_status", description = "Get the status of the Git Repository based on the repository path")
    public String getStatus(String repositoryPath) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Status status = git.status().call();

            StringBuilder result = new StringBuilder();
            result.append("Git Status:\n");
            result.append("Branch: ").append(git.getRepository().getBranch()).append("\n\n");

            if (!status.getAdded().isEmpty()) {
                result.append("Added:\n");
                status.getAdded().forEach(file -> result.append("  + ").append(file).append("\n"));
            }

            if (!status.getModified().isEmpty()) {
                result.append("Modified:\n");
                status.getModified().forEach(file -> result.append("  M ").append(file).append("\n"));
            }

            if (!status.getRemoved().isEmpty()) {
                result.append("Removed:\n");
                status.getRemoved().forEach(file -> result.append("  - ").append(file).append("\n"));
            }

            if (!status.getUntracked().isEmpty()) {
                result.append("Untracked:\n");
                status.getUntracked().forEach(file -> result.append("  ? ").append(file).append("\n"));
            }

            if (status.isClean()) {
                result.append("Working tree clean\n");
            }

            return result.toString();
        }
    }

    @Tool(name = "git_log", description = "Retrieves the commit history for a Git repository. Returns a formatted log" +
            " showing commit hashes, authors, dates, and messages. Use this to view the history of changes in the " +
            "repository.")
    public String getLog(String repositoryPath, int maxCount) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Iterable<RevCommit> logs = git.log().setMaxCount(maxCount).call();

            StringBuilder result = new StringBuilder();
            result.append("Git Log:\n\n");

            for (RevCommit commit : logs) {
                result.append("Commit: ").append(commit.getName()).append("\n");
                result
                        .append("Author: ")
                        .append(commit.getAuthorIdent().getName())
                        .append(" <")
                        .append(commit.getAuthorIdent().getEmailAddress())
                        .append(">\n");
                result.append("Date: ").append(commit.getAuthorIdent().getWhen()).append("\n");
                result.append("Message: ").append(commit.getFullMessage()).append("\n\n");
            }

            return result.toString();
        }
    }

    @Tool(name = "git_branches", description = "Lists all branches in the repository. Shows the current branch with " +
            "an asterisk (*) and all other available branches. Use this to see what branches exist in the repository.")
    public String listBranches(String repositoryPath) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            List<Ref> branches = git.branchList().call();

            StringBuilder result = new StringBuilder();
            result.append("Branches:\n");

            String currentBranch = git.getRepository().getBranch();

            for (Ref branch : branches) {
                String branchName = branch.getName().replace("refs/heads/", "");
                if (branchName.equals(currentBranch)) {
                    result.append("* ").append(branchName).append("\n");
                } else {
                    result.append("  ").append(branchName).append("\n");
                }
            }

            return result.toString();
        }
    }

    @Tool(name = "git_create_branch", description = "Creates a new branch in the repository with the specified name. " +
            "The new branch will be created from the current HEAD position. Use this when you need to start working " +
            "on a new feature or fix.")
    public String createBranch(String repositoryPath, String branchName) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            git.branchCreate().setName(branchName).call();

            return "Branch '" + branchName + "' created successfully";
        }
    }

    @Tool(name = "git_checkout", description = "Switches the working directory to the specified branch. Use this to " +
            "change between different branches in the repository to work on different features or versions.")
    public String checkoutBranch(String repositoryPath, String branchName) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            git.checkout().setName(branchName).call();

            return "Switched to branch '" + branchName + "'";
        }
    }

    @Tool(name = "git_commit", description = "Creates a new commit with the specified message and files. If no files " +
            "are specified, all changes in the working directory will be staged and committed. Use this to save your " +
            "changes to the repository history.")
    public String commit(String repositoryPath, String message,
                         List<String> files) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            // Add files
            if (files == null || files.isEmpty()) {
                git.add().addFilepattern(".").call();
            } else {
                for (String file : files) {
                    git.add().addFilepattern(file).call();
                }
            }

            // Commit
            RevCommit commit = git.commit().setMessage(message).call();

            return "Committed: " + commit.getName() + "\n" + message;
        }
    }

    @Tool(name = "git_push", description = "Pushes local commits to a remote repository. Uploads the specified branch" +
            " to the remote repository (e.g., 'origin'). Use this to share your local commits with others or backup " +
            "your work to a remote server.")
    public String push(String repositoryPath, String remote, String branch) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            git.push().setRemote(remote).add(branch).call();

            return "Pushed to " + remote + "/" + branch;
        }
    }

    @Tool(name = "git_pull", description = "Fetches and merges changes from the remote repository into the current " +
            "branch. Use this to update your local repository with the latest changes from the remote server.")
    public String pull(String repositoryPath) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            git.pull().call();

            return "Pull completed successfully";
        }
    }

    @Tool(name = "git_diff", description = "Shows the differences between two commits. If oldCommit and newCommit are" +
            " not specified, it defaults to comparing HEAD^ with HEAD (the last commit). Returns a detailed diff " +
            "showing what changed between the two commits. Use this to review changes between different versions of " +
            "the code.")
    public String diff(String repositoryPath, String oldCommit, String newCommit) throws IOException, GitAPIException {
        try (Git git = openRepository(repositoryPath)) {
            Repository repository = git.getRepository();

            ObjectId oldId = repository.resolve(oldCommit != null ? oldCommit : "HEAD^");
            ObjectId newId = repository.resolve(newCommit != null ? newCommit : "HEAD");

            try (ObjectReader reader = repository.newObjectReader()) {
                AbstractTreeIterator oldTreeIter = prepareTreeParser(repository, oldId);
                AbstractTreeIterator newTreeIter = prepareTreeParser(repository, newId);

                List<DiffEntry> diffs = git.diff().setOldTree(oldTreeIter).setNewTree(newTreeIter).call();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DiffFormatter formatter = new DiffFormatter(out)) {
                    formatter.setRepository(repository);

                    for (DiffEntry diff : diffs) {
                        formatter.format(diff);
                    }
                }

                return out.toString();
            }
        }
    }

    private Git openRepository(String repositoryPath) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
                .setGitDir(new File(repositoryPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();

        return new Git(repository);
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();
            return treeParser;
        }
    }

    /**
     * Extracts the repository name from a Git URL.
     * Examples:
     * https://github.com/user/repo.git -> repo
     * https://github.com/user/repo -> repo
     * git@github.com:user/repo.git -> repo
     */
    private String extractRepoName(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isEmpty()) {
            return "repository";
        }

        // Remove .git suffix if present
        String url = remoteUrl.endsWith(".git") ? remoteUrl.substring(0, remoteUrl.length() - 4) : remoteUrl;

        // Extract the last part of the path
        String[] parts = url.split("[/:]");
        return parts[parts.length - 1];
    }
}
