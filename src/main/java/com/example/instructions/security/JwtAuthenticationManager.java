package com.example.instructions.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive JWT Authentication Manager for WebFlux
 * Validates JWT tokens and creates authenticated principals
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.justOrEmpty(authentication)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    String token = (String) auth.getCredentials();
                    
                    try {
                        if (!jwtUtil.validateToken(token)) {
                            log.warn("Invalid JWT token");

                            return Mono.empty();
                        }

                        // Extract username
                        String username = jwtUtil.extractUsername(token);
                        
                        // Create an authenticated token
                        JwtAuthenticationToken authenticated = new JwtAuthenticationToken(
                                username,
                                List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))
                        );

                        log.debug("Successfully authenticated user: {}", username);
                        return Mono.just(authenticated);
                        
                    } catch (Exception e) {
                        log.warn("Failed to authenticate JWT: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }
}

