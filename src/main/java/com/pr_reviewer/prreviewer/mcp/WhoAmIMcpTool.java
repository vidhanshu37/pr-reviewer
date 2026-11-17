package com.pr_reviewer.prreviewer.mcp;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class WhoAmIMcpTool {

    @McpTool(
            name = "whoami",
            description = "Debug tool. Returns the job scope (job id, repo, PR, installation, sha) carried by the caller's token.")
    public String whoami() {
        McpJobScope scope = McpJobScope.fromCurrentRequest();
        return "jobId=" + scope.jobId()
                + ", repo=" + scope.repoFullName()
                + ", pr=" + scope.prNumber()
                + ", installation=" + scope.installationId()
                + ", sha=" + scope.headSha();
    }
}