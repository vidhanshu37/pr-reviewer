package com.pr_reviewer.prreviewer.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pr_reviewer.prreviewer.github.GitHubRepo;
import com.pr_reviewer.prreviewer.github.GitHubRepoRepository;
import com.pr_reviewer.prreviewer.github.GitHubSyncService;
import com.pr_reviewer.prreviewer.review.ReviewOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RequestMapping("/webhook")
@RequiredArgsConstructor
@RestController
@Slf4j
public class WebhookController {

    // this is testing msg
    private final WebhookSignatureVerifier verifier;
    private final ReviewOrchestratorService reviewOrchestratorService;
    private final ObjectMapper objectMapper;
    private final GitHubSyncService syncService;
    private final GitHubRepoRepository repoRepository;

    @PostMapping("/github")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId) throws JsonProcessingException {

        if (!verifier.isValid(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok("Event ignored");
        }

        if ("repository".equals(eventType)) {
            JsonNode node = objectMapper.readTree(payload);
            String action = node.get("action").asText();
            JsonNode repoNode = node.get("repository");

            Long githubRepoId = repoNode.get("id").asLong();
            Long ownerGithubId = repoNode.get("owner").get("id").asLong();

            if ("created".equals(action)) {
                GitHubRepo repo = repoRepository
                        .findByOwnerGithubIdAndGithubRepoId(ownerGithubId, githubRepoId)
                        .orElseGet(GitHubRepo::new);
                repo.setOwnerGithubId(ownerGithubId);
                repo.setGithubRepoId(githubRepoId);
                repo.setFullName(repoNode.get("full_name").asText());
                repo.setPrivateRepo(repoNode.get("private").asBoolean());
                repo.setLastSyncedAt(Instant.now());
                repoRepository.save(repo);
            } else if ("deleted".equals(action)) {
                repoRepository.findByOwnerGithubIdAndGithubRepoId(ownerGithubId, githubRepoId)
                        .ifPresent(repoRepository::delete);
            }
        }

        try {
            JsonNode json = objectMapper.readTree(payload);
            String action = json.get("action").asText();
            log.info("Received pull_request event, action={}, deliveryId={}", action, deliveryId);

            if ("opened".equals(action) || "synchronize".equals(action)) {
                reviewOrchestratorService.processPullRequestAsync(json, deliveryId);
            }
        } catch (Exception e) {
            log.error("Failed to process webhook payload, deliveryId={}", deliveryId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed payload");
        }
        return ResponseEntity.ok("Accepted");
    }

}