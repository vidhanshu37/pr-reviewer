package com.pr_reviewer.prreviewer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LLMReviewService {

    private final WebClient webClient;
    private final String model;

    public LLMReviewService(
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.api.base-url}") String baseUrl,
            @Value("${groq.model}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String generateSummary(String diff, List<Document> retrievedChunks) {
        String contextBlock = buildContextBlock(retrievedChunks);

        String prompt = """
                You are a senior software engineer reviewing a GitHub pull request.
                
                Below is relevant existing code from this repository, given as context.
                
                %s
                
                Review the diff below. Your response MUST include a section titled
                "Codebase Consistency" where you explicitly state whether the change
                follows patterns seen in the context above, or explain why the context
                wasn't relevant to this specific change. Do not skip this section.
                
                Also cover:
                1. What changed (high level)
                2. Potential risk areas or bugs
                
                Keep it under 220 words, use markdown formatting.
                
                DIFF:
                %s
                """.formatted(contextBlock, truncateIfNeeded(diff));
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "max_tokens", 500,
                "temperature", 0.3
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        log.info("Prompt length: {} chars. Context included: {} chars", prompt.length(), contextBlock.length());
        return response.at("/choices/0/message/content").asText();
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
