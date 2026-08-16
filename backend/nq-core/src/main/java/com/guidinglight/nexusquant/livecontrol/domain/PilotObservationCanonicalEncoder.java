package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.List;
import java.util.stream.Collectors;

/** prerequisite observation 与 instrument item 的确定性 canonical encoder。 */
public final class PilotObservationCanonicalEncoder {

    private PilotObservationCanonicalEncoder() {
    }

    public static String instrumentMetadataDigest(
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        return CanonicalDigestSupport.sha256(instrumentMetadataPayload(items));
    }

    public static String digest(PilotPrerequisiteObservation observation) {
        return CanonicalDigestSupport.sha256(encode(observation));
    }

    public static String encode(PilotPrerequisiteObservation observation) {
        PilotPrerequisiteObservation.Envelope envelope = observation.envelope();
        return "{" +
                "\"schemaVersion\":" + CanonicalDigestSupport.quote(PilotPrerequisiteObservation.ENVELOPE_SCHEMA) +
                ",\"observationType\":" + CanonicalDigestSupport.quote(observation.type().name()) +
                ",\"observationSchemaVersion\":" + CanonicalDigestSupport.quote(envelope.observationSchemaVersion()) +
                ",\"observationIdentity\":" + CanonicalDigestSupport.quote(envelope.observationIdentity()) +
                ",\"sourceIdentity\":" + CanonicalDigestSupport.quote(envelope.sourceIdentity()) +
                ",\"sourceSchemaVersion\":" + CanonicalDigestSupport.quote(envelope.sourceSchemaVersion()) +
                ",\"observedAt\":" + CanonicalDigestSupport.instant(envelope.observedAt()) +
                ",\"payload\":" + typedPayload(observation) + "}";
    }

    private static String typedPayload(PilotPrerequisiteObservation observation) {
        return switch (observation) {
            case PilotPrerequisiteObservation.InstrumentMetadata value -> "{" +
                    "\"instrumentMetadataDigest\":" + CanonicalDigestSupport.quote(value.instrumentMetadataDigest()) +
                    ",\"items\":" + instrumentItems(value.items()) + "}";
            case PilotPrerequisiteObservation.FeeSchedule value -> "{" +
                    "\"feeScheduleDigest\":" + CanonicalDigestSupport.quote(value.feeScheduleDigest()) +
                    ",\"feeTier\":" + CanonicalDigestSupport.quote(value.feeTier()) +
                    ",\"feeEvidenceClass\":" + CanonicalDigestSupport.quote(value.feeEvidenceClass().name()) +
                    ",\"makerFeeRate\":" + CanonicalDigestSupport.quote(
                            CanonicalDigestSupport.plainDecimal(value.makerFeeRate(), "makerFeeRate")) +
                    ",\"takerFeeRate\":" + CanonicalDigestSupport.quote(
                            CanonicalDigestSupport.plainDecimal(value.takerFeeRate(), "takerFeeRate")) +
                    ",\"feeLossTreatment\":" + CanonicalDigestSupport.quote(value.feeLossTreatment()) + "}";
            case PilotPrerequisiteObservation.BalanceSnapshot value -> "{" +
                    "\"balanceSnapshotDigest\":" + CanonicalDigestSupport.quote(value.balanceSnapshotDigest()) +
                    ",\"balanceCurrency\":" + CanonicalDigestSupport.quote(value.balanceCurrency()) +
                    ",\"availableBalance\":" + CanonicalDigestSupport.decimal(value.availableBalance()) + "}";
            case PilotPrerequisiteObservation.ClockSync value -> "{" +
                    "\"clockSyncObservationDigest\":" + CanonicalDigestSupport.quote(value.clockSyncObservationDigest()) +
                    ",\"signedTimestampSource\":" + CanonicalDigestSupport.quote(value.signedTimestampSource()) +
                    ",\"observedSkewMs\":" + value.observedSkewMs() + "}";
        };
    }

    private static String instrumentMetadataPayload(
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        return "{\"schemaVersion\":\"instrument-metadata-observation.v1\",\"items\":" +
                instrumentItems(items) + "}";
    }

    private static String instrumentItems(List<PilotPrerequisiteObservation.InstrumentItem> items) {
        return items.stream().map(item -> "{" +
                        "\"symbol\":" + CanonicalDigestSupport.quote(item.symbol()) +
                        ",\"tradingStatus\":" + CanonicalDigestSupport.quote(item.tradingStatus().name()) +
                        ",\"tickSize\":" + CanonicalDigestSupport.quote(
                                CanonicalDigestSupport.plainDecimal(item.tickSize(), "tickSize")) +
                        ",\"lotSize\":" + CanonicalDigestSupport.quote(
                                CanonicalDigestSupport.plainDecimal(item.lotSize(), "lotSize")) +
                        ",\"minimumOrderSize\":" + CanonicalDigestSupport.quote(
                                CanonicalDigestSupport.plainDecimal(item.minimumOrderSize(), "minimumOrderSize")) +
                        ",\"minimumOrderValue\":" + CanonicalDigestSupport.quote(
                                CanonicalDigestSupport.plainDecimal(item.minimumOrderValue(), "minimumOrderValue")) +
                        ",\"minimumOrderValueCurrency\":" +
                        CanonicalDigestSupport.quote(item.minimumOrderValueCurrency()) + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
