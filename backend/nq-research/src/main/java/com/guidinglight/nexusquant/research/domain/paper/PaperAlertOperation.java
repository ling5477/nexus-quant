package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;
import java.util.UUID;

/** 命令完成时的原告警快照；后续确认/解决不改写这份结果，当前状态另行读取。 */
public record PaperAlertOperation(
        UUID operationId,
        long ownerUserId,
        String actor,
        String paperRunId,
        String type,
        String inputSha256,
        PaperRunAlert result,
        Instant completedAt) {}
