package com.example.instructions.util;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.model.PlatformTrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TradeTransformerTest {

    private TradeTransformer tradeTransformer;

    @BeforeEach
    void setUp() {
        tradeTransformer = new TradeTransformer();
    }

    @Test
    void shouldMaskAccountNumber() {
        String accountNumber = "123456789";
        
        String maskedAccount = tradeTransformer.maskAccountNumber(accountNumber);
        
        assertThat(maskedAccount).isEqualTo("*****6789");
    }

    @Test
    void shouldMaskShortAccountNumber() {
        String accountNumber = "1234";
        
        String maskedAccount = tradeTransformer.maskAccountNumber(accountNumber);
        
        assertThat(maskedAccount).isEqualTo("1234");
    }

    @Test
    void shouldThrowExceptionForInvalidAccountNumber() {
        assertThatThrownBy(() -> tradeTransformer.maskAccountNumber(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account number cannot be null or empty");

        assertThatThrownBy(() -> tradeTransformer.maskAccountNumber(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account number cannot be null or empty");

        assertThatThrownBy(() -> tradeTransformer.maskAccountNumber("123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account number must be at least 4 characters");
    }

    @Test
    void shouldNormalizeSecurityId() {
        String securityId = "abc123";
        
        String normalizedSecurityId = tradeTransformer.normalizeSecurityId(securityId);
        
        assertThat(normalizedSecurityId).isEqualTo("ABC123");
    }

    @Test
    void shouldThrowExceptionForInvalidSecurityId() {
        assertThatThrownBy(() -> tradeTransformer.normalizeSecurityId("AB"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid security ID format");

        assertThatThrownBy(() -> tradeTransformer.normalizeSecurityId("TOOLONGSECURITYID123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid security ID format");

        assertThatThrownBy(() -> tradeTransformer.normalizeSecurityId("ABC-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid security ID format");
    }

    @Test
    void shouldNormalizeTradeTypes() {
        assertThat(tradeTransformer.normalizeTradeType("BUY")).isEqualTo("B");
        assertThat(tradeTransformer.normalizeTradeType("PURCHASE")).isEqualTo("B");
        assertThat(tradeTransformer.normalizeTradeType("SELL")).isEqualTo("S");
        assertThat(tradeTransformer.normalizeTradeType("SALE")).isEqualTo("S");
        assertThat(tradeTransformer.normalizeTradeType("SHORT")).isEqualTo("SS");
        assertThat(tradeTransformer.normalizeTradeType("SHORT_SELL")).isEqualTo("SS");
        
        // Case insensitive
        assertThat(tradeTransformer.normalizeTradeType("buy")).isEqualTo("B");
        assertThat(tradeTransformer.normalizeTradeType("sell")).isEqualTo("S");
    }

    @Test
    void shouldHandleUnknownTradeType() {
        String unknownType = "UNKNOWN";
        
        String normalizedType = tradeTransformer.normalizeTradeType(unknownType);
        
        assertThat(normalizedType).isEqualTo("U");
    }

    @Test
    void shouldTransformToPlatformTrade() {
        CanonicalTrade canonicalTrade = CanonicalTrade.builder()
            .tradeId("TRADE-123")
            .accountNumber("123456789")
            .securityId("abc123")
            .tradeType("BUY")
            .amount(new BigDecimal("100000"))
            .timestamp(LocalDateTime.of(2025, 8, 4, 21, 15, 33))
            .platformId("ACCT123")
            .build();

        PlatformTrade platformTrade = tradeTransformer.transformToPlatformTrade(canonicalTrade);

        assertThat(platformTrade).isNotNull();
        assertThat(platformTrade.platformId()).isEqualTo("ACCT123");
        
        PlatformTrade.TradeDetails tradeDetails = platformTrade.trade();
        assertThat(tradeDetails.account()).isEqualTo("*****6789");
        assertThat(tradeDetails.security()).isEqualTo("ABC123");
        assertThat(tradeDetails.type()).isEqualTo("B");
        assertThat(tradeDetails.amount()).isEqualTo(new BigDecimal("100000"));
        assertThat(tradeDetails.timestamp()).isEqualTo(LocalDateTime.of(2025, 8, 4, 21, 15, 33));
    }

    @Test
    void shouldValidateCanonicalTrade() {
        CanonicalTrade validTrade = CanonicalTrade.builder()
            .tradeId("TRADE-123")
            .accountNumber("123456789")
            .securityId("ABC123")
            .tradeType("BUY")
            .amount(new BigDecimal("100000"))
            .timestamp(LocalDateTime.now())
            .platformId("ACCT123")
            .build();

        assertThatCode(() -> tradeTransformer.validateCanonicalTrade(validTrade))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowExceptionForInvalidTrade() {
        CanonicalTrade invalidTrade = CanonicalTrade.builder()
            .tradeId("TRADE-123")
            .accountNumber(null)
            .securityId("ABC123")
            .tradeType("BUY")
            .amount(new BigDecimal("100000"))
            .timestamp(LocalDateTime.now())
            .platformId("ACCT123")
            .source("TEST")
            .status(CanonicalTrade.TradeStatus.RECEIVED)
            .processedAt(LocalDateTime.now())
            .build();

        assertThatThrownBy(() -> tradeTransformer.validateCanonicalTrade(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Trade cannot be null");

        assertThatThrownBy(() -> tradeTransformer.validateCanonicalTrade(invalidTrade))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account number is required");

        CanonicalTrade invalidTradeSecurityId = CanonicalTrade.builder()
            .tradeId("TRADE-123")
            .accountNumber("123456789")
            .securityId(null)
            .tradeType("BUY")
            .amount(new BigDecimal("100000"))
            .timestamp(LocalDateTime.now())
            .platformId("ACCT123")
            .source("TEST")
            .status(CanonicalTrade.TradeStatus.RECEIVED)
            .processedAt(LocalDateTime.now())
            .build();
        
        assertThatThrownBy(() -> tradeTransformer.validateCanonicalTrade(invalidTradeSecurityId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Security ID is required");
    }
}
