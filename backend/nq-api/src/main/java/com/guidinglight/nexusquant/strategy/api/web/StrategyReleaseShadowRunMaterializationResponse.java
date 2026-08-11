package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunMaterializationResult;

import java.time.Instant;
import java.util.UUID;

/** Release-to-Shadow materialization 的最小安全响应。 */
public record StrategyReleaseShadowRunMaterializationResponse(
        UUID shadowRunId,
        String publishRecordId,
        String artifactDigest,
        ShadowRunReleaseBindingMode bindingMode,
        ShadowRunStatus status,
        Instant createdAt,
        boolean idempotentReplay
) {
    public static StrategyReleaseShadowRunMaterializationResponse from(ShadowRunMaterializationResult result) {
        return new StrategyReleaseShadowRunMaterializationResponse(
                result.shadowRunId(),
                result.publishRecordId(),
                result.artifactDigest(),
                result.bindingMode(),
                result.status(),
                result.createdAt(),
                result.idempotentReplay()
        );
    }
}
