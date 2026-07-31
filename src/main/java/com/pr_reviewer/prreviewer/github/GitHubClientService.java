package com.pr_reviewer.prreviewer.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GitHubClientService {
    private final WebClient webClient;
    private final GitHubAppAuthService authService;

    public GitHubClientService(
            @Value("${github.api.base-url}") String baseUrl,
            GitHubAppAuthService authService) {
        this.authService = authService;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public String fetchPullRequestDiff(String owner, String repo, int prNumber, long installationId) {
        String token = authService.getInstallationToken(installationId);

        return webClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pr}", owner, repo, prNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void postComment(String owner, String repo, int prNumber, String body, long installationId) {
        String token = authService.getInstallationToken(installationId);
        webClient.post()
                .uri("/repos/{owner}/{repo}/issues/{pr}/comments", owner, repo, prNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .bodyValue(Map.of("body", body))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

}
