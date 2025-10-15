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
import java.util.stream.Stream;

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
        log.info("Processing trade instruction: {}", canonicalTrade.getTradeId());

        try {
            // Store canonical trade
            CanonicalTrade storedTrade = storeTrade(canonicalTrade);

            // Validate trade
            tradeTransformer.validateCanonicalTrade(storedTrade);
            storedTrade.setStatus(CanonicalTrade.TradeStatus.VALIDATED);
            updateTrade(storedTrade);

            // Transform to platform format
            PlatformTrade platformTrade = tradeTransformer.transformToPlatformTrade(storedTrade);
            storedTrade.setStatus(CanonicalTrade.TradeStatus.TRANSFORMED);
            updateTrade(storedTrade);

            // Publish to Kafka
            kafkaPublisher.publishTrade(platformTrade)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish trade {}: {}", storedTrade.getTradeId(), ex.getMessage());
                            storedTrade.setStatus(CanonicalTrade.TradeStatus.FAILED);
                        } else {
                            log.info("Successfully published trade {}", storedTrade.getTradeId());
                            storedTrade.setStatus(CanonicalTrade.TradeStatus.PUBLISHED);
                        }
                        updateTrade(storedTrade);
                    });

        } catch (Exception e) {
            log.error("Error processing trade instruction {}: {}", canonicalTrade.getTradeId(), e.getMessage(), e);
            canonicalTrade.setStatus(CanonicalTrade.TradeStatus.FAILED);
            updateTrade(canonicalTrade);
            throw e;
        }
    }

    /**
     * Process file upload using stream-based processing for efficiency
     * Benefits: Lower memory footprint, faster time-to-first-result, handles large files efficiently
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

            // Stream-based processing: trades are processed as they're parsed, not accumulated
            Stream<CanonicalTrade> tradeStream;

            if (filename.toLowerCase().endsWith(".csv")) {
                tradeStream = processCsvFileStream(file);
            } else if (filename.toLowerCase().endsWith(".json")) {
                tradeStream = processJsonFileStream(file);
            } else {
                throw new IllegalArgumentException("Unsupported file format. Only CSV and JSON files are accepted.");
            }

            // Process trades in parallel for better performance
            // Each trade is transformed and processed immediately without waiting for full file parse
            List<String> tradeIds = tradeStream
                    .parallel()  // Enable parallel processing
                    .map(trade -> {
                        trade.setSource("FILE_UPLOAD");
                        processTradeInstruction(trade);
                        return trade.getTradeId();
                    })
                    .collect(Collectors.toList());

            log.info("Successfully processed {} trades from file: {}", tradeIds.size(), filename);
            return tradeIds;

        } catch (Exception e) {
            log.error("Error processing file upload: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process file upload: " + e.getMessage(), e);
        }
    }

    /**
     * Process CSV file using stream-based approach (memory efficient)
     * Lazily evaluates each line - no accumulation in memory
     * Expected columns: account_number,security_id,trade_type,amount,timestamp,platform_id
     */
    private Stream<CanonicalTrade> processCsvFileStream(MultipartFile file) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
            
            return reader.lines()
                    .skip(1)  // Skip header line
                    .map(line -> parseCsvLine(line))
                    .filter(Optional::isPresent)  // Filter out parsing failures
                    .map(Optional::get)
                    .onClose(() -> {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            log.error("Error closing CSV reader: {}", e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Error opening CSV file: {}", e.getMessage());
            return Stream.empty();
        }
    }
    
    /**
     * Parse a single CSV line into CanonicalTrade
     * Returns empty Optional if parsing fails
     */
    private Optional<CanonicalTrade> parseCsvLine(String line) {
        try {
            String[] fields = line.split(",");
            if (fields.length != 6) {
                log.warn("Skipping invalid CSV line (expected 6 fields, got {}): {}", fields.length, line);
                return Optional.empty();
            }

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

            return Optional.of(trade);
        } catch (Exception e) {
            log.warn("Skipping invalid CSV line due to parsing error: {} - Error: {}", line, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Process JSON file using Jackson streaming API (memory efficient)
     * Supports both single trade object and array of trades
     * Uses streaming parser to handle large JSON files without loading entire content into memory
     */
    private Stream<CanonicalTrade> processJsonFileStream(MultipartFile file) {
        try {
            com.fasterxml.jackson.core.JsonParser parser = objectMapper.getFactory().createParser(file.getInputStream());
            
            // Check if it's an array or single object
            com.fasterxml.jackson.core.JsonToken token = parser.nextToken();
            
            if (token == null) {
                log.error("Empty JSON file");
                parser.close();
                return Stream.empty();
            }
            
            if (token == com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
                // Stream array elements - parser is positioned at START_ARRAY
                return parseJsonArray(parser);
            } else if (token == com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                // Single object - parser is positioned at START_OBJECT, readValue will consume it
                try {
                    CanonicalTrade trade = objectMapper.readValue(parser, CanonicalTrade.class);
                    return Stream.of(processJsonTrade(trade));
                } finally {
                    try {
                        parser.close();
                    } catch (IOException e) {
                        log.warn("Error closing JSON parser: {}", e.getMessage());
                    }
                }
            } else {
                log.error("Invalid JSON format - expected object or array, got: {}", token);
                parser.close();
                return Stream.empty();
            }
        } catch (IOException e) {
            log.error("Error parsing JSON file: {}", e.getMessage(), e);
            return Stream.empty();
        }
    }
    
    /**
     * Parse JSON array using streaming API
     * Returns a stream of trades parsed one at a time
     * Parser must be positioned at START_ARRAY token
     * 
     * Note: Parser access is sequential to avoid thread-safety issues.
     * Parallel processing happens AFTER parsing in processFileUpload().
     */
    private Stream<CanonicalTrade> parseJsonArray(com.fasterxml.jackson.core.JsonParser parser) {
        List<CanonicalTrade> trades = new ArrayList<>();
        try {
            com.fasterxml.jackson.core.JsonToken token;
            while ((token = parser.nextToken()) != null && token != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                if (token == com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                    try {
                        CanonicalTrade trade = objectMapper.readValue(parser, CanonicalTrade.class);
                        trades.add(processJsonTrade(trade));
                    } catch (IOException e) {
                        log.warn("Error parsing JSON trade object: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error parsing JSON array: {}", e.getMessage());
        } finally {
            try {
                parser.close();
            } catch (IOException e) {
                log.warn("Error closing JSON parser: {}", e.getMessage());
            }
        }
        return trades.stream();
    }
    
    /**
     * Process a parsed JSON trade - ensure trade ID and status are set
     */
    private CanonicalTrade processJsonTrade(CanonicalTrade trade) {
        if (trade.getTradeId() == null || trade.getTradeId().trim().isEmpty()) {
            trade.setTradeId(generateTradeId());
        }
        if (trade.getStatus() == null) {
            trade.setStatus(CanonicalTrade.TradeStatus.RECEIVED);
        }
        return trade;
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
        if (trade.getTradeId() == null) {
            trade.setTradeId(generateTradeId());
        }
        tradeStorage.put(trade.getTradeId(), trade);

        log.debug("Stored trade in memory: {}", trade.getTradeId());

        return trade;
    }

    /**
     * Update existing trade
     */
    public void updateTrade(CanonicalTrade trade) {
        if (trade.getTradeId() != null && tradeStorage.containsKey(trade.getTradeId())) {
            tradeStorage.put(trade.getTradeId(), trade);
            log.debug("Updated trade in memory: {}", trade.getTradeId());
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
                .filter(trade -> trade.getStatus() == status)
                .collect(Collectors.toList());
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
                        CanonicalTrade::getStatus,
                        Collectors.counting()
                ));

        stats.put("statusCounts", statusCounts);

        return stats;
    }
}
