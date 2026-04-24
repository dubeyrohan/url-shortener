package com.rohan.urlshortener.service;

import com.rohan.urlshortener.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Per-IP rate limiter using token bucket algorithm.
 *
 * Limit: 20 requests per minute per IP for write operations (URL creation).
 *
 * Why Bucket4j:
 *   - Token bucket allows bursts while maintaining average rate
 *   - In-memory for simplicity; for distributed deployment,
 *     Bucket4j supports a Redis-backed proxy for shared state.
 */
@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void checkRateLimit(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::newBucket);
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException();
        }
    }

    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(20)
                .refillIntervally(20, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
