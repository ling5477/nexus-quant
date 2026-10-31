package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;
import java.util.UUID;

/** 与计划和完成审计同事务提交的原回执；后续计划启停不能改写创建当时的状态。 */
public record StrategyScheduleCreation(
        UUID creationId,
        long ownerUserId,
        String actor,
        int requestVersion,
        String inputSha256,
        StrategyScheduleCreationSpec request,
        StrategySchedule result,
        String traceId,
        Instant completedAt) {}
