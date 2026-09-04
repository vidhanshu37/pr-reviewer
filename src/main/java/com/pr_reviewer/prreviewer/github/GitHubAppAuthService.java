package com.pr_reviewer.prreviewer.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GitHubAppAuthService {
    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key-path}")
    private String privateKeyPath;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.github.com")
            .build();

    private static class CachedToken {
        final String token;
        final Instant expiresAt;

        CachedToken(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isStillValid() {
            // refresh 5 minutes before actual expiry, as a safety buffer
            return Instant.now().isBefore(expiresAt.minusSeconds(300));
        }
    }

    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public String getInstallationToken(long installationId) {
        CachedToken cached = tokenCache.get(installationId);

        if(cached != null && cached.isStillValid()) {
            log.info("Using cached installation token for installationId={}", installationId);
            return cached.token;
        }

        log.info("Fetching fresh installation token for installationId={}", installationId);

        try {
            String jwt = generateAppJwt();
            JsonNode response = webClient.post()
                    .uri("/app/installations/{id}/access_tokens", installationId)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String token = response.get("token").asText();
            Instant expiresAt = response.has("expires_at")
                    ? Instant.parse(response.get("expires_at").asText())
                    : Instant.now().plusSeconds(3600); // fallback 1hr if expires_at is missing

            tokenCache.put(installationId, new CachedToken(token, expiresAt));
            log.info("Cached new installation token for installationId={}, expires at {}", installationId, expiresAt);
            return token;
        } catch (Exception e) {
            log.error("Failed to get installation token for installationId={}", installationId, e);
            throw new RuntimeException("Unable to obtain GitHub installation token", e);
        }
    }



    public String generateAppJwt() {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyPath);
            Instant now = Instant.now();
            return Jwts.builder()
                    .issuedAt(Date.from(now.minusSeconds(60)))
                    .expiration(Date.from(now.plusSeconds(600)))
                    .issuer(appId)
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate GitHub App JWT", e);
        }
    }

    // need pkcs8 format
    private PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = Files.readString(Path.of(path))
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public Long getInstallationIdForRepo(String owner, String repo) {
        try {
            String jwt = generateAppJwt();
            JsonNode response = webClient.get()
                    .uri("/repos/{owner}/{repo}/installation", owner, repo)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return response.get("id").asLong();
        } catch (Exception e) {

            log.warn("No GitHub App installation found for {}/{}: {}", owner, repo, e.getMessage());
            return null;
        }
    }

}
