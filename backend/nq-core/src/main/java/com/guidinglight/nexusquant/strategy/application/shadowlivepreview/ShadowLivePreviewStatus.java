package com.guidinglight.nexusquant.strategy.application.shadowlivepreview;

/**
 * ShadowLivePreviewStatus 是 GateQ-3 只读影子运行预览诊断状态。
 *
 * <p>Why: 这些状态只表达 no-side-effect preview 能否生成，不表达交易授权、实盘可用、
 * 下单批准或 private trading 可用；不得新增 LIVE_READY、TRADE_APPROVED、AUTHORIZED 等放行语义。
 */
public enum ShadowLivePreviewStatus {
    READY_FOR_NO_SIDE_EFFECT_PREVIEW,
    PREVIEW_BLOCKED_EVALUATION_GATE,
    PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON,
    PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION,
    PREVIEW_BLOCKED_DATA_QUALITY,
    PREVIEW_BLOCKED_MISSING_PAPER_EVIDENCE,
    PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE,
    PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE,
    UNKNOWN,
    NOT_AVAILABLE
}
