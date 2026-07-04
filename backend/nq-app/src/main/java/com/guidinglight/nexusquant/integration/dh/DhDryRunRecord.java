package com.guidinglight.nexusquant.integration.dh;

import java.time.Instant;

/**
 * DhDryRunRecord 是 NQ 侧唯一允许保存的 dry-run 摘要记录。
 *
 * <p>Why: record-only 边界要求记录 request/trace/tenant、decision/audit、结果和 fail-closed 原因，但禁止保存
 * HMAC secret、signature material、credential、raw payload 或可执行 order instruction。</p>
 *
 * @param requestId        request id
 * @param traceId          trace id
 * @param tenantId         tenant id
 * @param decisionId       DH decision id；失败或 parse error 时可为空
 * @param auditRef         DH audit reference；失败或 parse error 时可为空
 * @param action           accepted dry-run action；失败时可为空
 * @param biasOnly         LONG_BIAS / SHORT_BIAS 是否仅作为 bias 记录
 * @param accepted         response 是否通过 policy validation
 * @param failClosed       是否 fail-closed
 * @param errorCode        fail-closed error code；成功时可为空
 * @param failClosedReason 安全失败摘要；不得包含敏感值或 raw payload
 * @param recordedAt       record 写入时间
 */
public record DhDryRunRecord(
        String requestId,
        String traceId,
        String tenantId,
        String decisionId,
        String auditRef,
        DhDryRunAction action,
        boolean biasOnly,
        boolean accepted,
        boolean failClosed,
        DhDryRunErrorCode errorCode,
        String failClosedReason,
        Instant recordedAt) {
}
