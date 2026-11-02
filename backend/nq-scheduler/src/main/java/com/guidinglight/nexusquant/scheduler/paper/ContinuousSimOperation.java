package com.guidinglight.nexusquant.scheduler.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 只保存一次命令的原完成结果；当前游标与父运行状态始终通过正式查询读取。 */
public record ContinuousSimOperation(
        UUID operationId,
        long ownerUserId,
        String actor,
        ContinuousSimOperationRequest.Type type,
        String publishId,
        BigDecimal budget,
        String paperRunId,
        String inputSha256,
        String traceId,
        ContinuousSimRunService.Status result,
        Instant completedAt) {}
