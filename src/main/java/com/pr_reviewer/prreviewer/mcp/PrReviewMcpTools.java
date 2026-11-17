package com.pr_reviewer.prreviewer.mcp;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import com.pr_reviewer.prreviewer.github.GitHubClientService;
import com.pr_reviewer.prreviewer.review.DiffCompressionService;
import com.pr_reviewer.prreviewer.review.DiffFilterService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PrReviewMcpTools {
    private final GitHubClientService gitHubClientService;
    private final DiffFilterService diffFilterService;
    private final DiffCompressionService diffCompressionService;

    public PrReviewMcpTools(
            GitHubClientService gitHubClientService,
            DiffFilterService diffFilterService,
            DiffCompressionService diffCompressionService) {
        this.gitHubClientService = gitHubClientService;
        this.diffFilterService = diffFilterService;
        this.diffCompressionService = diffCompressionService;
    }

    @McpTool(
            name = "get_pr_diff",
            description = "Returns the compressed unified diff of the pull request under review (irrelevant files such as lockfiles and images are already removed). Takes no arguments.")
    public String getPrDiff() {
        return McpToolSupport.safely("get_pr_diff", () -> {
            McpJobScope scope = McpJobScope.fromCurrentRequest();

            List<FileDiff> allFiles = gitHubClientService.fetchPullRequestFiles(
                    scope.owner(), scope.repo(), scope.prNumber(), scope.installationId());
            List<FileDiff> relevantFiles = diffFilterService.filterRelevantFiles(allFiles);

            if (relevantFiles.isEmpty()) {
                return "No reviewable files in this pull request.";
            }

            String diff = diffCompressionService.buildCompressedDiff(relevantFiles);
            return "Pull request #" + scope.prNumber() + " in " + scope.repoFullName() + ": "
                    + relevantFiles.size() + " reviewable file(s) out of " + allFiles.size() + " changed.\n\n"
                    + diff;
        });
    }
}

