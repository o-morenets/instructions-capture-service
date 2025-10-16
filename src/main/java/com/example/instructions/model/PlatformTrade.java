package com.example.instructions.model;

import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Platform-specific trade format for accounting platform
 */
@Builder
public record PlatformTrade(

        @JsonProperty("platform_id")
        String platformId,

        @JsonProperty("trade")
        TradeDetails trade
) {
    @Builder
    public record TradeDetails(
            @JsonProperty("account")
            String account,

            @JsonProperty("security")
            String security,

            @JsonProperty("type")
            String type,

            @JsonProperty("amount")
            BigDecimal amount,

            @JsonProperty("timestamp")
            Instant timestamp
    ) {
    }
}
