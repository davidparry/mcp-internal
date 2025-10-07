# Git MCP Server

A Model Context Protocol (MCP) server implementation that provides Git operations through JGit, enabling AI assistants and other MCP clients to interact with Git repositories.

## Overview

This MCP server exposes Git functionality through a Spring Boot application using:
- **Spring AI MCP Server** (`spring-ai-starter-mcp-server`) for the MCP protocol implementation
- **JGit** (`org.eclipse.jgit:7.3.0.202506031305-r`) for Git operations

## Features

The server provides comprehensive Git operations through the `GitService` with **MCP Roots Integration**:

### MCP Roots Support

The Git MCP Server intelligently integrates with the MCP protocol's roots capability:

- **Automatic Root Detection**: Checks if the MCP client supports the roots capability
- **Blocking Initialization**: Waits for roots to be provided before executing any Git operations
- **Default Path Resolution**: Uses the first root from the client as the default local path for cloning repositories
- **Timeout Protection**: Has a 30-second timeout to prevent indefinite blocking
- **Fallback Support**: If no roots are provided, operations can still proceed with explicit paths

When a client provides roots, the first root's URI is used as the base directory for cloning repositories. If you clone without specifying a local path, the repository will be cloned into `<first-root>/<repo-name>`.

### Available Operations

1. **Clone Repository** - Clone a Git repository from a remote URL
2. **Get Status** - Show working tree status (modified, added, removed, untracked files)
3. **Get Log** - View commit history
4. **List Branches** - List all branches in the repository
5. **Create Branch** - Create a new branch
6. **Checkout Branch** - Switch to a different branch
7. **Commit Changes** - Stage and commit changes
8. **Push** - Push commits to a remote repository
9. **Pull** - Pull changes from a remote repository
10. **Diff** - Show differences between commits

## Architecture

The project is structured as follows:

```
src/main/java/ai/qodo/mcp/
├── GitApplication.java              # Spring Boot application entry point
├── config/
│   └── GitMcpConfiguration.java     # MCP server configuration
└── service/
    └── GitService.java              # Git operations service using JGit
```

### Components

1. **GitApplication**: Main Spring Boot application class
2. **GitMcpConfiguration**: Configuration class that sets up the GitService bean
3. **GitService**: Core service that wraps JGit operations and provides a clean API for Git commands

## Building and Running

### Prerequisites
- Java 21 or higher
- Gradle

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew bootRun
```

Or use the provided script:
```bash
./run-mcp-server.sh
```

The server will start and communicate via STDIO (standard input/output) as per the MCP protocol specification.

## Configuration

The server is configured in `application.properties`:

```properties
spring.application.name=git

# MCP Server Configuration
spring.ai.mcp.server.transport=stdio
spring.ai.mcp.server.enabled=true

# Logging
logging.level.org.springframework.ai.mcp=DEBUG
logging.level.ai.qodo.mcp=DEBUG
```

## Usage

### As an MCP Server

The server communicates via STDIO and can be integrated with MCP clients like Claude Desktop or other AI assistants.

#### Example MCP Client Configuration

Add to your MCP client configuration (e.g., `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "git": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/git-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### Direct API Usage

You can also use the `GitService` directly in your Java applications:

```java
@Autowired
private GitService gitService;

// Clone a repository
String result = gitService.cloneRepository(
    "https://github.com/user/repo.git", 
    "/tmp/myrepo"
);

// Get status
String status = gitService.getStatus("/path/to/repo");

// Get commit log
String log = gitService.getLog("/path/to/repo", 10);

// Create and checkout a branch
gitService.createBranch("/path/to/repo", "feature-branch");
gitService.checkoutBranch("/path/to/repo", "feature-branch");

// Commit changes
gitService.commit("/path/to/repo", "Initial commit", List.of());

// Push to remote
gitService.push("/path/to/repo", "origin", "main");
```

## API Reference

### GitService Methods

#### `setClientCapabilities(McpSchema.ClientCapabilities capabilities)`
Sets the MCP client capabilities. Called automatically during MCP initialization.

**Parameters:**
- `capabilities`: The client capabilities object from the MCP protocol

---

#### `setRoots(List<McpSchema.Root> roots)`
Sets the roots received from the MCP client. The first root's URI becomes the default local path.

**Parameters:**
- `roots`: List of root objects from the MCP client

---

#### `hasRootsCapability()`
Checks if the connected MCP client supports the roots capability.

