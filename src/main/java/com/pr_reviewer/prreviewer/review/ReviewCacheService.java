package com.pr_reviewer.prreviewer.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCacheService {
    private static final String CACHE_KEY_PREFIX = "pr-review:summary:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    public String hashDiff(String diffText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(diffText.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();

            for(byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }

            log.info("Generated hash for diff text: {}", hex.toString());
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing diff text", e);
        }
    }

    public String getCachedSummary(String diffHash) {
        String key = CACHE_KEY_PREFIX + diffHash;
        String cached = redisTemplate.opsForValue().get(key);

        if(cached != null) {
            log.info("Cache hit for key: {} and diff hash: {}", key, diffHash);
        } else {
            log.info("Cache miss for key: {} and diff hash: {}", key, diffHash);
        }

        return cached;
    }

    public void testingPurpose() {
        // this is for testing purpose !
    }

    public void putSummary(String diffHash, String summary) {
        String key = CACHE_KEY_PREFIX + diffHash;
        redisTemplate.opsForValue().set(key, summary, CACHE_TTL);
        log.info("Cached summary for key: {} and TTL: {}", key, CACHE_TTL.toDays());
    }

    private static final String DELIVERY_KEY_PREFIX = "pr-review:delivery:";
    private static final Duration DELIVERY_TTL = Duration.ofHours(24);

    /**
     * Returns true only the first time a given deliveryId is seen (within the TTL window),
     * so redelivered webhooks don't trigger duplicate reviews/comments.
     */
    public boolean markDeliveryProcessed(String deliveryId) {
        if (deliveryId == null) {
            return true;
        }
        String key = DELIVERY_KEY_PREFIX + deliveryId;
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, "1", DELIVERY_TTL);
        boolean isNew = Boolean.TRUE.equals(wasSet);
        if (!isNew) {
            log.info("Delivery {} already processed - skipping duplicate", deliveryId);
        }
        return isNew;
    }
}
