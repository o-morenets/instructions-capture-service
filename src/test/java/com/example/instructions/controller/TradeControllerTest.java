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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
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
        // Given
        String csvContent = "account_number,security_id,trade_type,amount,timestamp,platform_id\n" +
                "123456789,ABC123,BUY,100000,2025-08-04 21:15:33,ACCT123";
        MockMultipartFile file = new MockMultipartFile("file", "trades.csv", "text/csv", csvContent.getBytes());

        when(tradeService.processFileUpload(any())).thenReturn(Arrays.asList("TRADE-1", "TRADE-2"));

        // When & Then
        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.filename").value("trades.csv"))
                .andExpect(jsonPath("$.tradesProcessed").value(2))
                .andExpect(jsonPath("$.tradeIds").isArray());
    }

    @Test
    void shouldUploadJsonFileSuccessfully() throws Exception {
        // Given
        String jsonContent = "{\"accountNumber\":\"123456789\",\"securityId\":\"ABC123\"}";
        MockMultipartFile file = new MockMultipartFile("file", "trade.json", "application/json", jsonContent.getBytes());

        when(tradeService.processFileUpload(any())).thenReturn(List.of("TRADE-1"));

        // When & Then
        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.filename").value("trade.json"))
                .andExpect(jsonPath("$.tradesProcessed").value(1));
    }

    @Test
    void shouldRejectEmptyFile() throws Exception {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        // When & Then
        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    void shouldRejectUnsupportedFileFormat() throws Exception {
        // Given
        MockMultipartFile invalidFile = new MockMultipartFile("file", "invalid.txt", "text/plain", "content".getBytes());

        // When & Then
        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(invalidFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Only CSV and JSON files are supported"));
    }

    @Test
    void shouldRejectLargeFile() throws Exception {
        // Given
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.csv", "text/csv", largeContent);

        // When & Then
        mockMvc.perform(multipart("/api/v1/trades/upload")
                        .file(largeFile))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("File size exceeds 10MB limit"));
    }

    @Test
    void shouldGetTradeById() throws Exception {
        // Given
        when(tradeService.getTradeById("TRADE-123")).thenReturn(Optional.of(sampleTrade));

        // When & Then
        mockMvc.perform(get("/api/v1/trades/TRADE-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("TRADE-123"))
                .andExpect(jsonPath("$.accountNumber").value("123456789"))
                .andExpect(jsonPath("$.securityId").value("ABC123"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentTrade() throws Exception {
        // Given
        when(tradeService.getTradeById("NON-EXISTENT")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/trades/NON-EXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllTrades() throws Exception {
        // Given
        List<CanonicalTrade> trades = Collections.singletonList(sampleTrade);
        when(tradeService.getAllTrades(null)).thenReturn(trades);

        // When & Then
        mockMvc.perform(get("/api/v1/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].tradeId").value("TRADE-123"));
    }

    @Test
    void shouldGetTradesWithStatusFilter() throws Exception {
        // Given
        List<CanonicalTrade> trades = Collections.singletonList(sampleTrade);
        when(tradeService.getAllTrades(eq(CanonicalTrade.TradeStatus.RECEIVED))).thenReturn(trades);

        // When & Then
        mockMvc.perform(get("/api/v1/trades")
                        .param("status", "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].status").value("RECEIVED"));
    }

    @Test
    void shouldGetTradeStatistics() throws Exception {
        // Given
        when(tradeService.getTradeStatistics()).thenReturn(
                java.util.Map.of("totalTrades", 5, "statusCounts", java.util.Map.of("RECEIVED", 3L, "PUBLISHED", 2L))
        );

        // When & Then
        mockMvc.perform(get("/api/v1/trades/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(5))
                .andExpect(jsonPath("$.statusCounts.RECEIVED").value(3))
                .andExpect(jsonPath("$.statusCounts.PUBLISHED").value(2));
    }

    @Test
    void shouldClearAllTrades() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/trades/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("All trades cleared from memory"));
    }

    @Test
    void shouldReturnHealthCheck() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/trades/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("instructions-capture-service"));
    }
}
