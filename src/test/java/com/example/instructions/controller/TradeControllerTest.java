package com.example.instructions.controller;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(
    controllers = TradeController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com.example.instructions.security.*")
)
@ExtendWith(MockitoExtension.class)
class TradeControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public TradeService tradeService() {
            return org.mockito.Mockito.mock(TradeService.class);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TradeService tradeService;

    private CanonicalTrade sampleTrade;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(tradeService);
        
        sampleTrade = CanonicalTrade.builder()
                .tradeId("TRADE-123")
                .accountNumber("123456789")
                .securityId("ABC123")
                .tradeType("BUY")
                .amount(new BigDecimal("100000"))
                .timestamp(Instant.parse("2025-08-04T21:15:33Z"))
                .platformId("ACCT123")
                .status(CanonicalTrade.TradeStatus.RECEIVED)
                .build();
    }

    @Test
    void shouldGetTradeById() {
        when(tradeService.getTradeById("TRADE-123")).thenReturn(Optional.of(sampleTrade));

        webTestClient.get()
                .uri("/api/v1/trades/TRADE-123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CanonicalTrade.class)
                .isEqualTo(sampleTrade);
    }

    @Test
    void shouldReturnNotFoundForNonExistentTrade() {
        when(tradeService.getTradeById("NON-EXISTENT")).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/api/v1/trades/NON-EXISTENT")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldGetAllTrades() {
        Flux<CanonicalTrade> trades = Flux.just(sampleTrade);
        when(tradeService.getAllTrades(null)).thenReturn(trades);

        webTestClient.get()
                .uri("/api/v1/trades")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(CanonicalTrade.class)
                .contains(sampleTrade);
    }

    @Test
    void shouldGetTradesWithStatusFilter() {
        Flux<CanonicalTrade> trades = Flux.just(sampleTrade);
        when(tradeService.getAllTrades(eq(CanonicalTrade.TradeStatus.RECEIVED))).thenReturn(trades);

        webTestClient.get()
                .uri("/api/v1/trades?status=RECEIVED")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(CanonicalTrade.class)
                .contains(sampleTrade);
    }

    @Test
    void shouldReturnEmptyStreamWhenNoTrades() {
        Flux<CanonicalTrade> emptyTrades = Flux.empty();
        when(tradeService.getAllTrades(null)).thenReturn(emptyTrades);

        webTestClient.get()
                .uri("/api/v1/trades")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(CanonicalTrade.class)
                .hasSize(0);
    }

    @Test
    void shouldGetTradeStatistics() {
        when(tradeService.getTradeStatistics()).thenReturn(
                java.util.Map.of("totalTrades", 5, "statusCounts", java.util.Map.of("RECEIVED", 3L, "PUBLISHED", 2L))
        );

        webTestClient.get()
                .uri("/api/v1/trades/statistics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalTrades").isEqualTo(5)
                .jsonPath("$.statusCounts.RECEIVED").isEqualTo(3)
                .jsonPath("$.statusCounts.PUBLISHED").isEqualTo(2);
    }

    @Test
    void shouldClearAllTrades() {
        webTestClient.delete()
                .uri("/api/v1/trades/clear")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("All trades cleared from memory");
    }

    @Test
    void shouldReturnHealthCheck() {
        webTestClient.get()
                .uri("/api/v1/trades/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("instructions-capture-service");
    }
}
