package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Materialization command 的最小安全结果；不包含 path、storage key、manifest 或 creation plan。 */
public record ShadowRunMaterializationResult(
        UUID shadowRunId,
        String publishRecordId,
        String artifactDigest,
        ShadowRunReleaseBindingMode bindingMode,
        ShadowRunStatus status,
        Instant createdAt,
        boolean idempotentReplay
) {
    public ShadowRunMaterializationResult {
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        Objects.requireNonNull(bindingMode, "bindingMode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (bindingMode != ShadowRunReleaseBindingMode.RELEASE_BOUND) {
            throw new IllegalArgumentException("materialized Shadow Run must be RELEASE_BOUND");
        }
        if (status != ShadowRunStatus.CREATED) {
            throw new IllegalArgumentException("materialized Shadow Run must remain CREATED");
        }
    }
}
