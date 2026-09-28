package com.guidinglight.nexusquant.scheduler.control;

import java.time.Instant;
import java.util.UUID;

/** 固定任务的一份持久控制及最近执行状态。 */
public record ScheduledJobControl(
        String jobKey, boolean enabled, long fixedDelayMs, Instant nextRunAt,
        Instant lastStartedAt, Instant lastFinishedAt, String lastStatus,
        String lastErrorCode, int consecutiveFailures, long version,
        UUID activeRunId, String updatedBy, Instant updatedAt
) {
}
