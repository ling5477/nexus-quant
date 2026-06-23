package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterReadinessStatus 描述某个 adapter / venue / capability 的运行时就绪状态。
 * <p>
 * Why:
 * GateM-0 把 GateL 的 No-Real 文档边界落成运行时约束。调用方（交易入口、marketdata 入口、
 * 前端或上层服务）需要一个明确的就绪状态，避免把 OKX / Binance / Noop 误判为 real-ready。
 * 当前阶段除非另起 Gate，否则任何真实交易能力都不会出现 {@link #READY}。
 */
public enum AdapterReadinessStatus {

    /** 能力可用。GateM-0 内任何真实交易所能力都不允许进入该状态。 */
    READY,

    /** 通用未就绪：OKX / Binance 当前默认走该状态（endpoint sentinel / credential 未配置 / LIVE 未授权）。 */
    NOT_READY,

    /** no-real stub：Noop adapter 永远不是真实 provider。 */
    NO_REAL,

    /** 默认 endpoint 为 disabled:// sentinel，不可路由真实交易所。 */
    DISABLED_SENTINEL,

    /** runtime credential 未配置（*.unconfigured()），authenticated 请求网络前 fail-closed。 */
    CREDENTIAL_UNCONFIGURED,

    /** LIVE 未授权；真实下单 / 撤单类能力 fail-closed。 */
    LIVE_NOT_AUTHORIZED,

    /** 能力尚未实现（real provider / real permission probe NOT IMPLEMENTED）。 */
    CAPABILITY_NOT_IMPLEMENTED,

    /** 证据不足 / 未知 venue 或未知 capability，默认 fail-closed。 */
    UNKNOWN_REQUIRES_REVIEW;

    /**
     * 是否表示能力真实可用。
     *
     * @return 仅 {@link #READY} 返回 true；其余一律视为未就绪，便于上层 fail-closed 判断
     */
    public boolean isReady() {
        return this == READY;
    }
}
