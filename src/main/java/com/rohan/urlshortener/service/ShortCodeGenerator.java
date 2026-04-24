package com.rohan.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates short codes using Base62 encoding (a-z, A-Z, 0-9).
 *
 * Design choice: Base62 instead of Base64 because we want URL-safe characters
 * without needing URL encoding (no '+', '/', '=' chars).
 *
 * Length 7 gives us 62^7 = ~3.5 trillion combinations — more than enough
 * for a personal/demo project, with very low collision probability.
 */
@Component
public class ShortCodeGenerator {

    private static final String BASE62 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 7;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return sb.toString();
    }
}
