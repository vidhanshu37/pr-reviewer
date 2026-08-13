package com.pr_reviewer.prreviewer;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import com.pr_reviewer.prreviewer.review.DiffCompressionService;
import com.pr_reviewer.prreviewer.review.TokenEstimatorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BudgetExceededCheck {

    @Test
    void fitToBudget_includesFilesUntilBudgetExceeded() {
        DiffCompressionService service = new DiffCompressionService(new TokenEstimatorService());

        FileDiff big = new FileDiff();
        big.setFilename("Big.java");
        big.setPatch("a".repeat(4000 * 4)); // ~4000 tokens

        FileDiff small = new FileDiff();
        small.setFilename("Small.java");
        small.setPatch("a".repeat(100 * 4)); // ~100 tokens

        FileDiff overflow = new FileDiff();
        overflow.setFilename("Overflow.java");
        overflow.setPatch("a".repeat(1000 * 4)); // ~1000 tokens

        DiffCompressionService.FitResult result = service.fitToBudget(List.of(big, small, overflow));

        assertTrue(result.getIncludedFiles().stream().anyMatch(f -> f.getFilename().equals("Big.java")));
        assertTrue(result.getIncludedFiles().stream().anyMatch(f -> f.getFilename().equals("Small.java")));
        assertTrue(result.getOverflowFilenames().contains("Overflow.java"));
    }
}