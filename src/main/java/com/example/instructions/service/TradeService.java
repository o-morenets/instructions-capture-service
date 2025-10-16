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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
     * Process trade instruction (async for Kafka listener)
     */
    @Async
    public void processTradeInstruction(CanonicalTrade canonicalTrade) {
        log.info("Processing trade instruction: {}", canonicalTrade.getTradeId());

        try {
            // Use common processing logic
            CanonicalTrade storedTrade = validateAndTransformTrade(canonicalTrade);

            // Publish to Kafka asynchronously
            publishTradeToKafka(storedTrade);

        } catch (Exception e) {
            log.error("Error processing trade instruction {}: {}", canonicalTrade.getTradeId(), e.getMessage(), e);
            canonicalTrade.setStatus(CanonicalTrade.TradeStatus.FAILED);
            updateTrade(canonicalTrade);
            throw e;
        }
    }

    private CanonicalTrade validateAndTransformTrade(CanonicalTrade trade) {
        tradeTransformer.validateCanonicalTrade(trade);
        trade.setStatus(CanonicalTrade.TradeStatus.VALIDATED);

        return storeTrade(trade);
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

    private String generateTradeId() {
        return "TRADE-" + System.currentTimeMillis() + "-" + tradeIdCounter.getAndIncrement();
    }

    private void updateTrade(CanonicalTrade trade) {
        if (trade.getTradeId() != null && tradeStorage.containsKey(trade.getTradeId())) {
            tradeStorage.put(trade.getTradeId(), trade);
            log.debug("Updated trade in memory: {}", trade.getTradeId());
        }
    }

    private void publishTradeToKafka(CanonicalTrade storedTrade) {
        PlatformTrade platformTrade = tradeTransformer.transformToPlatformTrade(storedTrade);

        kafkaPublisher.publishTrade(platformTrade)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        storedTrade.setStatus(CanonicalTrade.TradeStatus.FAILED);
                    } else {
                        storedTrade.setStatus(CanonicalTrade.TradeStatus.PUBLISHED);
                    }
                    updateTrade(storedTrade);
                });
    }

    /**
     * Process file upload using reactive Flux streaming
     */
    public Mono<List<String>> processFileUploadReactive(MultipartFile file) {
        log.info("Processing file upload reactively: {} (size: {} bytes)",
                file.getOriginalFilename(), file.getSize());

        return Mono.defer(() -> {
            // Validate file
            if (file.isEmpty() || file.getSize() == 0) {
                return Mono.error(new IllegalArgumentException("File is empty"));
            }

            String filename = file.getOriginalFilename();
            if (filename == null) {
                return Mono.error(new IllegalArgumentException("File name cannot be null"));
            }

            // Determine file type and create appropriate Flux
            Flux<CanonicalTrade> tradeFlux;

            if (filename.toLowerCase().endsWith(".csv")) {
                tradeFlux = processCsvFileReactive(file);
            } else if (filename.toLowerCase().endsWith(".json")) {
                tradeFlux = processJsonFileReactive(file);
            } else {
                return Mono.error(new IllegalArgumentException(
                        "Unsupported file format. Only CSV and JSON files are accepted."));
            }

            // Process trades with controlled parallelism and backpressure
            return tradeFlux
                    .doOnNext(trade -> trade.setSource("FILE_UPLOAD"))
                    .flatMap(trade -> processTradeReactive(trade)
                                    .onErrorResume(error -> {
                                        log.error("Error processing trade {}: {}",
                                                trade.getTradeId(), error.getMessage());
                                        return Mono.empty(); // Skip failed trades
                                    }),
                            8) // Concurrency level - process 8 trades in parallel
                    .map(CanonicalTrade::getTradeId)
                    .collectList()
                    .doOnSuccess(tradeIds ->
                            log.info("Successfully processed {} trades from file: {}",
                                    tradeIds.size(), filename))
                    .doOnError(error ->
                            log.error("Error processing file upload: {}", error.getMessage(), error));
        });
    }

    private Flux<CanonicalTrade> processCsvFileReactive(MultipartFile file) {
        return Flux.using(
                // Resource factory - open the file
                () -> {
                    try {
                        return new BufferedReader(new InputStreamReader(file.getInputStream()));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to open CSV file", e);
                    }
                },
                // Stream factory - create Flux from lines
                reader -> Flux.fromStream(reader.lines())
                        .skip(1) // Skip header
                        .flatMap(line -> Mono.justOrEmpty(parseCsvLine(line)))
                        .subscribeOn(Schedulers.boundedElastic()), // Use IO thread pool
                // Resource cleanup
                reader -> {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        log.error("Error closing CSV reader: {}", e.getMessage());
                    }
                }
        );
    }

    private CanonicalTrade parseCsvLine(String line) {
        try {
            String[] fields = line.split(",");
            if (fields.length != 6) {
                log.warn("Skipping invalid CSV line (expected 6 fields, got {}): {}",
                        fields.length, line);
                return null;
            }

            return CanonicalTrade.builder()
                    .tradeId(generateTradeId())
                    .accountNumber(fields[0].trim())
                    .securityId(fields[1].trim())
                    .tradeType(fields[2].trim())
                    .amount(new BigDecimal(fields[3].trim()))
                    .timestamp(parseTimestamp(fields[4].trim()))
                    .platformId(fields[5].trim())
                    .status(CanonicalTrade.TradeStatus.RECEIVED)
                    .build();
        } catch (Exception e) {
            log.warn("Skipping invalid CSV line due to parsing error: {} - Error: {}",
                    line, e.getMessage());
            return null;
        }
    }

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

    private Flux<CanonicalTrade> processJsonFileReactive(MultipartFile file) {
        return Mono.fromCallable(() -> {
                    try {
                        // Read file content
                        byte[] bytes = file.getBytes();
                        if (bytes.length == 0) {
                            throw new IllegalArgumentException("File is empty");
                        }
                        String content = new String(bytes);
                        content = content.trim();

                        log.debug("Parsing JSON content (length: {}): {}", content.length(),
                                content.length() > 200 ? content.substring(0, 200) + "..." : content);

                        // Check if it's an array or single object
                        if (content.startsWith("[")) {

                            // Parse as array
                            CanonicalTrade[] trades = objectMapper.readValue(content, CanonicalTrade[].class);
                            log.debug("Parsed {} trades from JSON array", trades.length);
                            return Arrays.asList(trades);
                        } else if (content.startsWith("{")) {

                            // Parse as single object
                            CanonicalTrade trade = objectMapper.readValue(content, CanonicalTrade.class);
                            log.debug("Parsed 1 trade from JSON object");
                            return List.of(trade);
                        } else {
                            throw new IllegalArgumentException("Invalid JSON format");
                        }
                    } catch (IOException e) {
                        log.error("Failed to parse JSON", e);
                        throw new RuntimeException("Failed to parse JSON file", e);
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .map(this::processJsonTrade)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private CanonicalTrade processJsonTrade(CanonicalTrade trade) {
        if (trade.getTradeId() == null || trade.getTradeId().trim().isEmpty()) {
            trade.setTradeId(generateTradeId());
        }
        if (trade.getStatus() == null) {
            trade.setStatus(CanonicalTrade.TradeStatus.RECEIVED);
        }
        return trade;
    }

    private Mono<CanonicalTrade> processTradeReactive(CanonicalTrade trade) {
        return Mono.fromCallable(() -> validateAndTransformTrade(trade))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(storedTrade -> {

                    // Transform to platform format for Kafka
                    PlatformTrade platformTrade = tradeTransformer.transformToPlatformTrade(storedTrade);
                    storedTrade.setStatus(CanonicalTrade.TradeStatus.TRANSFORMED);
                    updateTrade(storedTrade);

                    // Publish to Kafka asynchronously - don't fail the whole operation if Kafka fails
                    return Mono.fromFuture(kafkaPublisher.publishTrade(platformTrade))
                            .map(result -> {
                                log.debug("Successfully published trade {} to Kafka", storedTrade.getTradeId());
                                storedTrade.setStatus(CanonicalTrade.TradeStatus.PUBLISHED);
                                updateTrade(storedTrade);
                                return storedTrade;
                            })
                            .onErrorResume(error -> {
                                log.error("Failed to publish trade {}: {}",
                                        storedTrade.getTradeId(), error.getMessage());
                                storedTrade.setStatus(CanonicalTrade.TradeStatus.FAILED);
                                updateTrade(storedTrade);

                                // Return the trade anyway, don't propagate the error
                                return Mono.just(storedTrade);
                            });
                })
                .onErrorResume(error -> {

                    // Handle errors during validation/transformation
                    log.error("Error processing trade {}: {}",
                            trade.getTradeId(), error.getMessage(), error);
                    trade.setStatus(CanonicalTrade.TradeStatus.FAILED);

                    // Return empty to skip this trade but continue processing others
                    return Mono.empty();
                });
    }

    /**
     * Get all trades with optional status filter
     */
    public Flux<CanonicalTrade> getAllTrades(CanonicalTrade.TradeStatus status) {
        if (status == null) {
            return Flux.fromIterable(tradeStorage.values());
        }

        return Flux.fromIterable(tradeStorage.values().stream()
                .filter(trade -> trade.getStatus() == status)
                .collect(Collectors.toList())
        );
    }

    /**
     * Retrieve trade by ID
     */
    public Optional<CanonicalTrade> getTradeById(String tradeId) {
        return Optional.ofNullable(tradeStorage.get(tradeId));
    }

    /**
     * Clear all trades from memory
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
