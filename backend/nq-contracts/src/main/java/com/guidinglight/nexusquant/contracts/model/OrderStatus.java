package com.guidinglight.nexusquant.contracts.model;

/**
 * OrderStatus 冻结 Gate A 状态机可用状态集合。
 */
public enum OrderStatus {
    NEW,
    VALIDATED,
    SUBMITTING,
    ACKED,
    PARTIALLY_FILLED,
    FILLED,
    CANCEL_REQUESTED,
    CANCELLED,
    REJECTED,
    FAILED
}
