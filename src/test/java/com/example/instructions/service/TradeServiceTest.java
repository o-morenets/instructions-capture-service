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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeTransformer tradeTransformer;

    @Mock
    private KafkaPublisher kafkaPublisher;

    @Mock
    private MultipartFile multipartFile;

    private TradeService tradeService;

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
    void shouldGenerateTradeIdWhenNull() {
        CanonicalTrade baseTrade = createSampleCanonicalTrade();
        CanonicalTrade trade = CanonicalTrade.builder()
                .tradeId(null)
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
        assertThat(storedTrade.getTradeId()).startsWith("TRADE-");
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

        List<CanonicalTrade> receivedTrades = tradeService.getAllTrades(CanonicalTrade.TradeStatus.RECEIVED);
        List<CanonicalTrade> allTrades = tradeService.getAllTrades(null);

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
    void shouldProcessCsvFile() throws IOException {
        String csvContent = "account_number,security_id,trade_type,amount,timestamp,platform_id\n" +
                "123456789,ABC123,BUY,100000,2025-08-04 21:15:33,ACCT123\n" +
                "987654321,XYZ789,SELL,50000,2025-08-04 21:16:33,ACCT456";

        when(multipartFile.getOriginalFilename()).thenReturn("trades.csv");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(csvContent.getBytes()));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn((long) csvContent.length());

        doNothing().when(tradeTransformer).validateCanonicalTrade(any());
        when(tradeTransformer.transformToPlatformTrade(any()))
                .thenReturn(createSamplePlatformTrade());
        when(kafkaPublisher.publishTrade(any(PlatformTrade.class)))
                .thenReturn(mock(java.util.concurrent.CompletableFuture.class));

        List<String> tradeIds = tradeService.processFileUploadReactive(multipartFile).block();

        assertThat(tradeIds).hasSize(2);
        assertThat(tradeIds).allMatch(id -> id.startsWith("TRADE-"));
    }

    @Test
    void shouldProcessJsonFile() throws IOException {
        String jsonContent = "{\"accountNumber\":\"123456789\",\"securityId\":\"ABC123\"," +
                "\"tradeType\":\"BUY\",\"amount\":100000,\"timestamp\":\"2025-08-04T21:15:33\"," +
                "\"platformId\":\"ACCT123\"}";

        when(multipartFile.getOriginalFilename()).thenReturn("trade.json");
        when(multipartFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(jsonContent.getBytes()));
        when(multipartFile.getBytes()).thenReturn(jsonContent.getBytes());
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn((long) jsonContent.length());

        doNothing().when(tradeTransformer).validateCanonicalTrade(any());
        when(tradeTransformer.transformToPlatformTrade(any()))
                .thenReturn(createSamplePlatformTrade());
        when(kafkaPublisher.publishTrade(any(PlatformTrade.class)))
                .thenReturn(mock(java.util.concurrent.CompletableFuture.class));

        List<String> tradeIds = tradeService.processFileUploadReactive(multipartFile).block();

        assertThat(tradeIds).hasSize(1);
        assertThat(tradeIds.getFirst()).startsWith("TRADE-");
    }

    @Test
    void shouldRejectInvalidFileFormat() {
        when(multipartFile.getOriginalFilename()).thenReturn("invalid.txt");
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(100L);

        assertThatThrownBy(() -> tradeService.processFileUploadReactive(multipartFile).block())
                .hasMessageContaining("Unsupported file format");
    }

    @Test
    void shouldRejectEmptyFile() {
        when(multipartFile.getOriginalFilename()).thenReturn("empty.csv");
        when(multipartFile.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> tradeService.processFileUploadReactive(multipartFile).block())
                .hasMessageContaining("File is empty");
    }

    @Test
    void shouldClearAllTrades() {
        tradeService.storeTrade(createSampleCanonicalTrade());
        assertThat(tradeService.getAllTrades(null)).isNotEmpty();

        tradeService.clearAllTrades();

        assertThat(tradeService.getAllTrades(null)).isEmpty();
    }

    private CanonicalTrade createSampleCanonicalTrade() {
        return CanonicalTrade.builder()
                .tradeId("TRADE-123")
                .accountNumber("123456789")
                .securityId("ABC123")
                .tradeType("BUY")
                .amount(new BigDecimal("100000"))
                .timestamp(LocalDateTime.of(2025, 8, 4, 21, 15, 33))
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
                        .timestamp(LocalDateTime.of(2025, 8, 4, 21, 15, 33))
                        .build())
                .build();
    }
}
