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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for trade instruction processing
 * Handles file uploads and trade management operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Validated
@Tag(name = "Trade Instructions", description = "API for processing trade instructions via file upload")
public class TradeController {

    private final TradeService tradeService;

    /**
     * Upload and process trade instructions file using reactive streaming (Flux)
     * Provides better memory management and backpressure for large files
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload trade instructions file (Reactive)",
            description = "Upload and process a CSV or JSON file using reactive streaming with Flux. " +
                    "Provides better memory management and backpressure for large files. " +
                    "CSV format: account_number,security_id,trade_type,amount,timestamp,platform_id. " +
                    "JSON format: Single trade object or array of trade objects."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or content"),
            @ApiResponse(responseCode = "413", description = "File too large"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> uploadTradeFile(
            @Parameter(description = "Trade instructions file (CSV or JSON)")
            @RequestParam("file") @NotNull MultipartFile file) {

        log.info("Received reactive file upload request: {} (size: {} bytes)",
                file.getOriginalFilename(), file.getSize());

        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("File is empty"));
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".csv") &&
                    !filename.toLowerCase().endsWith(".json"))) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Only CSV and JSON files are supported"));
            }

            // Process reactively and block to get result (for MVC controller)
            List<String> tradeIds = tradeService.processFileUploadReactive(file)
                    .block(); // Block to bridge reactive to imperative

            int tradeCount = tradeIds != null ? tradeIds.size() : 0;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File processed successfully");
            response.put("filename", filename);
            response.put("tradesProcessed", tradeCount);

            log.info("Successfully processed file upload: {} with {} trades",
                    filename, tradeCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing file upload: {}", e.getMessage(), e);

            return ResponseEntity.status(500)
                    .body(createErrorResponse("Failed to process file: " + e.getMessage()));
        }
    }

    /**
     * Get all trades with optional status filter
     */
    @GetMapping
    @Operation(
            summary = "Get all trades with optional status filter",
            description = "Retrieve all trades, optionally filtered by status"
    )
    @ApiResponse(responseCode = "200", description = "Trades retrieved successfully")
    public ResponseEntity<List<CanonicalTrade>> getAllTrades(
            @Parameter(description = "Filter by trade status (optional)")
            @RequestParam(value = "status", required = false) CanonicalTrade.TradeStatus status) {

        log.debug("Getting all trades with status filter: {}", status);

        List<CanonicalTrade> trades = tradeService.getAllTrades(status);

        return ResponseEntity.ok(trades);
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
    public ResponseEntity<CanonicalTrade> getTradeById(
            @Parameter(description = "Trade ID")
            @PathVariable String tradeId) {

        log.debug("Getting trade by ID: {}", tradeId);

        Optional<CanonicalTrade> trade = tradeService.getTradeById(tradeId);

        return trade.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
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
    public ResponseEntity<Map<String, Object>> clearAllTrades() {
        log.info("Clearing all trades from memory");

        tradeService.clearAllTrades();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "All trades cleared from memory");

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the trade processing service is healthy")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "instructions-capture-service");
        health.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(health);
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
    public ResponseEntity<Map<String, Object>> getTradeStatistics() {
        log.debug("Getting trade statistics");

        Map<String, Object> stats = tradeService.getTradeStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * Create error response map
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());

        return error;
    }
}
