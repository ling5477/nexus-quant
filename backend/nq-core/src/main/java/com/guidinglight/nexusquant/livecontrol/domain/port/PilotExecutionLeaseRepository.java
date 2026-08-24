package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable pilot lease lifecycle 与数据库级 one-PLACE/one-CANCEL binding port。 */
public interface PilotExecutionLeaseRepository {

    PilotExecutionLease create(PilotExecutionLease lease, String requestId, String traceId);

    Optional<PilotExecutionLease> find(UUID leaseId);

    PilotExecutionLease activate(UUID leaseId, long expectedVersion, Instant occurredAt,
                                 String requestId, String traceId);

    PilotExecutionLease bindPlaceAndConsume(UUID leaseId, UUID intentId, ExactPilotBinding binding,
                                            Instant occurredAt, String requestId, String traceId);

    void bindCancel(UUID leaseId, UUID intentId, Instant occurredAt);

    PilotExecutionLease close(UUID leaseId, PilotExecutionLease.Status terminal, Instant occurredAt,
                              String reasonCode, String requestId, String traceId);

    List<PilotExecutionLease> findRecoverable(Instant decisionAt);
}
