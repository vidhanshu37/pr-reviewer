package com.pr_reviewer.prreviewer.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pr_reviewer.prreviewer.review.ReviewOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/webhook")
@RequiredArgsConstructor
@RestController
public class WebhookController {

    private final WebhookSignatureVerifier verifier;
    private final ReviewOrchestratorService reviewOrchestratorService;
    private final ObjectMapper objectMapper;

    @PostMapping("/github")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType) {

        if (!verifier.isValid(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok("Event  ignored");
        }

        try {
            JsonNode json = objectMapper.readTree(payload);
            String action = json.get("action").asText();

            if ("opened".equals(action) || "synchronize".equals(action)) {
                reviewOrchestratorService.processPullRequestAsync(json);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed payload");
        }
        return ResponseEntity.ok("Accepted");
    }

}
