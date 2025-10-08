package ai.qodo.mcp.config;

import ai.qodo.mcp.service.GitService;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GitServiceIntegrationTest {

    @Autowired
    private GitService gitService;

    @Autowired
    private GitMcpConfiguration gitMcpConfiguration;

    @TempDir
    Path tempDir;

    private String clonedRepoPath;

    @BeforeEach
    void setUp() {
        // Set up roots with the temp directory as the base path
        McpSchema.Root root = new McpSchema.Root(
                tempDir.toUri().toString(),
                "Test Root"
        );

        gitMcpConfiguration.initRootPath(List.of(root));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up cloned repository if it exists
        if (clonedRepoPath != null) {
            File clonedRepo = new File(clonedRepoPath);
            if (clonedRepo.exists()) {
                deleteDirectory(clonedRepo);
            }
        }
    }

    @Test
    void testCloneRepositoryFromGitHub() throws GitAPIException, InterruptedException {
        // Clone a public repository to avoid SSH authentication issues
        // Using Spring PetClinic as a well-known public repository
        String remoteUrl = "https://github.com/spring-projects/spring-petclinic.git";
        
        String result = gitService.cloneRepository(remoteUrl);
        
        // Verify the clone was successful
        assertNotNull(result);
        assertTrue(result.contains("Repository cloned successfully"));
        assertTrue(result.contains("spring-petclinic"));
        
        // Extract the cloned repository path for cleanup
        clonedRepoPath = result.substring(result.indexOf(":") + 1).trim();
        
        // Verify the repository directory exists
        File repoDir = new File(clonedRepoPath);
        assertTrue(repoDir.exists(), "Cloned repository directory should exist");
        assertTrue(repoDir.isDirectory(), "Cloned repository should be a directory");
        
        // Verify .git directory exists
        File gitDir = new File(repoDir, ".git");
        assertTrue(gitDir.exists(), ".git directory should exist");
        assertTrue(gitDir.isDirectory(), ".git directory should be a directory");
    }

    @Test
    void testGetStatusAfterClone() throws GitAPIException, InterruptedException, IOException {
        // Clone the repository first
        String remoteUrl = "https://github.com/davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // Get the status of the cloned repository
        String status = gitService.getStatus(clonedRepoPath);
        
        // Verify status output
        assertNotNull(status);
        assertTrue(status.contains("Git Status"));
        assertTrue(status.contains("Branch:"));
        assertTrue(status.contains("Working tree clean") || status.contains("Untracked") || status.contains("Modified"));
    }

    @Test
    void testGetLogAfterClone() throws GitAPIException, InterruptedException, IOException {
        // Clone the repository first
        String remoteUrl = "https://github.com/davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // Get the log of the cloned repository
        String log = gitService.getLog(clonedRepoPath, 5);
        
        // Verify log output
        assertNotNull(log);
        assertTrue(log.contains("Git Log"));
        assertTrue(log.contains("Commit:"));
        assertTrue(log.contains("Author:"));
    }

    @Test
    void testListBranchesAfterClone() throws GitAPIException, InterruptedException, IOException {
        // Clone the repository first
        String remoteUrl = "https://github.com/davidparry/profile-octo-robot.git";
        String cloneResult = gitService.cloneRepository(remoteUrl);
        
        // Extract the cloned repository path
        clonedRepoPath = cloneResult.substring(cloneResult.indexOf(":") + 1).trim();
        
        // List branches of the cloned repository
        String branches = gitService.listBranches(clonedRepoPath);
        
        // Verify branches output
        assertNotNull(branches);
        assertTrue(branches.contains("Branches:"));
        assertTrue(branches.contains("master") || branches.contains("main") || branches.contains("trunk"));
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        Files.delete(file.toPath());
                    }
                }
            }
            Files.delete(directory.toPath());
        }
    }
}
