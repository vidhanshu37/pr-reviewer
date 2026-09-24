package com.pr_reviewer.prreviewer.review;

import java.time.Instant;

public record ReviewSummaryDto(
        Long id,
        String repoFullName,
        int prNumber,
        Instant createdAt
) {}