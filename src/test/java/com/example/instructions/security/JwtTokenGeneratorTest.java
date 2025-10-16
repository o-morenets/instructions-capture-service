package com.example.instructions.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=${JWT_SECRET}"
})
class JwtTokenGeneratorTest {

    @Value("${jwt.secret}")
    private String SECRET;

    private static final long EXPIRATION_TIME = 3600000; // 1 hour

    @Test
    void generateTestToken() {
        String token = generateToken("test-service", EXPIRATION_TIME);
        
        System.out.println("\n========================================");
        System.out.println("JWT Token for Testing:");
        System.out.println("========================================");
        System.out.println(token);
        System.out.println("========================================");
        System.out.println("\nUse this token in Authorization header:");
        System.out.println("Authorization: Bearer " + token);
        System.out.println("========================================\n");
        
        System.out.println("Example curl command:");
        System.out.println("curl -H \"Authorization: Bearer " + token + "\" \\");
        System.out.println("     http://localhost:8080/api/v1/trades");
        System.out.println("========================================\n");
    }

    @Test
    void generateLongLivedToken() {
        // Generate token valid for 24 hours
        String token = generateToken("test-service", 86400000L);
        
        System.out.println("\n========================================");
        System.out.println("Long-lived JWT Token (24 hours):");
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

