package ai.qodo.mcp.util;

public class ParameterStringBuilder {

    /**
     * Creates a simple concatenated string of all parameters
     * Format: "param1: value1, param2: value2, param3: value3"
     */
    public static String buildParameterString(String... paramPairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramPairs.length; i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(paramPairs[i]).append(": ").append(paramPairs[i + 1]);
        }
        return sb.toString();
    }
}
