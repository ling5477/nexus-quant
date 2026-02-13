package com.guidinglight.nexusquant.common.error;

/**
 * ErrorCode 定义系统级错误码占位集合。
 *
 * Why:
 * Gate A 需要先冻结跨模块可传递的错误语义，避免后续模块各自定义错误字符串导致审计、监控和契约漂移。
 *
 * What:
 * 当前仅提供最小错误码集合，后续按模块增量扩展。
 */
public enum ErrorCode {
    GENERIC_INTERNAL_ERROR,
    AUTH_INVALID_CREDENTIALS,
    ORDER_IDEMPOTENCY_CONFLICT,
    INVALID_STATE_TRANSITION,
    RISK_REJECTED
}
