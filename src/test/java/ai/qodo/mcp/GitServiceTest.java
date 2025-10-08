package ai.qodo.mcp;

import ai.qodo.mcp.config.GitMcpConfiguration;
import ai.qodo.mcp.service.GitService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    private GitService gitService;
    
    @TempDir
    Path tempDir;
    
    private Path repoPath;

    @Mock
    private GitMcpConfiguration mcpConfiguration;

    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        gitService = new GitService(mcpConfiguration);
        
        // Create a test repository
        repoPath = tempDir.resolve("test-repo");
        Files.createDirectories(repoPath);
        
        Git git = Git.init().setDirectory(repoPath.toFile()).call();
        
        // Create initial commit
        Path testFile = repoPath.resolve("test.txt");
        Files.writeString(testFile, "Hello World");
        
        git.add().addFilepattern("test.txt").call();
        git.commit().setMessage("Initial commit").call();
        
        git.close();
    }

    @Test
    void testGetStatus() throws IOException, GitAPIException {
        String status = gitService.getStatus(repoPath.toString());
        
        assertNotNull(status);
        assertTrue(status.contains("Git Status"));
        assertTrue(status.contains("Branch:"));
    }

    @Test
    void testGetLog() throws IOException, GitAPIException {
        String log = gitService.getLog(repoPath.toString(), 10);
        
        assertNotNull(log);
        assertTrue(log.contains("Git Log"));
        assertTrue(log.contains("Initial commit"));
    }

    @Test
    void testListBranches() throws IOException, GitAPIException {
        String branches = gitService.listBranches(repoPath.toString());
        
        assertNotNull(branches);
        assertTrue(branches.contains("Branches:"));
        assertTrue(branches.contains("master") || branches.contains("main"));
    }

    @Test
    void testCreateBranch() throws IOException, GitAPIException {
        String result = gitService.createBranch(repoPath.toString(), "feature-test");
        
        assertNotNull(result);
        assertTrue(result.contains("created successfully"));
        
        // Verify branch was created
        String branches = gitService.listBranches(repoPath.toString());
        assertTrue(branches.contains("feature-test"));
    }

    @Test
    void testCommit() throws IOException, GitAPIException {
        // Create a new file
        Path newFile = repoPath.resolve("new-file.txt");
        Files.writeString(newFile, "New content");
        
        String result = gitService.commit(repoPath.toString(), "Add new file", null);
        
        assertNotNull(result);
        assertTrue(result.contains("Committed"));
        assertTrue(result.contains("Add new file"));
    }

    @Test
    void testStatusWithChanges() throws IOException, GitAPIException {
        // Modify existing file
        Path testFile = repoPath.resolve("test.txt");
        Files.writeString(testFile, "Modified content");
        
        String status = gitService.getStatus(repoPath.toString());
        
        assertNotNull(status);
        assertTrue(status.contains("Modified") || status.contains("test.txt"));
    }
}
