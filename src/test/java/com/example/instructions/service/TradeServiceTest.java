package com.example.instructions.service;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.model.PlatformTrade;
import com.example.instructions.util.TradeTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeTransformer tradeTransformer;

    @Mock
    private KafkaPublisher kafkaPublisher;

    private TradeService tradeService;
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tradeService = new TradeService(tradeTransformer, kafkaPublisher, objectMapper);
        tradeService.clearAllTrades();
    }

    @Test
    void shouldStoreTrade() {
        CanonicalTrade trade = createSampleCanonicalTrade();

        tradeService.storeTrade(trade);

        Optional<CanonicalTrade> retrievedTrade = tradeService.getTradeById(trade.getTradeId());
        assertThat(retrievedTrade).isPresent();
        assertThat(retrievedTrade.get()).isEqualTo(trade);
    }

    @Test
    void shouldStoreTradeWithUUID() {
        CanonicalTrade baseTrade = createSampleCanonicalTrade();
        String uuid = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d";
        CanonicalTrade trade = CanonicalTrade.builder()
                .tradeId(uuid)
                .accountNumber(baseTrade.getAccountNumber())
                .securityId(baseTrade.getSecurityId())
                .tradeType(baseTrade.getTradeType())
                .amount(baseTrade.getAmount())
                .timestamp(baseTrade.getTimestamp())
                .platformId(baseTrade.getPlatformId())
                .source(baseTrade.getSource())
                .status(baseTrade.getStatus())
                .processedAt(baseTrade.getProcessedAt())
                .build();

        CanonicalTrade storedTrade = tradeService.storeTrade(trade);

        assertThat(storedTrade.getTradeId()).isNotNull();
        assertThat(storedTrade.getTradeId()).isEqualTo(uuid);
    }

    @Test
    void shouldGetAllTradesWithStatusFilter() {
        CanonicalTrade baseTrade = createSampleCanonicalTrade();
        CanonicalTrade trade1 = CanonicalTrade.builder()
                .tradeId(baseTrade.getTradeId())
                .accountNumber(baseTrade.getAccountNumber())
                .securityId(baseTrade.getSecurityId())
                .tradeType(baseTrade.getTradeType())
                .amount(baseTrade.getAmount())
                .timestamp(baseTrade.getTimestamp())
                .platformId(baseTrade.getPlatformId())
                .source(baseTrade.getSource())
                .status(CanonicalTrade.TradeStatus.RECEIVED)
                .processedAt(baseTrade.getProcessedAt())
                .build();

        CanonicalTrade trade2 = CanonicalTrade.builder()
                .tradeId("TRADE-2")
                .accountNumber(baseTrade.getAccountNumber())
                .securityId(baseTrade.getSecurityId())
                .tradeType(baseTrade.getTradeType())
                .amount(baseTrade.getAmount())
                .timestamp(baseTrade.getTimestamp())
                .platformId(baseTrade.getPlatformId())
                .source(baseTrade.getSource())
                .status(CanonicalTrade.TradeStatus.VALIDATED)
                .processedAt(baseTrade.getProcessedAt())
                .build();

        tradeService.storeTrade(trade1);
        tradeService.storeTrade(trade2);

        List<CanonicalTrade> receivedTrades = tradeService.getAllTrades(CanonicalTrade.TradeStatus.RECEIVED)
                .collectList().block();
        List<CanonicalTrade> allTrades = tradeService.getAllTrades(null)
                .collectList().block();

        assertThat(receivedTrades).hasSize(1);
        assertThat(receivedTrades.getFirst().getStatus()).isEqualTo(CanonicalTrade.TradeStatus.RECEIVED);
        assertThat(allTrades).hasSize(2);
    }

    @Test
    void shouldGetTradeStatistics() {
        CanonicalTrade baseTrade = createSampleCanonicalTrade();
        CanonicalTrade trade1 = CanonicalTrade.builder()
                .tradeId(baseTrade.getTradeId())
                .accountNumber(baseTrade.getAccountNumber())
                .securityId(baseTrade.getSecurityId())
                .tradeType(baseTrade.getTradeType())
                .amount(baseTrade.getAmount())
                .timestamp(baseTrade.getTimestamp())
                .platformId(baseTrade.getPlatformId())
                .source(baseTrade.getSource())
                .status(CanonicalTrade.TradeStatus.RECEIVED)
                .processedAt(baseTrade.getProcessedAt())
                .build();

        CanonicalTrade trade2 = CanonicalTrade.builder()
                .tradeId("TRADE-2")
                .accountNumber(baseTrade.getAccountNumber())
                .securityId(baseTrade.getSecurityId())
                .tradeType(baseTrade.getTradeType())
                .amount(baseTrade.getAmount())
                .timestamp(baseTrade.getTimestamp())
                .platformId(baseTrade.getPlatformId())
                .source(baseTrade.getSource())
                .status(CanonicalTrade.TradeStatus.PUBLISHED)
                .processedAt(baseTrade.getProcessedAt())
                .build();

        tradeService.storeTrade(trade1);
        tradeService.storeTrade(trade2);

        Map<String, Object> stats = tradeService.getTradeStatistics();

        assertThat(stats.get("totalTrades")).isEqualTo(2);
        assertThat(stats.get("statusCounts")).isInstanceOf(Map.class);
    }

    @Test
    void shouldProcessCsvFile() {
        String csvContent = """
                trade_id,account_number,security_id,trade_type,amount,timestamp,platform_id
                a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d,123456789,ABC123,BUY,100000,2025-08-04T21:15:33Z,ACCT123
                b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e,987654321,XYZ789,SELL,50000,2025-08-04T21:16:33Z,ACCT456""";

        FilePart filePart = createFilePart("trades.csv", csvContent);

        doNothing().when(tradeTransformer).validateCanonicalTrade(any());
        when(tradeTransformer.transformToPlatformTrade(any()))
                .thenReturn(createSamplePlatformTrade());
        SendResult<String, PlatformTrade> mockSendResult = mock(SendResult.class);
        when(kafkaPublisher.publishTrade(any(PlatformTrade.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult));

        List<String> tradeIds = tradeService.processFileUploadReactive(filePart).collectList().block();

        assertThat(tradeIds).hasSize(2);
        assertThat(tradeIds).contains("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d", "b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e");
    }

    @Test
    void shouldProcessJsonFile() {
        String jsonContent = "{\"tradeId\":\"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d\",\"accountNumber\":\"123456789\",\"securityId\":\"ABC123\"," +
                "\"tradeType\":\"BUY\",\"amount\":100000,\"timestamp\":\"2025-08-04T21:15:33Z\"," +
                "\"platformId\":\"ACCT123\"}";

        FilePart filePart = createFilePart("trade.json", jsonContent);

        doNothing().when(tradeTransformer).validateCanonicalTrade(any());
        when(tradeTransformer.transformToPlatformTrade(any()))
                .thenReturn(createSamplePlatformTrade());
        SendResult<String, PlatformTrade> mockSendResult = mock(SendResult.class);
        when(kafkaPublisher.publishTrade(any(PlatformTrade.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult));

        List<String> tradeIds = tradeService.processFileUploadReactive(filePart).collectList().block();

        assertThat(tradeIds).hasSize(1);
        assertThat(tradeIds.getFirst()).isEqualTo("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d");
    }

    @Test
    void shouldProcessJsonArrayWithMultipleTrades() {
        // JSON array with 3 trades (same structure as sample-trades-array.json)
        String jsonArray = """
                [
                  {
                    "tradeId": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
                    "accountNumber": "123456789",
                    "securityId": "ABC123",
                    "tradeType": "BUY",
                    "amount": 100000,
                    "timestamp": "2025-08-04T21:15:33Z",
                    "platformId": "ACCT123"
                  },
                  {
                    "tradeId": "b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e",
                    "accountNumber": "987654321",
                    "securityId": "XYZ789",
                    "tradeType": "SELL",
                    "amount": 50000,
                    "timestamp": "2025-08-04T21:16:33Z",
                    "platformId": "ACCT456"
                  },
                  {
                    "tradeId": "c3d4e5f6-a7b8-4c5d-0e1f-2a3b4c5d6e7f",
                    "accountNumber": "555666777",
                    "securityId": "DEF456",
                    "tradeType": "PURCHASE",
                    "amount": 75000,
                    "timestamp": "2025-08-04T21:17:33Z",
                    "platformId": "ACCT789"
                  }
                ]
                """;

        FilePart filePart = createFilePart("sample-trades-array.json", jsonArray);

        doNothing().when(tradeTransformer).validateCanonicalTrade(any());
        when(tradeTransformer.transformToPlatformTrade(any()))
                .thenReturn(createSamplePlatformTrade());
        SendResult<String, PlatformTrade> mockSendResult = mock(SendResult.class);
        when(kafkaPublisher.publishTrade(any(PlatformTrade.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult));

        List<String> tradeIds = tradeService.processFileUploadReactive(filePart).collectList().block();

        // Verify 3 trades were processed
        assertThat(tradeIds).hasSize(3);
        assertThat(tradeIds).contains(
                "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
                "b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e",
                "c3d4e5f6-a7b8-4c5d-0e1f-2a3b4c5d6e7f"
        );

        // Verify transformer was called 3 times
        verify(tradeTransformer, times(3)).validateCanonicalTrade(any(CanonicalTrade.class));
        verify(tradeTransformer, times(3)).transformToPlatformTrade(any(CanonicalTrade.class));
        verify(kafkaPublisher, times(3)).publishTrade(any(PlatformTrade.class));
    }

    @Test
    void shouldRejectInvalidFileFormat() {
        FilePart filePart = createFilePart("invalid.txt", "some content");

        assertThatThrownBy(() -> tradeService.processFileUploadReactive(filePart).collectList().block())
                .hasMessageContaining("Unsupported file format");
    }

    @Test
    void shouldRejectEmptyFile() {
        FilePart filePart = createFilePart("empty.csv", "");

        // Empty file will pass validation but return no results
        List<String> result = tradeService.processFileUploadReactive(filePart).collectList().block();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldClearAllTrades() {
        tradeService.storeTrade(createSampleCanonicalTrade());
        assertThat(tradeService.getAllTrades(null).collectList().block()).isNotEmpty();

        tradeService.clearAllTrades();

        assertThat(tradeService.getAllTrades(null).collectList().block()).isEmpty();
    }

    private CanonicalTrade createSampleCanonicalTrade() {
        return CanonicalTrade.builder()
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

    private PlatformTrade createSamplePlatformTrade() {
        return PlatformTrade.builder()
                .platformId("ACCT123")
                .trade(PlatformTrade.TradeDetails.builder()
                        .account("*****6789")
                        .security("ABC123")
                        .type("B")
                        .amount(new BigDecimal("100000"))
                        .timestamp(Instant.parse("2025-08-04T21:15:33Z"))
                        .build())
                .build();
    }

    /**
     * Helper method to create a mock FilePart for testing
     */
    private FilePart createFilePart(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        DataBuffer dataBuffer = bufferFactory.wrap(bytes);

        FilePart filePart = mock(FilePart.class);
        when(filePart.filename()).thenReturn(filename);
        lenient().when(filePart.content()).thenReturn(Flux.just(dataBuffer));

        return filePart;
    }
}
