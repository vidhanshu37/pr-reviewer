package com.pr_reviewer.prreviewer.review.repository;

import com.pr_reviewer.prreviewer.dto.Review;
import com.pr_reviewer.prreviewer.review.ReviewSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT new com.pr_reviewer.prreviewer.review.ReviewSummaryDto(" +
            "r.id, r.repoFullName, r.prNumber, r.createdAt) " +
            "FROM Review r ORDER BY r.createdAt DESC")
    Page<ReviewSummaryDto> findAllSummaries(Pageable pageable);
}