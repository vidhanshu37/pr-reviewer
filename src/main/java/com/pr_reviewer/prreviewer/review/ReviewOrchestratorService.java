package com.pr_reviewer.prreviewer.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.pr_reviewer.prreviewer.dto.FileDiff;
import com.pr_reviewer.prreviewer.github.GitHubClientService;
import com.pr_reviewer.prreviewer.llm.LLMReviewService;
import com.pr_reviewer.prreviewer.rag.CodeChunkingService;
import com.pr_reviewer.prreviewer.rag.CodebaseIndexingService;
import com.pr_reviewer.prreviewer.rag.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewOrchestratorService {

    private final GitHubClientService gitHubClientService;
    private final LLMReviewService llmReviewService;
    private final DiffFilterService diffFilterService;
    private final DiffChunkingService diffChunkingService;
    private final ReviewCacheService reviewCacheService;
    private final CodeChunkingService codeChunkingService;
    private final CodebaseIndexingService codebaseIndexingService;
    private final RetrievalService retrievalService;

    @Async("reviewTaskExecutor")
    public void processPullRequestAsync(JsonNode webhookPayload, String deliveryId) {
        String owner = webhookPayload.at("/repository/owner/login").asText();
        String repo = webhookPayload.at("/repository/name").asText();
        int prNumber = webhookPayload.at("/pull_request/number").asInt();
        long installationId = webhookPayload.at("/installation/id").asLong();

        if(!reviewCacheService.markDeliveryProcessed(deliveryId)) {
            return;
        }

        try {
            List<FileDiff> allFiles = gitHubClientService.fetchPullRequestFiles(owner, repo, prNumber, installationId);
            List<FileDiff> relevantFiles = diffFilterService.filterRelevantFiles(allFiles);

            String repoFullName = owner + "/" + repo;

            for (FileDiff file : relevantFiles) {
                if ("removed".equals(file.getStatus())) {
                    log.info("Skipping content fetch for deleted file: {}", file.getFilename());
                    continue;
                }
                String headSha = webhookPayload.at("/pull_request/head/sha").asText();
                try {
                    String fullContent = gitHubClientService.fetchFileContent(owner, repo, file.getFilename(), headSha, installationId);
                    List<Document> chunks = codeChunkingService.chunkFile(repoFullName, file.getFilename(), fullContent);
                    codebaseIndexingService.indexChunks(chunks);
                } catch (Exception e) {
                    log.warn("Skipping indexing for {} - failed to fetch content: {}", file.getFilename(), e.getMessage());
                }
            }

            if (relevantFiles.isEmpty()) {
                log.info("No relevant files to review for PR #{} on {}/{} — skipping LLM call", prNumber, owner, repo);
                return;
            }

            String diff = diffChunkingService.buildDiffText(relevantFiles);
            String diffHash = reviewCacheService.hashDiff(diff);

            Set<String> fileInThisPr = relevantFiles.stream()
                    .map(FileDiff::getFilename)
                    .collect(Collectors.toSet());

            List<Document> retrievedChunks = retrievalService.retrieveRelevantChunks(diff, fileInThisPr, repoFullName);
            log.info("vidhanshu ::: first retrieved chunk = {}",
                    retrievedChunks.isEmpty() ? "NONE" : retrievedChunks.get(0));

            String summary = reviewCacheService.getCachedSummary(diffHash);

            if(summary == null) {
                summary = llmReviewService.generateSummary(diff);
                reviewCacheService.putSummary(diffHash, summary);
            }

//            this will send the summary as a comment to the PR
            gitHubClientService.postComment(owner, repo, prNumber, summary, installationId);
            log.info("Review posted for PR #{} on {}/{}", prNumber, owner, repo);
        } catch (Exception e) {
            log.error("Failed to process PR #{} on {}/{}: {}", prNumber, owner, repo, e.getMessage(), e);
        }
    }
}
