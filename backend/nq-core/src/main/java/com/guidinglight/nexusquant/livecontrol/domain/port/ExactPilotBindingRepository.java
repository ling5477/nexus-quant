package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingConsumption;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Exact binding 的 durable event port；实现必须以 session 行锁保护 create/consume。 */
public interface ExactPilotBindingRepository {

    LiveSession lockSession(UUID sessionId);

    Instant currentTransactionTime();

    ExactPilotBinding createOrGet(ExactPilotBinding binding, LiveSession lockedSession);

    Optional<ExactPilotBinding> find(UUID sessionId, UUID bindingId);

    boolean isConsumed(UUID sessionId, UUID bindingId);

    ExactPilotBindingConsumption consume(
            ExactPilotBinding binding,
            LiveSession lockedSession,
            ExactPilotBinding.Correlation correlation,
            Instant consumedAt
    );
}
