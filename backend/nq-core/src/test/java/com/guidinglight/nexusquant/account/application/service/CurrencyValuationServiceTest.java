package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.common.currency.CurrencyCode;
import com.guidinglight.nexusquant.common.currency.CurrencyDefinition;
import com.guidinglight.nexusquant.common.currency.CurrencyValuationService;
import com.guidinglight.nexusquant.common.currency.CurrencyValuationService.Price;
import com.guidinglight.nexusquant.common.currency.ValuationMetadata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 独立于行情请求验证估值边界；明确测试价格只是夹具，不冒充真实市场报价。 */
class CurrencyValuationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-16T03:00:00Z");
    private static final Duration MAX_AGE = Duration.ofHours(2);
    private final CurrencyValuationService service =
            new CurrencyValuationService(
                    () ->
                            List.of(
                                    new CurrencyDefinition("USDT", "STABLECOIN", "Tether"),
                                    new CurrencyDefinition("USD", "FIAT", "US Dollar"),
                                    new CurrencyDefinition("CNY", "FIAT", "Chinese Yuan")));

    private Price price(String base, String quote, String rate, Instant observed, Instant visible) {
        return new Price(
                base,
                quote,
                new BigDecimal(rate),
                observed,
                visible,
                "EXPLICIT_TEST_FIXTURE",
                "fixture:17");
    }

    @Test
    void extensibleCodeIsNotAnEnablementOrAssetWhitelist() {
        assertEquals("BNB", CurrencyCode.require("BNB"));
        assertEquals("HKD", CurrencyCode.require("HKD"));
        for (String bad : List.of("usdt", " USDT", "U", "USD-USDT", "A".repeat(33)))
            assertThrows(IllegalArgumentException.class, () -> CurrencyCode.require(bad));
        assertTrue(service.supportsTarget("USDT"));
        assertFalse(service.supportsTarget("USD"));
        assertFalse(service.supportsTarget("CNY"));
        assertEquals(
                "TARGET_UNSUPPORTED",
                service.value("USDT", BigDecimal.ONE, "USD", NOW, MAX_AGE, null).status());
    }

    @Test
    void registryIsReadOnceAndValuationDoesNotIssuePerAssetQueries() {
        var reads = new AtomicInteger();
        var instance =
                new CurrencyValuationService(
                        () -> {
                            reads.incrementAndGet();
                            return List.of(new CurrencyDefinition("USDT", "STABLECOIN", "Tether"));
                        });
        assertEquals(0, reads.get(), "装配不能提前读取目录；必须等到实际估值请求");
        for (int index = 0; index < 100; index++)
            instance.value("USDT", BigDecimal.ONE, "USDT", NOW, MAX_AGE, null);
        assertEquals(1, reads.get());
    }

    @Test
    void identityIsExactButStablecoinsNeverDefaultToParity() {
        var amount = new BigDecimal("123.456789012345678901");
        assertEquals(amount, service.value("USDT", amount, "USDT", NOW, MAX_AGE, null).value());
        assertNull(service.value("USD", amount, "USDT", NOW, MAX_AGE, null).value());
        assertNull(service.value("USDC", amount, "USDT", NOW, MAX_AGE, null).value());
        var conversion =
                service.value(
                        "USDC",
                        new BigDecimal("100"),
                        "USDT",
                        NOW,
                        MAX_AGE,
                        price("USDC", "USDT", "0.997", NOW.minusSeconds(1), NOW));
        assertEquals(new BigDecimal("99.700"), conversion.value());
        assertEquals("fixture:17", conversion.price().sourceId());
        assertEquals(new BigDecimal("100"), conversion.amount());
    }

    @Test
    void invalidFutureExpiredAndWrongDirectionPricesCannotProduceAmounts() {
        var bad =
                List.of(
                        price("USDT", "BTC", "2", NOW, NOW),
                        price("BTC", "USDC", "2", NOW, NOW),
                        price("BTC", "USDT", "0", NOW, NOW),
                        price("BTC", "USDT", "-1", NOW, NOW),
                        price("BTC", "USDT", "2", NOW.plusNanos(1), NOW.plusNanos(1)),
                        price("BTC", "USDT", "2", NOW.minusSeconds(1), NOW.plusNanos(1)),
                        price("BTC", "USDT", "2", NOW.minusSeconds(7201), NOW),
                        price("BTC", "USDT", "2", NOW, NOW.minusSeconds(1)),
                        new Price("BTC", "USDT", BigDecimal.ONE, NOW, NOW, "", "fixture"));
        for (Price p : bad)
            assertNull(service.value("BTC", BigDecimal.ONE, "USDT", NOW, MAX_AGE, p).value());
        assertNotNull(
                service.value(
                                "BTC",
                                BigDecimal.ONE,
                                "USDT",
                                NOW,
                                MAX_AGE,
                                price("BTC", "USDT", "2", NOW.minusSeconds(7200), NOW))
                        .value());
    }

    @Test
    void zeroBalanceNeedsNoRateAndMissingNonzeroAssetInvalidatesTheTotal() {
        var zero = service.value("BNB", BigDecimal.ZERO, "USDT", NOW, MAX_AGE, null);
        var cash = service.value("USDT", new BigDecimal("100"), "USDT", NOW, MAX_AGE, null);
        assertEquals("ZERO_BALANCE", zero.status());
        assertEquals(
                0, service.total("USDT", List.of(zero, cash)).compareTo(new BigDecimal("100")));
        var absent = service.value("BNB", BigDecimal.ONE, "USDT", NOW, MAX_AGE, null);
        assertNull(service.total("USDT", List.of(absent, cash)));
        assertNull(service.total("USDT", List.of()));
    }

    @Test
    void roundsOnceAtFinalTotalAndPreservesSignedAmounts() {
        var tiny =
                service.value(
                        "USDT", new BigDecimal("0.00000000000000006"), "USDT", NOW, MAX_AGE, null);
        assertEquals(
                new BigDecimal("0.0000000000000001"), service.total("USDT", List.of(tiny, tiny)));
        var liability =
                service.value(
                        "ETH",
                        new BigDecimal("-0.1"),
                        "USDT",
                        NOW,
                        MAX_AGE,
                        price("ETH", "USDT", "3000.12345678", NOW, NOW));
        assertEquals(new BigDecimal("-300.012345678"), liability.value());
        assertNull(service.total("CNY", List.of(tiny)));
        var otherTarget =
                new CurrencyValuationService.AssetValue(
                        "USD",
                        BigDecimal.ONE,
                        "USD",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        "IDENTITY",
                        null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.total("USDT", List.of(tiny, otherTarget)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.value("BTC", new BigDecimal("1E+999"), "USDT", NOW, MAX_AGE, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.value("BTC", BigDecimal.ONE, "USDT", NOW, Duration.ZERO, null));
    }

    @Test
    void unknownMetadataCannotPretendToKnowCurrencyAndVerifiedRequiresSource() {
        assertNull(ValuationMetadata.unknown().currency());
        assertEquals("UNKNOWN", ValuationMetadata.unknown().status());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValuationMetadata("USDT", null, null, null, "UNKNOWN", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValuationMetadata("USDT", NOW, "", "v1", "VERIFIED", "{}"));
    }
}
