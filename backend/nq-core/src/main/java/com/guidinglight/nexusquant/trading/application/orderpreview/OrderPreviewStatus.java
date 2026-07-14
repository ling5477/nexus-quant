package com.guidinglight.nexusquant.trading.application.orderpreview;

/**
 * OrderPreviewStatus 表示 preview 各独立维度的评估状态。
 *
 * <p>`UNKNOWN` 与 `NOT_EVALUATED` 永远不得被解释为交易许可。</p>
 */
public enum OrderPreviewStatus {
    PASS,
    BLOCKED,
    UNKNOWN,
    NOT_EVALUATED
}
