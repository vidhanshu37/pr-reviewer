package com.pr_reviewer.prreviewer;


import com.pr_reviewer.prreviewer.webhook.WebhookSignatureVerifier;
import com.sun.jdi.event.ExceptionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Unit test - cover valid signature acceptance, invalid signature rejection
public class WebhookSignatureVerifierTest {
    private static final String SECRET = "test-webhook-secret";
    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WebhookSignatureVerifier();
        ReflectionTestUtils.setField(verifier, "webhookSecret", SECRET);
    }

    @Test
    void shouldAcceptValidSignature() throws Exception {
        String payload = "{\"action\":\"opened\"}";
        String validSignature = computeHmac(payload, SECRET);

        assertTrue(verifier.isValid(payload, validSignature));
    }

    @Test
    void shouldRejectSignatureComputedWithWrongSecret() throws Exception {
        String payload = "{\"action\":\"opened\"}";
        String signatureWithWrongSecret = computeHmac(payload, "wrong-secret");

        assertFalse(verifier.isValid(payload, signatureWithWrongSecret));
    }

    @Test
    void shouldRejectWhenPayloadIsTamperedAfterSigning() throws Exception {
        String originalPayload = "{\"action\":\"opened\"}";
        String signature = computeHmac(originalPayload, SECRET);
        String tamperedPayload = "{\"action\":\"closed\"}";

        assertFalse(verifier.isValid(tamperedPayload, signature));
    }

    @Test
    void shouldRejectNullSignatureHeader() {
        assertFalse(verifier.isValid("{\"action\":\"opened\"}", null));
    }

    @Test
    void shouldRejectSignatureMissingSha256Prefix() {
        assertFalse(verifier.isValid("{\"action\":\"opened\"}", "abcdef123456"));
    }

    @Test
    void shouldRejectEmptyPayloadWithMismatchedSignature() {
        assertFalse(verifier.isValid("", "sha256=deadbeef"));
    }

    private String computeHmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    }
}
