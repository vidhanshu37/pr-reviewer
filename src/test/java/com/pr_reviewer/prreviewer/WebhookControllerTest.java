//package com.pr_reviewer.prreviewer;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.pr_reviewer.prreviewer.review.ReviewOrchestratorService;
//import com.pr_reviewer.prreviewer.webhook.WebhookController;
//import com.pr_reviewer.prreviewer.webhook.WebhookSignatureVerifier;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//public class WebhookControllerTest {
//
//    @Mock
//    private WebhookSignatureVerifier signatureVerifier;
//
//    @Mock
//    private ReviewOrchestratorService reviewOrchestratorService;
//
//    private ObjectMapper objectMapper;
//
//    @InjectMocks
//    private WebhookController webhookController;
//
//    @BeforeEach
//    void stepUp() {
//        objectMapper = new ObjectMapper();
//        webhookController = new WebhookController(signatureVerifier, reviewOrchestratorService, objectMapper);
//    }
//
//    @Test
//    void shouldReturn401WhenSignatureIsInvalid() {
//        when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(false);
//
//        ResponseEntity<String> response = webhookController.handleWebhook(
//                "{\"action\":\"opened\"}", "sha256=bad", "pull_request");
//
//        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
//        verifyNoInteractions(reviewOrchestratorService);
//    }
//
//    @Test
//    void shouldIgnoreNonPullRequestEvents() {
//        when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(true);
//
//        ResponseEntity<String> response = webhookController.handleWebhook(
//                "{}", "sha256=good", "push");
//
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//        assertEquals("Event ignored", response.getBody());
//        verifyNoInteractions(reviewOrchestratorService);
//    }
//
//    @Test
//    void shouldTriggerReviewOnOpenedAction() {
//        when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(true);
//        String payload = "{\"action\":\"opened\",\"pull_request\":{\"number\":42}}";
//
//        ResponseEntity<String> response = webhookController.handleWebhook(
//                payload, "sha256=good", "pull_request");
//
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//        verify(reviewOrchestratorService, times(1)).processPullRequestAsync(any());
//    }
//
//    @Test
//    void shouldTriggerReviewOnSynchronizeAction() {
//        when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(true);
//        String payload = "{\"action\":\"synchronize\",\"pull_request\":{\"number\":7}}";
//
//        ResponseEntity<String> response = webhookController.handleWebhook(
//                payload, "sha256=good", "pull_request");
//
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//        verify(reviewOrchestratorService, times(1)).processPullRequestAsync(any());
//    }
//
//    @Test
//    void shouldNotTriggerReviewOnClosedAction() {
//        when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(true);
//        String payload = "{\"action\":\"closed\",\"pull_request\":{\"number\":9}}";
//
//        ResponseEntity<String> response = webhookController.handleWebhook(
//                payload, "sha256=good", "pull_request");
//
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//        verifyNoInteractions(reviewOrchestratorService);
//    }
//
//    @Test
//    void shouldReturn400OnMalformedPayload() {
//        when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(true);
//
//        ResponseEntity<String> response = webhookController.handleWebhook(
//                "not-valid-json", "sha256=good", "pull_request");
//
//        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
//        verifyNoInteractions(reviewOrchestratorService);
//    }
//}
//
