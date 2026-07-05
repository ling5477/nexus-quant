package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGate;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateEvidence;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateReason;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * StrategyEvaluationGateResponse 是 GateQ-1 只读 Strategy Evaluation Gate HTTP DTO。
 *
 * <p>Why: DTO 只暴露研究/评估准备度、证据缺口和阻断原因。它不包含 tradingReady / liveReady /
 * authorizedForTrading 字段，不返回 credential、secret、token、passphrase、private key、raw provider
 * payload，也不表达 LIVE、真实交易或 Shadow runner 已启用。
 */
@Schema(name = "StrategyEvaluationGateResponse", description = "Read-only strategy evaluation gate baseline")
public record StrategyEvaluationGateResponse(
        Scope scope,
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String gateStatus,
        String gateDecision,
        String evaluationStatus,
        String datasetQualityStatus,
        String paperEvidenceStatus,
        String publishTraceStatus,
        List<Evidence> requiredEvidence,
        List<Evidence> missingEvidence,
        List<Reason> blockers,
        List<Reason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
    public static StrategyEvaluationGateResponse from(StrategyEvaluationGate gate) {
        return new StrategyEvaluationGateResponse(
                Scope.from(gate.scope()),
                gate.strategyId(),
                gate.strategyVersionId(),
                gate.datasetId(),
                gate.evaluationId(),
                gate.publishId(),
                gate.paperRunId(),
                gate.gateStatus().name(),
                gate.gateDecision().name(),
                gate.evaluationStatus(),
                gate.datasetQualityStatus(),
                gate.paperEvidenceStatus(),
                gate.publishTraceStatus(),
                gate.requiredEvidence().stream().map(Evidence::from).toList(),
                gate.missingEvidence().stream().map(Evidence::from).toList(),
                gate.blockers().stream().map(Reason::from).toList(),
                gate.warnings().stream().map(Reason::from).toList(),
                gate.nextSteps(),
                gate.generatedAt()
        );
    }

    /** Scope 回显 evaluation gate 诊断范围；不代表交易授权或 LIVE 放行。 */
    public record Scope(
            String strategyId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            String publishId,
            String paperRunId
    ) {
        private static Scope from(StrategyEvaluationGateScope scope) {
            return new Scope(
                    scope.strategyId(),
                    scope.strategyVersionId(),
                    scope.datasetId(),
                    scope.evaluationId(),
                    scope.publishId(),
                    scope.paperRunId()
            );
        }
    }

    /** Evidence 描述 required / missing evidence，不承载敏感材料或交易指令。 */
    public record Evidence(String code, String status, String message) {
        private static Evidence from(StrategyEvaluationGateEvidence evidence) {
            return new Evidence(evidence.code(), evidence.status(), evidence.message());
        }
    }

    /** Reason 描述 blocker / warning，供前端和审计分类展示。 */
    public record Reason(String code, String severity, String message) {
        private static Reason from(StrategyEvaluationGateReason reason) {
            return new Reason(reason.code(), reason.severity(), reason.message());
        }
    }
}
