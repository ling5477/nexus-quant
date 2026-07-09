package com.guidinglight.nexusquant.strategy.application.pyartifactpreview;

/**
 * PythonEvaluationArtifactFreshness 描述 artifact 新鲜度的诊断状态。
 *
 * <p>No-file baseline 没有 artifact source，因此不能伪造 FRESH；MISSING / UNKNOWN 均保持 fail-closed。
 */
public enum PythonEvaluationArtifactFreshness {
    FRESH,
    STALE,
    MISSING,
    UNKNOWN
}
