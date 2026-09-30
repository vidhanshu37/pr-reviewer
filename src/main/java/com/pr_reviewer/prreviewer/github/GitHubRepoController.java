package com.pr_reviewer.prreviewer.github;

import com.pr_reviewer.prreviewer.dto.Review;
import com.pr_reviewer.prreviewer.llm.LLMReviewService;
import com.pr_reviewer.prreviewer.rag.RetrievalService;
import com.pr_reviewer.prreviewer.review.ReviewOrchestratorService;
import com.pr_reviewer.prreviewer.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
@Slf4j
public class GitHubRepoController {
    private final GitHubSyncService syncService;
    private final GitHubRepoRepository repoRepository;
    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitHubClientService clientService;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final ReviewOrchestratorService reviewOrchestratorService;
    private final RetrievalService retrievalService;
    private final ReviewRepository reviewRepository;

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private final LLMReviewService lLMReviewService;

    @GetMapping
    public List<GitHubRepo> getMyRepos(OAuth2AuthenticationToken authToken) {
        OAuth2User principal = authToken.getPrincipal();
        Long ownerGithubId = Long.valueOf(principal.getAttribute("id").toString());

        List<GitHubRepo> cached = repoRepository.findByOwnerGithubId(ownerGithubId);

        boolean stale = cached.isEmpty() || cached.stream()
                .anyMatch(r -> r.getLastSyncedAt() == null || r.getLastSyncedAt().isBefore(Instant.now().minus(CACHE_TTL)));

        if(stale)
            return syncService.syncRepos(authToken);

        return cached;
    }

    @PostMapping("/refresh")
    public List<GitHubRepo> forceRefreshRepos(OAuth2AuthenticationToken authToken) {
        return syncService.syncRepos(authToken);
    }

    @GetMapping("/{repoId}/pulls")
    public ResponseEntity<List<GitHubPullRequest>> getPullRequests(@PathVariable Long repoId, OAuth2AuthenticationToken authToken) {
        return repoRepository.findById(repoId)
                .map(repo -> {
                    List<GitHubPullRequest> cached = pullRequestRepository.findByRepo(repo);

                    boolean stale = cached.isEmpty() || cached.stream()
                            .anyMatch(pr -> pr.getLastSyncedAt() == null || pr.getLastSyncedAt().isBefore(Instant.now().minus(CACHE_TTL)));

                    List<GitHubPullRequest> result = stale
                            ? syncService.syncPullRequests(authToken, repo)
                            : cached;

                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{repoId}/files")
    public ResponseEntity<?> getRepoFiles(@PathVariable Long repoId) {
        return repoRepository.findById(repoId)
                .map(repo -> {
                    String[] parts = repo.getFullName().split("/", 2);
                    String owner = parts[0];
                    String repoName = parts[1];

                    Long installationId = gitHubAppAuthService.getInstallationIdForRepo(owner, repoName);

                    if(installationId == null) {
                        return ResponseEntity.status(409).body(
                                Map.of("error", "GitHub App is not installed on this repository. " +
                                        "Install it first to enable file review.")
                        );
                    }

                    String defaultBranch = clientService.fetchDefaultBranch(owner, repoName, installationId);
                    List<String> files = clientService.fetchRepoFileTree(owner, repoName, defaultBranch, installationId);

                    return ResponseEntity.ok(files);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Review single file
    @GetMapping("/{repoId}/files/review")
    public ResponseEntity<?> reviewFile(@PathVariable Long repoId, @RequestParam String path) {
        return repoRepository.findById(repoId)
                .map(repo -> {
                    String[] parts = repo.getFullName().split("/", 2);
                    String owner = parts[0];
                    String repoName = parts[1];

                    Long installationId = gitHubAppAuthService.getInstallationIdForRepo(owner, repoName);
                    if(installationId == null) {
                        return ResponseEntity.status(409).body(
                                Map.of("error", "GitHub App is not installed on this repository. " +
                                        "Install it first to enable file review.")
                        );
                    }

                    String defaultBranch = clientService.fetchDefaultBranch(owner, repoName, installationId);
                    String content = clientService.fetchFileContent(owner, repoName, path, defaultBranch, installationId);

                    List<Document> retrievedChunks = retrievalService.retrieveRelevantChunks(content, Set.of(path), repo.getFullName());

                    String summary = lLMReviewService.generateSummary(content, retrievedChunks);

                    return ResponseEntity.ok(Map.of(
                            "path", path,
                            "summary", summary,
                            "retrievedChunkCount", retrievedChunks.size()
                    ));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<GitHubRepo> ownedRepo(Long repoId, OAuth2AuthenticationToken authToken) {
        Long userId = Long.valueOf(authToken.getPrincipal().getAttribute("id").toString());
        return repoRepository.findById(repoId).filter(r -> userId.equals(r.getOwnerGithubId()));
    }

    @GetMapping("/{repoId}/pulls/{prNumber}/reviews")
    public ResponseEntity<List<Review>> getPullRequestReviews(@PathVariable Long repoId,
                                                              @PathVariable int prNumber, OAuth2AuthenticationToken authToken) {
        return ownedRepo(repoId, authToken)
                .map(repo -> ResponseEntity.ok(
                        reviewRepository.findByRepoFullNameAndPrNumberOrderByCreatedAtDesc(repo.getFullName(), prNumber)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{repoId}/pulls/{prNumber}/review")
    public ResponseEntity<?> requestPullRequestReview(@PathVariable Long repoId, @PathVariable int prNumber,
                                                      @RequestParam(defaultValue = "false") boolean post, OAuth2AuthenticationToken authToken) {
        return ownedRepo(repoId, authToken).<ResponseEntity<?>>map(repo -> {
            String[] parts = repo.getFullName().split("/", 2);
            Long installationId = gitHubAppAuthService.getInstallationIdForRepo(parts[0], parts[1]);
            if (installationId == null) {
                return ResponseEntity.status(409).body(Map.of("error",
                        "GitHub App is not installed on this repository. Install it first to request reviews."));
            }
            try {
                return ResponseEntity.ok(reviewOrchestratorService
                        .reviewPullRequestNow(parts[0], parts[1], prNumber, installationId, post));
            } catch (IllegalStateException e) {
                return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
            }
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
