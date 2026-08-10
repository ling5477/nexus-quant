package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;

import java.time.Instant;
import java.util.UUID;

/**
 * Release-to-Shadow admission 的 immutable 输入快照。
 *
 * <p>所有字段都由调用方提供为已读取的 production facts；record 故意允许空值进入纯决策服务，
 * 由服务统一 fail-closed 为稳定 reason code。该输入不携带 credential、账户、订单或 private payload。
 */
public record ReleaseToShadowAdmissionRequest(
        StrategyRelease release,
        String releaseAnchorId,
        String publishRecordId,
        String artifactDigest,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        StrategyValidationDecision validationDecision,
        Instant windowStart,
        Instant windowEnd,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy,
        String traceId
) {
}