**Returns:** `true` if client supports roots, `false` otherwise

---

#### `getDefaultLocalPath()`
Gets the default local path from the first root provided by the client.

**Returns:** The default path, or `null` if no roots were provided

---

#### `cloneRepository(String remoteUrl, String localPath)`
Clones a repository from a remote URL to a local directory.

**Behavior:**
- Blocks until roots are initialized (if client supports roots capability)
- If `localPath` is null/empty and roots are available, uses `<first-root>/<repo-name>`
- Extracts repository name from the remote URL automatically

**Parameters:**
- `remoteUrl`: The remote repository URL (e.g., https://github.com/user/repo.git)
- `localPath`: The local directory path where the repository will be cloned (optional if roots are available)

**Returns:** Success message with the actual clone path

**Example with roots:**
```java
// If first root is "file:///home/user/projects"
// This will clone to "/home/user/projects/myrepo"
gitService.cloneRepository("https://github.com/user/myrepo.git", null);
```

---

#### `getStatus(String repositoryPath)`
Gets the status of a Git repository showing modified, added, removed, and untracked files.

**Parameters:**
- `repositoryPath`: The path to the Git repository

**Returns:** Formatted status string

---

#### `getLog(String repositoryPath, int maxCount)`
Gets the commit history of a Git repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `maxCount`: Maximum number of commits to retrieve

**Returns:** Formatted log string with commit details

---

#### `listBranches(String repositoryPath)`
Lists all branches in the repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository

**Returns:** Formatted list of branches (current branch marked with *)

---

#### `createBranch(String repositoryPath, String branchName)`
Creates a new branch.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `branchName`: Name of the branch to create

**Returns:** Success message

---

#### `checkoutBranch(String repositoryPath, String branchName)`
Switches to a different branch.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `branchName`: Name of the branch to checkout

**Returns:** Success message

---

#### `commit(String repositoryPath, String message, List<String> files)`
Stages and commits changes.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `message`: The commit message
- `files`: List of files to commit (empty list commits all changes)

**Returns:** Commit hash and message

---

#### `push(String repositoryPath, String remote, String branch)`
Pushes commits to a remote repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `remote`: The remote name (e.g., "origin")
- `branch`: The branch name to push

**Returns:** Success message

---

#### `pull(String repositoryPath)`
Pulls changes from a remote repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository

**Returns:** Success message

---

#### `diff(String repositoryPath, String oldCommit, String newCommit)`
Shows differences between commits.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `oldCommit`: The old commit reference (null defaults to "HEAD^")
- `newCommit`: The new commit reference (null defaults to "HEAD")

**Returns:** Diff output

## Dependencies

```gradle
dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-mcp-server'
    implementation 'org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
}
```

## Testing

Run the tests:
```bash
./gradlew test
```

The project includes unit tests for the GitService that verify:
- Repository status retrieval
- Commit log retrieval
- Branch listing and creation
- Commit operations
- Status with changes

## Example Scenarios

### Scenario 1: Clone and Inspect a Repository
```java
// Clone
gitService.cloneRepository("https://github.com/user/repo.git", "/tmp/repo");

// Check status
String status = gitService.getStatus("/tmp/repo");

// View recent commits
String log = gitService.getLog("/tmp/repo", 5);
```

### Scenario 2: Create a Feature Branch and Commit
```java
// Create and checkout feature branch
gitService.createBranch("/path/to/repo", "feature-x");
gitService.checkoutBranch("/path/to/repo", "feature-x");

// Make changes (outside of this service)
// ...

// Commit changes
gitService.commit("/path/to/repo", "Add feature X", List.of());

// Push to remote
gitService.push("/path/to/repo", "origin", "feature-x");
```

### Scenario 3: Review Changes
```java
// Check what's changed
String status = gitService.getStatus("/path/to/repo");

// View diff
String diff = gitService.diff("/path/to/repo", "HEAD^", "HEAD");
```

## Troubleshooting

### Common Issues

1. **Repository not found**: Ensure the repository path includes the `.git` directory or is the root of a Git repository
2. **Authentication errors**: For private repositories, you may need to configure Git credentials
3. **Permission errors**: Ensure the application has read/write permissions to the repository directory

## License

This is an example implementation for educational purposes.

## Contributing

This is a demonstration project showing how to integrate JGit with Spring AI MCP Server. Feel free to extend it with additional Git operations or customize it for your needs.
