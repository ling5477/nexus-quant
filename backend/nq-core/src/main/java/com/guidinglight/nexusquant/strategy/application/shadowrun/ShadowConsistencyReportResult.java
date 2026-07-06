package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Shadow consistency report 的只读生成结果。
 *
 * <p>职责：返回本地 report id、状态、JSON 摘要和 no-side-effect 边界。Why：调用方需要知道
 * report 已持久化以及比较结论，但 result 不能包含真实交易执行字段、账户余额、credential material
 * 或任何交易放行语义。
 */
public record ShadowConsistencyReportResult(
        UUID reportId,
        UUID shadowRunId,
        String paperRunId,
        ShadowConsistencyComparisonStatus comparisonStatus,
        JsonNode metricDelta,
        JsonNode divergenceReasons,
        JsonNode limitations,
        boolean persisted,
        boolean noOrderSubmission,
        boolean noCredentialAccess,
        boolean noPrivateEndpoint,
        boolean noLedgerMutation,
        boolean noAccountMutation,
        boolean noExternalPrivateIo,
        String requestId,
        String traceId,
        Instant generatedAt
) {
}
