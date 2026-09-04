package com.pr_reviewer.prreviewer.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequest, Long> {
    // Used to list PRs for a specific repo on the dashboard
    List<GitHubPullRequest> findByRepo(GitHubRepo repo);

    // Used during sync to check if a PR is already cached (upsert logic)
    Optional<GitHubPullRequest> findByRepoAndPrNumber(GitHubRepo repo, int prNumber);
}
