package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunRequestCommand 是 NQ 侧调用 limited dry-run client 的最小输入。
 *
 * @param requestId       调用幂等与审计 ID；必须由上游显式传入，不允许匿名 fallback
 * @param traceId         跨仓 trace ID；必须参与 HMAC material
 * @param tenantId        租户 ID；必须参与 HMAC material
 * @param decisionContext 只读、脱敏、不可执行上下文
 */
public record DhDryRunRequestCommand(
        String requestId,
        String traceId,
        String tenantId,
        DhDryRunDecisionContext decisionContext) {
}
