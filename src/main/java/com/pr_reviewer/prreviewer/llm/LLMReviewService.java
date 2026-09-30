package com.pr_reviewer.prreviewer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LLMReviewService {

    private final WebClient webClient;
    private final String model;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public LLMReviewService(
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.api.base-url}") String baseUrl,
            @Value("${groq.model}") String model,
            CircuitBreakerFactory circuitBreakerFactory) {
        this.model = model;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String generateSummary(String compressedDiff, List<Document> retrievedChunks) {
        return circuitBreakerFactory.create("llmReview")
                .run(() -> callLlm(compressedDiff, retrievedChunks),
                        throwable -> handleFallback(throwable, compressedDiff));
    }

    private String handleFallback(Throwable throwable, String compressedDiff) {
        if (throwable instanceof CallNotPermittedException) {
            log.warn("LLM circuit breaker is OPEN — skipping call, diff length={}", compressedDiff.length());
        } else {
            log.error("LLM call failed and fell back: {}", throwable.getMessage());
        }
        return "";
    }

    private String callLlm(String compressedDiff, List<Document> retrievedChunks) {
        log.info("Compressed Difference : {} And Retrieved Chunk Length : {}", compressedDiff, retrievedChunks.size());
        String contextBlock = buildContextBlock(retrievedChunks);

        String prompt = """
                You are a senior software engineer reviewing a GitHub pull request.
                
                Below is relevant existing code from the same repository, provided as context
                to help you judge whether the new changes follow the codebase's existing patterns
                and conventions. Use it only as reference — do not review the context itself.
                
                %s
                
                Now review the following diff. Some files may be summarized by name only (see
                "OTHER MODIFIED FILES" / "DELETED FILES" sections) if the PR was too large to show
                every change in full — acknowledge these briefly if present, but focus your review
                on the files shown in full detail.
                
                Provide a concise summary covering:
                1. What changed (high level) — MUST be presented as a markdown table with exactly
                   two columns: "File" and "What changed". Each row should contain one relevant
                   file/component and a concise description of its high-level change.
                2. Potential risk areas or bugs
                3. Whether the change is consistent with the existing codebase patterns shown above (if any relevant context was provided)
                
                The "High-level changes" section MUST always use the following format:
                
                **High-level changes**
                
                | File | What changed |
                |------|--------------|
                | `FileName` | Concise description of the change. |
                | `AnotherFile` | Concise description of the change. |
                
                Do NOT use bullet points or a numbered list for the "High-level changes" section.
                Always use the table format, even when there is only one changed file.
                
                Keep it under 220 words, use markdown formatting.
                
                DIFF:
                %s
                """.formatted(contextBlock, compressedDiff);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "max_tokens", 1500,
                "temperature", 0.3,
                "reasoning_effort", "low"
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String finishReason = response.at("/choices/0/finish_reason").asText();
        String content = response.at("/choices/0/message/content").asText();

        log.info("Prompt length: {} chars. Context included: {} chars. finish_reason={}, content length={}",
                prompt.length(), contextBlock.length(), finishReason, content.length());

        if (content.isBlank()) {
            log.warn("LLM returned blank content. finish_reason={}, raw response={}", finishReason, response);
        }
        return content;
    }

    private String buildContextBlock(List<Document> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            log.info("No relevant retrieved chunks found !!!");
            return "No additional codebase context was available for this review.";
        }

        StringBuilder sb = new StringBuilder("EXISTING CODEBASE CONTEXT:\n");
        for (Document doc : retrievedChunks) {
            sb.append("### From: ").append(doc.getMetadata().get("filename")).append("\n");
            sb.append(doc.getText()).append("\n\n");
        }

        return sb.toString();
    }

    private String truncateIfNeeded(String diff) {
        int maxChars = 12000;
        return diff.length() > maxChars ? diff.substring(0, maxChars) + "\n...[truncated]" : diff;
    }
}