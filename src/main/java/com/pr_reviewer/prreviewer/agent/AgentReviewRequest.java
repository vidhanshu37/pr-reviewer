package com.pr_reviewer.prreviewer.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentReviewRequest(
        @JsonProperty("job_id") String jobId,
        String repo,
        @JsonProperty("pr_number") int prNumber,
        @JsonProperty("mcp_token") String mcpToken) {
}