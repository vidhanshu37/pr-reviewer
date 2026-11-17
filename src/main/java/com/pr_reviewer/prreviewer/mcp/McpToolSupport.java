package com.pr_reviewer.prreviewer.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Supplier;

public class McpToolSupport {
    private static final Logger log = LoggerFactory.getLogger(McpToolSupport.class);

    private McpToolSupport() {
    }

    static String safely(String toolName, Supplier<String> body) {
        try {
            return body.get();
        } catch (WebClientResponseException e) {
            log.warn("MCP tool {} failed: GitHub returned {}", toolName, e.getStatusCode());
            return "ERROR[github_" + e.getStatusCode().value() + "]: GitHub request failed in tool " + toolName + ".";
        } catch (RuntimeException e) {
            log.error("MCP tool {} failed", toolName, e);
            return "ERROR[internal]: tool " + toolName + " failed. Details are in the server log.";
        }
    }
}
