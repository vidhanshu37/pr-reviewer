package com.pr_reviewer.prreviewer.github;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "github_pull_requests",
        indexes = {
                @Index(name = "idx_github_pull_requests_repo_id", columnList = "repo_id"),
                @Index(name = "idx_github_pull_requests_repo_pr_number", columnList = "repo_id, prNumber", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
public class GitHubPullRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private GitHubRepo repo;

    private int prNumber;
    private String title;
    private String state; // Open or Close
    private Instant updatedAt;

    private Instant lastSyncedAt;
}
