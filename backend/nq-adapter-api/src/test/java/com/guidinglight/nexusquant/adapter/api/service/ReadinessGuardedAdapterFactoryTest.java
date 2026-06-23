package com.guidinglight.nexusquant.adapter.api.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
 * ReadinessGuardedAdapterFactoryTest 固定 GateM-2 装配原语：工厂产出的 adapter 必须已被 readiness guard 包装且 fail-closed。
 * <p>
 * Why:
 * 装配层用本工厂把 readiness guard 包到真实 / stub adapter 外层。本测试证明 guarded() 返回的 marketdata / trading
 * adapter 是 guard 包装后的类型，且未就绪时 fail-closed（marketdata subscribed=false、trading 抛出），delegate 不被触达。
 */
class ReadinessGuardedAdapterFactoryTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);

    private final AdapterReadinessService readiness = new DefaultAdapterReadinessService(FIXED_CLOCK);

    @Test
    void guardedMarketDataReturnsGuardWrappedAndFailClosed() {
        RecordingMarketDataAdapter delegate = new RecordingMarketDataAdapter("OKX");

        MarketDataAdapter assembled = ReadinessGuardedAdapterFactory.guarded(delegate, readiness);

        assertInstanceOf(ReadinessGuardedMarketDataAdapter.class, assembled);
        MarketDataSubscriptionAck ack =
                assembled.subscribeBars(new MarketDataSubscriptionRequest(1L, "OKX", "BTC-USDT", "trace-1"));
        assertFalse(ack.subscribed(), "assembled marketdata must be fail-closed");
        assertFalse(delegate.called, "delegate must not be reached when not ready");
    }

    @Test
    void guardedTradingReturnsGuardWrappedAndFailClosed() {
        FailingTradingAdapter delegate = new FailingTradingAdapter("OKX");

        TradingAdapter assembled = ReadinessGuardedAdapterFactory.guarded(delegate, readiness);

        assertInstanceOf(ReadinessGuardedTradingAdapter.class, assembled);
        assertThrows(IllegalStateException.class, () -> assembled.placeOrder(null));
    }

    @Test
    void guardedTradingIsFailClosedForBinanceAndUnknown() {
        for (String venue : new String[]{"BINANCE", "MYSTERY-DEX"}) {
            TradingAdapter assembled =
                    ReadinessGuardedAdapterFactory.guarded(new FailingTradingAdapter(venue), readiness);
            assertThrows(
                    IllegalStateException.class,
                    () -> assembled.placeOrder(null),
                    "assembled trading must fail-closed for venue=" + venue);
        }
    }

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
            return success(request);
        }

        @Override
        public MarketDataSubscriptionAck subscribeTrades(MarketDataSubscriptionRequest request) {
            return success(request);
        }

        @Override
        public MarketDataSubscriptionAck subscribeOrderBook(MarketDataSubscriptionRequest request) {
            return success(request);
        }

        private MarketDataSubscriptionAck success(MarketDataSubscriptionRequest request) {
            called = true;
            return new MarketDataSubscriptionAck(
                    true, venue, "bars", null, Instant.EPOCH, request == null ? null : request.traceId());
        }
    }

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
