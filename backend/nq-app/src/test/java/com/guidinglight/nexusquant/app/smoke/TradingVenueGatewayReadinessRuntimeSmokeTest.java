package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.app.config.ExchangeAdapterConfiguration;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.scheduler.service.AdapterBackedTradingVenueGateway;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.port.TradingCancelGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayFailure;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayResultCategory;
import com.guidinglight.nexusquant.trading.application.port.TradingOrderStatusSnapshot;
import com.guidinglight.nexusquant.trading.application.port.TradingPlaceGatewayResult;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * TradingVenueGatewayReadinessRuntimeSmokeTest 是 GateM-4 的消费侧 runtime smoke。
 * <p>
 * Why:
 * GateM-3 的 {@code ExchangeAdapterConfigurationReadinessTest} 已经证明 app 装配出的 OKX / Binance
 * trading adapter Bean 在被「直接」调用 place / cancel / query / list-open-orders 时 fail-closed。
 * 但真实运行时不是直接调 adapter，而是 scheduler 侧的 {@link AdapterBackedTradingVenueGateway}（一个
 * nq-scheduler @Component）按 venue 路由 {@code Collection<TradingAdapter>} 后再触发 adapter 调用。
 * <p>
 * 本 smoke 把「Spring app 装配的 guarded adapter 集合」喂给真实 gateway，再从 gateway 这个更高层的消费侧
 * 触发 placeOrder / cancelOrder / getOrderStatus，断言：
 * <ol>
 *   <li>OKX 与 Binance 两个 venue 都被 readiness guard fail-closed；</li>
 *   <li>gateway 把 readiness {@link IllegalStateException} 统一降级为可审计的
 *       {@link TradingGatewayResultCategory#REMOTE_UNAVAILABLE}，而不是 ACCEPTED / SUCCESS；</li>
 *   <li>失败信息携带 readiness guard 原文（"adapter capability not ready"），证明短路发生在
 *       validate / HTTP / cache / order mutation 之前——若真的触达远端，错误会是网络 / 业务错误码而非 readiness 文案；</li>
 *   <li>异常 / 失败 message 不泄漏 secret / apiKey / api_key / token / signature / passphrase；</li>
 *   <li>gateway 路由表无 duplicate venue（构造不抛 duplicate，且集合恰为 OKX + BINANCE 两个）。</li>
 * </ol>
 * <p>
 * 该测试只启动轻量 {@link AnnotationConfigApplicationContext}：无 DB、无 web server、无 scheduler 业务执行、
 * 无 .env / credential 读取、无外联。adapter Bean 的构造路径与 GateM-3 测试一致（bootstrap 不外联、不读真实凭证）。
 * 不启用 LIVE、不接 AI / DH、不实现 RealClient / real provider。
 */
class TradingVenueGatewayReadinessRuntimeSmokeTest {

    private static final String READINESS_DENIAL_MARKER = "adapter capability not ready";

    @Test
    void okxConsumerSideGatewayFailsClosedForPlaceCancelAndQuery() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            AdapterBackedTradingVenueGateway gateway = newGatewayFromSpringAssembledAdapters(context);

            assertGatewayFailsClosed("OKX", gateway);
            // listOpenOrders 没有上层 TradingVenueGateway 方法，因此在 gateway 消费的同一个
            // Spring 装配 guarded adapter Bean 上直接断言其 fail-closed，补齐第四个交易方法。
            assertListOpenOrdersFailsClosed(context.getBean("okxTradingAdapter", TradingAdapter.class), "OKX");
        }
    }

    @Test
    void binanceConsumerSideGatewayFailsClosedForPlaceCancelAndQuery() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            AdapterBackedTradingVenueGateway gateway = newGatewayFromSpringAssembledAdapters(context);

            assertGatewayFailsClosed("BINANCE", gateway);
            assertListOpenOrdersFailsClosed(context.getBean("binanceTradingAdapter", TradingAdapter.class), "BINANCE");
        }
    }

    @Test
    void springAssembledTradingAdapterCollectionHasOnlyOkxAndBinanceWithoutDuplicateVenue() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            Map<String, TradingAdapter> adapters = context.getBeansOfType(TradingAdapter.class);

            // 仅 OKX + Binance 两个 trading adapter；gateway 构造（下方）按 venue 收口为不可变 map，
            // 若存在 duplicate venue 会在构造期抛出，因此「构造成功 + 集合恰为 2」即可证明无重复路由。
            assertEquals(2, adapters.size());
            assertInstanceOf(OkxExchangeAdapter.class, context.getBean("okxTradingAdapter", TradingAdapter.class));
            assertInstanceOf(BinanceExchangeAdapter.class, context.getBean("binanceTradingAdapter", TradingAdapter.class));
            assertNotNull(newGatewayFromSpringAssembledAdapters(context));
        }
    }

    /**
     * 用 Spring app 装配出的 guarded adapter 集合构造真实 scheduler gateway，模拟 scheduler/service 消费侧。
     */
    private static AdapterBackedTradingVenueGateway newGatewayFromSpringAssembledAdapters(
            AnnotationConfigApplicationContext context) {
        return new AdapterBackedTradingVenueGateway(context.getBeansOfType(TradingAdapter.class).values());
    }

    private static AnnotationConfigApplicationContext newContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ExchangeAdapterConfiguration.class);
        context.refresh();
        return context;
    }

    /**
     * 从 gateway 消费侧依次触发 place / cancel / query，断言全部 fail-closed 且降级为 REMOTE_UNAVAILABLE。
     */
    private static void assertGatewayFailsClosed(String venue, AdapterBackedTradingVenueGateway gateway) {
        OrderRecord order = orderRecord(venue);

        TradingPlaceGatewayResult placeResult = gateway.placeOrder(order, placeRequest(venue));
        assertFalse(placeResult.accepted(), "place must not be accepted while readiness fail-closed");
        assertNull(placeResult.exchangeOrderId(), "fail-closed place must not yield an exchange order id");
        assertEquals(TradingGatewayResultCategory.REMOTE_UNAVAILABLE, placeResult.resultCategory());
        assertReadinessFailure(placeResult.failure());

        TradingCancelGatewayResult cancelResult = gateway.cancelOrder(order, cancelRequest(venue));
        assertFalse(cancelResult.accepted(), "cancel must not be accepted while readiness fail-closed");
        assertEquals(TradingGatewayResultCategory.REMOTE_UNAVAILABLE, cancelResult.resultCategory());
        assertReadinessFailure(cancelResult.failure());

        TradingOrderStatusSnapshot statusSnapshot = gateway.getOrderStatus(order, "trace-status-" + venue);
        assertEquals(TradingGatewayResultCategory.REMOTE_UNAVAILABLE, statusSnapshot.resultCategory());
        assertEquals("UNKNOWN", statusSnapshot.externalStatus());
        assertReadinessFailure(statusSnapshot.failure());
    }

    /**
     * listOpenOrders 没有上层 gateway 方法，直接在 Spring 装配的 guarded adapter Bean 上断言 fail-closed。
     */
    private static void assertListOpenOrdersFailsClosed(TradingAdapter adapter, String venue) {
        assertEquals(venue, adapter.venue());
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> adapter.listOpenOrders(new AdapterOpenOrdersQuery(1L, venue, "BTC-USDT", "trace-open-" + venue)));
        assertTrue(ex.getMessage().contains(READINESS_DENIAL_MARKER), "listOpenOrders must fail-closed: " + ex.getMessage());
        assertNoSecret(ex.getMessage());
    }

    /**
     * 断言失败由 readiness guard 触发，且 message 已脱敏。
     */
    private static void assertReadinessFailure(TradingGatewayFailure failure) {
        assertNotNull(failure, "fail-closed result must carry an auditable failure");
        assertEquals("ADAPTER_CALL_FAILED", failure.code());
        assertTrue(failure.retryable(), "readiness fail-closed is reported as retryable/deferrable, not fatal");
        assertNotNull(failure.message());
        // 关键证明：失败原文是 readiness guard 文案，而非 HTTP/网络/业务错误码——short-circuit 早于真实交易逻辑。
        assertTrue(
                failure.message().contains(READINESS_DENIAL_MARKER),
                "failure must originate from readiness guard, not from a reached delegate: " + failure.message());
        assertNoSecret(failure.message());
    }

    private static OrderRecord orderRecord(String venue) {
        return new OrderRecord(
                "ord-" + venue,
                1L,
                "run-" + venue,
                venue,
                "BTC-USDT",
                "coid-" + venue,
                "BUY",
                "LIMIT",
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                OrderStatus.ACCEPTED,
                "ORDER_ACKED_BY_ADAPTER",
                "trace-" + venue);
    }

    private static PlaceOrderRequest placeRequest(String venue) {
        return new PlaceOrderRequest(
                1L,
                "run-" + venue,
                venue,
                "coid-" + venue,
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "trace-place-" + venue);
    }

    private static CancelOrderRequest cancelRequest(String venue) {
        return new CancelOrderRequest(
                "ord-" + venue,
                1L,
                "coid-" + venue,
                "gate-m-runtime-smoke",
                "trace-cancel-" + venue);
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
}
