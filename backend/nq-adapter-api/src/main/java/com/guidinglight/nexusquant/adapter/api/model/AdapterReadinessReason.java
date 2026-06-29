package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterReadinessReason 描述某个能力当前不可用（或受限）的可审计原因。
 * <p>
 * Why:
 * 仅返回 status 不足以让调用方解释“为什么 fail-closed”。GateM-0 要求把 GateL-1B/1C/1D 已冻结的
 * no-real / disabled sentinel / credential missing / live disabled / capability forbidden 等边界，
 * 以结构化原因暴露给交易入口、marketdata 入口与上层服务。原因只用于解释边界，不构成真实交易授权。
 */
public enum AdapterReadinessReason {

    /** Noop / no-real stub 明确禁用，未创建真实订阅或真实执行（对应 GateL-1D NO_REAL_DISABLED）。 */
    NO_REAL_DISABLED,

    /** fake adapter 只能作为测试替身或显式 fake，不能被解释为真实交易所能力。 */
    FAKE_ADAPTER_DISABLED,

    /** stub adapter 只能作为占位实现，不能被解释为真实交易所能力。 */
    STUB_ADAPTER_DISABLED,

    /** PAPER / SIM 仅允许纸面语义，不能被提升为 LIVE / real-ready。 */
    READY_FOR_PAPER_ONLY,

    /** Paper order / fill / balance / risk / publish artefact 不构成真实交易授权。 */
    PAPER_ARTIFACT_NOT_REAL_AUTHORIZATION,

    /** 默认 endpoint 为 disabled:// sentinel，请求期 loud fail-closed（对应 GateL-1D NETWORK_DISABLED）。 */
    ENDPOINT_DISABLED_SENTINEL,

    /** runtime credential 未配置；authenticated 请求网络前 fail-closed（对应 GateL-1D CREDENTIALS_MISSING）。 */
    CREDENTIALS_MISSING,

    /** runtime credential 显式处于 unconfigured，占位不是可用 credential。 */
    CREDENTIAL_UNCONFIGURED,

    /** real provider / real permission probe 尚未实现，须另起 Gate。 */
    REAL_PROVIDER_NOT_IMPLEMENTED,

    /** future-real 入口仍处于 disabled 状态；不能作为真实 provider 使用。 */
    FUTURE_REAL_DISABLED,

    /** 当前没有真实 provider，可执行路径必须 fail-closed。 */
    NO_REAL_PROVIDER,

    /** LIVE 处于 DISABLED；真实交易能力 fail-closed（对应 GateL baseline LIVE DISABLED）。 */
    LIVE_DISABLED,

    /** LIVE 未获得明确授权；与 LIVE_DISABLED 一起防止 mutating 能力被误放行。 */
    LIVE_NOT_AUTHORIZED,

    /** permission probe 被禁用 / skipped，不能被解释为真实权限已验证。 */
    PERMISSION_PROBE_DISABLED,

    /** 该能力在 GateL / GateM-0 内被明确禁止（对应 GateL-1C FORBIDDEN_IN_GATEL）。 */
    CAPABILITY_FORBIDDEN_IN_GATEL,

    /**
     * provider raw payload 已被 producer suppression 抑制，是安全边界而非错误恢复入口
     * （对应 GateL-1D RAW_PAYLOAD_SUPPRESSED）。当前 readiness 评估不会主动产出该原因，
     * 保留为词汇完整性，供后续 future-real 实现 Gate 引用。
     */
    RAW_PAYLOAD_SUPPRESSED,

    /** 证据不足 / 未知 venue 或 capability，默认 fail-closed。 */
    UNKNOWN_REQUIRES_REVIEW
}
