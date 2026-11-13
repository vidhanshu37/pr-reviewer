package com.pr_reviewer.prreviewer.mcp;

import com.pr_reviewer.prreviewer.mcp.security.McpJwtService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WhoAmIMcpTool {

    @McpTool(
            name = "whoami",
            description = "Debug tool. Returns the job scope (job id, repo, PR) carried by the caller's token.")
    public Map<String, Object> whoami() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> result = new LinkedHashMap<>();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            result.put("error", "no JWT authentication found in SecurityContext");
            result.put("authentication", String.valueOf(authentication));
            return result;
        }

        Jwt jwt = jwtAuth.getToken();
        result.put("jobId", jwt.getSubject());
        result.put("repo", jwt.getClaimAsString(McpJwtService.CLAIM_REPO));
        result.put("pr", jwt.getClaim(McpJwtService.CLAIM_PR));
        result.put("scope", jwt.getClaimAsString(McpJwtService.CLAIM_SCOPE));
        return result;
    }
}