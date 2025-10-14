package com.example.instructions.service;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.model.PlatformTrade;
import com.example.instructions.util.TradeTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Core service for processing trade instructions
 * Handles in-memory storage and transformation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeTransformer tradeTransformer;
    private final KafkaPublisher kafkaPublisher;
    private final ObjectMapper objectMapper;

    /**
     * In-memory storage for canonical trades
     */
    private final Map<String, CanonicalTrade> tradeStorage = new ConcurrentHashMap<>();

    private final AtomicLong tradeIdCounter = new AtomicLong(1);

    /**
     * Process trade instruction - main method
     */
    @Async
    public void processTradeInstruction(CanonicalTrade canonicalTrade) {
        log.info("Processing trade instruction: {}", canonicalTrade.tradeId());

        try {
            // Store canonical trade
            CanonicalTrade storedTrade = storeTrade(canonicalTrade);

            // Validate trade
            tradeTransformer.validateCanonicalTrade(storedTrade);
            CanonicalTrade validatedTrade = createTradeWithStatus(storedTrade, CanonicalTrade.TradeStatus.VALIDATED);
            updateTrade(validatedTrade);

            // Transform to platform format
            PlatformTrade platformTrade = tradeTransformer.transformToPlatformTrade(validatedTrade);
            CanonicalTrade transformedTrade = createTradeWithStatus(validatedTrade, CanonicalTrade.TradeStatus.TRANSFORMED);
            updateTrade(transformedTrade);

            // Publish to Kafka
            kafkaPublisher.publishTrade(platformTrade)
                    .whenComplete((result, ex) -> {
                        CanonicalTrade finalTrade;
                        if (ex != null) {
                            log.error("Failed to publish trade {}: {}", transformedTrade.tradeId(), ex.getMessage());
                            finalTrade = createTradeWithStatus(transformedTrade, CanonicalTrade.TradeStatus.FAILED);
                        } else {
                            log.info("Successfully published trade {}", transformedTrade.tradeId());
                            finalTrade = createTradeWithStatus(transformedTrade, CanonicalTrade.TradeStatus.PUBLISHED);
                        }
                        updateTrade(finalTrade);
                    });

        } catch (Exception e) {
            log.error("Error processing trade instruction {}: {}", canonicalTrade.tradeId(), e.getMessage(), e);
            CanonicalTrade failedTrade = createTradeWithStatus(canonicalTrade, CanonicalTrade.TradeStatus.FAILED);
            updateTrade(failedTrade);
            throw e;
        }
    }

    /**
     * Process file upload - handles CSV and JSON files
     */
    public List<String> processFileUpload(MultipartFile file) {
        log.info("Processing file upload: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());

        try {
            // Validate file is not empty
            if (file.isEmpty() || file.getSize() == 0) {
                throw new IllegalArgumentException("File is empty");
            }

            String filename = file.getOriginalFilename();
            if (filename == null) {
                throw new IllegalArgumentException("File name cannot be null");
            }

            List<CanonicalTrade> trades;

            if (filename.toLowerCase().endsWith(".csv")) {
                trades = processCsvFile(file);
            } else if (filename.toLowerCase().endsWith(".json")) {
                trades = processJsonFile(file);
            } else {
                throw new IllegalArgumentException("Unsupported file format. Only CSV and JSON files are accepted.");
            }

            log.info("Successfully parsed {} trades from file: {}", trades.size(), filename);

            // Process each trade asynchronously

            return trades.stream()
                    .map(trade -> {
                        CanonicalTrade tradeWithSource = createTradeWithSource(trade, "FILE_UPLOAD");
                        processTradeInstruction(tradeWithSource);
                        return tradeWithSource.tradeId();
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error processing file upload: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process file upload: " + e.getMessage(), e);
        }
    }

    /**
     * Process CSV file format
     * Expected columns: account_number,security_id,trade_type,amount,timestamp,platform_id
     */
    private List<CanonicalTrade> processCsvFile(MultipartFile file) throws IOException {
        List<CanonicalTrade> trades = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // Skip header line
                    continue;
                }

                String[] fields = line.split(",");
                if (fields.length != 6) {
                    log.warn("Skipping invalid CSV line (expected 6 fields, got {}): {}", fields.length, line);
                    continue;
                }

                try {
                    CanonicalTrade trade = CanonicalTrade.builder()
                            .tradeId(generateTradeId())
                            .accountNumber(fields[0].trim())
                            .securityId(fields[1].trim())
                            .tradeType(fields[2].trim())
                            .amount(new BigDecimal(fields[3].trim()))
                            .timestamp(parseTimestamp(fields[4].trim()))
                            .platformId(fields[5].trim())
                            .status(CanonicalTrade.TradeStatus.RECEIVED)
                            .build();

                    trades.add(trade);

                } catch (Exception e) {
                    log.warn("Skipping invalid CSV line due to parsing error: {} - Error: {}", line, e.getMessage());
                }
            }
        }

        return trades;
    }

    /**
     * Process JSON file format - can be single trade or array of trades
     */
    private List<CanonicalTrade> processJsonFile(MultipartFile file) throws IOException {
        List<CanonicalTrade> trades = new ArrayList<>();

        try {
            String jsonContent = new String(file.getBytes());

            // Try to parse as array first
            if (jsonContent.trim().startsWith("[")) {
                CanonicalTrade[] tradeArray = objectMapper.readValue(jsonContent, CanonicalTrade[].class);
                trades.addAll(Arrays.asList(tradeArray));
            } else {
                // Parse as single trade object
                CanonicalTrade trade = objectMapper.readValue(jsonContent, CanonicalTrade.class);
                trades.add(trade);
            }

            // Ensure trade IDs are generated if not present and create new instances with proper values
            List<CanonicalTrade> processedTrades = new ArrayList<>();
            for (CanonicalTrade trade : trades) {
                String tradeId = (trade.tradeId() == null || trade.tradeId().trim().isEmpty()) ? generateTradeId() : trade.tradeId();
                CanonicalTrade.TradeStatus status = (trade.status() == null) ? CanonicalTrade.TradeStatus.RECEIVED : trade.status();

                CanonicalTrade processedTrade = CanonicalTrade.builder()
                        .tradeId(tradeId)
                        .accountNumber(trade.accountNumber())
                        .securityId(trade.securityId())
                        .tradeType(trade.tradeType())
                        .amount(trade.amount())
                        .timestamp(trade.timestamp())
                        .platformId(trade.platformId())
                        .source(trade.source())
                        .status(status)
                        .processedAt(trade.processedAt())
                        .build();

                processedTrades.add(processedTrade);
            }
            trades = processedTrades;

        } catch (Exception e) {
            log.error("Error parsing JSON file: {}", e.getMessage());
            throw new IOException("Failed to parse JSON file: " + e.getMessage(), e);
        }

        return trades;
    }

    /**
     * Parse timestamp from string with multiple format support
     */
    private LocalDateTime parseTimestamp(String timestampStr) {
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }

        throw new IllegalArgumentException("Unable to parse timestamp: " + timestampStr);
    }

    /**
     * Store trade in memory
     */
    public CanonicalTrade storeTrade(CanonicalTrade trade) {
        CanonicalTrade tradeToStore = trade;

        if (trade.tradeId() == null) {
            tradeToStore = CanonicalTrade.builder()
                    .tradeId(generateTradeId())
                    .accountNumber(trade.accountNumber())
                    .securityId(trade.securityId())
                    .tradeType(trade.tradeType())
                    .amount(trade.amount())
                    .timestamp(trade.timestamp())
                    .platformId(trade.platformId())
                    .source(trade.source())
                    .status(trade.status())
                    .processedAt(trade.processedAt())
                    .build();
        }
        tradeStorage.put(tradeToStore.tradeId(), tradeToStore);

        log.debug("Stored trade in memory: {}", tradeToStore.tradeId());

        return tradeToStore;
    }

    /**
     * Update existing trade
     */
    public void updateTrade(CanonicalTrade trade) {
        if (trade.tradeId() != null && tradeStorage.containsKey(trade.tradeId())) {
            tradeStorage.put(trade.tradeId(), trade);
            log.debug("Updated trade in memory: {}", trade.tradeId());
        }
    }

    /**
     * Retrieve trade by ID
     */
    public Optional<CanonicalTrade> getTradeById(String tradeId) {
        return Optional.ofNullable(tradeStorage.get(tradeId));
    }

    /**
     * Get all trades with optional status filter
     */
    public List<CanonicalTrade> getAllTrades(CanonicalTrade.TradeStatus status) {
        if (status == null) {
            return new ArrayList<>(tradeStorage.values());
        }

        return tradeStorage.values().stream()
                .filter(trade -> trade.status() == status)
                .collect(Collectors.toList());
    }

    /**
     * Create a new trade instance with updated status
     */
    private CanonicalTrade createTradeWithStatus(CanonicalTrade trade, CanonicalTrade.TradeStatus status) {
        return CanonicalTrade.builder()
                .tradeId(trade.tradeId())
                .accountNumber(trade.accountNumber())
                .securityId(trade.securityId())
                .tradeType(trade.tradeType())
                .amount(trade.amount())
                .timestamp(trade.timestamp())
                .platformId(trade.platformId())
                .source(trade.source())
                .status(status)
                .processedAt(trade.processedAt())
                .build();
    }

    /**
     * Create a new trade instance with updated source
     */
    private CanonicalTrade createTradeWithSource(CanonicalTrade trade, String source) {
        return CanonicalTrade.builder()
                .tradeId(trade.tradeId())
                .accountNumber(trade.accountNumber())
                .securityId(trade.securityId())
                .tradeType(trade.tradeType())
                .amount(trade.amount())
                .timestamp(trade.timestamp())
                .platformId(trade.platformId())
                .source(source)
                .status(trade.status())
                .processedAt(trade.processedAt())
                .build();
    }

    /**
     * Generate unique trade ID
     */
    private String generateTradeId() {
        return "TRADE-" + System.currentTimeMillis() + "-" + tradeIdCounter.getAndIncrement();
    }

    /**
     * Clear all trades from memory (useful for testing)
     */
    public void clearAllTrades() {
        tradeStorage.clear();
        log.info("Cleared all trades from memory");
    }

    /**
     * Get trade statistics
     */
    public Map<String, Object> getTradeStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalTrades", tradeStorage.size());

        Map<CanonicalTrade.TradeStatus, Long> statusCounts = tradeStorage.values().stream()
                .collect(Collectors.groupingBy(
                        CanonicalTrade::status,
                        Collectors.counting()
                ));

        stats.put("statusCounts", statusCounts);

        return stats;
    }
}
