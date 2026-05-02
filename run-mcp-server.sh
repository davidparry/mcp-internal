#!/bin/bash

# Load environment variables from .env if present
if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

# Build the project
echo "Building MCP Internal Server..."
if ./gradlew clean build -x test; then
    echo "Build successful!"
    echo "Starting MCP Internal Server on http://localhost:8080/mcp ..."
    java -jar build/libs/mcp-internal-1.0.4.jar
else
    echo "Build failed!"
    exit 1
fi
