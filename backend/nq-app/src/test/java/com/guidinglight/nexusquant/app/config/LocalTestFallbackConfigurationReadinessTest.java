package com.guidinglight.nexusquant.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.guidinglight.nexusquant.adapter.api.service.AdapterReadinessService;
import com.guidinglight.nexusquant.adapter.api.service.DefaultAdapterReadinessService;
import com.guidinglight.nexusquant.adapter.api.service.MarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.ReadinessGuardedMarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionAck;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionRequest;

import org.junit.jupiter.api.Test;

/**
 * LocalTestFallbackConfigurationReadinessTest 固定 GateM-2 装配层行为：本配置装配出的行情 adapter 默认被 readiness guard 包装且 fail-closed。
 * <p>
 * Why:
 * 直接实例化配置类调用 @Bean 工厂方法（不启动 Spring context、不连 DB、不外联），证明装配点把 readiness guard 包到外层，
 * PAPER / OKX / BINANCE 行情订阅一律 fail-closed（subscribed=false），不会把 stub 误判为真实订阅成功，且不泄漏敏感词。
 */
class LocalTestFallbackConfigurationReadinessTest {

    private final LocalTestFallbackConfiguration configuration = new LocalTestFallbackConfiguration();
    private final AdapterReadinessService readiness = new DefaultAdapterReadinessService();

    private static MarketDataSubscriptionRequest request(String venue) {
        return new MarketDataSubscriptionRequest(1L, venue, "BTC-USDT", "trace-" + venue);
    }

    @Test
    void paperMarketDataIsGuardWrappedAndFailClosed() {
        MarketDataAdapter adapter = configuration.paperMarketDataAdapter(readiness);

        assertInstanceOf(ReadinessGuardedMarketDataAdapter.class, adapter);
        assertEquals("PAPER", adapter.venue());

        MarketDataSubscriptionAck ack = adapter.subscribeBars(request("PAPER"));
        assertFalse(ack.subscribed());
        assertNotNull(ack.error());
        // PAPER 属 no-real 家族：readiness guard 外层产出 NO_REAL_DISABLED。
        assertEquals("NO_REAL_DISABLED", ack.error().code());
        assertFalse(ack.error().retryable());
        assertNoSecret(ack.error().message());
    }

    @Test
    void okxMarketDataIsGuardWrappedAndFailClosed() {
        MarketDataAdapter adapter = configuration.okxMarketDataAdapter(readiness);

        assertInstanceOf(ReadinessGuardedMarketDataAdapter.class, adapter);
        assertEquals("OKX", adapter.venue());

        MarketDataSubscriptionAck ack = adapter.subscribeTrades(request("OKX"));
        assertFalse(ack.subscribed(), "OKX marketdata must be fail-closed");
        // OKX 经 readiness guard 外层：endpoint sentinel fail-closed。
        assertEquals("ENDPOINT_DISABLED_SENTINEL", ack.error().code());
        assertNoSecret(ack.error().message());
    }

    @Test
    void binanceMarketDataIsGuardWrappedAndFailClosed() {
        MarketDataAdapter adapter = configuration.binanceMarketDataAdapter(readiness);

        assertInstanceOf(ReadinessGuardedMarketDataAdapter.class, adapter);
        assertEquals("BINANCE", adapter.venue());

        MarketDataSubscriptionAck ack = adapter.subscribeOrderBook(request("BINANCE"));
        assertFalse(ack.subscribed());
        assertNotNull(ack.error());
        assertEquals("ENDPOINT_DISABLED_SENTINEL", ack.error().code());
        assertNoSecret(ack.error().message());
    }

    private static void assertNoSecret(String message) {
        String lower = message.toLowerCase();
        assertFalse(lower.contains("secret"));
        assertFalse(lower.contains("apikey"));
        assertFalse(lower.contains("token"));
        assertFalse(lower.contains("signature"));
        assertFalse(lower.contains("passphrase"));
    }
}
