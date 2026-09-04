package com.pr_reviewer.prreviewer.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fetches the logged-in user's own repos and PR's from github using their OAuth2 access token,
 * then upsert into our caches
 */
@Slf4j
@Service
public class GitHubSyncService {

    private final WebClient webClient;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GitHubRepoRepository repoRepository;
    private final GitHubPullRequestRepository pullRequestRepository;

    public GitHubSyncService(
            @Value("${github.api.base-url}") String baseUrl,
            OAuth2AuthorizedClientService authorizedClientService,
            GitHubRepoRepository repoRepository,
            GitHubPullRequestRepository pullRequestRepository) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.authorizedClientService = authorizedClientService;
        this.repoRepository = repoRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    private String getUserAccessToken(OAuth2AuthenticationToken authToken) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authToken.getAuthorizedClientRegistrationId(), // "github"
                authToken.getName()
        );
        return client.getAccessToken().getTokenValue();
    }

    @SuppressWarnings("unchecked")
    public List<GitHubRepo> syncRepos(OAuth2AuthenticationToken authToken) {
        String token = getUserAccessToken(authToken);
        OAuth2User principal = authToken.getPrincipal();
        Long ownerGithubId = Long.valueOf(principal.getAttribute("id").toString());

        Map<String, Object>[] repos = webClient.get()
                .uri("/user/repos?per_page=100")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(Map[].class)
                .block();

        if (repos == null) {
            log.info("No repos returned from GitHub for user {}", ownerGithubId);
            return List.of();
        }

        for (Map<String, Object> repoData : repos) {
            Long githubRepoId = Long.valueOf(repoData.get("id").toString());

            GitHubRepo repo = repoRepository
                    .findByOwnerGithubIdAndGithubRepoId(ownerGithubId, githubRepoId)
                    .orElseGet(GitHubRepo::new);

            repo.setOwnerGithubId(ownerGithubId);
            repo.setGithubRepoId(githubRepoId);
            repo.setFullName((String) repoData.get("full_name"));
            repo.setPrivateRepo((Boolean) repoData.get("private"));
            repo.setLastSyncedAt(Instant.now());

            repoRepository.save(repo);
        }

        log.info("Synced {} repos for GitHub user {}", repos.length, ownerGithubId);
        return repoRepository.findByOwnerGithubId(ownerGithubId);
    }

    @SuppressWarnings("unchecked")
    public List<GitHubPullRequest> syncPullRequests(OAuth2AuthenticationToken authToken, GitHubRepo repo) {
        String token = getUserAccessToken(authToken);
        String[] parts = repo.getFullName().split("/", 2);
        String owner = parts[0];
        String repoName = parts[1];

        Map<String, Object>[] pulls = webClient.get()
                .uri("/repos/{owner}/{repo}/pulls?state=all&per_page=100", owner, repoName)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(Map[].class)
                .block();

        if (pulls == null) {
            return List.of();
        }

        for (Map<String, Object> prData : pulls) {
            int prNumber = (Integer) prData.get("number");

            GitHubPullRequest pr = pullRequestRepository
                    .findByRepoAndPrNumber(repo, prNumber)
                    .orElseGet(GitHubPullRequest::new);

            pr.setRepo(repo);
            pr.setPrNumber(prNumber);
            pr.setTitle((String) prData.get("title"));
            pr.setState((String) prData.get("state"));
            pr.setUpdatedAt(Instant.parse((String) prData.get("updated_at")));
            pr.setLastSyncedAt(Instant.now());

            pullRequestRepository.save(pr);
        }

        log.info("Synced {} PRs for repo {}", pulls.length, repo.getFullName());
        return pullRequestRepository.findByRepo(repo);
    }
}