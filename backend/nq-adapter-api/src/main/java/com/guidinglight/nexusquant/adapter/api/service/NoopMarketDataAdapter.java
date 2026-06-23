package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionAck;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionRequest;
import java.time.Instant;
import java.util.Objects;

/**
 * NoopMarketDataAdapter 提供 no-real 阶段的行情 stub。
 * <p>
 * Why:
 * GateL-1B-D 要求调用方能区分真实市场数据订阅成功与 no-real / disabled / stub 状态。
 * 因此本 adapter 只保留 contract wiring 能力，不把 bars、trades、order-book 任一路径伪装成
 * 普通 success，避免上层误判为真实 provider 已就绪或可用于 LIVE / future-real readiness。
 */
public class NoopMarketDataAdapter implements MarketDataAdapter {

    /**
     * GateL-1B-D 固定的 no-real disabled 错误码。
     * <p>
     * Why:
     * 复用现有 AdapterError，不新增 DTO / enum / HTTP API；调用方可用该 code 区分 stub disabled
     * 与真实市场数据订阅成功。
     */
    public static final String NO_REAL_DISABLED_CODE = "NO_REAL_DISABLED";
    private static final String NO_REAL_DISABLED_MESSAGE =
            "Market data is disabled for the no-real stub adapter; no provider subscription was created.";

    private final String venue;

    /**
     * 构造 no-real 行情 adapter。
     *
     * @param venue 统一交易所/adapter 标识；不可为 null，用于 ack 中保留路由来源
     * @throws NullPointerException 当 venue 为 null 时抛出，避免生成不可审计的空 venue ack
     */
    public NoopMarketDataAdapter(String venue) {
        this.venue = Objects.requireNonNull(venue, "venue must not be null");
    }

    /**
     * 返回当前 no-real adapter 的 venue 标识。
     *
     * @return 构造时传入的 venue；无外部 IO、副作用或真实 provider 访问
     */
    @Override
    public String venue() {
        return venue;
    }

    /**
     * 处理 K 线订阅请求。
     *
     * @param request 行情订阅请求；当前仅透传 traceId，避免误创建真实订阅
     * @return no-real disabled ack，固定 subscribed=false 且携带 NO_REAL_DISABLED
     */
    @Override
    public MarketDataSubscriptionAck subscribeBars(MarketDataSubscriptionRequest request) {
        return ack("bars", request.traceId());
    }

    /**
     * 处理成交订阅请求。
     *
     * @param request 行情订阅请求；当前仅透传 traceId，避免误创建真实订阅
     * @return no-real disabled ack，固定 subscribed=false 且携带 NO_REAL_DISABLED
     */
    @Override
    public MarketDataSubscriptionAck subscribeTrades(MarketDataSubscriptionRequest request) {
        return ack("trades", request.traceId());
    }

    /**
     * 处理盘口订阅请求。
     *
     * @param request 行情订阅请求；当前仅透传 traceId，避免误创建真实订阅
     * @return no-real disabled ack，固定 subscribed=false 且携带 NO_REAL_DISABLED
     */
    @Override
    public MarketDataSubscriptionAck subscribeOrderBook(MarketDataSubscriptionRequest request) {
        return ack("order-book", request.traceId());
    }

    private MarketDataSubscriptionAck ack(String channel, String traceId) {
        // GateL-1B-D intentionally uses a terminal, non-retryable failure category for the noop stub.
        // Retrying cannot create a real subscription, and returning subscribed=true would make callers
        // treat the no-real adapter as a live market data provider.
        AdapterError error = new AdapterError(
                NO_REAL_DISABLED_CODE,
                NO_REAL_DISABLED_MESSAGE,
                AdapterResultCategory.FATAL_FAILURE,
                false
        );
        return new MarketDataSubscriptionAck(false, venue, channel, error, Instant.now(), traceId);
    }
}
