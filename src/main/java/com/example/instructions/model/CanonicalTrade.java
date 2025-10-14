package com.example.instructions.model;

import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Canonical trade representation - normalized internal format
 * Used as intermediate format before platform-specific transformation
 */
@Builder
public record CanonicalTrade(

        String tradeId,

        @NotBlank(message = "Account number is required")
        String accountNumber,

        @NotBlank(message = "Security ID is required")
        @Pattern(regexp = "^[A-Z0-9]{3,12}$", message = "Security ID must be 3-12 alphanumeric characters")
        String securityId,

        @NotBlank(message = "Trade type is required")
        String tradeType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Timestamp is required")
        LocalDateTime timestamp,

        @NotBlank(message = "Platform ID is required")
        String platformId,

        String source,

        TradeStatus status,

        LocalDateTime processedAt
) {
    /**
     * Compact constructor with default values
     */
    public CanonicalTrade {
        if (status == null) {
            status = TradeStatus.RECEIVED;
        }
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }


    public enum TradeStatus {
        RECEIVED,
        VALIDATED,
        TRANSFORMED,
        PUBLISHED,
        FAILED
    }
}
