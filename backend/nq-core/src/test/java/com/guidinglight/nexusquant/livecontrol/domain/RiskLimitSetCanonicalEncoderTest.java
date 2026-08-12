package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RiskLimitSetCanonicalEncoderTest {

    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-08-12T01:02:03Z");

    @Test
    void shouldProduceStableGoldenDigestIndependentOfScaleAndSymbolOrder() {
        RiskLimitSet first = limits(List.of("ETH-USDT", "BTC-USDT"), "100.0");
        RiskLimitSet semanticallyEqual = limits(List.of("BTC-USDT", "ETH-USDT"), "100.00000000");

        assertEquals(first.canonicalDigest(), semanticallyEqual.canonicalDigest());
        assertEquals("75ef817c87d74807998a38c55127dfb7a8a5e396e4dab02f1ccdbb3ff0719137",
                first.canonicalDigest());
    }

    @Test
    void shouldChangeDigestWhenAnyRiskScopeChanges() {
        RiskLimitSet baseline = limits(List.of("BTC-USDT", "ETH-USDT"), "100");
        RiskLimitSet changed = new RiskLimitSet(
                ID, 1, decimal("100"), decimal("11"), decimal("50"), decimal("5"), decimal("8"),
                3, 20, List.of("BTC-USDT", "ETH-USDT"), 600, decimal("15"), decimal("20"),
                1000, 9900, 7, CREATED_AT
        );

        assertNotEquals(baseline.canonicalDigest(), changed.canonicalDigest());
    }

    @Test
    void shouldRejectPrecisionInsteadOfRounding() {
        assertThrows(IllegalArgumentException.class, () -> new RiskLimitSet(
                ID, 1, new BigDecimal("100.000000001"), decimal("10"), decimal("50"), decimal("5"),
                decimal("8"), 3, 20, List.of("BTC-USDT"), 600, decimal("15"), decimal("20"),
                1000, 9900, 7, CREATED_AT
        ));
    }

    private static RiskLimitSet limits(List<String> symbols, String capital) {
        return new RiskLimitSet(
                ID, 1, new BigDecimal(capital), decimal("10"), decimal("50"), decimal("5"), decimal("8"),
                3, 20, symbols, 600, decimal("15"), decimal("20"), 1000, 9900, 7, CREATED_AT
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
