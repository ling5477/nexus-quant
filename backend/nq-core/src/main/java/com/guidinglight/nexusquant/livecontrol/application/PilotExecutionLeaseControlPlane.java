package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotPrePlaceRecoveryRepository.Authorization;

/** Minimal live pilot lease lifecycle；不直接调用provider。 */
public interface PilotExecutionLeaseControlPlane {

    PilotExecutionLease createAndActivate(
            AuthenticatedLiveControlActor actor,
            ExactPilotBinding binding,
            BigDecimal maxNotional,
            Instant expiresAt,
            ExactPilotBinding.Correlation correlation
    );

    PilotExecutionLease createReplacementAndActivate(
            AuthenticatedLiveControlActor actor,
            ExactPilotBinding binding,
            BigDecimal maxNotional,
            Instant expiresAt,
            ExactPilotBinding.Correlation correlation,
            Authorization authorization
    );

    Optional<Authorization> prepareZeroIntentReplacement(
            AuthenticatedLiveControlActor actor,
            long exchangeAccountId,
            long credentialReferenceId,
            String instrument,
            BigDecimal maxNotional,
            ExactPilotBinding.Correlation correlation
    );

    PilotExecutionLease bindPlace(
            AuthenticatedLiveControlActor actor,
            UUID leaseId,
            UUID intentId,
            ExactPilotBinding binding,
            ExactPilotBinding.Correlation correlation
    );

    void bindCancel(UUID leaseId, UUID intentId);

    PilotExecutionLease close(
            AuthenticatedLiveControlActor actor,
            UUID leaseId,
            PilotExecutionLease.Status terminal,
            String reasonCode,
            ExactPilotBinding.Correlation correlation
    );

    void recoverAtStartup();

    Optional<PilotExecutionLease> findConsumedForRecovery();

    PilotExecutionLease resumeConsumed(
            AuthenticatedLiveControlActor actor,
            PilotExecutionLease lease,
            ExactPilotBinding.Correlation correlation
    );

    void suspendConsumedForRecovery(
            AuthenticatedLiveControlActor actor,
            PilotExecutionLease lease,
            String reasonCode,
            ExactPilotBinding.Correlation correlation
    );
}
