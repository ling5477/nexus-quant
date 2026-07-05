package com.guidinglight.nexusquant.research.api.web;

import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingEvidence;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingPreview;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingReason;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * PythonEvaluationArtifactBindingPreviewResponse 是 GateQ-4 只读 binding preview HTTP DTO。
 *
 * <p>Why: response 只暴露 validation / binding preview 证据、阻断和下一步。它不包含
 * tradingReady、liveReady、authorizedForTrading 字段，不返回敏感材料，也不表示 artifact 已入库。
 */
@Schema(name = "PythonEvaluationArtifactBindingPreviewResponse", description = "Read-only Python evaluation artifact binding preview")
public record PythonEvaluationArtifactBindingPreviewResponse(
        Scope scope,
        String bindingStatus,
        String validationStatus,
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
        List<Evidence> requiredEvidence,
        List<Evidence> missingEvidence,
        List<Reason> blockers,
        List<Reason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
    public static PythonEvaluationArtifactBindingPreviewResponse from(PythonEvaluationArtifactBindingPreview preview) {
        return new PythonEvaluationArtifactBindingPreviewResponse(
                Scope.from(preview.scope()),
                preview.bindingStatus().name(),
                preview.validationStatus().name(),
                preview.artifactType(),
                preview.runMode(),
                preview.datasetId(),
                preview.strategyVersion(),
                preview.evaluationVersion(),
                preview.parametersHash(),
                preview.checksumStatus(),
                preview.schemaStatus(),
                preview.metricsStatus(),
                preview.offlineBoundaryStatus(),
                preview.traceabilityStatus(),
                preview.requiredEvidence().stream().map(Evidence::from).toList(),
                preview.missingEvidence().stream().map(Evidence::from).toList(),
                preview.blockers().stream().map(Reason::from).toList(),
                preview.warnings().stream().map(Reason::from).toList(),
                preview.nextSteps(),
                preview.generatedAt()
        );
    }

    /** Scope 回显 expected anchors 与 artifact anchors；不表示写库或发布动作。 */
    public record Scope(
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
        private static Scope from(PythonEvaluationArtifactBindingScope scope) {
            return new Scope(
                    scope.source(),
                    scope.dryRun(),
                    scope.expectedDatasetId(),
                    scope.expectedStrategyVersionId(),
                    scope.expectedStrategyVersion(),
                    scope.expectedEvaluationVersion(),
                    scope.expectedChecksum(),
                    scope.expectedParametersHash(),
                    scope.artifactDatasetId(),
                    scope.artifactStrategyVersion(),
                    scope.artifactEvaluationVersion(),
                    scope.artifactChecksum(),
                    scope.artifactParametersHash()
            );
        }
    }

    /** Evidence 描述 schema/checksum/hash/boundary 等只读校验项。 */
    public record Evidence(String code, String status, String message) {
        private static Evidence from(PythonEvaluationArtifactBindingEvidence evidence) {
            return new Evidence(evidence.code(), evidence.status(), evidence.message());
        }
    }

    /** Reason 描述 blocker/warning；message 不回显敏感值或本地路径。 */
    public record Reason(String code, String severity, String message) {
        private static Reason from(PythonEvaluationArtifactBindingReason reason) {
            return new Reason(reason.code(), reason.severity(), reason.message());
        }
    }
}
