package com.guidinglight.nexusquant.adapter.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCapability;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessDecision;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessReason;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * DefaultAdapterReadinessServiceTest 固定 GateM-0 adapter readiness runtime enforcement。
 * <p>
 * Why:
 * GateM-0 把 GateL No-Real 文档边界落成运行时约束。本测试证明 OKX / Binance / Noop / 未知 venue
 * 在当前 baseline 下不会被误判为 real-ready：真实交易能力一律 allowed=false、status != READY、
 * reasons 非空，LIVE 未授权时真实下单能力 fail-closed，未知 venue / capability fail-closed。
 * 测试不访问外网、不读取 .env、不需要真实 credential。
 */
class DefaultAdapterReadinessServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);

    private final AdapterReadinessService service = new DefaultAdapterReadinessService(FIXED_CLOCK);

    @Test
    void noopMarketDataSubscriptionsAreNoRealDisabled() {
        for (AdapterCapability capability : new AdapterCapability[]{
                AdapterCapability.SUBSCRIBE_BARS,
                AdapterCapability.SUBSCRIBE_TRADES,
                AdapterCapability.SUBSCRIBE_ORDERBOOK,
                AdapterCapability.PUBLIC_MARKETDATA
        }) {
            AdapterReadinessDecision decision = service.evaluate("NOOP", capability);

            assertFalse(decision.allowed(), "noop marketdata must not be allowed: " + capability);
            assertFalse(decision.isReadyAndAllowed());
            assertEquals(AdapterReadinessStatus.NO_REAL, decision.status());
            assertTrue(decision.reasons().contains(AdapterReadinessReason.NO_REAL_DISABLED));
            assertTrue(decision.reasons().contains(AdapterReadinessReason.NO_REAL_PROVIDER));
            assertEquals("NOOP", decision.venue());
            assertNotNull(decision.checkedAt());
        }
    }

    @Test
    void noopTradingIsNotReady() {
        AdapterReadinessDecision decision = service.evaluate("NOOP", AdapterCapability.PLACE_ORDER);

        assertFalse(decision.allowed());
        assertEquals(AdapterReadinessStatus.NO_REAL, decision.status());
        assertFalse(decision.status().isReady());
        assertFalse(decision.reasons().isEmpty());
    }

    @Test
    void paperAndSimArePaperOnlyButNeverLiveReady() {
        for (String venue : new String[]{"PAPER", "SIM"}) {
            AdapterReadinessDecision decision = service.evaluate(venue, AdapterCapability.PLACE_ORDER);

            assertFalse(decision.allowed(), venue + " must not allow real trading");
            assertFalse(decision.liveAuthorized(), venue + " must not be live-authorized");
            assertEquals(AdapterReadinessStatus.NO_REAL, decision.status());
            assertTrue(decision.reasons().contains(AdapterReadinessReason.READY_FOR_PAPER_ONLY));
            assertTrue(decision.reasons().contains(
                    AdapterReadinessReason.PAPER_ARTIFACT_NOT_REAL_AUTHORIZATION));
            assertTrue(decision.reasons().contains(AdapterReadinessReason.NO_REAL_PROVIDER));
            assertFalse(decision.status().isReady());
        }
    }

    @Test
    void fakeAndStubAdaptersAreExplicitlyNotLiveReady() {
        AdapterReadinessDecision fake = service.evaluate("FAKE", AdapterCapability.PLACE_ORDER);
        AdapterReadinessDecision stub = service.evaluate("STUB", AdapterCapability.CANCEL_ORDER);

        assertEquals(AdapterReadinessStatus.FAKE, fake.status());
        assertFalse(fake.allowed());
        assertFalse(fake.liveAuthorized());
        assertTrue(fake.reasons().contains(AdapterReadinessReason.FAKE_ADAPTER_DISABLED));
        assertTrue(fake.reasons().contains(AdapterReadinessReason.NO_REAL_PROVIDER));
        assertFalse(fake.status().isReady());

        assertEquals(AdapterReadinessStatus.STUB, stub.status());
        assertFalse(stub.allowed());
        assertFalse(stub.liveAuthorized());
        assertTrue(stub.reasons().contains(AdapterReadinessReason.STUB_ADAPTER_DISABLED));
        assertTrue(stub.reasons().contains(AdapterReadinessReason.NO_REAL_PROVIDER));
        assertFalse(stub.status().isReady());
    }

    @Test
    void futureRealAdapterIsNotImplementedAndNoRealProvider() {
        AdapterReadinessDecision decision = service.evaluate("FUTURE_REAL", AdapterCapability.PLACE_ORDER);

        assertFalse(decision.allowed());
        assertFalse(decision.liveAuthorized());
        assertEquals(AdapterReadinessStatus.NOT_IMPLEMENTED, decision.status());
        assertTrue(decision.reasons().contains(AdapterReadinessReason.FUTURE_REAL_DISABLED));
        assertTrue(decision.reasons().contains(AdapterReadinessReason.NO_REAL_PROVIDER));
        assertTrue(decision.reasons().contains(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED));
        assertTrue(decision.reasons().contains(AdapterReadinessReason.LIVE_DISABLED));
        assertTrue(decision.reasons().contains(AdapterReadinessReason.LIVE_NOT_AUTHORIZED));
        assertFalse(decision.status().isReady());
    }

    @Test
    void okxTradingCapabilitiesAreNotReadyAndNotFutureRealReady() {
        for (AdapterCapability capability : new AdapterCapability[]{
                AdapterCapability.SPOT_TRADING,
                AdapterCapability.PLACE_ORDER,
                AdapterCapability.CANCEL_ORDER,
                AdapterCapability.QUERY_ORDER,
                AdapterCapability.ACCOUNT_BALANCE
        }) {
            AdapterReadinessDecision decision = service.evaluate("OKX", capability);

            assertFalse(decision.allowed(), "OKX trading must not be allowed: " + capability);
            assertFalse(decision.liveAuthorized());
            assertEquals(AdapterReadinessStatus.NOT_READY, decision.status());
            // OKX 既有 adapter 不是 future-real-ready：永远不会是 READY。
            assertFalse(decision.status().isReady());
            assertFalse(decision.reasons().isEmpty());
            assertTrue(
                    decision.reasons().contains(AdapterReadinessReason.ENDPOINT_DISABLED_SENTINEL)
                            || decision.reasons().contains(AdapterReadinessReason.CREDENTIALS_MISSING)
                            || decision.reasons().contains(AdapterReadinessReason.LIVE_DISABLED),
                    "OKX trading reasons must explain fail-closed: " + decision.reasons());
        }
    }

    @Test
    void binanceTradingCapabilitiesAreNotReadyAndNotFutureRealReady() {
        for (AdapterCapability capability : new AdapterCapability[]{
                AdapterCapability.SPOT_TRADING,
                AdapterCapability.PLACE_ORDER,
                AdapterCapability.CANCEL_ORDER,
                AdapterCapability.QUERY_ORDER,
                AdapterCapability.ACCOUNT_BALANCE
        }) {
            AdapterReadinessDecision decision = service.evaluate("BINANCE", capability);

            assertFalse(decision.allowed(), "Binance trading must not be allowed: " + capability);
            assertFalse(decision.liveAuthorized());
            assertEquals(AdapterReadinessStatus.NOT_READY, decision.status());
            assertFalse(decision.status().isReady());
            assertTrue(
                    decision.reasons().contains(AdapterReadinessReason.ENDPOINT_DISABLED_SENTINEL)
                            || decision.reasons().contains(AdapterReadinessReason.CREDENTIALS_MISSING)
                            || decision.reasons().contains(AdapterReadinessReason.LIVE_DISABLED),
                    "Binance trading reasons must explain fail-closed: " + decision.reasons());
        }
    }

    @Test
    void liveMutatingTradingIsFailClosedWhenLiveDisabled() {
        AdapterReadinessDecision decision = service.evaluate("OKX", AdapterCapability.PLACE_ORDER);

        assertFalse(decision.allowed());
        assertFalse(decision.liveAuthorized());
        assertTrue(decision.reasons().contains(AdapterReadinessReason.LIVE_DISABLED));
        assertTrue(decision.reasons().contains(AdapterReadinessReason.LIVE_NOT_AUTHORIZED));
    }

    @Test
    void permissionProbeReportsRealProviderNotImplemented() {
        AdapterReadinessDecision okx = service.evaluate("OKX", AdapterCapability.PERMISSION_PROBE);
        AdapterReadinessDecision binance = service.evaluate("BINANCE", AdapterCapability.PERMISSION_PROBE);

        assertFalse(okx.allowed());
        assertFalse(binance.allowed());
        assertTrue(okx.reasons().contains(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED));
        assertTrue(binance.reasons().contains(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED));
        assertTrue(okx.reasons().contains(AdapterReadinessReason.PERMISSION_PROBE_DISABLED));
        assertTrue(binance.reasons().contains(AdapterReadinessReason.PERMISSION_PROBE_DISABLED));
    }

    @Test
    void permissionProbeDisabledOrSkippedIsNeverVerified() {
        for (String venue : new String[]{"NOOP", "PAPER", "SIM", "FAKE", "STUB", "FUTURE_REAL", "OKX", "BINANCE"}) {
            AdapterReadinessDecision decision = service.evaluate(venue, AdapterCapability.PERMISSION_PROBE);

            assertFalse(decision.allowed(), "permission probe must not be allowed: " + venue);
            assertFalse(decision.liveAuthorized(), "permission probe must not be live-authorized: " + venue);
            assertTrue(decision.reasons().contains(AdapterReadinessReason.PERMISSION_PROBE_DISABLED),
                    "permission probe must be explicitly disabled/skipped: " + decision);
            assertFalse(decision.status().isReady(), "disabled/skipped probe must not be READY: " + venue);
        }
    }

    @Test
    void exchangePublicMarketDataReportsRealProviderNotImplemented() {
        AdapterReadinessDecision decision = service.evaluate("OKX", AdapterCapability.HISTORICAL_OHLCV);

        assertFalse(decision.allowed());
        assertTrue(decision.reasons().contains(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED));
    }

    @Test
    void marketDataReadinessDoesNotAuthorizeTradingReadiness() {
        AdapterReadinessDecision marketData = service.evaluate("OKX", AdapterCapability.PUBLIC_MARKETDATA);
        AdapterReadinessDecision trading = service.evaluate("OKX", AdapterCapability.PLACE_ORDER);

        assertFalse(marketData.allowed());
        assertFalse(trading.allowed());
        assertTrue(marketData.reasons().contains(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED));
        assertTrue(trading.reasons().contains(AdapterReadinessReason.LIVE_DISABLED));
        assertTrue(trading.reasons().contains(AdapterReadinessReason.CREDENTIAL_UNCONFIGURED));
        assertFalse(marketData.reasons().contains(AdapterReadinessReason.LIVE_DISABLED),
                "marketdata denial must not be treated as trading authorization or trading denial");
    }

    @Test
    void unknownVenueFailsClosed() {
        AdapterReadinessDecision decision = service.evaluate("MYSTERY-DEX", AdapterCapability.PLACE_ORDER);

        assertFalse(decision.allowed());
        assertEquals(AdapterReadinessStatus.UNKNOWN_REQUIRES_REVIEW, decision.status());
        assertTrue(decision.reasons().contains(AdapterReadinessReason.UNKNOWN_REQUIRES_REVIEW));
    }

    @Test
    void nullVenueFailsClosed() {
        AdapterReadinessDecision decision = service.evaluate(null, AdapterCapability.PLACE_ORDER);

        assertFalse(decision.allowed());
        assertEquals(AdapterReadinessStatus.UNKNOWN_REQUIRES_REVIEW, decision.status());
        assertEquals("UNKNOWN", decision.venue());
    }

    @Test
    void nullCapabilityFailsClosed() {
        AdapterReadinessDecision decision = service.evaluate("OKX", null);

        assertFalse(decision.allowed());
        assertEquals(AdapterReadinessStatus.UNKNOWN_REQUIRES_REVIEW, decision.status());
        assertTrue(decision.reasons().contains(AdapterReadinessReason.UNKNOWN_REQUIRES_REVIEW));
        // GateM-1 修正 GateM-0 P2-2：null capability 不再误报为 PLACE_ORDER，统一自描述为 UNSPECIFIED。
        assertEquals(AdapterCapability.UNSPECIFIED, decision.capability());
    }

    @Test
    void unspecifiedCapabilityFailsClosed() {
        // 显式传入 UNSPECIFIED 也必须 fail-closed，不绑定任何真实能力。
        AdapterReadinessDecision decision = service.evaluate("OKX", AdapterCapability.UNSPECIFIED);

        assertFalse(decision.allowed());
        assertEquals(AdapterReadinessStatus.UNKNOWN_REQUIRES_REVIEW, decision.status());
        assertEquals(AdapterCapability.UNSPECIFIED, decision.capability());
        assertTrue(decision.reasons().contains(AdapterReadinessReason.UNKNOWN_REQUIRES_REVIEW));
    }

    @Test
    void everyKnownVenueCapabilityIsFailClosed() {
        // 全矩阵回归：GateM-0 内 NOOP / OKX / BINANCE 的任一能力都不得 allowed，且必须给出原因。
        for (String venue : new String[]{"NOOP", "PAPER", "SIM", "FAKE", "STUB", "FUTURE_REAL", "OKX", "BINANCE", "okx", " binance "}) {
            for (AdapterCapability capability : AdapterCapability.values()) {
                AdapterReadinessDecision decision = service.evaluate(venue, capability);

                assertFalse(decision.allowed(),
                        "must be fail-closed: venue=" + venue + ", capability=" + capability);
                assertFalse(decision.status().isReady());
                assertFalse(decision.liveAuthorized());
                assertFalse(decision.reasons().isEmpty());
            }
        }
    }

    @Test
    void isAllowedIsFalseForExchangeTrading() {
        assertFalse(service.isAllowed("OKX", AdapterCapability.PLACE_ORDER));
        assertFalse(service.isAllowed("BINANCE", AdapterCapability.SPOT_TRADING));
        assertFalse(service.isAllowed("NOOP", AdapterCapability.SUBSCRIBE_BARS));
    }

    @Test
    void requireReadyThrowsWhenNotReady() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.requireReady("OKX", AdapterCapability.PLACE_ORDER));

        assertTrue(ex.getMessage().contains("not ready"));
        // 守卫异常信息不得包含 credential / secret 字样。
        assertFalse(ex.getMessage().toLowerCase().contains("secret"));
        assertFalse(ex.getMessage().toLowerCase().contains("apikey"));
    }

    @Test
    void decisionCannotBeAllowedWhenNotReady() {
        // Fail-closed 不变量：无法构造“未就绪却 allowed=true”的决策。
        assertThrows(IllegalArgumentException.class, () -> new AdapterReadinessDecision(
                "OKX",
                AdapterCapability.PLACE_ORDER,
                AdapterReadinessStatus.NOT_READY,
                true,
                false,
                java.util.List.of(AdapterReadinessReason.LIVE_DISABLED),
                Instant.now(FIXED_CLOCK),
                "should fail"));
    }

    @Test
    void notAllowedDecisionMustCarryReason() {
        // Fail-closed 不变量：拒绝必须可解释。
        assertThrows(IllegalArgumentException.class, () -> new AdapterReadinessDecision(
                "OKX",
                AdapterCapability.PLACE_ORDER,
                AdapterReadinessStatus.NOT_READY,
                false,
                false,
                java.util.List.of(),
                Instant.now(FIXED_CLOCK),
                "missing reason"));
    }
}
