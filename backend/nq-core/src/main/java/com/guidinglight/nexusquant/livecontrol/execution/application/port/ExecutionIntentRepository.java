package com.guidinglight.nexusquant.livecontrol.execution.application.port;

import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptDraft;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Execution persistence/CAS port。所有方法均为单个短事务；实现不得调用 exchange。
 */
public interface ExecutionIntentRepository {

    ExecutionIntent createOrGet(ExecutionIntentDraft draft);

    Optional<ExecutionIntent> find(UUID intentId);

    Optional<ExecutionIntent> claim(UUID intentId, String workerId, UUID claimToken, Duration lease);

    Optional<ExecutionIntent> markSendStarted(UUID intentId, long expectedVersion, UUID claimToken);

    Optional<ExecutionIntent> markAmbiguousForRecovery(UUID intentId, long expectedVersion, UUID claimToken);

    ExecutionIntent appendReceiptAndTransition(
            UUID intentId,
            long expectedVersion,
            UUID claimToken,
            ExecutionReceiptDraft receipt,
            ExecutionIntentState target
    );
}
