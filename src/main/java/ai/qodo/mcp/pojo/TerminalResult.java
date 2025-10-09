package ai.qodo.mcp.pojo;

/**
 * Result object for terminal command execution.
 */
public record TerminalResult(
        String output,
        String error,
        int exitCode,
        boolean isError,
        String errorMessage
) {
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        
        if (isError && errorMessage != null) {
            result.append("ERROR: ").append(errorMessage).append("\n\n");
        }
        
        if (output != null && !output.isEmpty()) {
            result.append("Output:\n").append(output);
        }
        
        if (error != null && !error.isEmpty()) {
            result.append("\nError Stream:\n").append(error);
        }
        
        result.append("\nExit Code: ").append(exitCode);
        
        return result.toString();
    }
}
