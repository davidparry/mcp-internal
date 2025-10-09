package ai.qodo.mcp;

import ai.qodo.mcp.config.TerminalMcpConfiguration;
import ai.qodo.mcp.pojo.TerminalResult;
import ai.qodo.mcp.service.TerminalService;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class TerminalServiceTest {

    private TerminalService terminalService;
    
    @TempDir
    Path tempDir;
    
    @Mock
    private TerminalMcpConfiguration mcpConfiguration;
    
    @Mock
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock the configuration to return the temp directory
        when(mcpConfiguration.getDefaultLocalPath()).thenReturn(tempDir.toString());
        when(mcpConfiguration.isRootsInitialized()).thenReturn(true);
        when(mcpConfiguration.getRootsLatch()).thenReturn(new CountDownLatch(0));
        when(mcpConfiguration.getDefaultTimeoutSeconds()).thenReturn(30L);
        
        terminalService = new TerminalService(mcpConfiguration);
    }

    @Test
    void testExecuteSimpleCommand() throws InterruptedException {
        TerminalResult result = terminalService.executeCommand("echo Hello World", null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("Hello World"));
    }

    @Test
    void testExecuteCommandWithTimeout() throws InterruptedException {
        // Test with a very short timeout
        TerminalResult result = terminalService.executeCommand("sleep 10", 1, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.errorMessage().contains("Timeout") || result.errorMessage().contains("timeout"));
    }

    @Test
    void testBlockedCommand() throws InterruptedException {
        when(mcpConfiguration.getBlockedCommands()).thenReturn(Set.of("rm"));

        TerminalResult result = terminalService.executeCommand("rm -rf /", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.error().contains("blocked") || result.error().contains("security"));
    }

    @Test
    void testCommandWithNonZeroExit() throws InterruptedException {
        // This command should fail on most systems
        TerminalResult result = terminalService.executeCommand("ls /nonexistent-directory-12345", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
        assertNotEquals(0, result.exitCode());
    }

    @Test
    void testCommandInWorkingDirectory() throws InterruptedException, IOException {
        // Create a test file in the temp directory
        Path testFile = tempDir.resolve("test-file.txt");
        Files.writeString(testFile, "test content");
        
        // List files in the directory
        TerminalResult result = terminalService.executeCommand("ls", null, toolContext);
        
        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.output().contains("test-file.txt"));
    }

    @Test
    void testEmptyCommand() throws InterruptedException {
        TerminalResult result = terminalService.executeCommand("", null, toolContext);
        
        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    void testCommandWithStderr() throws InterruptedException {
        // Command that writes to stderr (works on Unix-like systems)
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "echo Error message 1>&2" : "echo 'Error message' >&2";
        
        TerminalResult result = terminalService.executeCommand(command, null, toolContext);
        
        assertNotNull(result);
        // The command itself succeeds, but writes to stderr
        assertFalse(result.isError());
        assertTrue(result.error().contains("Error message") || result.output().contains("Error message"));
    }

    @Test
    void testMultipleBlockedCommands() throws InterruptedException {
        String[] blockedCommands = {"rm test.txt", "sudo ls", "shutdown now", "chmod 777 file"};
        
        for (String command : blockedCommands) {
            TerminalResult result = terminalService.executeCommand(command, null, toolContext);
            
            assertNotNull(result, "Result should not be null for command: " + command);
            assertTrue(result.isError(), "Command should be blocked: " + command);
        }
    }

    @Test
    void testTerminalResultToString() throws InterruptedException {
        TerminalResult result = terminalService.executeCommand("echo test", null, toolContext);
        
        String resultString = result.toString();
        assertNotNull(resultString);
        assertTrue(resultString.contains("Exit Code"));
    }
}
