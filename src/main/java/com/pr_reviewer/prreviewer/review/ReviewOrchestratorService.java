package com.pr_reviewer.prreviewer.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.pr_reviewer.prreviewer.github.GitHubClientService;
import com.pr_reviewer.prreviewer.llm.LLMReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewOrchestratorService {

    private final GitHubClientService gitHubClientService;
    private final LLMReviewService llmReviewService;

    @Async("reviewTaskExecutor")
    public void processPullRequestAsync(JsonNode webhookPayload) {
        String owner = webhookPayload.at("/repository/owner/login").asText();
        String repo = webhookPayload.at("/repository/name").asText();
        int prNumber = webhookPayload.at("/pull_request/number").asInt();
        long installationId = webhookPayload.at("/installation/id").asLong();

        try {
            String diff = gitHubClientService.fetchPullRequestDiff(owner, repo, prNumber, installationId);
            String summary = llmReviewService.generateSummary(diff);

            gitHubClientService.postComment(owner, repo, prNumber, summary, installationId);
            log.info("Review posted for PR #{} on {}/{}", prNumber, owner, repo);
        } catch (Exception e) {
            log.error("Failed to process PR #{} on {}/{}: {}", prNumber, owner, repo, e.getMessage(), e);
        }
    }
}
