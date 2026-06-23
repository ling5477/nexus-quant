package com.guidinglight.nexusquant.adapter.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionAck;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionRequest;

import org.junit.jupiter.api.Test;

/**
 * NoopMarketDataAdapterTest 固定 GateL-1B-D 的 no-real marketdata status hardening。
 * <p>
 * Why:
 * NoopMarketDataAdapter 只用于 contract wiring 和 no-real disabled 占位。bars、trades、order-book
 * 任一路径都不能返回普通 success，否则调用方可能把 stub 误认为真实市场数据订阅已经建立。
 */
class NoopMarketDataAdapterTest {

    private static final MarketDataSubscriptionRequest REQUEST =
            new MarketDataSubscriptionRequest(101L, "NOOP", "BTC-USDT", "trc-noop-marketdata");

    @Test
    void shouldReturnNoRealDisabledForBarsSubscription() {
        MarketDataSubscriptionAck ack = new NoopMarketDataAdapter("NOOP").subscribeBars(REQUEST);

        assertNoRealDisabled(ack, "bars");
    }

    @Test
    void shouldReturnNoRealDisabledForTradesSubscription() {
        MarketDataSubscriptionAck ack = new NoopMarketDataAdapter("NOOP").subscribeTrades(REQUEST);

        assertNoRealDisabled(ack, "trades");
    }

    @Test
    void shouldReturnNoRealDisabledForOrderBookSubscription() {
        MarketDataSubscriptionAck ack = new NoopMarketDataAdapter("NOOP").subscribeOrderBook(REQUEST);

        assertNoRealDisabled(ack, "order-book");
    }

    private static void assertNoRealDisabled(MarketDataSubscriptionAck ack, String expectedChannel) {
        assertFalse(ack.subscribed());
        assertEquals("NOOP", ack.venue());
        assertEquals(expectedChannel, ack.channel());
        assertEquals("trc-noop-marketdata", ack.traceId());
        assertNotNull(ack.ts());
        assertNotNull(ack.error());
        assertEquals(NoopMarketDataAdapter.NO_REAL_DISABLED_CODE, ack.error().code());
        assertEquals(AdapterResultCategory.FATAL_FAILURE, ack.error().category());
        assertFalse(ack.error().retryable());
    }
}
