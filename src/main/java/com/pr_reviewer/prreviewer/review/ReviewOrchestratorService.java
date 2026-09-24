package com.pr_reviewer.prreviewer.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.pr_reviewer.prreviewer.dto.FileDiff;
import com.pr_reviewer.prreviewer.dto.Review;
import com.pr_reviewer.prreviewer.github.GitHubClientService;
import com.pr_reviewer.prreviewer.llm.LLMReviewService;
import com.pr_reviewer.prreviewer.rag.RepoIndexingService;
import com.pr_reviewer.prreviewer.rag.RetrievalService;
import com.pr_reviewer.prreviewer.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final RepoIndexingService repoIndexingService;
    private final RetrievalService retrievalService;
    private final DiffCompressionService diffCompressionService;
    private final ReviewRepository reviewRepository;

    @Async("reviewTaskExecutor")
    public void processPullRequestAsync(JsonNode webhookPayload, String deliveryId) {
        String owner = webhookPayload.at("/repository/owner/login").asText();
        String repo = webhookPayload.at("/repository/name").asText();
        int prNumber = webhookPayload.at("/pull_request/number").asInt();
        long installationId = webhookPayload.at("/installation/id").asLong();

//        if(!reviewCacheService.markDeliveryProcessed(deliveryId)) {
//            return;
//        }

        try {
            List<FileDiff> allFiles = gitHubClientService.fetchPullRequestFiles(owner, repo, prNumber, installationId);
            List<FileDiff> relevantFiles = diffFilterService.filterRelevantFiles(allFiles);

            String repoFullName = owner + "/" + repo;

            String headSha = webhookPayload.at("/pull_request/head/sha").asText();

            for (FileDiff file : relevantFiles) {
                if ("removed".equals(file.getStatus())) {
                    log.info("Skipping content fetch for deleted file: {}", file.getFilename());
                    continue;
                }
                repoIndexingService.indexFileAsync(owner, repo, repoFullName, headSha, installationId, file);
            }

            if (relevantFiles.isEmpty()) {
                log.info("No relevant files to review for PR #{} on {}/{} — skipping LLM call", prNumber, owner, repo);
                return;
            }

            String diff = diffCompressionService.buildCompressedDiff(relevantFiles);
//            String diffHash = reviewCacheService.hashDiff(diff);

            Set<String> fileInThisPr = relevantFiles.stream()
                    .map(FileDiff::getFilename)
                    .collect(Collectors.toSet());

            List<Document> retrievedChunks = retrievalService.retrieveRelevantChunks(diff, fileInThisPr, repoFullName);
            log.info("vidhanshu ::: first retrieved chunk = {}",
                    retrievedChunks.isEmpty() ? "NONE" : retrievedChunks.get(0));

//            String summary = reviewCacheService.getCachedSummary(diffHash);

//            if(summary == null) {
               String summary = llmReviewService.generateSummary(diff, retrievedChunks);

//                reviewCacheService.putSummary(diffHash, summary);
//            }

//            this will send the summary as a comment to the PR

            if(summary.isBlank()) {
                log.error("LLM returned blank summary for PR #{} on {} - skipping comment post", prNumber, repoFullName);
                return;
            }

            gitHubClientService.postComment(owner, repo, prNumber, summary, installationId);
            log.info("Review posted for PR #{} on {}/{}", prNumber, owner, repo);

            Review reviewRecord = new Review();
            reviewRecord.setRepoFullName(repoFullName);
            reviewRecord.setPrNumber(prNumber);
            reviewRecord.setSummary(summary);
            reviewRecord.setRetrievedChunkCount(retrievedChunks.size());
            reviewRecord.setCreatedAt(Instant.now());
            reviewRepository.save(reviewRecord);
        } catch (Exception e) {
            log.error("Failed to process PR #{} on {}/{}: {}", prNumber, owner, repo, e.getMessage(), e);
        }
    }
}
