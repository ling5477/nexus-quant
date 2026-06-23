package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterCapability 枚举 adapter 在运行时可被请求的能力维度。
 * <p>
 * Why:
 * readiness 判定需要按能力区分约束来源：交易类能力需要 credential 与（mutating 时）LIVE 授权；
 * marketdata 类能力需要真实 provider；permission probe 需要真实 probe 实现。把这些分类固化为枚举属性，
 * 让 {@code AdapterReadinessService} 能稳定选择 fail-closed 原因，而不是散落 if/else。
 *
 * <p>三个属性仅用于 readiness 原因选择，不代表当前任何能力被授权。GateM-0 内所有真实能力均 fail-closed。
 */
public enum AdapterCapability {

    SPOT_TRADING(true, true, false),
    PLACE_ORDER(true, true, false),
    CANCEL_ORDER(true, true, false),
    QUERY_ORDER(true, false, false),
    ACCOUNT_BALANCE(true, false, false),
    PUBLIC_MARKETDATA(false, false, true),
    PRIVATE_MARKETDATA(true, false, true),
    PERMISSION_PROBE(true, false, false),
    HISTORICAL_OHLCV(false, false, true),
    SUBSCRIBE_BARS(false, false, true),
    SUBSCRIBE_TRADES(false, false, true),
    SUBSCRIBE_ORDERBOOK(false, false, true);

    private final boolean requiresCredentials;
    private final boolean liveMutating;
    private final boolean marketData;

    AdapterCapability(boolean requiresCredentials, boolean liveMutating, boolean marketData) {
        this.requiresCredentials = requiresCredentials;
        this.liveMutating = liveMutating;
        this.marketData = marketData;
    }

    /**
     * 是否需要私有凭证。
     *
     * @return true 表示该能力调用真实交易所私有接口，credential 未配置时必须 fail-closed
     */
    public boolean requiresCredentials() {
        return requiresCredentials;
    }

    /**
     * 是否为会改变真实账户状态的下单 / 撤单类能力。
     *
     * @return true 表示该能力需要 LIVE 授权；GateL baseline LIVE DISABLED 时必须 fail-closed
     */
    public boolean isLiveMutating() {
        return liveMutating;
    }

    /**
     * 是否为行情类能力（public / private / historical / 订阅）。
     *
     * @return true 表示该能力依赖真实 marketdata provider，当前 real provider 未实现
     */
    public boolean isMarketData() {
        return marketData;
    }
}
