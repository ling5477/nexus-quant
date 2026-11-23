package com.guidinglight.nexusquant.scheduler.control;

import java.time.Instant;
import java.util.UUID;

/** 原请求的持久回执；任务失败与结果未知分别保存，不能用当前任务行冒充历史操作结果。 */
public record ScheduledJobOperation(
        UUID operationId,
        long ownerUserId,
        String actor,
        Type type,
        String jobKey,
        String inputSha256,
        String traceId,
        Status status,
        ScheduledJobControl control,
        SchedulerDispatcher.ExecutionOutcome result,
        String reasonCode,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
    public enum Type {
        UPDATE_CONTROL,
        RUN_ONCE
    }

    public enum Status {
        STARTING,
        RUNNING,
        COMPLETED,
        REJECTED,
        OUTCOME_UNKNOWN
    }
}
