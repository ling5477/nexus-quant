package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

/**
 * PythonEvaluationArtifactBindingScope 回显本次只读绑定预览范围。
 *
 * <p>Why: scope 只描述 Java 侧 expected anchors 与 artifact 实际解析出的 anchors，
 * 方便 review 判断 mismatch；它不是 import target，也不是数据库写入计划。
 */
public record PythonEvaluationArtifactBindingScope(
        String source,
        boolean dryRun,
        String expectedDatasetId,
        String expectedStrategyVersionId,
        String expectedStrategyVersion,
        String expectedEvaluationVersion,
        String expectedChecksum,
        String expectedParametersHash,
        String artifactDatasetId,
        String artifactStrategyVersion,
        String artifactEvaluationVersion,
        String artifactChecksum,
        String artifactParametersHash
) {
}
