package com.guidinglight.nexusquant.strategy.application.pyartifactpreview;

/**
 * PythonEvaluationArtifactChecksumStatus 描述 artifact checksum 的诊断状态。
 *
 * <p>VALID 只表示 payload 与 checksum 自洽，不表示策略有效、真实收益、ML ready、live execution ready
 * 或交易授权。GateT-4 No-file baseline 默认使用 NOT_CHECKED。
 */
public enum PythonEvaluationArtifactChecksumStatus {
    VALID,
    INVALID,
    MISSING,
    NOT_CHECKED,
    UNKNOWN
}
