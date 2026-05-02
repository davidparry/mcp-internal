#!/bin/bash

# Launch the MCP Inspector UI.
# The server must already be running on http://localhost:8080/mcp
#
# When the Inspector UI opens, enter the bearer token in the
# Authentication section of the sidebar:
#   Header Name:  Authorization
#   Header Value: Bearer DAVIDSUPERSECRETTOKEN
#
# Alternatively, use CLI mode to test a single method:
#   npx @modelcontextprotocol/inspector --cli http://localhost:8080/mcp \
#     --transport streamable-http \
#     --header "Authorization: Bearer DAVIDSUPERSECRETTOKEN" \
#     --method tools/list

npx @modelcontextprotocol/inspector --config config.json --server git-mcp
