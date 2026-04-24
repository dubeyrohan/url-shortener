package com.rohan.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUrlRequest {

    @NotBlank(message = "URL cannot be blank")
    @Size(max = 2048, message = "URL too long (max 2048 chars)")
    @Pattern(
        regexp = "^https?://.+",
        message = "URL must start with http:// or https://"
    )
    private String longUrl;

    /** Optional TTL in seconds. If null, URL never expires. */
    private Long ttlSeconds;
}
