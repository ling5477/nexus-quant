package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * prerequisite observation 与 instrument item 的确定性 canonical encoder。
 */
public final class PilotObservationCanonicalEncoder {

    private PilotObservationCanonicalEncoder() {
    }

    public static String instrumentMetadataDigest(
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        return instrumentMetadataDigest(PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION, items);
    }

    public static String instrumentMetadataDigest(
            String schemaVersion,
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        return CanonicalDigestSupport.sha256(instrumentMetadataPayload(schemaVersion, items));
    }

    public static String digest(PilotPrerequisiteObservation observation) {
        return CanonicalDigestSupport.sha256(encode(observation));
    }

    public static String feeScheduleDigest(
            List<String> symbols,
            String feeTier,
            BigDecimal makerFeeRate,
            BigDecimal takerFeeRate
    ) {
        String symbolPayload = symbols.stream().map(CanonicalDigestSupport::quote)
                .collect(Collectors.joining(",", "[", "]"));
        return CanonicalDigestSupport.sha256("{" +
                "\"schemaVersion\":\"fee-schedule-observation.v1\"" +
                ",\"symbols\":" + symbolPayload +
                ",\"feeTier\":" + CanonicalDigestSupport.quote(feeTier) +
                ",\"feeEvidenceClass\":\"OBSERVED_PRIVATE\"" +
                ",\"makerFeeRate\":" + CanonicalDigestSupport.quote(
                CanonicalDigestSupport.plainDecimal(makerFeeRate, "makerFeeRate")) +
                ",\"takerFeeRate\":" + CanonicalDigestSupport.quote(
                CanonicalDigestSupport.plainDecimal(takerFeeRate, "takerFeeRate")) + "}");
    }

    public static String balanceSnapshotDigest(BigDecimal availableBalance) {
        return CanonicalDigestSupport.sha256("{" +
                "\"schemaVersion\":\"balance-snapshot-observation.v1\"" +
                ",\"balanceCurrency\":\"USDT\"" +
                ",\"availableBalance\":" + CanonicalDigestSupport.decimal(availableBalance) + "}");
    }

    public static String clockSyncDigest(String timestampSource, long observedSkewMs) {
        return CanonicalDigestSupport.sha256("{" +
                "\"schemaVersion\":\"clock-sync-observation.v1\"" +
                ",\"signedTimestampSource\":" + CanonicalDigestSupport.quote(timestampSource) +
                ",\"observedSkewMs\":" + observedSkewMs + "}");
    }

    public static String marketSnapshotDigest(
            String instrument,
            BigDecimal bestAsk,
            java.time.Instant observedAt,
            String sourceIdentity,
            String sourceSchemaVersion
    ) {
        return CanonicalDigestSupport.sha256("{" +
                "\"schemaVersion\":\"market-snapshot-observation.v1\"" +
                ",\"instrument\":" + CanonicalDigestSupport.quote(instrument) +
                ",\"bestAsk\":" + CanonicalDigestSupport.quote(
                CanonicalDigestSupport.plainDecimal(bestAsk, "bestAsk")) +
                ",\"observedAt\":" + CanonicalDigestSupport.instant(observedAt) +
                ",\"sourceIdentity\":" + CanonicalDigestSupport.quote(sourceIdentity) +
                ",\"sourceSchemaVersion\":" + CanonicalDigestSupport.quote(sourceSchemaVersion) + "}");
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
                    ",\"items\":" + instrumentItems(value.envelope().observationSchemaVersion(), value.items()) + "}";
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
            case PilotPrerequisiteObservation.MarketSnapshot value -> "{" +
                    "\"marketSnapshotDigest\":" + CanonicalDigestSupport.quote(value.marketSnapshotDigest()) +
                    ",\"instrument\":" + CanonicalDigestSupport.quote(value.instrument()) +
                    ",\"bestAsk\":" + CanonicalDigestSupport.quote(
                    CanonicalDigestSupport.plainDecimal(value.bestAsk(), "bestAsk")) + "}";
        };
    }

    private static String instrumentMetadataPayload(
            String schemaVersion,
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        PilotScopeBinding.require(PilotPrerequisiteObservation.InstrumentMetadata.isSupportedSchema(schemaVersion),
                "instrument observation schema mismatch");
        return "{\"schemaVersion\":" + CanonicalDigestSupport.quote(schemaVersion) + ",\"items\":" +
                instrumentItems(schemaVersion, items) + "}";
    }

    private static String instrumentItems(
            String schemaVersion,
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        return items.stream().map(item -> instrumentItem(schemaVersion, item))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String instrumentItem(
            String schemaVersion,
            PilotPrerequisiteObservation.InstrumentItem item
    ) {
        String prefix = "{" +
                "\"symbol\":" + CanonicalDigestSupport.quote(item.symbol()) +
                ",\"tradingStatus\":" + CanonicalDigestSupport.quote(item.tradingStatus().name()) +
                ",\"tickSize\":" + CanonicalDigestSupport.quote(
                CanonicalDigestSupport.plainDecimal(item.tickSize(), "tickSize")) +
                ",\"lotSize\":" + CanonicalDigestSupport.quote(
                CanonicalDigestSupport.plainDecimal(item.lotSize(), "lotSize")) +
                ",\"minimumOrderSize\":" + CanonicalDigestSupport.quote(
                CanonicalDigestSupport.plainDecimal(item.minimumOrderSize(), "minimumOrderSize"));
        if (PilotPrerequisiteObservation.InstrumentMetadata.LEGACY_SCHEMA_VERSION.equals(schemaVersion)) {
            return prefix +
                    ",\"minimumOrderValue\":" + CanonicalDigestSupport.quote(
                    CanonicalDigestSupport.plainDecimal(item.minimumOrderValue(), "minimumOrderValue")) +
                    ",\"minimumOrderValueCurrency\":" +
                    CanonicalDigestSupport.quote(item.minimumOrderValueCurrency()) + "}";
        }
        String evidence = prefix + ",\"minimumOrderValueEvidenceClass\":" +
                CanonicalDigestSupport.quote(item.minimumOrderValueEvidenceClass().name());
        if (item.minimumOrderValueEvidenceClass()
                == PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_PUBLISHED) {
            return evidence +
                    ",\"minimumOrderValue\":" + CanonicalDigestSupport.quote(
                    CanonicalDigestSupport.plainDecimal(item.minimumOrderValue(), "minimumOrderValue")) +
                    ",\"minimumOrderValueCurrency\":" +
                    CanonicalDigestSupport.quote(item.minimumOrderValueCurrency()) + "}";
        }
        return evidence + "}";
    }
}
