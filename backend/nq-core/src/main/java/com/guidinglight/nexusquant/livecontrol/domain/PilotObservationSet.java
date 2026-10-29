package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 同一事务提交的 exact five-observation set。 */
public record PilotObservationSet(
        UUID id,
        UUID pilotScopeId,
        PilotPrerequisiteObservation.InstrumentMetadata instrumentMetadata,
        PilotPrerequisiteObservation.FeeSchedule feeSchedule,
        PilotPrerequisiteObservation.BalanceSnapshot balanceSnapshot,
        PilotPrerequisiteObservation.ClockSync clockSync,
        PilotPrerequisiteObservation.MarketSnapshot marketSnapshot
) {
    public PilotObservationSet {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
        for (PilotPrerequisiteObservation observation : List.of(
                instrumentMetadata, feeSchedule, balanceSnapshot, clockSync, marketSnapshot)) {
            Objects.requireNonNull(observation, "observation must not be null");
            PilotScopeBinding.require(id.equals(observation.observationSetId()), "observation set identity mismatch");
            PilotScopeBinding.require(pilotScopeId.equals(observation.pilotScopeId()), "pilot scope identity mismatch");
            PilotScopeBinding.require(
                    PilotObservationCanonicalEncoder.digest(observation).equals(observation.observationPayloadHash()),
                    "observation payload hash is not canonical"
            );
        }
        PilotScopeBinding.require(
                PilotObservationCanonicalEncoder.instrumentMetadataDigest(
                        instrumentMetadata.envelope().observationSchemaVersion(), instrumentMetadata.items())
                        .equals(instrumentMetadata.instrumentMetadataDigest()),
                "instrument metadata digest is not canonical"
        );
        PilotScopeBinding.require(
                PilotObservationCanonicalEncoder.marketSnapshotDigest(
                        marketSnapshot.instrument(), marketSnapshot.bestAsk(),
                        marketSnapshot.envelope().observedAt(),
                        marketSnapshot.envelope().sourceIdentity(),
                        marketSnapshot.envelope().sourceSchemaVersion())
                        .equals(marketSnapshot.marketSnapshotDigest()),
                "market snapshot digest is not canonical"
        );
        PilotScopeBinding.require(
                instrumentMetadata.items().stream()
                        .anyMatch(item -> item.symbol().equals(marketSnapshot.instrument())),
                "market snapshot instrument is outside the observation set"
        );
    }

    public List<PilotPrerequisiteObservation> observations() {
        return List.of(instrumentMetadata, feeSchedule, balanceSnapshot, clockSync, marketSnapshot);
    }
}
