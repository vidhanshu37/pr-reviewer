package com.pr_reviewer.prreviewer.mcp.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class McpJwtService {
    public static final String CLAIM_REPO = "repo";
    public static final String CLAIM_PR = "pr";
    public static final String CLAIM_SCOPE = "scope";
    public static final String SCOPE_READ = "mcp:read";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;
    private final String audience;

    public McpJwtService(
            @Value("${mcp.jwt.secret}") String secret,
            @Value("${mcp.jwt.ttl-minutes}") long ttlMinutes,
            @Value("${mcp.jwt.issuer}") String issuer,
            @Value("${mcp.jwt.audience}") String audience) {

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("mcp.jwt.secret must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(secretBytes, "HmacSHA256");
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issue(String jobId, String repo, int prNumber) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(jobId)
                .claim(CLAIM_REPO, repo)
                .claim(CLAIM_PR, prNumber)
                .claim(CLAIM_SCOPE, SCOPE_READ)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
