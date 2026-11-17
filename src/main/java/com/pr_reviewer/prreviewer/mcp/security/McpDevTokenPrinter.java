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
    private final long installationId;
    private final String headSha;

    public McpDevTokenPrinter(
            McpJwtService jwtService,
            @Value("${mcp.devtoken.repo:dev/test-repo}") String repo,
            @Value("${mcp.devtoken.pr:1}") int prNumber,
            @Value("${mcp.devtoken.installation:1}") long installationId,
            @Value("${mcp.devtoken.sha:0000000000000000000000000000000000000000}") String headSha) {
        this.jwtService = jwtService;
        this.repo = repo;
        this.prNumber = prNumber;
        this.installationId = installationId;
        this.headSha = headSha;
    }

    @Override
    public void run(ApplicationArguments args) {
        String token = jwtService.issue("dev-" + UUID.randomUUID(), repo, prNumber, installationId, headSha);
        log.warn("DEV ONLY - MCP job token for repo={} pr={} installation={} sha={}. Never enable mcp.devtoken outside local development.\n{}",
                repo, prNumber, installationId, headSha, token);
    }
}