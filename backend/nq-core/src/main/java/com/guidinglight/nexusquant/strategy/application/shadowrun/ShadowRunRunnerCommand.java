package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shadow Run runner skeleton 的本地输入命令。
 *
 * <p>职责：承载调用方传入的只读输入和 preview artifact。命令不包含任何 adapter、credential、
 * private endpoint 或真实交易执行参数；payload 字段会在 service 层再次通过
 * {@code ShadowRunSensitiveDataGuard} 校验，避免敏感字段进入本地 fact。
 */
public record ShadowRunRunnerCommand(
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        Instant windowStart,
        Instant windowEnd,
        String requestId,
        String idempotencyKey,
        String traceId,
        JsonNode inputMarketdataPayload,
        JsonNode strategyDecisionPayload,
        JsonNode riskPreflightPayload,
        JsonNode orderIntentPreviewPayload,
        List<ShadowRunRunnerIssue> blockers
) {

    public ShadowRunRunnerCommand {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
