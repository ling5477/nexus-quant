package com.guidinglight.nexusquant.strategy.strategyrelease.application.model;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 原创建命令的已确认结果；创建状态与当前诊断状态分开，查询不表示重新执行创建或启动运行。
 */
public record ShadowRunCreationResult(
        UUID shadowRunId,
        String publishRecordId,
        String artifactDigest,
        ShadowRunReleaseBindingMode bindingMode,
        ShadowRunStatus creationStatus,
        ShadowRunStatus runStatus,
        Instant createdAt,
        Instant updatedAt) {}
