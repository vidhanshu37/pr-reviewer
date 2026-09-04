package com.pr_reviewer.prreviewer.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GitHubRepoRepository extends JpaRepository<GitHubRepo, Long> {

    // Used to list "my repos" for the logged-in user on the dashboard
    List<GitHubRepo> findByOwnerGithubId(Long ownerGithubId);

    // Used during sync to check if a repo is already cached (upsert logic)
    Optional<GitHubRepo> findByOwnerGithubIdAndGithubRepoId(Long ownerGithubId, Long githubRepoId);
}
