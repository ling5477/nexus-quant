package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreview;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewEvidence;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewReason;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewScope;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewSideEffectPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ShadowLivePreviewResponse 是 GateQ-3 Shadow Live no-side-effect preview HTTP DTO。
 *
 * <p>Why: DTO 只暴露只读预览范围、证据状态、副作用禁止项、阻断原因和下一步提示。
 * 它不包含 tradingReady、liveReady、authorizedForTrading 字段，不返回敏感材料、raw provider
 * payload，也不表达 LIVE、真实交易或真实 Shadow runner 已启用。
 */
@Schema(name = "ShadowLivePreviewResponse", description = "Read-only Shadow Live no-side-effect preview")
public record ShadowLivePreviewResponse(
        Scope scope,
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId,
        String runnerStatus,
        String previewStatus,
        String evaluationGateStatus,
        String paperShadowComparisonStatus,
        List<SideEffectPolicy> sideEffectPolicy,
        String inputFactStatus,
        String traceStatus,
        String orderIntentPreviewStatus,
        String riskPreflightPreviewStatus,
        List<Evidence> requiredEvidence,
        List<Evidence> missingEvidence,
        List<Reason> blockers,
        List<Reason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
    public static ShadowLivePreviewResponse from(ShadowLivePreview preview) {
        return new ShadowLivePreviewResponse(
                Scope.from(preview.scope()),
                preview.strategyId(),
                preview.strategyVersionId(),
                preview.datasetId(),
                preview.evaluationId(),
                preview.publishId(),
                preview.paperRunId(),
                preview.shadowRunId(),
                preview.runnerStatus(),
                preview.previewStatus().name(),
                preview.evaluationGateStatus(),
                preview.paperShadowComparisonStatus(),
                preview.sideEffectPolicy().stream().map(SideEffectPolicy::from).toList(),
                preview.inputFactStatus(),
                preview.traceStatus(),
                preview.orderIntentPreviewStatus(),
                preview.riskPreflightPreviewStatus(),
                preview.requiredEvidence().stream().map(Evidence::from).toList(),
                preview.missingEvidence().stream().map(Evidence::from).toList(),
                preview.blockers().stream().map(Reason::from).toList(),
                preview.warnings().stream().map(Reason::from).toList(),
                preview.nextSteps(),
                preview.generatedAt()
        );
    }

    /** Scope 回显 Shadow Live preview 查询范围；不代表交易授权或运行放行。 */
    public record Scope(
            String strategyId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            String publishId,
            String paperRunId,
            String shadowRunId
    ) {
        private static Scope from(ShadowLivePreviewScope scope) {
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

    /** SideEffectPolicy 描述本轮 preview hard boundary，全部为 FORBIDDEN。 */
    public record SideEffectPolicy(String code, String status, String message) {
        private static SideEffectPolicy from(ShadowLivePreviewSideEffectPolicy policy) {
            return new SideEffectPolicy(policy.code(), policy.status(), policy.message());
        }
    }

    /** Evidence 描述 required / missing evidence，不承载交易指令或敏感材料。 */
    public record Evidence(String code, String status, String message) {
        private static Evidence from(ShadowLivePreviewEvidence evidence) {
            return new Evidence(evidence.code(), evidence.status(), evidence.message());
        }
    }

    /** Reason 描述 blocker / warning，供前端和审计分类展示。 */
    public record Reason(String code, String severity, String message) {
        private static Reason from(ShadowLivePreviewReason reason) {
            return new Reason(reason.code(), reason.severity(), reason.message());
        }
    }
}
