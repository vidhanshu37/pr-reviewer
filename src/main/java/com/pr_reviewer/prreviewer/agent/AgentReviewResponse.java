package com.pr_reviewer.prreviewer.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentReviewResponse(
        @JsonProperty("job_id") String jobId,
        String status,
        List<Finding> findings,
        List<String> trace,
        List<String> warnings,
        Map<String, Object> stats,
        String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finding(
            String file,
            Integer line,
            String severity,
            String category,
            String message,
            String suggestion,
            String verdict,
            String confidence) {

        public boolean isPossible() {
            return "possible".equalsIgnoreCase(confidence);
        }
    }

    public boolean isDone() {
        return "done".equals(status);
    }

    public boolean isPartial() {
        return "partial".equals(status);
    }

    public static AgentReviewResponse failed(String jobId, String error) {
        return new AgentReviewResponse(jobId, "failed", List.of(), List.of(), List.of(), Map.of(), error);
    }
}