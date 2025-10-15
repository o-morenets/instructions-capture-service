package com.example.instructions.model;

import lombok.*;
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
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class CanonicalTrade {

    private String tradeId;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "Security ID is required")
    @Pattern(regexp = "^[A-Z0-9]{3,12}$", message = "Security ID must be 3-12 alphanumeric characters")
    private String securityId;

    @NotBlank(message = "Trade type is required")
    private String tradeType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;

    @NotBlank(message = "Platform ID is required")
    private String platformId;

    private String source;

    @Builder.Default
    private TradeStatus status = TradeStatus.RECEIVED;

    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();

    public enum TradeStatus {
        RECEIVED,
        VALIDATED,
        TRANSFORMED,
        PUBLISHED,
        FAILED
    }
}
