package com.pr_reviewer.prreviewer.review.repository;

import com.pr_reviewer.prreviewer.dto.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
