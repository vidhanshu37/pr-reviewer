package com.pr_reviewer.prreviewer.agent;

import com.pr_reviewer.prreviewer.review.ReviewJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

@Slf4j
@Service
public class AgentClient {
    private final WebClient webClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final Duration timeout;

    public AgentClient(
            @Value("${agent.base-url}") String baseUrl,
            @Value("${agent.timeout-seconds}") long timeoutSeconds,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public AgentReviewResponse review(ReviewJob job, String jobToken) {
        AgentReviewRequest request = new AgentReviewRequest(
                job.jobId(), job.repoFullName(), job.prNumber(), jobToken);

        return circuitBreakerFactory.create("agentReview").run(
                () -> call(request),
                throwable -> {
                    log.warn("Agent call failed jobId={} cause={}", job.jobId(), throwable.getClass().getSimpleName());
                    return AgentReviewResponse.failed(job.jobId(), "agent_unavailable: " + throwable.getClass().getSimpleName());
                });
    }

    private AgentReviewResponse call(AgentReviewRequest request) {
        return webClient.post()
                .uri("/review")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentReviewResponse.class)
                .block(timeout);
    }
}
