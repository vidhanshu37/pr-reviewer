package com.pr_reviewer.prreviewer.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "mcp.devtoken.enabled", havingValue = "true")
public class McpDevTokenPrinter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpDevTokenPrinter.class);

    private final McpJwtService jwtService;
    private final String repo;
    private final int prNumber;

    public McpDevTokenPrinter(
            McpJwtService jwtService,
            @Value("${mcp.devtoken.repo:dev/test-repo}") String repo,
            @Value("${mcp.devtoken.pr:1}") int prNumber) {
        this.jwtService = jwtService;
        this.repo = repo;
        this.prNumber = prNumber;
    }

    @Override
    public void run(ApplicationArguments args) {
        String token = jwtService.issue("dev-" + UUID.randomUUID(), repo, prNumber);
        log.warn("DEV ONLY - MCP job token for repo={} pr={}. Never enable mcp.devtoken outside local development.\n{}",
                repo, prNumber, token);
    }
}