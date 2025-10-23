package com.example.instructions.util;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.model.PlatformTrade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for transforming trade data between different formats
 * Handles sensitive data masking and normalization
 */
@Slf4j
@Component
public class TradeTransformer {

    private static final Map<String, String> TRADE_TYPE_MAPPINGS = Map.of(
            "BUY", "B",
            "PURCHASE", "B",
            "SELL", "S",
            "SALE", "S",
            "SHORT", "SS",
            "SHORT_SELL", "SS"
    );

    private static final Pattern SECURITY_PATTERN = Pattern.compile("^[A-Z0-9]{3,12}$");

    /**
     * Transform canonical trade to platform-specific format
     * Applies masking and normalization rules
     */
    public PlatformTrade transformToPlatformTrade(CanonicalTrade canonicalTrade) {
        log.debug("Transforming canonical trade to platform format for trade: {}", canonicalTrade.getTradeId());

        try {
            String maskedAccount = maskAccountNumber(canonicalTrade.getAccountNumber());
            String normalizedSecurity = normalizeSecurityId(canonicalTrade.getSecurityId());
            String normalizedTradeType = normalizeTradeType(canonicalTrade.getTradeType());

            PlatformTrade.TradeDetails tradeDetails = PlatformTrade.TradeDetails.builder()
                    .account(maskedAccount)
                    .security(normalizedSecurity)
                    .type(normalizedTradeType)
                    .amount(canonicalTrade.getAmount())
                    .timestamp(Instant.from(canonicalTrade.getTimestamp()))
                    .build();

            PlatformTrade platformTrade = PlatformTrade.builder()
                    .platformId(canonicalTrade.getPlatformId())
                    .trade(tradeDetails)
                    .build();

            log.debug("Successfully transformed trade {} to platform format",
                    canonicalTrade.getTradeId());

            return platformTrade;
        } catch (Exception e) {
            log.error("Error transforming trade {} to platform format: {}", canonicalTrade.getTradeId(), e.getMessage());
            throw new IllegalArgumentException("Failed to transform trade: " + e.getMessage(), e);
        }
    }

    /**
     * Mask account number showing only the last 4 digits
     * Example: "123456789" -> "*****6789"
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be null or empty");
        }

        String cleanAccountNumber = accountNumber.trim();
        if (cleanAccountNumber.length() < 4) {
            throw new IllegalArgumentException("Account number must be at least 4 characters");
        }

        String lastFourDigits = cleanAccountNumber.substring(cleanAccountNumber.length() - 4);
        int maskLength = Math.max(0, cleanAccountNumber.length() - 4);
        String maskedPortion = new String(new char[maskLength]).replace('\0', '*');

        return maskedPortion + lastFourDigits;
    }

    /**
     * Normalize security ID to uppercase and validate a format
     */
    public String normalizeSecurityId(String securityId) {
        if (securityId == null || securityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Security ID cannot be null or empty");
        }

        String normalizedSecurityId = securityId.trim().toUpperCase();

        if (!SECURITY_PATTERN.matcher(normalizedSecurityId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid security ID format. Must be 3-12 alphanumeric characters: " + securityId);
        }

        return normalizedSecurityId;
    }

    /**
     * Normalize trade type to standard codes
     * Maps various trade type inputs to standardized codes
     */
    public String normalizeTradeType(String tradeType) {
        if (tradeType == null || tradeType.trim().isEmpty()) {
            throw new IllegalArgumentException("Trade type cannot be null or empty");
        }

        String upperTradeType = tradeType.trim().toUpperCase();

        String mappedType = TRADE_TYPE_MAPPINGS.get(upperTradeType);
        if (mappedType != null) {
            return mappedType;
        }

        for (Map.Entry<String, String> entry : TRADE_TYPE_MAPPINGS.entrySet()) {
            if (upperTradeType.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // If no mapping found, return the first character as fallback, but log a warning
        log.warn("Unknown trade type '{}', using first character as fallback", tradeType);

        return upperTradeType.substring(0, 1);
    }

    /**
     * Validate that canonical trade has required fields
     */
    public void validateCanonicalTrade(CanonicalTrade trade) {
        if (trade == null) {
            throw new IllegalArgumentException("Trade cannot be null");
        }

        if (trade.getAccountNumber() == null || trade.getAccountNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Account number is required");
        }

        if (trade.getSecurityId() == null || trade.getSecurityId().trim().isEmpty()) {
            throw new IllegalArgumentException("Security ID is required");
        }

        if (trade.getTradeType() == null || trade.getTradeType().trim().isEmpty()) {
            throw new IllegalArgumentException("Trade type is required");
        }

        if (trade.getAmount() == null || trade.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (trade.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        if (trade.getPlatformId() == null || trade.getPlatformId().trim().isEmpty()) {
            throw new IllegalArgumentException("Platform ID is required");
        }
    }
}
