package com.guidinglight.nexusquant.integration.dh;

import java.util.List;

/**
 * DhDryRunRequestEnvelope 是发送给 DH `POST /api/ai/decision-dry-runs` 的 wire payload。
 *
 * @param requestId             请求 ID；参与 header binding 和签名
 * @param traceId               trace ID；参与 header binding 和签名
 * @param tenantId              tenant ID；参与 header binding 和签名
 * @param source                source；当前只允许配置注入的 NQ_DRYRUN，禁止 fallback
 * @param timestamp             RFC3339 / ISO-8601 UTC Z timestamp，禁止 epoch seconds / milliseconds
 * @param nonce                 单次请求唯一 nonce
 * @param schemaVersion         dry-run schema version；必须与 header 一致
 * @param dryRun                必须恒为 true
 * @param decisionContext       只读上下文
 * @param forbiddenCapabilities 显式禁止能力清单；用于审计和 DH policy gate
 */
public record DhDryRunRequestEnvelope(
        String requestId,
        String traceId,
        String tenantId,
        String source,
        String timestamp,
        String nonce,
        String schemaVersion,
        boolean dryRun,
        DhDryRunDecisionContext decisionContext,
        List<String> forbiddenCapabilities) {
}
