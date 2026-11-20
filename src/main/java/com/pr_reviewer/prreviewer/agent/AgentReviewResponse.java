package com.pr_reviewer.prreviewer.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AgentReviewResponse(
        @JsonProperty("job_id") String jobId,
        String status,
        List<Finding> findings,
        List<String> trace,
        String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finding(
            String file,
            Integer line,
            String severity,
            String category,
            String message,
            String suggestion) {
    }

    public boolean isDone() {
        return "done".equals(status);
    }

    public static AgentReviewResponse failed(String jobId, String error) {
        return new AgentReviewResponse(jobId, "failed", List.of(), List.of(), error);
    }
}
