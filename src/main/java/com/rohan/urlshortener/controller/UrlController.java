package com.rohan.urlshortener.controller;

import com.rohan.urlshortener.dto.CreateUrlRequest;
import com.rohan.urlshortener.dto.UrlResponse;
import com.rohan.urlshortener.service.RateLimiterService;
import com.rohan.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiter;

    /** Create a new short URL. */
    @PostMapping("/api/v1/urls")
    @ResponseStatus(HttpStatus.CREATED)
    public UrlResponse createUrl(@Valid @RequestBody CreateUrlRequest request,
                                  HttpServletRequest httpRequest) {
        rateLimiter.checkRateLimit(getClientIp(httpRequest));
        return urlService.createShortUrl(request);
    }

    /** Get stats for a short URL. */
    @GetMapping("/api/v1/urls/{shortCode}")
    public UrlResponse getStats(@PathVariable String shortCode) {
        return urlService.getUrlStats(shortCode);
    }

    /** Redirect short URL to long URL. */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String longUrl = urlService.resolveShortUrl(shortCode);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(longUrl))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
