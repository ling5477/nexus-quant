package com.guidinglight.nexusquant.strategy.application.shadowlivepreview.model;

/**
 * ShadowLivePreviewSideEffectPolicy 固化只读预览的副作用禁止项。
 *
 * <p>Why: 策略验证只允许 read-only preview。每条 policy 都是硬边界，service 在返回 READY 状态时
 * 也只能说明这些副作用被禁止，不能把 preview 升级为 runner、交易或外联执行。
 */
public record ShadowLivePreviewSideEffectPolicy(String code, String status, String message) {
}
