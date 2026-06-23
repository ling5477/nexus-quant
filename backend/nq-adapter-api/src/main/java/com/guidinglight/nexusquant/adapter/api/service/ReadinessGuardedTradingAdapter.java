package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCapability;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * ReadinessGuardedTradingAdapter 把 GateM-0 的 {@link AdapterReadinessService} 接到交易动作入口。
 * <p>
 * Why:
 * GateM-1 要在真实下单 / 撤单 / 查单路径前置 readiness fail-closed，但不改 nq-core 主链路语义。
 * 本类是一个 decorator：每个交易动作在调用真实 delegate 之前先 {@link AdapterReadinessService#requireReady}，
 * 未就绪时抛出 {@link IllegalStateException}，**绝不触达 delegate**。在 GateM baseline 下 readiness 恒为未就绪，
 * 因此真实下单 / 撤单永远 fail-closed（包括 LIVE DISABLED 时的 mutating 能力、未知 venue、credential 未配置）。
 *
 * <p>无 IO、无 credential、无网络、无副作用。requireReady 抛出的异常信息只含 venue / capability / status / message，
 * 不含 credential、apiKey、token、signature 或 raw payload。保留 delegate 调用分支供未来另起 Gate 接入真实 provider。
 */
public final class ReadinessGuardedTradingAdapter implements TradingAdapter {

    private final TradingAdapter delegate;
    private final AdapterReadinessService readinessService;

    /**
     * @param delegate         被守卫的真实 / stub 交易 adapter；不可为空
     * @param readinessService readiness 评估服务；不可为空
     */
    public ReadinessGuardedTradingAdapter(
            TradingAdapter delegate, AdapterReadinessService readinessService) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.readinessService = Objects.requireNonNull(readinessService, "readinessService must not be null");
    }

    @Override
    public String venue() {
        return delegate.venue();
    }

    @Override
    public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
        // Fail-closed：未就绪即抛出，绝不触达真实下单。
        readinessService.requireReady(delegate.venue(), AdapterCapability.PLACE_ORDER);
        return delegate.placeOrder(request);
    }

    @Override
    public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
        readinessService.requireReady(delegate.venue(), AdapterCapability.CANCEL_ORDER);
        return delegate.cancelOrder(request);
    }

    @Override
    public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
        readinessService.requireReady(delegate.venue(), AdapterCapability.QUERY_ORDER);
        return delegate.getOrder(query);
    }

    @Override
    public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
        readinessService.requireReady(delegate.venue(), AdapterCapability.QUERY_ORDER);
        return delegate.listOpenOrders(query);
    }
}
