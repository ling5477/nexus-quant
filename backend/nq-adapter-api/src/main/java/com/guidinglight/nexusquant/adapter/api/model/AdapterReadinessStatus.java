package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterReadinessStatus 描述某个 adapter / venue / capability 的运行时就绪状态。
 * <p>
 * Why:
 * GateM-0 把 GateL 的 No-Real 文档边界落成运行时约束。调用方（交易入口、marketdata 入口、
 * 前端或上层服务）需要一个明确的就绪状态，避免把 OKX / Binance / Noop 误判为 real-ready。
 * 当前阶段除非另起 Gate，否则任何真实交易能力都不会出现 {@link #READY}。除 {@code READY} 外的所有状态，
 * 包括名称里带 paper-ready 的状态，都只表达“当前可解释地不可实盘”，不构成 LIVE 或真实交易授权。
 */
public enum AdapterReadinessStatus {

    /** 能力可用。GateM-0 内任何真实交易所能力都不允许进入该状态。 */
    READY,

    /** 通用未就绪：OKX / Binance 当前默认走该状态（endpoint sentinel / credential 未配置 / LIVE 未授权）。 */
    NOT_READY,

    /** no-real stub：Noop adapter 永远不是真实 provider。 */
    NO_REAL,

    /** fake adapter：仅可作为测试替身或显式 fake，不得被解释为真实 provider。 */
    FAKE,

    /** stub adapter：仅可作为占位实现，不得被解释为真实 provider。 */
    STUB,

    /** PAPER / SIM 仅允许纸面语义，不得提升为 LIVE 或 real-ready。 */
    READY_FOR_PAPER_ONLY,

    /** 默认 endpoint 为 disabled:// sentinel，不可路由真实交易所。 */
    DISABLED_SENTINEL,

    /** runtime credential 未配置（*.unconfigured()），authenticated 请求网络前 fail-closed。 */
    CREDENTIAL_UNCONFIGURED,

    /** LIVE 未授权；真实下单 / 撤单类能力 fail-closed。 */
    LIVE_NOT_AUTHORIZED,

    /** future-real adapter 入口尚未实现；只能作为未来 Gate 的禁用占位。 */
    FUTURE_REAL_DISABLED,

    /** permission probe 被显式禁用 / skipped，不得解释为真实权限已验证。 */
    PERMISSION_PROBE_DISABLED,

    /** 能力未实现；保留给 future-real / real provider 入口做非授权表达。 */
    NOT_IMPLEMENTED,

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
