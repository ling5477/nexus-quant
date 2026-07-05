package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

/**
 * PythonEvaluationArtifactBindingReason 描述 blocker / warning。
 *
 * <p>Why: reason 只输出稳定 code 和安全摘要，不回显敏感字段值、文件路径或 provider 私有信息。
 */
public record PythonEvaluationArtifactBindingReason(
        String code,
        String severity,
        String message
) {
}
