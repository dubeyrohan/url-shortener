package com.rohan.urlshortener.service;

import com.rohan.urlshortener.dto.CreateUrlRequest;
import com.rohan.urlshortener.dto.UrlResponse;
import com.rohan.urlshortener.exception.UrlExpiredException;
import com.rohan.urlshortener.exception.UrlNotFoundException;
import com.rohan.urlshortener.model.Url;
import com.rohan.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Core service for URL shortening with Redis caching.
 *
 * Caching strategy:
 *   - Cache-aside pattern: check Redis first, fall back to DB on miss
 *   - TTL of 5 minutes for cached entries (configurable)
 *   - On cache miss, populate cache after DB read
 *   - On URL creation, eagerly write to cache (write-through)
 *
 * Why Redis over in-memory cache (Caffeine):
 *   - Survives application restarts
 *   - Shared across multiple service instances (horizontal scalability)
 *   - Standard choice for distributed caching at scale
 *
 * Why 5-minute TTL:
 *   - URLs are immutable once created — long TTL is safe
 *   - 5 min balances cache hit rate vs. memory usage for cold URLs
 *   - In production, this should be tuned based on access patterns
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private static final String CACHE_PREFIX = "url:";
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes
    private static final int MAX_GENERATION_RETRIES = 5;

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator codeGenerator;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Creates a new short URL.
     * Retries on rare collisions in short code generation.
     */
    @Transactional
    public UrlResponse createShortUrl(CreateUrlRequest request) {
        String shortCode = generateUniqueShortCode();

        LocalDateTime expiresAt = null;
        if (request.getTtlSeconds() != null && request.getTtlSeconds() > 0) {
            expiresAt = LocalDateTime.now().plusSeconds(request.getTtlSeconds());
        }

        Url url = Url.builder()
                .shortCode(shortCode)
                .longUrl(request.getLongUrl())
                .expiresAt(expiresAt)
                .clickCount(0L)
                .build();

        Url saved = urlRepository.save(url);
        log.info("Created short URL: {} -> {}", shortCode, request.getLongUrl());

        // Write-through caching — populate cache eagerly so first read is a hit
        cacheUrl(shortCode, request.getLongUrl());

        return toResponse(saved);
    }

    /**
     * Resolves a short code to its long URL.
     * Cache-aside pattern: Redis first, DB on miss.
     */
    public String resolveShortUrl(String shortCode) {
        // Step 1 — try cache first
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) {
            log.debug("Cache HIT for shortCode: {}", shortCode);
            // Async increment so we don't block the redirect
            incrementClickCountAsync(shortCode);
            return cached;
        }

        log.debug("Cache MISS for shortCode: {}", shortCode);

        // Step 2 — fall back to DB
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Step 3 — check expiration
        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UrlExpiredException(shortCode);
        }

        // Step 4 — populate cache for next time
        cacheUrl(shortCode, url.getLongUrl());

        // Step 5 — increment click count
        incrementClickCountAsync(shortCode);

        return url.getLongUrl();
    }

    public UrlResponse getUrlStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return toResponse(url);
    }

    private String generateUniqueShortCode() {
        for (int i = 0; i < MAX_GENERATION_RETRIES; i++) {
            String candidate = codeGenerator.generate();
            if (!urlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
            log.warn("Short code collision on attempt {}: {}", i + 1, candidate);
        }
        throw new IllegalStateException(
                "Failed to generate unique short code after " + MAX_GENERATION_RETRIES + " attempts");
    }

    private void cacheUrl(String shortCode, String longUrl) {
        try {
            redisTemplate.opsForValue().set(
                    CACHE_PREFIX + shortCode,
                    longUrl,
                    CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            // Cache failures should never break the request — log and continue
            log.error("Failed to cache shortCode: {}", shortCode, e);
        }
    }

    private void incrementClickCountAsync(String shortCode) {
        try {
            urlRepository.incrementClickCount(shortCode);
        } catch (Exception e) {
            log.error("Failed to increment click count for: {}", shortCode, e);
        }
    }

    private UrlResponse toResponse(Url url) {
        return UrlResponse.builder()
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .longUrl(url.getLongUrl())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .clickCount(url.getClickCount())
                .build();
    }
}
