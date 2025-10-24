package com.example.instructions.controller;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST Controller for trade instruction processing
 * Handles file uploads and trade management operations
 */
@RestController
@RequestMapping("/api/v1/trades")
@Slf4j
@RequiredArgsConstructor
@Validated
@Tag(name = "Trade Instructions", description = "API for processing trade instructions via file upload")
public class TradeController {

    private final TradeService tradeService;

    /**
     * Upload and process trade instructions file using reactive streaming (Flux)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload trade instructions file (Reactive)",
            description = "Upload and process a CSV or JSON file using reactive streaming with Flux. " +
                    "Provides better memory management and backpressure for large files. " +
                    "CSV format: tradeId,account_number,security_id,trade_type,amount,timestamp,platform_id. " +
                    "JSON format: Single trade object or array of trade objects."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or content"),
            @ApiResponse(responseCode = "413", description = "File too large"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<Map<String, Object>> uploadTradeFile(
            @Parameter(description = "Trade instructions file (CSV or JSON)")
            @RequestPart("file") @NotNull Mono<FilePart> filePartMono
    ) {
        return filePartMono.flatMap(filePart -> {
            log.info("Received reactive file upload request: {}", filePart.filename());

            return tradeService.processFileUploadReactive(filePart)
                    .collectList()
                    .map(tradeIds -> {
                        Map<String, Object> response = Map.of(
                                "success", true,
                                "message", "File processed successfully",
                                "filename", filePart.filename(),
                                "tradesProcessed", tradeIds.size()
                        );

                        log.info("Successfully processed file upload: {} with {} trades",
                                filePart.filename(), tradeIds.size());

                        return response;
                    });
        });
    }

    /**
     * Get all trades with an optional status filter (fully reactive with WebFlux)
     * Streams trades as newline-delimited JSON (NDJSON) for better client compatibility
     */
    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(
            summary = "Stream all trades with optional status filter",
            description = "Retrieve all trades as NDJSON stream (fully reactive, auto-closes connection)"
    )
    @ApiResponse(responseCode = "200", description = "Trades streaming successfully")
    public Flux<CanonicalTrade> getAllTrades(
            @Parameter(description = "Filter by trade status (optional)")
            @RequestParam(value = "status", required = false) CanonicalTrade.TradeStatus status) {

        log.info("Getting all trades with status filter: {}", status);

        return tradeService.getAllTrades(status)
                .doOnComplete(() -> log.info("Completed streaming {} trades", status));
    }

    /**
     * Get trade by ID
     */
    @GetMapping("/{tradeId}")
    @Operation(summary = "Get trade by ID", description = "Retrieve a specific trade by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trade found"),
            @ApiResponse(responseCode = "404", description = "Trade not found")
    })
    public Mono<CanonicalTrade> getTradeById(
            @Parameter(description = "Trade ID")
            @PathVariable String tradeId) {

        log.debug("Getting trade by ID: {}", tradeId);

        return Mono.justOrEmpty(tradeService.getTradeById(tradeId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade not found")));
    }

    /**
     * Clear all trades from memory (for testing/admin purposes)
     */
    @DeleteMapping("/clear")
    @Operation(
            summary = "Clear all trades",
            description = "Clear all trades from in-memory storage (for testing purposes)"
    )
    @ApiResponse(responseCode = "200", description = "All trades cleared successfully")
    public Mono<Map<String, Object>> clearAllTrades() {
        log.info("Clearing all trades from memory");

        return Mono.fromRunnable(tradeService::clearAllTrades)
                .then(Mono.just(Map.of(
                        "success", true,
                        "message", "All trades cleared from memory"
                )));
    }

    /**
     * Get trade processing statistics
     */
    @GetMapping("/statistics")
    @Operation(
            summary = "Get trade processing statistics",
            description = "Get statistics about processed trades including counts by status"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    public Mono<Map<String, Object>> getTradeStatistics() {
        log.debug("Getting trade statistics");

        return Mono.fromCallable(tradeService::getTradeStatistics);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the trade processing service is healthy")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public Mono<Map<String, Object>> healthCheck() {
        return Mono.just(Map.of(
                "status", "UP",
                "service", "instructions-capture-service",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
