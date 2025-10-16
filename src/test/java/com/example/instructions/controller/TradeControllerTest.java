package com.example.instructions.controller;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeService tradeService;

    private CanonicalTrade sampleTrade;

    @BeforeEach
    void setUp() {
        sampleTrade = CanonicalTrade.builder()
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

    @Test
    void shouldUploadCsvFileSuccessfully() throws Exception {
        String csvContent = "account_number,security_id,trade_type,amount,timestamp,platform_id\n" +
                "123456789,ABC123,BUY,100000,2025-08-04 21:15:33,ACCT123";
        MockMultipartFile file = new MockMultipartFile("file", "trades.csv", "text/csv", csvContent.getBytes());

        when(tradeService.processFileUploadReactive(any())).thenReturn(Mono.just(Arrays.asList("TRADE-1", "TRADE-2")));

        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.filename").value("trades.csv"))
                .andExpect(jsonPath("$.tradesProcessed").value(2));
    }

    @Test
    void shouldUploadJsonFileSuccessfully() throws Exception {
        String jsonContent = "{\"accountNumber\":\"123456789\",\"securityId\":\"ABC123\"}";
        MockMultipartFile file = new MockMultipartFile("file", "trade.json", "application/json", jsonContent.getBytes());

        when(tradeService.processFileUploadReactive(any())).thenReturn(Mono.just(List.of("TRADE-1")));

        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.filename").value("trade.json"))
                .andExpect(jsonPath("$.tradesProcessed").value(1));
    }

    @Test
    void shouldRejectEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        // Mock service to throw IllegalArgumentException like it does in real code
        when(tradeService.processFileUploadReactive(any()))
                .thenReturn(Mono.error(new IllegalArgumentException("File is empty")));

        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Validation failed: File is empty"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldRejectUnsupportedFileFormat() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile("file", "invalid.txt", "text/plain", "content".getBytes());

        // Mock service to throw IllegalArgumentException like it does in real code
        when(tradeService.processFileUploadReactive(any()))
                .thenReturn(Mono.error(new IllegalArgumentException("Unsupported file format. Only CSV and JSON files are accepted.")));

        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(invalidFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Validation failed: Unsupported file format. Only CSV and JSON files are accepted."))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldGetTradeById() throws Exception {
        when(tradeService.getTradeById("TRADE-123")).thenReturn(Optional.of(sampleTrade));

        mockMvc.perform(get("/api/v1/trades/TRADE-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("TRADE-123"))
                .andExpect(jsonPath("$.accountNumber").value("123456789"))
                .andExpect(jsonPath("$.securityId").value("ABC123"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentTrade() throws Exception {
        when(tradeService.getTradeById("NON-EXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trades/NON-EXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllTrades() throws Exception {
        Flux<CanonicalTrade> trades = Flux.just(sampleTrade);
        when(tradeService.getAllTrades(null)).thenReturn(trades);

        mockMvc.perform(get("/api/v1/trades"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/x-ndjson"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TRADE-123")));
    }

    @Test
    void shouldGetTradesWithStatusFilter() throws Exception {
        Flux<CanonicalTrade> trades = Flux.just(sampleTrade);
        when(tradeService.getAllTrades(eq(CanonicalTrade.TradeStatus.RECEIVED))).thenReturn(trades);

        mockMvc.perform(get("/api/v1/trades")
                        .param("status", "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/x-ndjson"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RECEIVED")));
    }

    @Test
    void shouldGetTradeStatistics() throws Exception {
        when(tradeService.getTradeStatistics()).thenReturn(
                java.util.Map.of("totalTrades", 5, "statusCounts", java.util.Map.of("RECEIVED", 3L, "PUBLISHED", 2L))
        );

        mockMvc.perform(get("/api/v1/trades/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(5))
                .andExpect(jsonPath("$.statusCounts.RECEIVED").value(3))
                .andExpect(jsonPath("$.statusCounts.PUBLISHED").value(2));
    }

    @Test
    void shouldClearAllTrades() throws Exception {
        mockMvc.perform(delete("/api/v1/trades/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("All trades cleared from memory"));
    }

    @Test
    void shouldReturnHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/trades/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("instructions-capture-service"));
    }
}
