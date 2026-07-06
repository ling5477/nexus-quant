package com.guidinglight.nexusquant.strategy.application.shadowrun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shadow Run runner skeleton 的本地输入命令。
 *
 * <p>职责：承载调用方传入的只读输入和结构化 preview artifact。命令不包含任何 adapter、
 * credential、private endpoint 或真实交易执行参数；结构化模型会在 service 层序列化为脱敏
 * snapshot payload，避免把 preview 误用成交易授权。
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
        com.fasterxml.jackson.databind.JsonNode inputMarketdataPayload,
        StrategyDecisionTrace strategyDecisionTrace,
        RiskPreflightSnapshot riskPreflightSnapshot,
        OrderIntentPreview orderIntentPreview,
        List<ShadowRunRunnerIssue> blockers
) {

    public ShadowRunRunnerCommand {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
