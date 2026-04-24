package com.rohan.urlshortener;

import com.rohan.urlshortener.dto.CreateUrlRequest;
import com.rohan.urlshortener.dto.UrlResponse;
import com.rohan.urlshortener.exception.UrlNotFoundException;
import com.rohan.urlshortener.model.Url;
import com.rohan.urlshortener.repository.UrlRepository;
import com.rohan.urlshortener.service.ShortCodeGenerator;
import com.rohan.urlshortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock private UrlRepository urlRepository;
    @Mock private ShortCodeGenerator codeGenerator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createShortUrl_shouldGenerateAndPersist() {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setLongUrl("https://example.com/very/long/url");

        when(codeGenerator.generate()).thenReturn("abc1234");
        when(urlRepository.existsByShortCode("abc1234")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(inv -> {
            Url u = inv.getArgument(0);
            u.setId(1L);
            u.setCreatedAt(LocalDateTime.now());
            return u;
        });

        UrlResponse resp = urlService.createShortUrl(req);

        assertThat(resp.getShortCode()).isEqualTo("abc1234");
        assertThat(resp.getShortUrl()).isEqualTo("http://localhost:8080/abc1234");
        assertThat(resp.getLongUrl()).isEqualTo("https://example.com/very/long/url");
        verify(valueOperations).set(eq("url:abc1234"), eq(req.getLongUrl()), eq(300L), any());
    }

    @Test
    void resolveShortUrl_shouldReturnFromCache_whenCacheHit() {
        when(valueOperations.get("url:abc1234"))
                .thenReturn("https://example.com/cached");

        String result = urlService.resolveShortUrl("abc1234");

        assertThat(result).isEqualTo("https://example.com/cached");
        verify(urlRepository, never()).findByShortCode(any());
    }

    @Test
    void resolveShortUrl_shouldFallBackToDb_whenCacheMiss() {
        Url url = Url.builder()
                .shortCode("abc1234")
                .longUrl("https://example.com/from-db")
                .createdAt(LocalDateTime.now())
                .clickCount(0L)
                .build();

        when(valueOperations.get("url:abc1234")).thenReturn(null);
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        String result = urlService.resolveShortUrl("abc1234");

        assertThat(result).isEqualTo("https://example.com/from-db");
        // cache should be populated after miss
        verify(valueOperations).set(eq("url:abc1234"), eq(url.getLongUrl()), eq(300L), any());
    }

    @Test
    void resolveShortUrl_shouldThrow_whenNotFound() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveShortUrl("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }
}
