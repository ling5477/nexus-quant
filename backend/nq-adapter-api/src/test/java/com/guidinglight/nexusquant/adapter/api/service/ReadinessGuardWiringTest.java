package com.guidinglight.nexusquant.adapter.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionAck;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * ReadinessGuardWiringTest 固定 GateM-1 readiness guard 接入行情订阅与交易动作入口的 fail-closed 行为。
 * <p>
 * Why:
 * GateM-1 把 GateM-0 AdapterReadinessService 接进 marketdata 与 trading runtime 入口。本测试证明：
 * OKX / Binance / Noop / 未知 venue 经 guard 后仍 fail-closed，marketdata 永远不返回 subscribed=true，
 * trading mutating 能力在 LIVE disabled 时抛出且绝不触达真实 delegate，异常信息不泄漏 secret。
 * 测试不访问外网、不读取 .env、不需要真实 credential。
 */
class ReadinessGuardWiringTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);

    private final AdapterReadinessService readiness = new DefaultAdapterReadinessService(FIXED_CLOCK);

    // ---- marketdata guard ----

    @Test
    void guardedMarketDataIsFailClosedForNoop() {
        RecordingMarketDataAdapter delegate = new RecordingMarketDataAdapter("NOOP");
        ReadinessGuardedMarketDataAdapter guarded =
                new ReadinessGuardedMarketDataAdapter(delegate, readiness);

        MarketDataSubscriptionAck ack = guarded.subscribeBars(request("NOOP"));

        assertFalse(ack.subscribed(), "Noop marketdata must not be subscribed=true");
        assertFalse(delegate.called, "delegate must not be called when not ready");
        assertNotNull(ack.error());
        assertEquals("NO_REAL_DISABLED", ack.error().code());
        assertFalse(ack.error().retryable());
        assertNoSecret(ack.error().message());
    }

    @Test
    void guardedMarketDataIsFailClosedForOkx() {
        RecordingMarketDataAdapter delegate = new RecordingMarketDataAdapter("OKX");
        ReadinessGuardedMarketDataAdapter guarded =
                new ReadinessGuardedMarketDataAdapter(delegate, readiness);

        MarketDataSubscriptionAck ack = guarded.subscribeTrades(request("OKX"));

        assertFalse(ack.subscribed());
        assertFalse(delegate.called);
        assertEquals("ENDPOINT_DISABLED_SENTINEL", ack.error().code());
        assertNoSecret(ack.error().message());
    }

    @Test
    void guardedMarketDataIsFailClosedForBinance() {
        RecordingMarketDataAdapter delegate = new RecordingMarketDataAdapter("BINANCE");
        ReadinessGuardedMarketDataAdapter guarded =
                new ReadinessGuardedMarketDataAdapter(delegate, readiness);

        MarketDataSubscriptionAck ack = guarded.subscribeOrderBook(request("BINANCE"));

        assertFalse(ack.subscribed());
        assertFalse(delegate.called);
        assertNotNull(ack.error());
        assertFalse(ack.error().retryable());
    }

    @Test
    void guardedMarketDataIsFailClosedForUnknownVenue() {
        RecordingMarketDataAdapter delegate = new RecordingMarketDataAdapter("MYSTERY-DEX");
        ReadinessGuardedMarketDataAdapter guarded =
                new ReadinessGuardedMarketDataAdapter(delegate, readiness);

        MarketDataSubscriptionAck ack = guarded.subscribeBars(request("MYSTERY-DEX"));

        assertFalse(ack.subscribed());
        assertFalse(delegate.called);
        assertEquals("UNKNOWN_REQUIRES_REVIEW", ack.error().code());
    }

    @Test
    void guardedMarketDataNeverReturnsSubscribedTrueAcrossChannels() {
        for (String venue : new String[]{"NOOP", "OKX", "BINANCE", "MYSTERY-DEX"}) {
            RecordingMarketDataAdapter delegate = new RecordingMarketDataAdapter(venue);
            ReadinessGuardedMarketDataAdapter guarded =
                    new ReadinessGuardedMarketDataAdapter(delegate, readiness);

            assertFalse(guarded.subscribeBars(request(venue)).subscribed());
            assertFalse(guarded.subscribeTrades(request(venue)).subscribed());
            assertFalse(guarded.subscribeOrderBook(request(venue)).subscribed());
            assertFalse(delegate.called, "delegate must never be reached: venue=" + venue);
        }
    }

    // ---- trading guard ----

    @Test
    void guardedTradingPlaceOrderFailsClosedForOkx() {
        FailingTradingAdapter delegate = new FailingTradingAdapter("OKX");
        ReadinessGuardedTradingAdapter guarded =
                new ReadinessGuardedTradingAdapter(delegate, readiness);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guarded.placeOrder(orderRequest("OKX")));

        assertTrue(ex.getMessage().contains("not ready"));
        assertNoSecret(ex.getMessage());
    }

    @Test
    void guardedTradingPlaceOrderFailsClosedForBinance() {
        FailingTradingAdapter delegate = new FailingTradingAdapter("BINANCE");
        ReadinessGuardedTradingAdapter guarded =
                new ReadinessGuardedTradingAdapter(delegate, readiness);

        assertThrows(
                IllegalStateException.class,
                () -> guarded.placeOrder(orderRequest("BINANCE")));
    }

    @Test
    void guardedTradingLiveMutatingFailsClosedWhenLiveDisabled() {
        // place / cancel 是 live-mutating，LIVE DISABLED 时必须 fail-closed，且绝不触达 delegate。
        FailingTradingAdapter delegate = new FailingTradingAdapter("OKX");
        ReadinessGuardedTradingAdapter guarded =
                new ReadinessGuardedTradingAdapter(delegate, readiness);

        assertThrows(IllegalStateException.class, () -> guarded.placeOrder(orderRequest("OKX")));
        assertThrows(IllegalStateException.class, () -> guarded.cancelOrder((AdapterCancelRequest) null));
    }

    @Test
    void guardedTradingQueryFailsClosed() {
        FailingTradingAdapter delegate = new FailingTradingAdapter("OKX");
        ReadinessGuardedTradingAdapter guarded =
                new ReadinessGuardedTradingAdapter(delegate, readiness);

        assertThrows(IllegalStateException.class, () -> guarded.getOrder((AdapterOrderQuery) null));
        assertThrows(IllegalStateException.class, () -> guarded.listOpenOrders((AdapterOpenOrdersQuery) null));
    }

    @Test
    void guardedTradingFailsClosedForNoopAndUnknownVenue() {
        for (String venue : new String[]{"NOOP", "MYSTERY-DEX"}) {
            FailingTradingAdapter delegate = new FailingTradingAdapter(venue);
            ReadinessGuardedTradingAdapter guarded =
                    new ReadinessGuardedTradingAdapter(delegate, readiness);

            assertThrows(
                    IllegalStateException.class,
                    () -> guarded.placeOrder(orderRequest(venue)),
                    "trading must fail-closed for venue=" + venue);
        }
    }

    @Test
    void guardExceptionMessageDoesNotLeakSecrets() {
        FailingTradingAdapter delegate = new FailingTradingAdapter("OKX");
        ReadinessGuardedTradingAdapter guarded =
                new ReadinessGuardedTradingAdapter(delegate, readiness);

        try {
            guarded.placeOrder(orderRequest("OKX"));
        } catch (IllegalStateException ex) {
            assertNoSecret(ex.getMessage());
            return;
        }
        throw new AssertionError("expected fail-closed IllegalStateException");
    }

    // ---- helpers ----

    private static MarketDataSubscriptionRequest request(String venue) {
        return new MarketDataSubscriptionRequest(1L, venue, "BTC-USDT", "trace-" + venue);
    }

    private static AdapterOrderRequest orderRequest(String venue) {
        return new AdapterOrderRequest(
                null, null, 1L, venue, "BTC-USDT", "cli-1", null,
                "BUY", "LIMIT", java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, null,
                null, null, null, "trace-1");
    }

    private static void assertNoSecret(String message) {
        String lower = message.toLowerCase();
        assertFalse(lower.contains("secret"), "message must not leak secret: " + message);
        assertFalse(lower.contains("apikey"), "message must not leak apiKey: " + message);
        assertFalse(lower.contains("token"), "message must not leak token: " + message);
        assertFalse(lower.contains("signature"), "message must not leak signature: " + message);
        assertFalse(lower.contains("passphrase"), "message must not leak passphrase: " + message);
    }

    /** 行情 delegate 测试替身：若 guard 误放行则 called=true 且 subscribed=true，可被断言捕获。 */
    private static final class RecordingMarketDataAdapter implements MarketDataAdapter {
        private final String venue;
        private boolean called = false;

        private RecordingMarketDataAdapter(String venue) {
            this.venue = venue;
        }

        @Override
        public String venue() {
            return venue;
        }

        @Override
        public MarketDataSubscriptionAck subscribeBars(MarketDataSubscriptionRequest request) {
            return success(request, "bars");
        }

        @Override
        public MarketDataSubscriptionAck subscribeTrades(MarketDataSubscriptionRequest request) {
            return success(request, "trades");
        }

        @Override
        public MarketDataSubscriptionAck subscribeOrderBook(MarketDataSubscriptionRequest request) {
            return success(request, "order-book");
        }

        private MarketDataSubscriptionAck success(MarketDataSubscriptionRequest request, String channel) {
            called = true;
            return new MarketDataSubscriptionAck(
                    true, venue, channel, null, Instant.EPOCH,
                    request == null ? null : request.traceId());
        }
    }

    /** 交易 delegate 测试替身：任何方法被触达即抛 AssertionError，证明 guard fail-closed 时绝不放行。 */
    private static final class FailingTradingAdapter implements TradingAdapter {
        private final String venue;

        private FailingTradingAdapter(String venue) {
            this.venue = venue;
        }

        @Override
        public String venue() {
            return venue;
        }

        @Override
        public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
            throw new AssertionError("delegate.placeOrder must not be called when not ready");
        }

        @Override
        public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
            throw new AssertionError("delegate.cancelOrder must not be called when not ready");
        }

        @Override
        public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
            throw new AssertionError("delegate.getOrder must not be called when not ready");
        }

        @Override
        public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
            throw new AssertionError("delegate.listOpenOrders must not be called when not ready");
        }
    }
}
