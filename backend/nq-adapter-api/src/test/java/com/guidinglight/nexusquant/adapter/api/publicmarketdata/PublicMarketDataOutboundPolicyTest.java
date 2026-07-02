package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * PublicMarketDataOutboundPolicyTest 固化 O-1 public REST allowlist / private denylist 边界。
 *
 * <p>Why: 后续真实 public smoke 只能在手动 profile 下另起 O-5 执行；O-1 必须先用纯规则测试证明
 * public category 可通过、private/signed/credential/permission-probe 类别 fail-closed，且 disabled fallback
 * 不会被解释为交易授权。</p>
 */
class PublicMarketDataOutboundPolicyTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-01T00:00:00Z"),
            ZoneOffset.UTC);

    private final PublicMarketDataOutboundPolicy policy = new PublicMarketDataOutboundPolicy(FIXED_CLOCK);

    @Test
    void shouldAllowOnlyMinimumPublicRestCategories() {
        for (PublicMarketDataEndpointCategory category : new PublicMarketDataEndpointCategory[]{
                PublicMarketDataEndpointCategory.SERVER_TIME,
                PublicMarketDataEndpointCategory.INSTRUMENTS,
                PublicMarketDataEndpointCategory.TICKER,
                PublicMarketDataEndpointCategory.OHLCV
        }) {
            PublicMarketDataOutboundDecision decision = policy.evaluate(
                    PublicMarketDataOutboundRequest.publicGet("FAKE", category, "/public/" + category.name()));

            assertTrue(decision.allowed(), "category must be allowed: " + category);
            assertEquals(PublicMarketDataOutboundErrorCategory.NONE, decision.errorCategory());
            assertEquals(Instant.now(FIXED_CLOCK), decision.checkedAt());
        }
    }

    @Test
    void shouldDenyOptionalLaterPublicCategoriesByDefault() {
        for (PublicMarketDataEndpointCategory category : new PublicMarketDataEndpointCategory[]{
                PublicMarketDataEndpointCategory.ORDER_BOOK,
                PublicMarketDataEndpointCategory.RECENT_TRADES,
                PublicMarketDataEndpointCategory.PUBLIC_WEBSOCKET
        }) {
            PublicMarketDataOutboundDecision decision = policy.evaluate(
                    PublicMarketDataOutboundRequest.publicGet("FAKE", category, "/public/" + category.name()));

            assertFalse(decision.allowed(), "optional later category must stay disabled: " + category);
            assertEquals(PublicMarketDataOutboundErrorCategory.DENIED, decision.errorCategory());
        }
    }

    @Test
    void shouldDenyPrivateEndpointCategories() {
        for (PublicMarketDataEndpointCategory category : new PublicMarketDataEndpointCategory[]{
                PublicMarketDataEndpointCategory.ACCOUNT,
                PublicMarketDataEndpointCategory.BALANCE,
                PublicMarketDataEndpointCategory.ORDER,
                PublicMarketDataEndpointCategory.CANCEL,
                PublicMarketDataEndpointCategory.AMEND,
                PublicMarketDataEndpointCategory.POSITIONS,
                PublicMarketDataEndpointCategory.WALLET,
                PublicMarketDataEndpointCategory.TRANSFER,
                PublicMarketDataEndpointCategory.WITHDRAW,
                PublicMarketDataEndpointCategory.DEPOSIT,
                PublicMarketDataEndpointCategory.SUBACCOUNT,
                PublicMarketDataEndpointCategory.PRIVATE_WEBSOCKET,
                PublicMarketDataEndpointCategory.SIGNED_REQUEST,
                PublicMarketDataEndpointCategory.API_KEY_VALIDATION,
                PublicMarketDataEndpointCategory.REAL_PERMISSION_PROBE,
                PublicMarketDataEndpointCategory.AUTHENTICATED
        }) {
            PublicMarketDataOutboundDecision decision = policy.evaluate(
                    PublicMarketDataOutboundRequest.publicGet("FAKE", category, "/private/" + category.name()));

            assertFalse(decision.allowed(), "private category must be denied: " + category);
            assertTrue(decision.reason().contains("forbidden"));
        }
    }

    @Test
    void shouldDenySignedOrAuthenticatedRequestEvenWhenCategoryLooksPublic() {
        PublicMarketDataOutboundRequest signed = new PublicMarketDataOutboundRequest(
                "FAKE",
                PublicMarketDataEndpointCategory.TICKER,
                "/api/v5/market/ticker?instId=BTC-USDT",
                false,
                true,
                "trc-1",
                "req-1",
                "1m");
        PublicMarketDataOutboundRequest authenticated = new PublicMarketDataOutboundRequest(
                "FAKE",
                PublicMarketDataEndpointCategory.OHLCV,
                "/api/v3/klines",
                true,
                false,
                null,
                null,
                null);

        assertFalse(policy.evaluate(signed).allowed());
        assertFalse(policy.evaluate(authenticated).allowed());
    }

    @Test
    void shouldDenyPrivateOrCredentialLikeTokensInPath() {
        PublicMarketDataOutboundRequest request = PublicMarketDataOutboundRequest.publicGet(
                "FAKE",
                PublicMarketDataEndpointCategory.TICKER,
                "/api/v5/market/ticker?apiKey=leaked&signature=bad");

        PublicMarketDataOutboundDecision decision = policy.evaluate(request);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("credential-like"));
    }

    @Test
    void shouldDenyEndpointReferencesThatCanEscapeBaseAuthority() {
        for (String endpointPath : new String[]{
                "http://example.invalid/ticker",
                "https://example.invalid/ticker",
                "//example.invalid/ticker",
                "//user@example.invalid/ticker",
                "/ticker#frag",
                "?symbol=BTC-USDT",
                "",
                "   "
        }) {
            PublicMarketDataOutboundDecision decision = policy.evaluate(
                    PublicMarketDataOutboundRequest.publicGet(
                            "FAKE",
                            PublicMarketDataEndpointCategory.TICKER,
                            endpointPath));

            assertFalse(decision.allowed(), "endpoint path must fail closed: " + endpointPath);
            assertEquals(PublicMarketDataOutboundErrorCategory.DENIED, decision.errorCategory());
            assertTrue(decision.reason().contains("relative public REST path"));
        }
    }

    @Test
    void shouldAllowPathOnlyEndpointReferencesWithQuery() {
        for (String endpointPath : new String[]{
                "/ticker?symbol=BTC-USDT",
                "ticker?symbol=BTC-USDT",
                "/api/v5/market/candles?instId=BTC-USDT"
        }) {
            PublicMarketDataOutboundDecision decision = policy.evaluate(
                    PublicMarketDataOutboundRequest.publicGet(
                            "FAKE",
                            PublicMarketDataEndpointCategory.TICKER,
                            endpointPath));

            assertTrue(decision.allowed(), "path-only endpoint must be allowed: " + endpointPath);
        }
    }

    @Test
    void disabledClientShouldReturnFallbackWithoutTradingAuthorization() {
        DisabledPublicMarketDataOutboundClient client = new DisabledPublicMarketDataOutboundClient(
                PublicMarketDataQualitySummary.DataOrigin.FIXTURE,
                FIXED_CLOCK);
        PublicMarketDataOutboundResult result = client.fetch(PublicMarketDataOutboundRequest.publicGet(
                "FAKE",
                PublicMarketDataEndpointCategory.OHLCV,
                "/fixture/klines"));
        PublicMarketDataQualitySummary summary = PublicMarketDataSourceHealthMapper.map(result);

        assertEquals(PublicMarketDataOutboundErrorCategory.DISABLED, result.errorCategory());
        assertEquals(PublicMarketDataQualitySummary.SourceStatus.DISABLED, summary.sourceStatus());
        assertEquals(PublicMarketDataQualitySummary.DataOrigin.FIXTURE, summary.dataOrigin());
        assertTrue(summary.fallbackUsed());
        assertFalse(summary.tradingAuthorization());
    }

    @Test
    void settingsShouldKeepBoundedTimeoutAndRetryDefaults() {
        PublicMarketDataOutboundSettings settings = PublicMarketDataOutboundSettings.defaults();

        assertEquals(3, settings.connectTimeout().toSeconds());
        assertEquals(5, settings.readTimeout().toSeconds());
        assertEquals(8, settings.totalRequestTimeout().toSeconds());
        assertEquals(2, settings.maxRetries());
        assertEquals(3, settings.maxAttempts());
        assertDoesNotThrow(() -> new PublicMarketDataOutboundSettings(
                settings.connectTimeout(),
                settings.readTimeout(),
                settings.totalRequestTimeout(),
                2,
                settings.firstBackoff(),
                settings.secondBackoff()));
        assertThrows(IllegalArgumentException.class, () -> new PublicMarketDataOutboundSettings(
                settings.connectTimeout(),
                settings.readTimeout(),
                settings.totalRequestTimeout(),
                3,
                settings.firstBackoff(),
                settings.secondBackoff()));
    }

    @Test
    void sourceHealthMapperShouldCoverLatencyStaleAndGapResiduals() {
        PublicMarketDataOutboundResult highLatency = new PublicMarketDataOutboundResult(
                "FAKE",
                PublicMarketDataEndpointCategory.TICKER,
                PublicMarketDataOutboundErrorCategory.NONE,
                200,
                java.time.Duration.ofSeconds(3),
                1,
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                1,
                false,
                0,
                false,
                Instant.now(FIXED_CLOCK),
                "accepted");
        PublicMarketDataOutboundResult staleWithGap = new PublicMarketDataOutboundResult(
                "FAKE",
                PublicMarketDataEndpointCategory.OHLCV,
                PublicMarketDataOutboundErrorCategory.GAP,
                200,
                java.time.Duration.ofMillis(50),
                1,
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                1,
                true,
                2,
                false,
                Instant.now(FIXED_CLOCK),
                "gap detected");

        PublicMarketDataQualitySummary highLatencySummary = PublicMarketDataSourceHealthMapper.map(highLatency);
        PublicMarketDataQualitySummary staleGapSummary = PublicMarketDataSourceHealthMapper.map(staleWithGap);

        assertEquals(PublicMarketDataQualitySummary.SourceHealth.DEGRADED, highLatencySummary.sourceHealth());
        assertEquals(PublicMarketDataQualitySummary.Freshness.STALE, staleGapSummary.freshness());
        assertEquals(2, staleGapSummary.gapCount());
        assertFalse(highLatencySummary.tradingAuthorization());
        assertFalse(staleGapSummary.tradingAuthorization());
    }
}
