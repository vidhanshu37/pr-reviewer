package com.pr_reviewer.prreviewer.review;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ReviewJob(
        String jobId,
        String owner,
        String repo,
        int prNumber,
        long installationId,
        String headSha
) {
    public String repoFullName() {
        return owner + "/" + repo;
    }

    public static ReviewJob fromWebhook(JsonNode payload) {
        String owner = payload.at("/repository/owner/login").asText();
        String repo = payload.at("/repository/name").asText();
        int prNumber = payload.at("/pull_request/number").asInt();
        long installationId = payload.at("/installation/id").asLong();
        String headSha = payload.at("/pull_request/head/sha").asText();

        if (owner.isBlank() || repo.isBlank() || prNumber <= 0
                || installationId <= 0 || headSha.isBlank()) {
            throw new IllegalArgumentException("Webhook payload is missing required PR fields");
        }
        return new ReviewJob(UUID.randomUUID().toString(), owner, repo, prNumber, installationId, headSha);
    }

}
