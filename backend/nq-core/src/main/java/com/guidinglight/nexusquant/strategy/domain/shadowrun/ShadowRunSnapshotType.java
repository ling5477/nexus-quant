package com.guidinglight.nexusquant.strategy.domain.shadowrun;

/**
 * Shadow Run 本地快照类型。
 *
 * <p>这些类型只允许保存脱敏的输入和本地决策快照，不保存 private endpoint payload、
 * credential material、真实账户余额或真实订单状态。
 */
public enum ShadowRunSnapshotType {
    INPUT_MARKETDATA,
    STRATEGY_DECISION,
    RISK_PREFLIGHT,
    ORDER_INTENT_PREVIEW
}
