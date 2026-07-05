package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

import java.time.Instant;
import java.util.List;

/**
 * PythonEvaluationArtifactBindingPreview 是 GateQ-4 API 的 core read model。
 *
 * <p>Why: read model 只描述 artifact schema / checksum / boundary validation 的预览结果。
 * 最高状态 VALID_FOR_BINDING_PREVIEW 仍然不代表 Java fact 已写入、策略已批准、交易已授权或
 * Python 产物达到 ML / live execution ready。
 */
public record PythonEvaluationArtifactBindingPreview(
        PythonEvaluationArtifactBindingScope scope,
        PythonEvaluationArtifactBindingStatus bindingStatus,
        PythonEvaluationArtifactBindingStatus validationStatus,
        String artifactType,
        String runMode,
        String datasetId,
        String strategyVersion,
        String evaluationVersion,
        String parametersHash,
        String checksumStatus,
        String schemaStatus,
        String metricsStatus,
        String offlineBoundaryStatus,
        String traceabilityStatus,
        List<PythonEvaluationArtifactBindingEvidence> requiredEvidence,
        List<PythonEvaluationArtifactBindingEvidence> missingEvidence,
        List<PythonEvaluationArtifactBindingReason> blockers,
        List<PythonEvaluationArtifactBindingReason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
}
