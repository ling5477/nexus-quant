package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * AuditRecorded 表示审计日志事实。
 *
 * @param domain 领域，例如 ORDER/LEDGER
 * @param action 动作名
 * @param subjectId 业务对象 ID
 * @param outcome 执行结果，例如 SUCCESS/FAIL
 * @param reason 补充原因
 * @param ts 记录时间
 */
public record AuditRecorded(
        @JsonProperty("domain") String domain,
        @JsonProperty("action") String action,
        @JsonProperty("subject_id") String subjectId,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}
