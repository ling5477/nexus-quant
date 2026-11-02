package com.guidinglight.nexusquant.scheduler.control;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.UUID;

/** 同一操作编号冻结完整输入；继续原请求时不得换任务、动作、版本或控制值。 */
public record ScheduledJobOperationRequest(
        UUID operationId,
        ScheduledJobOperation.Type type,
        String jobKey,
        Boolean enabled,
        @JsonDeserialize(using = ScheduledOperationWholeNumberDeserializer.class) Long fixedDelayMs,
        @JsonDeserialize(using = ScheduledOperationWholeNumberDeserializer.class)
                Long expectedVersion) {
    public void validate() {
        if (operationId == null || type == null)
            throw new IllegalArgumentException("INVALID_SCHEDULER_OPERATION");
        if (jobKey == null || !jobKey.matches("[A-Z][A-Z0-9_]{1,47}")) {
            throw new IllegalArgumentException("INVALID_SCHEDULER_JOB_KEY");
        }
        if (type == ScheduledJobOperation.Type.UPDATE_CONTROL) {
            if (expectedVersion == null
                    || expectedVersion < 0
                    || (enabled == null && fixedDelayMs == null)) {
                throw new IllegalArgumentException("INVALID_SCHEDULER_CONTROL_OPERATION");
            }
        } else if (enabled != null || fixedDelayMs != null || expectedVersion != null) {
            throw new IllegalArgumentException("INVALID_SCHEDULER_RUN_OPERATION");
        }
    }
}
