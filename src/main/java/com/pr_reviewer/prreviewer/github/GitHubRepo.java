package com.pr_reviewer.prreviewer.github;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(
        name = "github_repos",
        indexes = {
                @Index(name = "idx_github_owner_github_id", columnList = "ownerGithubId"),
                @Index(name = "idx_github_repos_owner_repo", columnList = "ownerGithubId, githubRepoId", unique = true)
        }
)
@NoArgsConstructor
public class GitHubRepo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerGithubId;

    private Long githubRepoId;
    private String fullName;
    private boolean privateRepo;

    private Instant lastSyncedAt;
}
