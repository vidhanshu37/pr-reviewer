package com.pr_reviewer.prreviewer.mcp;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import com.pr_reviewer.prreviewer.github.GitHubClientService;
import com.pr_reviewer.prreviewer.rag.RetrievalService;
import com.pr_reviewer.prreviewer.review.DiffCompressionService;
import com.pr_reviewer.prreviewer.review.DiffFilterService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.ai.document.Document;
import java.util.Set;

import java.util.List;


@Component
public class PrReviewMcpTools {
    private final GitHubClientService gitHubClientService;
    private final DiffFilterService diffFilterService;
    private final DiffCompressionService diffCompressionService;
    private static final int MAX_PATH_CHARS = 300;
    private static final int MAX_FILE_CHARS = 12_000;
    private static final int MAX_QUERY_CHARS = 500;
    private static final int MAX_CHUNK_CHARS = 1_500;
    private static final int MAX_FULL_DIFF_CHARS = 80_000;

    private final RetrievalService retrievalService;

    public PrReviewMcpTools(
            GitHubClientService gitHubClientService,
            DiffFilterService diffFilterService,
            DiffCompressionService diffCompressionService,
            RetrievalService retrievalService) {
        this.gitHubClientService = gitHubClientService;
        this.diffFilterService = diffFilterService;
        this.diffCompressionService = diffCompressionService;
        this.retrievalService = retrievalService;
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

    @McpTool(
            name = "get_full_pr_diff",
            description = "Returns the full unified diff of the pull request under review, without the small token budget of get_pr_diff. "
                    + "Irrelevant files are removed. Very large diffs are capped at about 80,000 characters; files that do not fit are listed by name only. Takes no arguments.")
    public String getFullPrDiff() {
        return McpToolSupport.safely("get_full_pr_diff", () -> {
            McpJobScope scope = McpJobScope.fromCurrentRequest();

            List<FileDiff> allFiles = gitHubClientService.fetchPullRequestFiles(
                    scope.owner(), scope.repo(), scope.prNumber(), scope.installationId());
            List<FileDiff> relevantFiles = diffFilterService.filterRelevantFiles(allFiles);

            if (relevantFiles.isEmpty()) {
                return "No reviewable files in this pull request.";
            }

            String diff = diffCompressionService.buildFullDiff(relevantFiles, MAX_FULL_DIFF_CHARS);
            return "Pull request #" + scope.prNumber() + " in " + scope.repoFullName() + ": "
                    + relevantFiles.size() + " reviewable file(s) out of " + allFiles.size() + " changed.\n\n"
                    + diff;
        });
    }

    @McpTool(name = "get_file_content",
            description = "Returns the content of one file in the repository at the head commit of the pull request "
                    + "under review. Long files are truncated.")
    public String getFileContent(
            @McpToolParam(description = "Repository-relative file path, for example src/main/java/App.java",
                    required = true) String path) {
        return McpToolSupport.safely("get_file_content", () -> {
            McpJobScope scope = McpJobScope.fromCurrentRequest();

            if (!isSafePath(path)) {
                return "ERROR[invalid_path]: use a repository-relative path without '..'.";
            }

            String content;
            try {
                content = gitHubClientService.fetchFileContent(
                        scope.owner(), scope.repo(), path, scope.headSha(), scope.installationId());
            } catch (WebClientResponseException.NotFound e) {
                return "ERROR[not_found]: no file '" + path + "' at the pull request head commit.";
            }

            if (content == null || content.isEmpty()) {
                return "ERROR[empty]: file '" + path + "' is empty or could not be read as text.";
            }
            return truncate(content, MAX_FILE_CHARS);

        });
    }

    @McpTool(
            name = "search_codebase",
            description = "Semantic search over the already-indexed code of this repository. "
                    + "Returns the most relevant code chunks with their file names.")
    public String searchCodebase(
            @McpToolParam(description = "Natural-language description of the code or convention to look for",
                    required = true) String query) {
        return McpToolSupport.safely("search_codebase", () -> {
            McpJobScope scope = McpJobScope.fromCurrentRequest();

            if (query == null || query.isBlank()) {
                return "ERROR[invalid_query]: query must not be empty.";
            }

            String trimmedQuery = query.trim();
            if (trimmedQuery.length() > MAX_QUERY_CHARS) {
                trimmedQuery = trimmedQuery.substring(0, MAX_QUERY_CHARS);
            }

            List<Document> documents = retrievalService.retrieveRelevantChunks(
                    trimmedQuery, Set.of(), scope.repoFullName());

            if (documents.isEmpty()) {
                return "No indexed code found for this query.";
            }

            StringBuilder result = new StringBuilder();
            int index = 1;
            for (Document document : documents) {
                Object filename = document.getMetadata().get("filename");
                String text = document.getText() == null ? "" : document.getText();
                result.append("### Result ").append(index++).append(": ").append(filename).append('\n')
                        .append(truncate(text, MAX_CHUNK_CHARS)).append("\n\n");
            }
            return result.toString();
        });
    }


    private static boolean isSafePath(String path) {
        return path != null
                && !path.isBlank()
                && path.length() <= MAX_PATH_CHARS
                && !path.startsWith("/")
                && !path.contains("..")
                && !path.contains("\\");
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars)
                + "\n[truncated: showing first " + maxChars + " of " + text.length() + " characters]";
    }
}

