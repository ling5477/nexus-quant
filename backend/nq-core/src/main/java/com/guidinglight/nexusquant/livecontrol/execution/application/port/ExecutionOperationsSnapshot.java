package com.guidinglight.nexusquant.livecontrol.execution.application.port;

import java.time.Instant;

/**
 * 只读执行运维投影；所有值都是脱敏状态或标识，不包含 credential、exchange payload 或交易授权。
 */
public record ExecutionOperationsSnapshot(
        Instant observedAt,
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
        String receiptState
) {
}
