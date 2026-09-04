package com.pr_reviewer.prreviewer.github;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
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
        List<FileDiff> allFiles = new ArrayList<>();
        int page = 1;
        int perPage = 100;

        while (true) {
            FileDiff[] pageResults = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pr}/files?per_page={perPage}&page={page}",
                            owner, repo, prNumber, perPage, page)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(FileDiff[].class)
                    .block();

            if(pageResults == null || pageResults.length == 0) {
                break;
            }

            allFiles.addAll(Arrays.asList(pageResults));
            log.info("Fetched page {} - {} files (running total: {})", page, pageResults.length, allFiles.size());

            // last page
            if(pageResults.length < perPage) {
                break;
            }
            page++;
        }

        log.info("Total files fetched for PR #{}: {}", prNumber, allFiles.size());
        return allFiles;
    }

    public String fetchFileContent(String owner, String repo, String path, String ref, long installationId) {

        String token = authService.getInstallationToken(installationId);

        Map<String, Object> response = webClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}", owner, repo, path, ref)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String base64Content = (String) response.get("content");
        return new String(Base64.getMimeDecoder().decode(base64Content.replace("\n", "")), StandardCharsets.UTF_8);
    }

    public String fetchDefaultBranch(String owner, String repo, long installationId) {
        String token = authService.getInstallationToken(installationId);

        Map<String, Object> response = webClient.get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return (String) response.get("default_branch");
    }

    @SuppressWarnings("unchecked")
    public List<String> fetchRepoFileTree(String owner, String repo, String branch, long installationId) {
        String token = authService.getInstallationToken(installationId);

        Map<String, Object> response =  webClient.get()
                .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> tree = (List<Map<String, Object>>) response.get("tree");
        List<String> filePaths = new ArrayList<>();

        for(Map<String, Object> entry : tree) {
            if("blob".equals(entry.get("type"))) {
                filePaths.add((String) entry.get("path"));
            }
        }

        log.info("Fetched {} files from {}/{} tree ({})", filePaths.size(), owner, repo, branch);
        return filePaths;
    }
}
