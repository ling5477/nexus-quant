package com.guidinglight.nexusquant.validationreview.domain;

/**
 * 本地人工复核 lifecycle 状态。
 *
 * <p>这些状态只表达操作员复核进度，不表示策略批准、交易授权或 LIVE readiness。
 */
public enum ValidationReviewState {
    OPEN,
    ACKNOWLEDGED,
    ESCALATED,
    RESOLVED,
    CLOSED;

    /**
     * 判断状态是否为不可继续流转的终态。
     *
     * @return 仅 {@link #CLOSED} 返回 true
     */
    public boolean terminal() {
        return this == CLOSED;
    }
}
