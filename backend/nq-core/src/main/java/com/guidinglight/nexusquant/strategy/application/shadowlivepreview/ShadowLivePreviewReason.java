package com.guidinglight.nexusquant.strategy.application.shadowlivepreview;

/**
 * ShadowLivePreviewReason 描述 preview blocker 或 warning。
 *
 * <p>Why: 调用方需要看到明确阻断原因和边界提示，而不是把 UNKNOWN / NOT_AVAILABLE
 * 解释为通过或实盘准备完成。
 */
public record ShadowLivePreviewReason(String code, String severity, String message) {
}
