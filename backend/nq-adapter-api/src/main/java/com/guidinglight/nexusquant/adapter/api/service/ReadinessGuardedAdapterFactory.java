package com.guidinglight.nexusquant.adapter.api.service;

/**
 * ReadinessGuardedAdapterFactory 是把 readiness guard 装配到 adapter 的统一入口。
 * <p>
 * Why:
 * GateM-2 要在装配层（Spring configuration / factory / registry）把 {@link ReadinessGuardedMarketDataAdapter}
 * 与 {@link ReadinessGuardedTradingAdapter} 包到真实 / stub adapter 外层，让调用方默认拿到经过 readiness 守卫的
 * adapter，而不是孤立 decorator。本工厂把“包装”这一步集中成可复用、可单测的纯函数装配原语，避免装配点散落手写
 * decorator 构造。readiness guard 是外层权威，被包装的 delegate 是内层兜底（GateM-1 P2-2 收敛）。
 *
 * <p>无 IO、无 credential、无网络、无副作用。
 */
public final class ReadinessGuardedAdapterFactory {

    private ReadinessGuardedAdapterFactory() {
    }

    /**
     * 用 readiness guard 包装行情 adapter。
     *
     * @param delegate         真实 / stub 行情 adapter；不可为空
     * @param readinessService readiness 评估服务；不可为空
     * @return readiness 守卫后的行情 adapter（外层 guard，内层 delegate）
     */
    public static MarketDataAdapter guarded(MarketDataAdapter delegate, AdapterReadinessService readinessService) {
        return new ReadinessGuardedMarketDataAdapter(delegate, readinessService);
    }

    /**
     * 用 readiness guard 包装交易 adapter。
     *
     * @param delegate         真实 / stub 交易 adapter；不可为空
     * @param readinessService readiness 评估服务；不可为空
     * @return readiness 守卫后的交易 adapter（外层 guard，内层 delegate）
     */
    public static TradingAdapter guarded(TradingAdapter delegate, AdapterReadinessService readinessService) {
        return new ReadinessGuardedTradingAdapter(delegate, readinessService);
    }
}
