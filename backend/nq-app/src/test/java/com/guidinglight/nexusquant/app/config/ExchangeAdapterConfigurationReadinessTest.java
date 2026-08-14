package com.guidinglight.nexusquant.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ExchangeAdapterConfigurationReadinessTest 固定 GateM-3 trading 装配层 fail-closed 行为。
 * <p>
 * Why:
 * OKX / Binance Bean 仍需保留具体类型给历史消费者，同时默认 TradingAdapter 集合不能出现同 venue duplicate。
 * 本测试证明 app 装配后的具体 adapter 本体已在交易动作入口 fail-closed，防止 AdapterBackedTradingVenueGateway
 * 绕过 readiness guard 触达真实交易逻辑。本测试只启动轻量 Spring context，无 DB、无外联、无 credential 读取。
 */
class ExchangeAdapterConfigurationReadinessTest {

    @Test
    void defaultTradingAdapterCollectionContainsOnlyGuardedOkxAndBinanceAdaptersWithoutDuplicateVenue() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            assertNotNull(context.getBean(OkxExchangeAdapter.class));
            assertNotNull(context.getBean(BinanceExchangeAdapter.class));

            TradingAdapterCollector collector = context.getBean(TradingAdapterCollector.class);
            Map<String, TradingAdapter> adapters = collector.byVenue();

            assertEquals(2, adapters.size());
            assertInstanceOf(OkxExchangeAdapter.class, adapters.get("OKX"));
            assertInstanceOf(BinanceExchangeAdapter.class, adapters.get("BINANCE"));
            assertTrue(context.getBeansOfType(SpotExecutionProviderPort.class).isEmpty(),
                    "GateY provider contract must not be wired into default runtime");
        }
    }

    @Test
    void okxTradingAdapterBeanFailsClosedForAllTradingMethods() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            TradingAdapter adapter = context.getBean("okxTradingAdapter", TradingAdapter.class);

            assertGuardedAndFailClosed("OKX", adapter);
        }
    }

    @Test
    void binanceTradingAdapterBeanFailsClosedForAllTradingMethods() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            TradingAdapter adapter = context.getBean("binanceTradingAdapter", TradingAdapter.class);

            assertGuardedAndFailClosed("BINANCE", adapter);
        }
    }

    @Test
    void gateWProfileDoesNotRegisterMutatingTradingOrPrivateWebSocketBeans() {
        try (AnnotationConfigApplicationContext context = newContext("gatew")) {
            assertTrue(context.getBeansOfType(TradingAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxExchangeAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(BinanceExchangeAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxWsClient.class).isEmpty());
            assertTrue(context.getBeansOfType(BinanceWsClient.class).isEmpty());
            assertTrue(context.getBeansOfType(SpotExecutionProviderPort.class).isEmpty());
        }
    }

    private static AnnotationConfigApplicationContext newContext(String... activeProfiles) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(activeProfiles);
        context.register(ExchangeAdapterConfiguration.class, ReadinessTestConfiguration.class);
        context.refresh();
        return context;
    }

    private static void assertGuardedAndFailClosed(String venue, TradingAdapter adapter) {
        assertEquals(venue, adapter.venue());

        assertFailsClosedWithoutSecret(() -> adapter.placeOrder(orderRequest(venue)));
        assertFailsClosedWithoutSecret(() -> adapter.cancelOrder(cancelRequest(venue)));
        assertFailsClosedWithoutSecret(() -> adapter.getOrder(orderQuery(venue)));
        assertFailsClosedWithoutSecret(() -> adapter.listOpenOrders(openOrdersQuery(venue)));
    }

    private static void assertFailsClosedWithoutSecret(ThrowingRunnable action) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, action::run);
        String message = ex.getMessage();
        assertTrue(message.contains("adapter capability not ready"));
        assertNoSecret(message);
    }

    private static AdapterOrderRequest orderRequest(String venue) {
        return new AdapterOrderRequest(
                "req-" + venue,
                "order-" + venue,
                1L,
                venue,
                "BTC-USDT",
                "client-" + venue,
                "idem-" + venue,
                "BUY",
                "LIMIT",
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                "GTC",
                "manual",
                null,
                "trace-" + venue);
    }

    private static AdapterCancelRequest cancelRequest(String venue) {
        return new AdapterCancelRequest(
                "cancel-" + venue,
                "order-" + venue,
                1L,
                venue,
                "BTC-USDT",
                "client-" + venue,
                "external-" + venue,
                "gate-m-readiness-test",
                "trace-cancel-" + venue);
    }

    private static AdapterOrderQuery orderQuery(String venue) {
        return new AdapterOrderQuery(
                1L,
                venue,
                "BTC-USDT",
                "client-" + venue,
                "external-" + venue,
                "trace-query-" + venue);
    }

    private static AdapterOpenOrdersQuery openOrdersQuery(String venue) {
        return new AdapterOpenOrdersQuery(
                1L,
                venue,
                "BTC-USDT",
                "trace-open-orders-" + venue);
    }

    private static void assertNoSecret(String message) {
        String lower = message.toLowerCase();
        assertFalse(lower.contains("secret"), "message must not leak secret: " + message);
        assertFalse(lower.contains("apikey"), "message must not leak apiKey: " + message);
        assertFalse(lower.contains("api_key"), "message must not leak api_key: " + message);
        assertFalse(lower.contains("token"), "message must not leak token: " + message);
        assertFalse(lower.contains("signature"), "message must not leak signature: " + message);
        assertFalse(lower.contains("passphrase"), "message must not leak passphrase: " + message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record TradingAdapterCollector(Collection<TradingAdapter> adapters) {

        private Map<String, TradingAdapter> byVenue() {
            return adapters.stream()
                    .collect(Collectors.toUnmodifiableMap(TradingAdapter::venue, Function.identity()));
        }
    }

    @Configuration
    static class ReadinessTestConfiguration {

        @Bean
        TradingAdapterCollector tradingAdapterCollector(Collection<TradingAdapter> adapters) {
            return new TradingAdapterCollector(adapters);
        }
    }
}
