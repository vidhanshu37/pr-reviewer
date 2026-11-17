package com.pr_reviewer.prreviewer.mcp;

import com.pr_reviewer.prreviewer.mcp.security.McpJwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record McpJobScope(
        String jobId,
        String owner,
        String repo,
        int prNumber,
        long installationId,
        String headSha) {

    public String repoFullName() {
        return owner + "/" + repo;
    }

    public static McpJobScope fromCurrentRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("No JWT authentication found in SecurityContext");
        }

        Jwt jwt = jwtAuth.getToken();
        String repoFullName = requireString(jwt, McpJwtService.CLAIM_REPO);
        int slash = repoFullName.indexOf('/');
        if (slash <= 0 || slash == repoFullName.length() - 1) {
            throw new IllegalStateException("Malformed repo claim: expected owner/name");
        }

        return new McpJobScope(
                jwt.getSubject(),
                repoFullName.substring(0, slash),
                repoFullName.substring(slash + 1),
                requireNumber(jwt, McpJwtService.CLAIM_PR).intValue(),
                requireNumber(jwt, McpJwtService.CLAIM_INSTALLATION).longValue(),
                requireString(jwt, McpJwtService.CLAIM_SHA));
    }

    private static String requireString(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing claim: " + claim);
        }
        return value;
    }

    private static Number requireNumber(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Missing or non-numeric claim: " + claim);
        }
        return number;
    }
}