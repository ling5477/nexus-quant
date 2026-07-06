package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shadow Run runner skeleton 的只读结果对象。
 *
 * <p>结果只描述本地 fact lifecycle、事件/快照计数、幂等复用、风险阻断/告警和
 * no-side-effect guard 状态；不包含真实订单、账户余额、credential material、
 * private endpoint response 或交易授权字段。
 */
public record ShadowRunRunnerResult(
        UUID shadowRunId,
        ShadowRunStatus status,
        boolean idempotentReplay,
        String idempotencyKey,
        String requestId,
        String traceId,
        boolean noOrderSubmission,
        boolean noCredentialAccess,
        boolean noPrivateEndpoint,
        boolean noLedgerMutation,
        boolean noAccountMutation,
        boolean noExternalPrivateIo,
        boolean orderIntentPreviewOnly,
        int eventCount,
        int snapshotCount,
        List<ShadowRunRunnerStep> completedSteps,
        List<ShadowRunRunnerIssue> blockers,
        List<ShadowRunRunnerIssue> warnings,
        List<String> nextSteps,
        String failureCode,
        String failureMessage,
        Instant generatedAt
) {

    public ShadowRunRunnerResult {
        completedSteps = completedSteps == null ? List.of() : List.copyOf(completedSteps);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
    }
}
