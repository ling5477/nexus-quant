package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for immutable pilot scope and typed prerequisite facts。 */
@Repository
public class JdbcPilotScopeRepository implements PilotScopeRepository {

    private static final String SCOPE_SELECT = """
            SELECT pilot_scope_id, session_id, instrument_metadata_digest,
                   instrument_source_identity, instrument_source_schema_version, instrument_maximum_age_ms,
                   fee_schedule_digest, fee_tier, fee_evidence_class, fee_source_identity,
                   fee_source_schema_version, fee_maximum_age_ms, balance_source_identity,
                   balance_source_schema_version, balance_maximum_age_ms, clock_source_identity,
                   clock_source_schema_version, clock_maximum_age_ms, signed_timestamp_source,
                   maximum_tolerated_skew_ms, endpoint_policy_version, endpoint_policy_digest,
                   provider_contract_identity, provider_artifact_digest, worker_identity,
                   worker_release_digest, pilot_scope_hash, created_by, created_at
            FROM pilot_scope_bindings
            """;
    private static final String OBSERVATION_SELECT = """
            SELECT observation_id, pilot_scope_id, observation_set_id, observation_type,
                   observation_schema_version, observation_identity, source_identity,
                   source_schema_version, observed_at, recorded_at, recorder_identity,
                   observation_payload_hash, instrument_metadata_digest, fee_schedule_digest,
                   balance_snapshot_digest, clock_sync_observation_digest, fee_tier,
                   fee_evidence_class, maker_fee_rate, taker_fee_rate, fee_loss_treatment,
                   balance_currency, available_balance, signed_timestamp_source, observed_skew_ms
            FROM pilot_prerequisite_observations
            """;
    private static final String APPROVAL_SELECT = """
            SELECT approval_id, session_id, scope_schema_version, pilot_scope_id, scope_hash,
                   release_digest, risk_limit_set_digest, approver_id, approver_role, decision,
                   reason, approved_at, expires_at
            FROM operator_approvals
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPilotScopeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PilotScopeBinding materialize(LiveSession session, PilotScopeBinding scope) {
        requireCanonicalScope(session, scope);
        int inserted = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO pilot_scope_bindings (
                        pilot_scope_id, session_id, scope_schema_version,
                        instrument_metadata_digest, instrument_source_identity,
                        instrument_source_schema_version, instrument_maximum_age_ms,
                        fee_schedule_digest, fee_tier, fee_evidence_class, fee_source_identity,
                        fee_source_schema_version, fee_maximum_age_ms, balance_source_identity,
                        balance_source_schema_version, balance_maximum_age_ms, clock_source_identity,
                        clock_source_schema_version, clock_maximum_age_ms, signed_timestamp_source,
                        maximum_tolerated_skew_ms, endpoint_policy_version, endpoint_policy_digest,
                        provider_contract_identity, provider_artifact_digest, worker_identity,
                        worker_release_digest, pilot_scope_hash, created_by, created_at
                    ) VALUES (?, ?, 'pilot-scope.v1', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (session_id) DO NOTHING
                    """);
            int index = 1;
            statement.setObject(index++, scope.id());
            statement.setObject(index++, scope.sessionId());
            statement.setString(index++, scope.instrumentMetadataDigest());
            statement.setString(index++, scope.instrumentSourceIdentity());
            statement.setString(index++, scope.instrumentSourceSchemaVersion());
            statement.setLong(index++, scope.instrumentMaximumAgeMs());
            statement.setString(index++, scope.feeScheduleDigest());
            statement.setString(index++, scope.feeTier());
            statement.setString(index++, scope.feeEvidenceClass().name());
            statement.setString(index++, scope.feeSourceIdentity());
            statement.setString(index++, scope.feeSourceSchemaVersion());
            statement.setLong(index++, scope.feeMaximumAgeMs());
            statement.setString(index++, scope.balanceSourceIdentity());
            statement.setString(index++, scope.balanceSourceSchemaVersion());
            statement.setLong(index++, scope.balanceMaximumAgeMs());
            statement.setString(index++, scope.clockSourceIdentity());
            statement.setString(index++, scope.clockSourceSchemaVersion());
            statement.setLong(index++, scope.clockMaximumAgeMs());
            statement.setString(index++, scope.signedTimestampSource());
            statement.setLong(index++, scope.maximumToleratedSkewMs());
            statement.setString(index++, scope.endpointPolicyVersion());
            statement.setString(index++, scope.endpointPolicyDigest());
            statement.setString(index++, scope.providerContractIdentity());
            statement.setString(index++, scope.providerArtifactDigest());
            statement.setString(index++, scope.workerIdentity());
            statement.setString(index++, scope.workerReleaseDigest());
            statement.setString(index++, scope.pilotScopeHash());
            statement.setLong(index++, scope.createdBy());
            statement.setTimestamp(index, timestamp(scope.createdAt()));
            return statement;
        });
        if (inserted == 1) {
            return scope;
        }
        PilotScopeBinding existing = findBySessionId(session.id()).orElseThrow(() -> new LiveControlException(
                "PILOT_SCOPE_MATERIALIZATION_CONFLICT", "pilot scope conflict was not reconstructable"));
        if (PilotScopeCanonicalEncoder.encode(session, existing).equals(PilotScopeCanonicalEncoder.encode(session, scope))
                && existing.pilotScopeHash().equals(scope.pilotScopeHash())) {
            return existing;
        }
        throw new LiveControlException(
                "PILOT_SCOPE_MATERIALIZATION_CONFLICT",
                "session is already bound to a different immutable pilot scope"
        );
    }

    @Override
    public Optional<PilotScopeBinding> findBySessionId(UUID sessionId) {
        return first(jdbcTemplate.query(SCOPE_SELECT + " WHERE session_id = ?", this::mapScope, sessionId));
    }

    @Override
    public Optional<PilotScopeBinding> lockBySessionId(UUID sessionId) {
        return first(jdbcTemplate.query(
                SCOPE_SELECT + " WHERE session_id = ? FOR UPDATE", this::mapScope, sessionId));
    }

    @Override
    public PilotObservationSet appendObservationSet(PilotScopeBinding scope, PilotObservationSet observations) {
        if (!scope.id().equals(observations.pilotScopeId())) {
            throw new IllegalArgumentException("pilot scope identity mismatch");
        }
        Optional<PilotObservationSet> replay = findReplay(observations);
        if (replay.isPresent()) {
            return replay.get();
        }
        for (PilotPrerequisiteObservation observation : observations.observations()) {
            if (!insertObservation(observation)) {
                return findReplay(observations).orElseThrow(JdbcPilotScopeRepository::identityConflict);
            }
        }
        for (PilotPrerequisiteObservation.InstrumentItem item : observations.instrumentMetadata().items()) {
            jdbcTemplate.update("""
                    INSERT INTO pilot_instrument_observation_items (
                        observation_id, observation_type, symbol, trading_status, tick_size, lot_size,
                        minimum_order_size, minimum_order_value_evidence_class,
                        minimum_order_value, minimum_order_value_currency
                    ) VALUES (?, 'INSTRUMENT_METADATA', ?, ?, ?, ?, ?, ?, ?, ?)
                    """, observations.instrumentMetadata().id(), item.symbol(), item.tradingStatus().name(),
                    item.tickSize(), item.lotSize(), item.minimumOrderSize(),
                    item.minimumOrderValueEvidenceClass().name(), item.minimumOrderValue(),
                    item.minimumOrderValueCurrency());
        }
        return observations;
    }

    @Override
    public Optional<PilotObservationSet> findObservationSet(UUID pilotScopeId, UUID observationSetId) {
        List<PilotPrerequisiteObservation> values = jdbcTemplate.query(
                OBSERVATION_SELECT + " WHERE pilot_scope_id = ? AND observation_set_id = ? ORDER BY observation_type",
                this::mapObservation, pilotScopeId, observationSetId);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() != 4) {
            throw new LiveControlException("PILOT_OBSERVATION_SET_INCOMPLETE", "stored observation set is incomplete");
        }
        Map<PilotPrerequisiteObservation.ObservationType, PilotPrerequisiteObservation> typed =
                new EnumMap<>(PilotPrerequisiteObservation.ObservationType.class);
        values.forEach(value -> typed.put(value.type(), value));
        return Optional.of(new PilotObservationSet(
                observationSetId, pilotScopeId,
                (PilotPrerequisiteObservation.InstrumentMetadata) typed.get(
                        PilotPrerequisiteObservation.ObservationType.INSTRUMENT_METADATA),
                (PilotPrerequisiteObservation.FeeSchedule) typed.get(
                        PilotPrerequisiteObservation.ObservationType.FEE_SCHEDULE),
                (PilotPrerequisiteObservation.BalanceSnapshot) typed.get(
                        PilotPrerequisiteObservation.ObservationType.BALANCE_SNAPSHOT),
                (PilotPrerequisiteObservation.ClockSync) typed.get(
                        PilotPrerequisiteObservation.ObservationType.CLOCK_SYNC)
        ));
    }

    @Override
    public Optional<PilotObservationSet> findLatestCompleteObservationSet(UUID pilotScopeId) {
        List<UUID> ids = jdbcTemplate.query("""
                SELECT observation_set_id
                FROM pilot_prerequisite_observations
                WHERE pilot_scope_id = ?
                GROUP BY observation_set_id
                HAVING count(*) = 4 AND count(DISTINCT observation_type) = 4
                ORDER BY max(observed_at) DESC, observation_set_id DESC
                LIMIT 1
                """, (row, rowNumber) -> row.getObject(1, UUID.class), pilotScopeId);
        return ids.isEmpty() ? Optional.empty() : findObservationSet(pilotScopeId, ids.getFirst());
    }

    @Override
    public Instant currentTransactionTime() {
        return jdbcTemplate.queryForObject(
                "SELECT transaction_timestamp()", (row, rowNumber) -> row.getTimestamp(1).toInstant());
    }

    @Override
    public Optional<OperatorApproval> findValidPilotApproval(PilotScopeBinding scope, Instant decisionAt) {
        return first(jdbcTemplate.query(APPROVAL_SELECT + """
                WHERE session_id = ? AND scope_schema_version = 'pilot-scope.v1'
                  AND pilot_scope_id = ? AND scope_hash = ?
                  AND decision = 'APPROVED' AND approved_at <= ? AND expires_at > ?
                ORDER BY approved_at DESC, approval_id DESC LIMIT 1
                """, this::mapApproval, scope.sessionId(), scope.id(), scope.pilotScopeHash(),
                timestamp(decisionAt), timestamp(decisionAt)));
    }

    private Optional<PilotObservationSet> findReplay(PilotObservationSet requested) {
        List<ObservationIdentity> existing = new ArrayList<>();
        for (PilotPrerequisiteObservation observation : requested.observations()) {
            List<ObservationIdentity> matches = jdbcTemplate.query("""
                    SELECT observation_id, observation_set_id, observation_payload_hash
                    FROM pilot_prerequisite_observations
                    WHERE pilot_scope_id = ? AND observation_type = ?
                      AND source_identity = ? AND observation_identity = ?
                    """, (row, rowNumber) -> new ObservationIdentity(
                            row.getObject("observation_id", UUID.class),
                            row.getObject("observation_set_id", UUID.class),
                            row.getString("observation_payload_hash")),
                    observation.pilotScopeId(), observation.type().name(),
                    observation.envelope().sourceIdentity(), observation.envelope().observationIdentity());
            if (!matches.isEmpty()) {
                ObservationIdentity match = matches.getFirst();
                if (!match.payloadHash().equals(observation.observationPayloadHash())) {
                    throw identityConflict();
                }
                existing.add(match);
            }
        }
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        UUID setId = existing.getFirst().setId();
        if (existing.size() != 4 || existing.stream().anyMatch(value -> !value.setId().equals(setId))) {
            throw identityConflict();
        }
        return findObservationSet(requested.pilotScopeId(), setId);
    }

    private boolean insertObservation(PilotPrerequisiteObservation observation) {
        if (!PilotObservationCanonicalEncoder.digest(observation).equals(observation.observationPayloadHash())) {
            throw new IllegalArgumentException("observation payload hash is not canonical");
        }
        PilotPrerequisiteObservation.Envelope envelope = observation.envelope();
        Variant variant = Variant.from(observation);
        int inserted = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO pilot_prerequisite_observations (
                        observation_id, pilot_scope_id, observation_set_id, observation_type,
                        observation_schema_version, observation_identity, source_identity,
                        source_schema_version, observed_at, recorded_at, recorder_identity,
                        observation_payload_hash, instrument_metadata_digest, fee_schedule_digest,
                        balance_snapshot_digest, clock_sync_observation_digest, fee_tier,
                        fee_evidence_class, maker_fee_rate, taker_fee_rate, fee_loss_treatment,
                        balance_currency, available_balance, signed_timestamp_source, observed_skew_ms
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """);
            int index = 1;
            statement.setObject(index++, envelope.id());
            statement.setObject(index++, envelope.pilotScopeId());
            statement.setObject(index++, envelope.observationSetId());
            statement.setString(index++, observation.type().name());
            statement.setString(index++, envelope.observationSchemaVersion());
            statement.setString(index++, envelope.observationIdentity());
            statement.setString(index++, envelope.sourceIdentity());
            statement.setString(index++, envelope.sourceSchemaVersion());
            statement.setTimestamp(index++, timestamp(envelope.observedAt()));
            statement.setTimestamp(index++, timestamp(envelope.recordedAt()));
            statement.setString(index++, envelope.recorderIdentity());
            statement.setString(index++, envelope.observationPayloadHash());
            statement.setString(index++, variant.instrumentDigest());
            statement.setString(index++, variant.feeDigest());
            statement.setString(index++, variant.balanceDigest());
            statement.setString(index++, variant.clockDigest());
            statement.setString(index++, variant.feeTier());
            statement.setString(index++, variant.feeEvidenceClass());
            statement.setBigDecimal(index++, variant.makerFeeRate());
            statement.setBigDecimal(index++, variant.takerFeeRate());
            statement.setString(index++, variant.feeLossTreatment());
            statement.setString(index++, variant.balanceCurrency());
            statement.setBigDecimal(index++, variant.availableBalance());
            statement.setString(index++, variant.signedTimestampSource());
            if (variant.observedSkewMs() == null) {
                statement.setObject(index, null);
            } else {
                statement.setLong(index, variant.observedSkewMs());
            }
            return statement;
        });
        if (inserted != 1) {
            return false;
        }
        return true;
    }

    private PilotScopeBinding mapScope(ResultSet row, int rowNumber) throws SQLException {
        return new PilotScopeBinding(
                row.getObject("pilot_scope_id", UUID.class), row.getObject("session_id", UUID.class),
                row.getString("instrument_metadata_digest"), row.getString("instrument_source_identity"),
                row.getString("instrument_source_schema_version"), row.getLong("instrument_maximum_age_ms"),
                row.getString("fee_schedule_digest"), row.getString("fee_tier"),
                PilotScopeBinding.FeeEvidenceClass.valueOf(row.getString("fee_evidence_class")),
                row.getString("fee_source_identity"), row.getString("fee_source_schema_version"),
                row.getLong("fee_maximum_age_ms"), row.getString("balance_source_identity"),
                row.getString("balance_source_schema_version"), row.getLong("balance_maximum_age_ms"),
                row.getString("clock_source_identity"), row.getString("clock_source_schema_version"),
                row.getLong("clock_maximum_age_ms"), row.getString("signed_timestamp_source"),
                row.getLong("maximum_tolerated_skew_ms"), row.getString("endpoint_policy_version"),
                row.getString("endpoint_policy_digest"), row.getString("provider_contract_identity"),
                row.getString("provider_artifact_digest"), row.getString("worker_identity"),
                row.getString("worker_release_digest"), row.getString("pilot_scope_hash"),
                row.getLong("created_by"), instant(row, "created_at")
        );
    }

    private PilotPrerequisiteObservation mapObservation(ResultSet row, int rowNumber) throws SQLException {
        PilotPrerequisiteObservation.Envelope envelope = new PilotPrerequisiteObservation.Envelope(
                row.getObject("observation_id", UUID.class), row.getObject("pilot_scope_id", UUID.class),
                row.getObject("observation_set_id", UUID.class), row.getString("observation_schema_version"),
                row.getString("observation_identity"), row.getString("source_identity"),
                row.getString("source_schema_version"), instant(row, "observed_at"),
                instant(row, "recorded_at"), row.getString("recorder_identity"),
                row.getString("observation_payload_hash")
        );
        PilotPrerequisiteObservation.ObservationType type =
                PilotPrerequisiteObservation.ObservationType.valueOf(row.getString("observation_type"));
        return switch (type) {
            case INSTRUMENT_METADATA -> new PilotPrerequisiteObservation.InstrumentMetadata(
                    envelope, row.getString("instrument_metadata_digest"), mapInstrumentItems(envelope.id()));
            case FEE_SCHEDULE -> new PilotPrerequisiteObservation.FeeSchedule(
                    envelope, row.getString("fee_schedule_digest"), row.getString("fee_tier"),
                    PilotScopeBinding.FeeEvidenceClass.valueOf(row.getString("fee_evidence_class")),
                    row.getBigDecimal("maker_fee_rate"), row.getBigDecimal("taker_fee_rate"),
                    row.getString("fee_loss_treatment"));
            case BALANCE_SNAPSHOT -> new PilotPrerequisiteObservation.BalanceSnapshot(
                    envelope, row.getString("balance_snapshot_digest"), row.getString("balance_currency"),
                    row.getBigDecimal("available_balance"));
            case CLOCK_SYNC -> new PilotPrerequisiteObservation.ClockSync(
                    envelope, row.getString("clock_sync_observation_digest"),
                    row.getString("signed_timestamp_source"), row.getLong("observed_skew_ms"));
        };
    }

    private List<PilotPrerequisiteObservation.InstrumentItem> mapInstrumentItems(UUID observationId) {
        return jdbcTemplate.query("""
                SELECT symbol, trading_status, tick_size, lot_size, minimum_order_size,
                       minimum_order_value_evidence_class, minimum_order_value,
                       minimum_order_value_currency
                FROM pilot_instrument_observation_items
                WHERE observation_id = ? ORDER BY symbol
                """, (row, rowNumber) -> new PilotPrerequisiteObservation.InstrumentItem(
                        row.getString("symbol"),
                        PilotPrerequisiteObservation.TradingStatus.valueOf(row.getString("trading_status")),
                        row.getBigDecimal("tick_size"), row.getBigDecimal("lot_size"),
                        row.getBigDecimal("minimum_order_size"),
                        PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.valueOf(
                                row.getString("minimum_order_value_evidence_class")),
                        row.getBigDecimal("minimum_order_value"),
                        row.getString("minimum_order_value_currency")), observationId);
    }

    private OperatorApproval mapApproval(ResultSet row, int rowNumber) throws SQLException {
        return new OperatorApproval(
                row.getObject("approval_id", UUID.class), row.getObject("session_id", UUID.class),
                row.getString("scope_schema_version"), row.getObject("pilot_scope_id", UUID.class),
                row.getString("scope_hash"), row.getString("release_digest"),
                row.getString("risk_limit_set_digest"), row.getLong("approver_id"),
                row.getString("approver_role"), OperatorApproval.Decision.valueOf(row.getString("decision")),
                row.getString("reason"), instant(row, "approved_at"), instant(row, "expires_at")
        );
    }

    private static void requireCanonicalScope(LiveSession session, PilotScopeBinding scope) {
        if (!scope.hasCanonicalHash(session)) {
            throw new IllegalArgumentException("pilot scope hash is not canonical for the exact session");
        }
    }

    private static LiveControlException identityConflict() {
        return new LiveControlException(
                "PREREQUISITE_OBSERVATION_IDENTITY_CONFLICT",
                "observation identity is already bound to a different payload or set"
        );
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet row, String name) throws SQLException {
        Timestamp value = row.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private record ObservationIdentity(UUID id, UUID setId, String payloadHash) {
    }

    private record Variant(
            String instrumentDigest,
            String feeDigest,
            String balanceDigest,
            String clockDigest,
            String feeTier,
            String feeEvidenceClass,
            BigDecimal makerFeeRate,
            BigDecimal takerFeeRate,
            String feeLossTreatment,
            String balanceCurrency,
            BigDecimal availableBalance,
            String signedTimestampSource,
            Long observedSkewMs
    ) {
        private static Variant from(PilotPrerequisiteObservation observation) {
            return switch (observation) {
                case PilotPrerequisiteObservation.InstrumentMetadata value -> new Variant(
                        value.instrumentMetadataDigest(), null, null, null, null, null,
                        null, null, null, null, null, null, null);
                case PilotPrerequisiteObservation.FeeSchedule value -> new Variant(
                        null, value.feeScheduleDigest(), null, null, value.feeTier(),
                        value.feeEvidenceClass().name(), value.makerFeeRate(), value.takerFeeRate(),
                        value.feeLossTreatment(), null, null, null, null);
                case PilotPrerequisiteObservation.BalanceSnapshot value -> new Variant(
                        null, null, value.balanceSnapshotDigest(), null, null, null,
                        null, null, null, value.balanceCurrency(), value.availableBalance(), null, null);
                case PilotPrerequisiteObservation.ClockSync value -> new Variant(
                        null, null, null, value.clockSyncObservationDigest(), null, null,
                        null, null, null, null, null, value.signedTimestampSource(), value.observedSkewMs());
            };
        }
    }
}
