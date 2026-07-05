package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

/**
 * PaperShadowComparisonStatus 是 GateQ-2 Paper vs Shadow 只读对照的诊断状态。
 *
 * <p>Why: 本枚举只表达证据是否足以进入只读对照查看。`READY_FOR_COMPARISON` 不是交易授权，
 * 也不得扩展出 LIVE_READY、TRADE_APPROVED、AUTHORIZED 等放行语义。
 */
public enum PaperShadowComparisonStatus {
    READY_FOR_COMPARISON,
    BLOCKED_MISSING_STRATEGY_VERSION,
    BLOCKED_EVALUATION_GATE,
    BLOCKED_MISSING_PAPER_RUN,
    BLOCKED_SHADOW_NOT_IMPLEMENTED,
    BLOCKED_MISSING_SHADOW_RUN,
    BLOCKED_DATA_QUALITY,
    BLOCKED_TRACE_INCOMPLETE,
    UNKNOWN,
    NOT_AVAILABLE,
    NOT_IMPLEMENTED
}
