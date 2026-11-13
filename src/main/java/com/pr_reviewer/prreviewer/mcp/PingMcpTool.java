package com.pr_reviewer.prreviewer.mcp;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class PingMcpTool {
    @McpTool(name = "ping", description = "Health-check tool. Returns 'pong: <message>' to prove the MCP server is reachable.")
    public String ping(
            @McpToolParam(description = "Any short text to echo back", required = true) String message) {
        return "pong: " + message;
    }
}
