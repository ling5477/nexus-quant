package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCapability;
import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessDecision;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessReason;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionAck;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionRequest;

import java.util.Objects;
import java.util.function.Function;

/**
 * ReadinessGuardedMarketDataAdapter 把 GateM-0 的 {@link AdapterReadinessService} 接到行情订阅入口。
 * <p>
 * Why:
 * GateM-1 要把 service-level guard 接进真实 runtime 路径，但不改主链路语义。本类是一个 decorator：
 * 任何 bars / trades / order-book 订阅在调用真实 provider 之前，先按 venue + capability 做 readiness 评估；
 * 未就绪时直接返回 fail-closed 的 {@code subscribed=false} ack（携带 readiness 原因），**绝不调用 delegate**，
 * 因此 marketdata 路径在 GateM baseline 下永远不会被误判为 success（满足 Noop NO_REAL_DISABLED 不得当成功）。
 *
 * <p>无 IO、无 credential、无网络、无副作用；线程安全取决于 delegate 与 service（默认实现均无状态）。
 * 当前 baseline 下 readiness 恒为未就绪，delegate 实际不会被触达；保留 delegate 调用分支是为未来
 * 另起 Gate 接入真实 provider 后能直接复用同一 guard。
 */
public final class ReadinessGuardedMarketDataAdapter implements MarketDataAdapter {

    private final MarketDataAdapter delegate;
    private final AdapterReadinessService readinessService;

    /**
     * @param delegate         被守卫的真实 / stub 行情 adapter；不可为空
     * @param readinessService readiness 评估服务；不可为空
     */
    public ReadinessGuardedMarketDataAdapter(
            MarketDataAdapter delegate, AdapterReadinessService readinessService) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.readinessService = Objects.requireNonNull(readinessService, "readinessService must not be null");
    }

    @Override
    public String venue() {
        return delegate.venue();
    }

    @Override
    public MarketDataSubscriptionAck subscribeBars(MarketDataSubscriptionRequest request) {
        return guard(AdapterCapability.SUBSCRIBE_BARS, "bars", request, delegate::subscribeBars);
    }

    @Override
    public MarketDataSubscriptionAck subscribeTrades(MarketDataSubscriptionRequest request) {
        return guard(AdapterCapability.SUBSCRIBE_TRADES, "trades", request, delegate::subscribeTrades);
    }

    @Override
    public MarketDataSubscriptionAck subscribeOrderBook(MarketDataSubscriptionRequest request) {
        return guard(AdapterCapability.SUBSCRIBE_ORDERBOOK, "order-book", request, delegate::subscribeOrderBook);
    }

    /**
     * 订阅前置 readiness guard。
     *
     * @param capability 本次订阅对应的能力维度
     * @param channel    通道名，用于 ack 与日志定位
     * @param request    订阅请求；fail-closed 分支仅透传 traceId，不创建真实订阅
     * @param realCall   就绪时才执行的真实订阅；GateM baseline 下不会被触达
     * @return 未就绪时返回 fail-closed disabled ack；就绪时返回真实订阅结果
     */
    private MarketDataSubscriptionAck guard(
            AdapterCapability capability,
            String channel,
            MarketDataSubscriptionRequest request,
            Function<MarketDataSubscriptionRequest, MarketDataSubscriptionAck> realCall) {
        AdapterReadinessDecision decision = readinessService.evaluate(delegate.venue(), capability);
        if (!decision.isReadyAndAllowed()) {
            // Fail-closed：未就绪时绝不触达真实 provider，也绝不返回 subscribed=true。
            return failClosedAck(channel, request, decision);
        }
        return realCall.apply(request);
    }

    /**
     * 把未就绪决策映射为 disabled marketdata ack。复用既有 AdapterError，不新增 DTO。
     * error.code 取主原因枚举名（如 NO_REAL_DISABLED / ENDPOINT_DISABLED_SENTINEL / UNKNOWN_REQUIRES_REVIEW），
     * message 只含 venue / status / reasons，不含 credential、signature 或 raw payload。
     */
    private static MarketDataSubscriptionAck failClosedAck(
            String channel, MarketDataSubscriptionRequest request, AdapterReadinessDecision decision) {
        AdapterReadinessReason primaryReason = decision.reasons().get(0);
        AdapterError error = new AdapterError(
                primaryReason.name(),
                "market data not ready: venue=" + decision.venue()
                        + ", status=" + decision.status()
                        + ", reasons=" + decision.reasons(),
                AdapterResultCategory.FATAL_FAILURE,
                false);
        String traceId = request == null ? null : request.traceId();
        return new MarketDataSubscriptionAck(false, decision.venue(), channel, error, decision.checkedAt(), traceId);
    }
}
