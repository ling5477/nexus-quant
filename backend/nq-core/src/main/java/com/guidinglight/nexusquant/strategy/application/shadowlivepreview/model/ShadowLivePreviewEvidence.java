package com.guidinglight.nexusquant.strategy.application.shadowlivepreview.model;

/**
 * ShadowLivePreviewEvidence 描述生成 no-side-effect preview 所需的只读证据项。
 *
 * <p>Why: 策略验证必须 fail-closed；每个证据项都要能表达 SATISFIED、MISSING、FAILED、
 * NOT_AVAILABLE 或 NOT_IMPLEMENTED，避免把缺失事实伪造成可预览。
 */
public record ShadowLivePreviewEvidence(String code, String status, String message) {
}
