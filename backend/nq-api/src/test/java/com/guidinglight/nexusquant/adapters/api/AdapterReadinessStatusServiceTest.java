package com.guidinglight.nexusquant.adapters.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCapability;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessDecision;
import com.guidinglight.nexusquant.adapter.api.service.AdapterReadinessService;
import com.guidinglight.nexusquant.adapter.api.service.DefaultAdapterReadinessService;
import com.guidinglight.nexusquant.adapters.api.dto.AdapterReadinessItemResponse;
import com.guidinglight.nexusquant.adapters.api.dto.AdapterReadinessResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * AdapterReadinessStatusServiceTest 固定 GateM-5A 只读 readiness 聚合的 fail-closed 行为。
 * <p>
 * Why:
 * 证明只读 status service 永远不会把 OKX / Binance 报成可实盘，不把 Noop 报成 success，PLACE/CANCEL 在 LIVE
 * disabled 下 liveAuthorized=false，permission probe 暴露 real provider not implemented，且响应不泄漏 secret。
 * 同时证明 service 只调用 {@link AdapterReadinessService#evaluate}（静态决策），不触达任何 adapter delegate。
 */
class AdapterReadinessStatusServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);
    private static final Set<String> SECRET_TOKENS =
            Set.of("secret", "apikey", "api_key", "token", "signature", "passphrase");
    private static final Set<String> EXCHANGE_VENUES = Set.of("OKX", "BINANCE");
    private static final Set<String> NO_REAL_VENUES = Set.of("NOOP", "PAPER", "SIM");

    @Test
    void currentReadinessReturnsFullFailClosedMatrixWithoutDelegate() {
        RecordingReadinessService recording = new RecordingReadinessService(new DefaultAdapterReadinessService(FIXED_CLOCK));
        AdapterReadinessStatusService service = new AdapterReadinessStatusService(recording, FIXED_CLOCK);

        AdapterReadinessResponse response = service.currentReadiness();

        assertEquals(Instant.parse("2026-06-23T00:00:00Z"), response.generatedAt());
        // 5 venues × 9 capabilities = 45 条目；service 只通过 evaluate() 读取静态决策，无其它交互。
        assertEquals(45, response.items().size());
        assertEquals(45, recording.evaluateCalls(), "service must only read static readiness via evaluate(), no delegate");

        Set<String> venues = response.items().stream()
                .map(AdapterReadinessItemResponse::venue)
                .collect(Collectors.toSet());
        assertTrue(venues.containsAll(EXCHANGE_VENUES), "must report OKX and BINANCE");
        assertTrue(venues.containsAll(NO_REAL_VENUES), "must report NOOP / PAPER / SIM");

        // 关键 fail-closed 不变量：无 READY、无 allowed、无 liveAuthorized。
        assertTrue(response.items().stream().noneMatch(item -> "READY".equals(item.status())), "no capability may be READY");
        assertTrue(response.items().stream().noneMatch(AdapterReadinessItemResponse::allowed), "no capability may be allowed");
        assertTrue(response.items().stream().noneMatch(AdapterReadinessItemResponse::liveAuthorized), "no capability may be live-authorized");
    }

    @Test
    void okxAndBinanceCapabilitiesAreAllFailClosed() {
        AdapterReadinessResponse response = newService().currentReadiness();

        List<AdapterReadinessItemResponse> exchangeItems = response.items().stream()
                .filter(item -> EXCHANGE_VENUES.contains(item.venue()))
                .toList();

        assertEquals(18, exchangeItems.size(), "OKX + BINANCE × 9 capabilities");
        for (AdapterReadinessItemResponse item : exchangeItems) {
            assertFalse(item.allowed(), "exchange capability must not be allowed: " + item);
            assertFalse(item.liveAuthorized(), "exchange capability must not be live-authorized: " + item);
            assertNotEquals("READY", item.status(), "exchange capability must not be READY: " + item);
            assertFalse(item.reasons().isEmpty(), "fail-closed item must carry reasons: " + item);
        }
    }

    @Test
    void noRealFamilyIsNeverReportedAsSuccess() {
        AdapterReadinessResponse response = newService().currentReadiness();

        List<AdapterReadinessItemResponse> noRealItems = response.items().stream()
                .filter(item -> NO_REAL_VENUES.contains(item.venue()))
                .toList();

        assertEquals(27, noRealItems.size(), "NOOP + PAPER + SIM × 9 capabilities");
        for (AdapterReadinessItemResponse item : noRealItems) {
            assertFalse(item.allowed(), "no-real capability must not be allowed: " + item);
            assertEquals("NO_REAL", item.status(), "no-real venue must report NO_REAL status: " + item);
            assertTrue(item.reasons().contains("NO_REAL_DISABLED"), "no-real reason must be present: " + item);
        }
    }

    @Test
    void mutatingCapabilitiesAreNotLiveAuthorized() {
        AdapterReadinessResponse response = newService().currentReadiness();

        List<AdapterReadinessItemResponse> mutating = response.items().stream()
                .filter(item -> "PLACE_ORDER".equals(item.capability()) || "CANCEL_ORDER".equals(item.capability()))
                .toList();

        assertEquals(10, mutating.size(), "PLACE_ORDER + CANCEL_ORDER across 5 venues");
        for (AdapterReadinessItemResponse item : mutating) {
            assertFalse(item.allowed(), "mutating capability must not be allowed: " + item);
            assertFalse(item.liveAuthorized(), "mutating capability must not be live-authorized: " + item);
            assertNotEquals("READY", item.status(), "mutating capability must not be READY: " + item);
        }
        // OKX / Binance 的 mutating 能力必须明确带 LIVE_DISABLED 原因。
        assertTrue(mutating.stream()
                        .filter(item -> EXCHANGE_VENUES.contains(item.venue()))
                        .allMatch(item -> item.reasons().contains("LIVE_DISABLED")),
                "exchange mutating capability must carry LIVE_DISABLED reason");
    }

    @Test
    void permissionProbeReportsRealProviderNotImplemented() {
        AdapterReadinessResponse response = newService().currentReadiness();

        List<AdapterReadinessItemResponse> probes = response.items().stream()
                .filter(item -> "PERMISSION_PROBE".equals(item.capability()))
                .filter(item -> EXCHANGE_VENUES.contains(item.venue()))
                .toList();

        assertEquals(2, probes.size(), "OKX + BINANCE permission probe");
        for (AdapterReadinessItemResponse item : probes) {
            assertFalse(item.allowed(), "permission probe must not be allowed: " + item);
            assertTrue(item.reasons().contains("REAL_PROVIDER_NOT_IMPLEMENTED"),
                    "permission probe must report real provider not implemented: " + item);
        }
    }

    @Test
    void responseNeverLeaksSecrets() {
        AdapterReadinessResponse response = newService().currentReadiness();

        for (AdapterReadinessItemResponse item : response.items()) {
            assertNoSecret(item.venue());
            assertNoSecret(item.capability());
            assertNoSecret(item.status());
            assertNoSecret(item.message());
            item.reasons().forEach(AdapterReadinessStatusServiceTest::assertNoSecret);
        }
    }

    private static AdapterReadinessStatusService newService() {
        return new AdapterReadinessStatusService(new DefaultAdapterReadinessService(FIXED_CLOCK), FIXED_CLOCK);
    }

    private static void assertNoSecret(String value) {
        String lower = value.toLowerCase();
        for (String token : SECRET_TOKENS) {
            assertFalse(lower.contains(token), "readiness field must not leak '" + token + "': " + value);
        }
    }

    /**
     * RecordingReadinessService 仅统计 evaluate 调用次数，证明 status service 不触达任何 adapter delegate。
     * <p>
     * AdapterReadinessService 接口本身不暴露 adapter delegate / HTTP / credential 入口，因此只要 service 仅依赖
     * 该接口，就在类型层面保证了「只读静态决策」；本记录器进一步断言调用次数与矩阵规模一致。
     */
    private static final class RecordingReadinessService implements AdapterReadinessService {

        private final AdapterReadinessService delegate;
        private int evaluateCalls;

        private RecordingReadinessService(AdapterReadinessService delegate) {
            this.delegate = delegate;
        }

        @Override
        public AdapterReadinessDecision evaluate(String venue, AdapterCapability capability) {
            evaluateCalls++;
            return delegate.evaluate(venue, capability);
        }

        private int evaluateCalls() {
            return evaluateCalls;
        }
    }
}
