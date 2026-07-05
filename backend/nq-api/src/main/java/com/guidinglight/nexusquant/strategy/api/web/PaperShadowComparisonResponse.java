package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparison;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonEvidence;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonReason;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PaperShadowComparisonResponse 是 GateQ-2 Paper vs Shadow 只读 HTTP DTO。
 *
 * <p>Why: DTO 只暴露对照范围、证据状态、阻断原因和下一步提示。它不包含 tradingReady、
 * liveReady、authorizedForTrading 字段，不返回敏感材料、raw provider payload，也不表达 LIVE、
 * 真实交易或 Shadow runner 已启用。
 */
@Schema(name = "PaperShadowComparisonResponse", description = "Read-only Paper vs Shadow comparison baseline")
public record PaperShadowComparisonResponse(
        Scope scope,
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId,
        String paperRunStatus,
        String shadowRunStatus,
        String comparisonStatus,
        String evaluationGateStatus,
        String paperEvidenceStatus,
        String shadowEvidenceStatus,
        String dataQualityStatus,
        boolean comparable,
        List<Evidence> requiredEvidence,
        List<Evidence> missingEvidence,
        List<Reason> blockers,
        List<Reason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
    public static PaperShadowComparisonResponse from(PaperShadowComparison comparison) {
        return new PaperShadowComparisonResponse(
                Scope.from(comparison.scope()),
                comparison.strategyId(),
                comparison.strategyVersionId(),
                comparison.datasetId(),
                comparison.evaluationId(),
                comparison.publishId(),
                comparison.paperRunId(),
                comparison.shadowRunId(),
                comparison.paperRunStatus(),
                comparison.shadowRunStatus(),
                comparison.comparisonStatus().name(),
                comparison.evaluationGateStatus(),
                comparison.paperEvidenceStatus(),
                comparison.shadowEvidenceStatus(),
                comparison.dataQualityStatus(),
                comparison.comparable(),
                comparison.requiredEvidence().stream().map(Evidence::from).toList(),
                comparison.missingEvidence().stream().map(Evidence::from).toList(),
                comparison.blockers().stream().map(Reason::from).toList(),
                comparison.warnings().stream().map(Reason::from).toList(),
                comparison.nextSteps(),
                comparison.generatedAt()
        );
    }

    /** Scope 回显 Paper vs Shadow 对照范围；不代表交易授权或 LIVE 放行。 */
    public record Scope(
            String strategyId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            String publishId,
            String paperRunId,
            String shadowRunId
    ) {
        private static Scope from(PaperShadowComparisonScope scope) {
            return new Scope(
                    scope.strategyId(),
                    scope.strategyVersionId(),
                    scope.datasetId(),
                    scope.evaluationId(),
                    scope.publishId(),
                    scope.paperRunId(),
                    scope.shadowRunId()
            );
        }
    }

    /** Evidence 描述 required / missing evidence，不承载交易指令或敏感材料。 */
    public record Evidence(String code, String status, String message) {
        private static Evidence from(PaperShadowComparisonEvidence evidence) {
            return new Evidence(evidence.code(), evidence.status(), evidence.message());
        }
    }

    /** Reason 描述 blocker / warning，供前端和审计分类展示。 */
    public record Reason(String code, String severity, String message) {
        private static Reason from(PaperShadowComparisonReason reason) {
            return new Reason(reason.code(), reason.severity(), reason.message());
        }
    }
}
