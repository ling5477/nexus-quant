package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

/**
 * PythonEvaluationArtifactBindingStatus 是 GateQ-4 绑定预览的 fail-closed 状态枚举。
 *
 * <p>Why: 这些状态只表达 artifact 是否能进入只读 binding preview，不表达入库成功、
 * 策略发布、Paper/Shadow run 启动、LIVE 放行、AI/ML 就绪或交易授权。
 */
public enum PythonEvaluationArtifactBindingStatus {
    VALID_FOR_BINDING_PREVIEW,
    BLOCKED_SCHEMA_INVALID,
    BLOCKED_UNSUPPORTED_SCHEMA_VERSION,
    BLOCKED_RUN_MODE_NOT_OFFLINE,
    BLOCKED_DATASET_MISMATCH,
    BLOCKED_STRATEGY_VERSION_MISMATCH,
    BLOCKED_CHECKSUM_MISMATCH,
    BLOCKED_PARAMETERS_HASH_MISMATCH,
    BLOCKED_METRICS_INCOMPLETE,
    BLOCKED_TRACEABILITY_INCOMPLETE,
    BLOCKED_BOUNDARY_VIOLATION,
    UNKNOWN,
    NOT_AVAILABLE
}
