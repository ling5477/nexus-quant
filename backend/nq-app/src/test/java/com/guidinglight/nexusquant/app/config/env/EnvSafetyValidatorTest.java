package com.guidinglight.nexusquant.app.config.env;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * EnvSafetyValidatorTest 固化 GateK Batch 5B-ENV 的 fail-closed 冲突矩阵。
 *
 * <p>Why: 这些规则不能靠 GitHub Actions 文本检查或“没有外联日志”证明。测试直接喂入脱敏
 * 环境事实，确保 CI/test/paper 默认安全，且 LIVE、real provider、real exchange endpoint、
 * real credential material、AI/DH runtime 一旦进入 CI/test/no-outbound 边界就启动失败。</p>
 */
class EnvSafetyValidatorTest {

    @Test
    void shouldAllowCiTestDefaultSafeBoundary() {
        assertDoesNotThrow(() -> EnvSafetyValidator.validate(defaultSafeFacts()));
        assertTrue(EnvSafetyValidator.validate(defaultSafeFacts()).isEmpty());
    }

    @Test
    void shouldFailClosedForLiveAndNoOutboundConflict() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of("ci"),
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                Map.of(),
                Map.of()
        ));

        assertContains(violations, "LIVE=true conflicts with no-outbound=true");
    }

    @Test
    void shouldFailClosedForCiLiveConflict() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of(),
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                Map.of(),
                Map.of()
        ));

        assertContains(violations, "CI=true forbids LIVE=true");
    }

    @Test
    void shouldFailClosedForCiRealProvider() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of(),
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                Map.of(),
                Map.of()
        ));

        assertContains(violations, "CI=true forbids real provider/client");
    }

    @Test
    void shouldFailClosedForTestProfileRealExchange() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of("test"),
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                Map.of(),
                Map.of()
        ));

        assertContains(violations, "CI/test profile forbids real exchange enabled");
    }

    @Test
    void shouldFailClosedForNoOutboundRealExchangeEndpoint() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of("paper"),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                Map.of(),
                Map.of("NQ_BINANCE_REAL_BASE_URL", "https://api.binance.com")
        ));

        assertContains(violations, "no-outbound=true forbids real exchange endpoint: NQ_BINANCE_REAL_BASE_URL");
    }

    @Test
    void shouldTreatOnlyUnifiedPlaceholdersAsSafeCredentialValues() {
        var safeViolations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of("test"),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                Map.of(
                        "NQ_OKX_DOME_API_KEY", "PLACEHOLDER_ONLY",
                        "NQ_OKX_DOME_API_SECRET", "DO_NOT_COMMIT_REAL_VALUE",
                        "NQ_OKX_DOME_API_PASSPHRASE", "REPLACE_WITH_LOCAL_PLACEHOLDER"
                ),
                Map.of()
        ));
        var unsafeViolations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of("test"),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                Map.of("NQ_OKX_REAL_API_KEY", "looks-like-real-material"),
                Map.of()
        ));

        assertTrue(safeViolations.isEmpty());
        assertContains(unsafeViolations, "CI/test profile forbids real credential material: NQ_OKX_REAL_API_KEY");
    }

    @Test
    void shouldFailClosedForCiAiAndDhRuntime() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of(),
                true,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                Map.of(),
                Map.of()
        ));

        assertContains(violations, "CI/test profile forbids AI runtime");
        assertContains(violations, "CI/test profile forbids DH runtime");
    }

    @Test
    void shouldFailClosedForPublicMarketdataManualLiveAiDhOrRealProvider() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of("public-marketdata-manual"),
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                Map.of(),
                Map.of()
        ));

        assertContains(violations, "public-marketdata-manual profile forbids LIVE=true");
        assertContains(violations, "public-marketdata-manual profile forbids AI runtime");
        assertContains(violations, "public-marketdata-manual profile forbids DH runtime");
        assertContains(violations, "public-marketdata-manual profile forbids real provider/client/exchange");
    }

    @Test
    void shouldAllowPublicMarketdataManualWithoutCredentialFacts() {
        var violations = EnvSafetyValidator.validate(new EnvSafetyValidator.Facts(
                Set.of("public-marketdata-manual"),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Map.of(),
                Map.of()
        ));

        assertTrue(violations.isEmpty());
    }

    private static EnvSafetyValidator.Facts defaultSafeFacts() {
        return new EnvSafetyValidator.Facts(
                Set.of("ci"),
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                Map.of("NQ_OKX_DOME_API_KEY", "REPLACE_WITH_LOCAL_PLACEHOLDER"),
                Map.of("NQ_OKX_DOME_BASE_URL", "PLACEHOLDER_ONLY")
        );
    }

    private static void assertContains(Iterable<String> violations, String expected) {
        for (String violation : violations) {
            if (violation.equals(expected)) {
                return;
            }
        }
        throw new AssertionError("Expected violation not found: " + expected + " in " + violations);
    }
}
