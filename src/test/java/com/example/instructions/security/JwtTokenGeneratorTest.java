package com.example.instructions.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility test class for generating JWT tokens for testing
 * Run this test to generate a valid JWT token for testing the API
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Slf4j
@TestPropertySource(properties = {"jwt.secret=${JWT_SECRET}"})
class JwtTokenGeneratorTest {

    @Value("${jwt.secret}")
    private String SECRET;

    private static final long EXPIRATION_TIME = 3600000; // 1 hour

    @Test
    void generateTestToken() {
        String token = generateToken("test-service", EXPIRATION_TIME);

        System.out.println("\n========================================");
        System.out.println("JWT Access Token for Testing (1 hour):");
        System.out.println("========================================");
        System.out.println(token);
        System.out.println("========================================");
    }

    @Test
    void generateLongLivedToken() {
        String token = generateToken("test-service", 86400000L);

        System.out.println("\n========================================");
        System.out.println("Long-lived JWT Access Token (24 hours):");
        System.out.println("========================================");
        System.out.println(token);
        System.out.println("========================================\n");
    }

    private String generateToken(String subject, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }
}

