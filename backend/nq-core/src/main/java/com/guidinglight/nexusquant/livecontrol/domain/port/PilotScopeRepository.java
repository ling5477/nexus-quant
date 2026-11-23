package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Pilot scope 与 prerequisite stored facts 的 domain port；transaction/JDBC 由 infra 提供。 */
public interface PilotScopeRepository {

    PilotScopeBinding materialize(LiveSession session, PilotScopeBinding scope);

    Optional<PilotScopeBinding> findBySessionId(UUID sessionId);

    Optional<PilotScopeBinding> lockBySessionId(UUID sessionId);

    PilotObservationSet appendObservationSet(PilotScopeBinding scope, PilotObservationSet observations);

    Optional<PilotObservationSet> findObservationSet(UUID pilotScopeId, UUID observationSetId);

    Optional<PilotObservationSet> findLatestCompleteObservationSet(UUID pilotScopeId);

    Instant currentTransactionTime();

    Optional<OperatorApproval> findValidPilotApproval(PilotScopeBinding scope, Instant decisionAt);
}
