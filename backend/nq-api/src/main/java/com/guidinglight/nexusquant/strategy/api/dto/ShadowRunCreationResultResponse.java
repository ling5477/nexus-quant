package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunCreationResult;

import java.time.Instant;
import java.util.UUID;

/** 原创建结果的只读响应；不返回操作人、原编号、审计载荷，也不把查询称为重放或启动。 */
public record ShadowRunCreationResultResponse(
        UUID shadowRunId,
        String publishRecordId,
        String artifactDigest,
        ShadowRunReleaseBindingMode bindingMode,
        ShadowRunStatus creationStatus,
        ShadowRunStatus runStatus,
        Instant createdAt,
        Instant updatedAt) {
    public static ShadowRunCreationResultResponse from(ShadowRunCreationResult result) {
        return new ShadowRunCreationResultResponse(
                result.shadowRunId(),
                result.publishRecordId(),
                result.artifactDigest(),
                result.bindingMode(),
                result.creationStatus(),
                result.runStatus(),
                result.createdAt(),
                result.updatedAt());
    }
}
