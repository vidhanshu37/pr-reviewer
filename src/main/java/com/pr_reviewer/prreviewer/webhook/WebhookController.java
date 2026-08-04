package com.pr_reviewer.prreviewer.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pr_reviewer.prreviewer.review.ReviewOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/webhook")
@RequiredArgsConstructor
@RestController
@Slf4j
public class WebhookController {

    private final WebhookSignatureVerifier verifier;
    private final ReviewOrchestratorService reviewOrchestratorService;
    private final ObjectMapper objectMapper;

    @PostMapping("/github")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId) {

        if (!verifier.isValid(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok("Event ignored");
        }

        try {
            JsonNode json = objectMapper.readTree(payload);
            String action = json.get("action").asText();
            log.info("Received pull_request event, action={}, deliveryId={}", action, deliveryId);

            if ("opened".equals(action) || "synchronize".equals(action)) {
                reviewOrchestratorService.processPullRequestAsync(json, deliveryId);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed payload");
        }
        return ResponseEntity.ok("Accepted");
    }

}