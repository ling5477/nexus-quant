package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 四类 prerequisite observation 的 sealed typed fact contract。 */
public sealed interface PilotPrerequisiteObservation permits
        PilotPrerequisiteObservation.InstrumentMetadata,
        PilotPrerequisiteObservation.FeeSchedule,
        PilotPrerequisiteObservation.BalanceSnapshot,
        PilotPrerequisiteObservation.ClockSync, PilotPrerequisiteObservation.MarketSnapshot {

    String ENVELOPE_SCHEMA = "prerequisite-observation-envelope.v1";

    Envelope envelope();

    ObservationType type();

    default UUID id() {
        return envelope().id();
    }

    default UUID pilotScopeId() {
        return envelope().pilotScopeId();
    }

    default UUID observationSetId() {
        return envelope().observationSetId();
    }

    default String observationPayloadHash() {
        return envelope().observationPayloadHash();
    }

    record Envelope(
            UUID id,
            UUID pilotScopeId,
            UUID observationSetId,
            String observationSchemaVersion,
            String observationIdentity,
            String sourceIdentity,
            String sourceSchemaVersion,
            Instant observedAt,
            Instant recordedAt,
            String recorderIdentity,
            String observationPayloadHash
    ) {
        public Envelope {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
            Objects.requireNonNull(observationSetId, "observationSetId must not be null");
            PilotScopeBinding.requireText(observationSchemaVersion, 64, "observationSchemaVersion");
            PilotScopeBinding.requireText(observationIdentity, 128, "observationIdentity");
            PilotScopeBinding.requireText(sourceIdentity, 128, "sourceIdentity");
            PilotScopeBinding.requireText(sourceSchemaVersion, 64, "sourceSchemaVersion");
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            Objects.requireNonNull(recordedAt, "recordedAt must not be null");
            PilotScopeBinding.requireText(recorderIdentity, 128, "recorderIdentity");
            PilotScopeBinding.requireDigest(observationPayloadHash, "observationPayloadHash");
        }

        public Envelope withPayloadHash(String value) {
            return new Envelope(
                    id, pilotScopeId, observationSetId, observationSchemaVersion, observationIdentity,
                    sourceIdentity, sourceSchemaVersion, observedAt, recordedAt, recorderIdentity, value
            );
        }
    }

    record InstrumentMetadata(Envelope envelope, String instrumentMetadataDigest, List<InstrumentItem> items)
            implements PilotPrerequisiteObservation {
        public static final String LEGACY_SCHEMA_VERSION = "instrument-metadata-observation.v1";
        public static final String SCHEMA_VERSION = "instrument-metadata-observation.v2";

        public InstrumentMetadata {
            Objects.requireNonNull(envelope, "envelope must not be null");
            PilotScopeBinding.require(isSupportedSchema(envelope.observationSchemaVersion()),
                    "instrument observation schema mismatch");
            PilotScopeBinding.requireDigest(instrumentMetadataDigest, "instrumentMetadataDigest");
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            PilotScopeBinding.require(items.size() >= 1 && items.size() <= 2,
                    "instrument observation requires one or two items");
            List<String> symbols = items.stream().map(InstrumentItem::symbol).toList();
            PilotScopeBinding.require(symbols.equals(symbols.stream().distinct().sorted().toList()),
                    "instrument items must be sorted and unique");
            if (LEGACY_SCHEMA_VERSION.equals(envelope.observationSchemaVersion())) {
                PilotScopeBinding.require(items.stream().allMatch(item ->
                                item.minimumOrderValueEvidenceClass()
                                        == MinimumOrderValueEvidenceClass.LEGACY_V40_REQUIRED),
                        "legacy instrument observation items require LEGACY_V40_REQUIRED evidence");
            } else {
                PilotScopeBinding.require(items.stream().noneMatch(item ->
                                item.minimumOrderValueEvidenceClass()
                                        == MinimumOrderValueEvidenceClass.LEGACY_V40_REQUIRED),
                        "v2 instrument observation items cannot use legacy evidence");
            }
        }

        public static boolean isSupportedSchema(String value) {
            return SCHEMA_VERSION.equals(value) || LEGACY_SCHEMA_VERSION.equals(value);
        }

        @Override
        public ObservationType type() {
            return ObservationType.INSTRUMENT_METADATA;
        }
    }

    record FeeSchedule(
            Envelope envelope,
            String feeScheduleDigest,
            String feeTier,
            PilotScopeBinding.FeeEvidenceClass feeEvidenceClass,
            BigDecimal makerFeeRate,
            BigDecimal takerFeeRate,
            String feeLossTreatment
    ) implements PilotPrerequisiteObservation {
        public static final String SCHEMA_VERSION = "fee-schedule-observation.v1";
        public static final String LOSS_TREATMENT = "INCLUDE_IN_DAILY_LOSS_AND_CAPITAL_USAGE";

        public FeeSchedule {
            Objects.requireNonNull(envelope, "envelope must not be null");
            PilotScopeBinding.require(SCHEMA_VERSION.equals(envelope.observationSchemaVersion()),
                    "fee observation schema mismatch");
            PilotScopeBinding.requireDigest(feeScheduleDigest, "feeScheduleDigest");
            PilotScopeBinding.requireText(feeTier, 64, "feeTier");
            Objects.requireNonNull(feeEvidenceClass, "feeEvidenceClass must not be null");
            requireRate(makerFeeRate, "makerFeeRate");
            requireRate(takerFeeRate, "takerFeeRate");
            PilotScopeBinding.require(LOSS_TREATMENT.equals(feeLossTreatment), "feeLossTreatment is unsupported");
        }

        @Override
        public ObservationType type() {
            return ObservationType.FEE_SCHEDULE;
        }

        private static void requireRate(BigDecimal value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            PilotScopeBinding.require(value.compareTo(BigDecimal.ONE.negate()) >= 0
                    && value.compareTo(BigDecimal.ONE) <= 0, name + " is outside [-1,1]");
            PilotScopeBinding.require(value.scale() <= 12, name + " has more than 12 decimal places");
        }
    }

    record BalanceSnapshot(
            Envelope envelope,
            String balanceSnapshotDigest,
            String balanceCurrency,
            BigDecimal availableBalance
    ) implements PilotPrerequisiteObservation {
        public static final String SCHEMA_VERSION = "balance-snapshot-observation.v1";
        public static final String CURRENCY = "USDT";

        public BalanceSnapshot {
            Objects.requireNonNull(envelope, "envelope must not be null");
            PilotScopeBinding.require(SCHEMA_VERSION.equals(envelope.observationSchemaVersion()),
                    "balance observation schema mismatch");
            PilotScopeBinding.requireDigest(balanceSnapshotDigest, "balanceSnapshotDigest");
            PilotScopeBinding.require(CURRENCY.equals(balanceCurrency), "balanceCurrency must be USDT");
            availableBalance = CanonicalDigestSupport.money(availableBalance, "availableBalance");
            PilotScopeBinding.require(availableBalance.signum() >= 0, "availableBalance must not be negative");
        }

        @Override
        public ObservationType type() {
            return ObservationType.BALANCE_SNAPSHOT;
        }
    }

    record ClockSync(
            Envelope envelope,
            String clockSyncObservationDigest,
            String signedTimestampSource,
            long observedSkewMs
    ) implements PilotPrerequisiteObservation {
        public static final String SCHEMA_VERSION = "clock-sync-observation.v1";

        public ClockSync {
            Objects.requireNonNull(envelope, "envelope must not be null");
            PilotScopeBinding.require(SCHEMA_VERSION.equals(envelope.observationSchemaVersion()),
                    "clock observation schema mismatch");
            PilotScopeBinding.requireDigest(clockSyncObservationDigest, "clockSyncObservationDigest");
            PilotScopeBinding.require(PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE.equals(signedTimestampSource),
                    "signedTimestampSource is unsupported");
            PilotScopeBinding.require(observedSkewMs >= -1_000 && observedSkewMs <= 1_000,
                    "observedSkewMs is outside the hard bound");
        }

        @Override
        public ObservationType type() {
            return ObservationType.CLOCK_SYNC;
        }
    }

    record InstrumentItem(
            String symbol,
            TradingStatus tradingStatus,
            BigDecimal tickSize,
            BigDecimal lotSize,
            BigDecimal minimumOrderSize,
            MinimumOrderValueEvidenceClass minimumOrderValueEvidenceClass,
            BigDecimal minimumOrderValue,
            String minimumOrderValueCurrency
    ) {
        public InstrumentItem {
            PilotScopeBinding.require(symbol != null && symbol.matches("[A-Z0-9]{2,20}-USDT"),
                    "symbol is not canonical");
            Objects.requireNonNull(tradingStatus, "tradingStatus must not be null");
            requirePositive(tickSize, "tickSize");
            requirePositive(lotSize, "lotSize");
            requirePositive(minimumOrderSize, "minimumOrderSize");
            Objects.requireNonNull(minimumOrderValueEvidenceClass,
                    "minimumOrderValueEvidenceClass must not be null");
            switch (minimumOrderValueEvidenceClass) {
                case VENUE_PUBLISHED -> {
                    requirePositive(minimumOrderValue, "minimumOrderValue");
                    PilotScopeBinding.requireText(
                            minimumOrderValueCurrency, 16, "minimumOrderValueCurrency");
                }
                case VENUE_NOT_PUBLISHED -> PilotScopeBinding.require(
                        minimumOrderValue == null && minimumOrderValueCurrency == null,
                        "VENUE_NOT_PUBLISHED cannot carry minimum order value facts");
                case LEGACY_V40_REQUIRED -> {
                    requirePositive(minimumOrderValue, "minimumOrderValue");
                    PilotScopeBinding.require("USDT".equals(minimumOrderValueCurrency),
                            "legacy minimumOrderValueCurrency must be USDT");
                }
            }
        }

        private static void requirePositive(BigDecimal value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            PilotScopeBinding.require(value.signum() > 0, name + " must be positive");
            PilotScopeBinding.require(value.scale() <= 18, name + " has more than 18 decimal places");
        }
    }
    record MarketSnapshot(
            Envelope envelope,
            String marketSnapshotDigest,
            String instrument,
            BigDecimal bestAsk
    ) implements PilotPrerequisiteObservation {
        public static final String SCHEMA_VERSION = "market-snapshot-observation.v1";

        public MarketSnapshot {
            Objects.requireNonNull(envelope, "envelope must not be null");
            PilotScopeBinding.require(SCHEMA_VERSION.equals(envelope.observationSchemaVersion()),
                    "market observation schema mismatch");
            PilotScopeBinding.requireDigest(marketSnapshotDigest, "marketSnapshotDigest");
            PilotScopeBinding.require(instrument != null && instrument.matches("[A-Z0-9]{2,20}-USDT"),
                    "market instrument is not canonical");
            bestAsk = CanonicalDigestSupport.money(bestAsk, "bestAsk");
            PilotScopeBinding.require(bestAsk.signum() > 0, "bestAsk must be positive");
        }

        @Override
        public ObservationType type() {
            return ObservationType.MARKET_SNAPSHOT;
        }
    }

    enum ObservationType {
        INSTRUMENT_METADATA,
        FEE_SCHEDULE,
        BALANCE_SNAPSHOT,
        CLOCK_SYNC,
        MARKET_SNAPSHOT
    }

    enum TradingStatus {
        LIVE,
        SUSPEND,
        PREOPEN,
        TEST
    }

    enum MinimumOrderValueEvidenceClass {
        VENUE_PUBLISHED,
        VENUE_NOT_PUBLISHED,
        LEGACY_V40_REQUIRED
    }
}
