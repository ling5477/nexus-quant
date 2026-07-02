package com.guidinglight.nexusquant.adapter.api.dataquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataEndpointCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundErrorCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundResult;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataQualitySummary;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * DataQualitySourceHealthMapperTest 固化 GateO O-2 的 O-1 result 到 Data Quality mapping。
 *
 * <p>Why: mapper 只能把 public marketdata 结果转成 diagnostic 状态；测试不创建 HTTP client、不访问真实
 * OKX/Binance/Bybit/Gate/Coinbase/Kraken、不读取 credential，也不依赖 LIVE、AI 或 DH runtime。</p>
 */
class DataQualitySourceHealthMapperTest {

    private static final Instant CHECKED_AT = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    void successShouldMapToHealthyWithoutTradingAuthorization() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(request(), result(
                PublicMarketDataOutboundErrorCategory.NONE,
                200,
                Duration.ofMillis(120),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                3,
                false,
                0,
                false));

        assertEquals(DataQualitySummary.SourceStatus.ENABLED, summary.sourceStatus());
        assertEquals(DataQualitySummary.SourceHealth.HEALTHY, summary.sourceHealth());
        assertEquals(DataQualitySummary.FreshnessStatus.FRESH, summary.freshnessStatus());
        assertEquals(DataQualitySummary.GapStatus.NONE, summary.gapStatus());
        assertEquals(DataQualitySummary.DataOrigin.FAKE_SERVER, summary.dataOrigin());
        assertEquals(DataQualitySummary.ErrorCategory.NONE, summary.errorCategory());
        assertEquals(120L, summary.latencyMs());
        assertEquals(CHECKED_AT, summary.lastSuccessAt());
        assertNull(summary.lastFailureAt());
        assertEquals("trc-o2", summary.traceId());
        assertEquals("req-o2", summary.requestId());
    }

    @Test
    void highLatencySuccessShouldMapToDegraded() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(request(), result(
                PublicMarketDataOutboundErrorCategory.NONE,
                200,
                Duration.ofMillis(2_100),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                1,
                false,
                0,
                false));

        assertEquals(DataQualitySummary.SourceStatus.DEGRADED, summary.sourceStatus());
        assertEquals(DataQualitySummary.SourceHealth.DEGRADED, summary.sourceHealth());
        assertEquals(DataQualitySummary.ErrorCategory.NONE, summary.errorCategory());
        assertNotNull(summary.degradedReason());
    }

    @Test
    void rateLimitShouldMapToRateLimited() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.RATE_LIMITED,
                429,
                Duration.ofMillis(80),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                0,
                false,
                0,
                false));

        assertEquals(DataQualitySummary.SourceStatus.RATE_LIMITED, summary.sourceStatus());
        assertEquals(DataQualitySummary.SourceHealth.RATE_LIMITED, summary.sourceHealth());
        assertEquals(DataQualitySummary.FreshnessStatus.ERROR, summary.freshnessStatus());
        assertEquals(DataQualitySummary.ErrorCategory.RATE_LIMITED, summary.errorCategory());
    }

    @Test
    void timeoutShouldMapToTimeoutHealthAndErrorFreshness() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.TIMEOUT,
                0,
                Duration.ofSeconds(8),
                PublicMarketDataQualitySummary.DataOrigin.LOCAL_DB,
                0,
                false,
                0,
                true));

        assertEquals(DataQualitySummary.SourceStatus.ERROR, summary.sourceStatus());
        assertEquals(DataQualitySummary.SourceHealth.TIMEOUT, summary.sourceHealth());
        assertEquals(DataQualitySummary.FreshnessStatus.ERROR, summary.freshnessStatus());
        assertEquals(DataQualitySummary.ErrorCategory.TIMEOUT, summary.errorCategory());
        assertEquals(DataQualitySummary.DataOrigin.LOCAL_DB, summary.dataOrigin());
    }

    @Test
    void server5xxShouldMapToError() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.TEMPORARY_FAILURE,
                503,
                Duration.ofMillis(90),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                0,
                false,
                0,
                false));

        assertEquals(DataQualitySummary.SourceStatus.ERROR, summary.sourceStatus());
        assertEquals(DataQualitySummary.SourceHealth.ERROR, summary.sourceHealth());
        assertEquals(DataQualitySummary.ErrorCategory.TEMPORARY_FAILURE, summary.errorCategory());
    }

    @Test
    void malformedResponseShouldMapToInvalidResponse() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.INVALID_RESPONSE,
                200,
                Duration.ofMillis(60),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                0,
                false,
                0,
                false));

        assertEquals(DataQualitySummary.SourceHealth.ERROR, summary.sourceHealth());
        assertEquals(DataQualitySummary.ErrorCategory.INVALID_RESPONSE, summary.errorCategory());
    }

    @Test
    void disabledSourceShouldMapToDisabledWithoutSystemUnavailableMeaning() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.DISABLED,
                0,
                Duration.ZERO,
                PublicMarketDataQualitySummary.DataOrigin.FIXTURE,
                0,
                false,
                0,
                true));

        assertEquals(DataQualitySummary.SourceStatus.DISABLED, summary.sourceStatus());
        assertEquals(DataQualitySummary.SourceHealth.UNKNOWN, summary.sourceHealth());
        assertEquals(DataQualitySummary.FreshnessStatus.DISABLED, summary.freshnessStatus());
        assertEquals(DataQualitySummary.GapStatus.UNKNOWN, summary.gapStatus());
        assertEquals(DataQualitySummary.DataOrigin.FIXTURE, summary.dataOrigin());
        assertNotNull(summary.disabledReason());
    }

    @Test
    void fallbackOriginsShouldRemainSeparatedFromTradingAuthorization() {
        assertEquals(DataQualitySummary.DataOrigin.LOCAL_DB, DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.DISABLED,
                0,
                Duration.ZERO,
                PublicMarketDataQualitySummary.DataOrigin.LOCAL_DB,
                0,
                false,
                0,
                true)).dataOrigin());
        assertEquals(DataQualitySummary.DataOrigin.FIXTURE, DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.DISABLED,
                0,
                Duration.ZERO,
                PublicMarketDataQualitySummary.DataOrigin.FIXTURE,
                0,
                false,
                0,
                true)).dataOrigin());
        assertEquals(DataQualitySummary.DataOrigin.FAKE_SERVER, DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.DISABLED,
                0,
                Duration.ZERO,
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                0,
                false,
                0,
                true)).dataOrigin());
    }

    @Test
    void staleAndGapEvidenceShouldMapToFreshnessAndGapStatus() {
        DataQualitySummary stale = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.NONE,
                200,
                Duration.ofMillis(90),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                1,
                true,
                0,
                false));
        DataQualitySummary gap = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.NONE,
                200,
                Duration.ofMillis(90),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                1,
                false,
                2,
                false));

        assertEquals(DataQualitySummary.FreshnessStatus.STALE, stale.freshnessStatus());
        assertEquals(DataQualitySummary.SourceHealth.DEGRADED, stale.sourceHealth());
        assertEquals(DataQualitySummary.GapStatus.GAP, gap.gapStatus());
        assertEquals(2, gap.gapCount());
    }

    @Test
    void publicOutboundCompatibilityOriginShouldOnlySurfaceAsPublicCandidate() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.NONE,
                200,
                Duration.ofMillis(90),
                PublicMarketDataQualitySummary.DataOrigin.PUBLIC_OUTBOUND,
                1,
                false,
                0,
                false));

        assertEquals(DataQualitySummary.DataOrigin.PUBLIC_CANDIDATE, summary.dataOrigin());
    }

    @Test
    void summaryShouldNotExposeTradingAuthorizationFieldAndShouldRedactDiagnosticText() {
        DataQualitySummary redacted = new DataQualitySummary(
                "FAKE_TICKER",
                "FAKE",
                "BTC-USDT",
                "1m",
                DataQualitySummary.DataOrigin.FAKE_SERVER,
                DataQualitySummary.SourceStatus.ERROR,
                DataQualitySummary.SourceHealth.ERROR,
                DataQualitySummary.FreshnessStatus.ERROR,
                DataQualitySummary.GapStatus.NONE,
                null,
                CHECKED_AT,
                10L,
                DataQualitySummary.ErrorCategory.INVALID_RESPONSE,
                0,
                "apiKey=fake-secret",
                null,
                "trc",
                "req");

        boolean hasAuthorizationField = Arrays.stream(DataQualitySummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(name -> name.toLowerCase().contains("authorization"));
        assertFalse(hasAuthorizationField);
        assertEquals("<redacted>", redacted.degradedReason());
    }

    @Test
    void mapperShouldNotRequireRealHttpCredentialLiveAiOrDhRuntime() {
        DataQualitySummary summary = DataQualitySourceHealthMapper.map(result(
                PublicMarketDataOutboundErrorCategory.NONE,
                200,
                Duration.ofMillis(1),
                PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER,
                1,
                false,
                0,
                false));

        assertEquals(DataQualitySummary.SourceHealth.HEALTHY, summary.sourceHealth());
        assertNull(summary.disabledReason());
    }

    private static PublicMarketDataOutboundRequest request() {
        return new PublicMarketDataOutboundRequest(
                "FAKE",
                PublicMarketDataEndpointCategory.OHLCV,
                "/klines?symbol=BTC-USDT",
                false,
                false,
                "trc-o2",
                "req-o2",
                "1m");
    }

    private static PublicMarketDataOutboundResult result(
            PublicMarketDataOutboundErrorCategory errorCategory,
            int statusCode,
            Duration latency,
            PublicMarketDataQualitySummary.DataOrigin origin,
            int rowCount,
            boolean stale,
            int gapCount,
            boolean fallbackUsed
    ) {
        return new PublicMarketDataOutboundResult(
                "FAKE",
                PublicMarketDataEndpointCategory.OHLCV,
                errorCategory,
                statusCode,
                latency,
                statusCode == 0 ? 0 : 1,
                origin,
                rowCount,
                stale,
                gapCount,
                fallbackUsed,
                CHECKED_AT,
                "safe diagnostic");
    }
}
