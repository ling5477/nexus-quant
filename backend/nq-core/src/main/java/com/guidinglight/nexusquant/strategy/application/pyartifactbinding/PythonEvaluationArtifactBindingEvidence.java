package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

/**
 * PythonEvaluationArtifactBindingEvidence 描述 GateQ-4 校验项证据状态。
 *
 * <p>Why: evidence 用于让 API caller 明确知道哪些 schema/checksum/hash/boundary 条件已满足，
 * 哪些条件缺失；它不承载敏感材料、文件路径或交易指令。
 */
public record PythonEvaluationArtifactBindingEvidence(
        String code,
        String status,
        String message
) {
}
