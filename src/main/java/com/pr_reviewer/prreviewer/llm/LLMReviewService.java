package com.pr_reviewer.prreviewer.llm;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

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

    public String generateSummary(String diff) {
        String prompt = """
                You are reviewing a GitHub pull request. Below is the diff of changes.
                Provide a concise summary covering:
                1. What changed (high level)
                2. Potential risk areas or bugs
                3. Any obvious code quality concerns

                Keep it under 200 words, use markdown formatting.

                DIFF:
                %s
                """.formatted(truncateIfNeeded(diff));

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

        return response.at("/choices/0/message/content").asText();
    }

    private String truncateIfNeeded(String diff) {
        int maxChars = 12000;
        return diff.length() > maxChars ? diff.substring(0, maxChars) + "\n...[truncated]" : diff;
    }
}
