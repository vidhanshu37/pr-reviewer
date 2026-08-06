package com.pr_reviewer.prreviewer.github;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

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


    public List<FileDiff> fetchPullRequestFiles(String owner, String repo, int prNumber, long installationId) {
        String token = authService.getInstallationToken(installationId);

        FileDiff[] files = webClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pr}/files", owner, repo, prNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(FileDiff[].class)
                .block();

        return Arrays.asList(files);
    }

    public String fetchFileContent(String owner, String repo, String path, String ref, long installationId) {

        for(int i=0; i<5; i++) {
            // this is for testing purpose
        }

        String token = authService.getInstallationToken(installationId);

        Map<String, Object> response = webClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}", owner, repo, path, ref)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String base64Content = (String)response.get("content");
        return new String(Base64.getMimeDecoder().decode(base64Content.replace("\n", "")), StandardCharsets.UTF_8);
    }

}
