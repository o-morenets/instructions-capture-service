package com.example.instructions.service;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.model.PlatformTrade;
import com.example.instructions.util.TradeTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test to verify JSON array processing with sample-trades-array.json structure
 */
class JsonArrayProcessingTest {

    @Mock
    private TradeTransformer tradeTransformer;

    @Mock
    private KafkaPublisher kafkaPublisher;

    @Mock
    private MultipartFile multipartFile;

    private TradeService tradeService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        tradeService = new TradeService(tradeTransformer, kafkaPublisher, objectMapper);
    }

    @Test
    void shouldProcessJsonArrayWithMultipleTrades() throws IOException {
        // JSON array with 3 trades (same structure as sample-trades-array.json)
        String jsonArray = """
                [
                  {
                    "accountNumber": "123456789",
                    "securityId": "ABC123",
                    "tradeType": "BUY",
                    "amount": 100000,
                    "timestamp": "2025-08-04T21:15:33",
                    "platformId": "ACCT123"
                  },
                  {
                    "accountNumber": "987654321",
                    "securityId": "XYZ789",
                    "tradeType": "SELL",
                    "amount": 50000,
                    "timestamp": "2025-08-04T21:16:33",
                    "platformId": "ACCT456"
                  },
                  {
                    "accountNumber": "555666777",
                    "securityId": "DEF456",
                    "tradeType": "PURCHASE",
                    "amount": 75000,
                    "timestamp": "2025-08-04T21:17:33",
                    "platformId": "ACCT789"
                  }
                ]
                """;

        when(multipartFile.getOriginalFilename()).thenReturn("sample-trades-array.json");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(jsonArray.getBytes()));
        when(multipartFile.getBytes()).thenReturn(jsonArray.getBytes());
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn((long) jsonArray.length());

        doNothing().when(tradeTransformer).validateCanonicalTrade(any());
        when(tradeTransformer.transformToPlatformTrade(any()))
                .thenReturn(createSamplePlatformTrade());
        when(kafkaPublisher.publishTrade(any(PlatformTrade.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        List<String> tradeIds = tradeService.processFileUploadReactive(multipartFile).block();

        // Verify 3 trades were processed
        assertThat(tradeIds).hasSize(3);
        assertThat(tradeIds).allMatch(id -> id.startsWith("TRADE-"));
        
        // Verify transformer was called 3 times
        verify(tradeTransformer, times(3)).validateCanonicalTrade(any(CanonicalTrade.class));
        verify(tradeTransformer, times(3)).transformToPlatformTrade(any(CanonicalTrade.class));
        verify(kafkaPublisher, times(3)).publishTrade(any(PlatformTrade.class));
        
        System.out.println("✅ Successfully processed JSON array with 3 trades");
        System.out.println("   Trade IDs: " + tradeIds);
    }

    private PlatformTrade createSamplePlatformTrade() {
        return PlatformTrade.builder()
                .platformId("ACCT123")
                .trade(PlatformTrade.TradeDetails.builder()
                        .account("123456789")
                        .security("ABC123")
                        .type("BUY")
                        .amount(new java.math.BigDecimal("100000"))
                        .timestamp(java.time.LocalDateTime.parse("2025-08-04T21:15:33"))
                        .build())
                .build();
    }
}

