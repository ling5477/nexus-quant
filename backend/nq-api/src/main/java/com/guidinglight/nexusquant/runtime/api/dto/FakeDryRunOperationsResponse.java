package com.guidinglight.nexusquant.runtime.api.dto;

import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionOperationsSnapshot;

import java.time.Instant;

/** 只读、脱敏的 fake dry-run 运维状态；永远不表达 LIVE 或生产 worker 授权。 */
public record FakeDryRunOperationsResponse(
        Instant observedAt,
        String mode,
        String liveState,
        String killState,
        String sessionId,
        String sessionState,
        String approvalState,
        String riskDigest,
        String workerHealth,
        String workerIdentity,
        String releaseIdentity,
        String releaseDigest,
        String intentId,
        String intentState,
        String receiptState,
        boolean tradingAuthorization,
        boolean productionStartAuthorization
) {
    public static FakeDryRunOperationsResponse from(ExecutionOperationsSnapshot value) {
        return new FakeDryRunOperationsResponse(value.observedAt(), "FAKE_ONLY_DRY_RUN", "DISABLED",
                value.killState(), value.sessionId(), value.sessionState(), value.approvalState(), value.riskDigest(),
                value.workerHealth(), value.workerIdentity(), value.releaseIdentity(), value.releaseDigest(),
                value.intentId(), value.intentState(), value.receiptState(), false, false);
    }

    public static FakeDryRunOperationsResponse unavailable(Instant now) {
        return new FakeDryRunOperationsResponse(now, "FAKE_ONLY_DRY_RUN", "DISABLED", "UNKNOWN", "-",
                "NOT_OBSERVED", "NOT_OBSERVED", "-", "NOT_OBSERVED", "-", "NOT_RECORDED",
                "NOT_RECORDED", "-", "NOT_OBSERVED", "NOT_OBSERVED", false, false);
    }
}
