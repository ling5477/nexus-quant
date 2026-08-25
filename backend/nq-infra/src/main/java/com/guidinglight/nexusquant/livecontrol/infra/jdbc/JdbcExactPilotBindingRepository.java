package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingConsumption;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBindingCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 复用 V39 append-only {@code live_session_events} 的 exact binding durable adapter。
 *
 * <p>CREATE/CONSUME 事件都在 session 行锁保护下追加；canonical binding 作为 JSON string 保存，
 * 避免 JSONB presentation normalization 参与 digest。该 adapter 不读 execution/order/ledger 表。</p>
 */
@Repository
public class JdbcExactPilotBindingRepository implements ExactPilotBindingRepository {

    static final String CREATE_COMMAND = "CREATE_EXACT_PILOT_BINDING";
    static final String CONSUME_COMMAND = "CONSUME_EXACT_PILOT_BINDING";
    private static final String EVENT_SCHEMA = "exact-pilot-binding-event.v1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LiveControlRepository liveControlRepository;

    public JdbcExactPilotBindingRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            LiveControlRepository liveControlRepository
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.liveControlRepository = Objects.requireNonNull(
                liveControlRepository, "liveControlRepository must not be null");
    }

    @Override
    public LiveSession lockSession(UUID sessionId) {
        return liveControlRepository.lockSession(sessionId).orElseThrow(() ->
                new LiveControlException("LIVE_SESSION_NOT_FOUND", "exact binding session was not found"));
    }

    @Override
    public Instant currentTransactionTime() {
        return jdbcTemplate.queryForObject(
                "SELECT transaction_timestamp()", (row, rowNumber) -> row.getTimestamp(1).toInstant());
    }

    @Override
    public ExactPilotBinding createOrGet(ExactPilotBinding binding, LiveSession lockedSession) {
        requireLockedSession(binding, lockedSession);
        if (!binding.hasCanonicalDigest()) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_DIGEST_MISMATCH", "binding digest is not canonical");
        }
        Optional<ExactPilotBinding> existing = findCreated(binding.sessionId());
        if (existing.isPresent()) {
            if (existing.get().equals(binding)) {
                return existing.get();
            }
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_IDEMPOTENCY_CONFLICT",
                    "session is already bound to different exact pilot facts"
            );
        }
        String metadata = createMetadata(binding);
        liveControlRepository.appendSessionEvent(new LiveSessionEvent(
                UUID.randomUUID(), binding.sessionId(), 1, lockedSession.state(), lockedSession.state(),
                CREATE_COMMAND, binding.account().ownerId(), binding.correlation().requestId(),
                binding.correlation().traceId(), "EXACT_PILOT_BINDING_VERIFIED",
                binding.correlation().idempotencyKey(), ExactPilotBindingCanonicalEncoder.eventDigest(
                CREATE_COMMAND, binding.bindingDigest(), binding.correlation().idempotencyKey()),
                metadata, binding.bindingCreatedAt()
        ));
        return binding;
    }

    @Override
    public Optional<ExactPilotBinding> find(UUID sessionId, UUID bindingId) {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT metadata::TEXT
                FROM live_session_events
                WHERE session_id = ? AND command = ? AND metadata ->> 'bindingId' = ?
                ORDER BY sequence_no
                """, String.class, sessionId, CREATE_COMMAND, bindingId.toString());
        if (rows.size() > 1) {
            throw corrupted("multiple create events exist for one binding identity");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(parseBinding(rows.getFirst()));
    }

    @Override
    public boolean isConsumed(UUID sessionId, UUID bindingId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM live_session_events
                WHERE session_id = ? AND command = ? AND metadata ->> 'bindingId' = ?
                """, Integer.class, sessionId, CONSUME_COMMAND, bindingId.toString());
        if (count == null || count < 0 || count > 1) {
            throw corrupted("binding consume event cardinality is invalid");
        }
        return count == 1;
    }

    @Override
    public ExactPilotBindingConsumption consume(
            ExactPilotBinding binding,
            LiveSession lockedSession,
            ExactPilotBinding.Correlation correlation,
            Instant consumedAt
    ) {
        requireLockedSession(binding, lockedSession);
        Objects.requireNonNull(correlation, "correlation must not be null");
        Objects.requireNonNull(consumedAt, "consumedAt must not be null");
        ExactPilotBinding stored = find(binding.sessionId(), binding.id()).orElseThrow(() ->
                new LiveControlException("EXACT_PILOT_BINDING_NOT_FOUND", "exact pilot binding was not found"));
        if (!stored.equals(binding) || !stored.hasCanonicalDigest()) {
            throw corrupted("stored binding differs from the verified binding");
        }
        if (isConsumed(binding.sessionId(), binding.id())) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_ALREADY_CONSUMED", "exact pilot binding was already consumed");
        }
        liveControlRepository.appendSessionEvent(new LiveSessionEvent(
                UUID.randomUUID(), binding.sessionId(), 1, lockedSession.state(), lockedSession.state(),
                CONSUME_COMMAND, binding.account().ownerId(), correlation.requestId(), correlation.traceId(),
                "EXACT_PILOT_BINDING_CONSUMED", correlation.idempotencyKey(),
                ExactPilotBindingCanonicalEncoder.eventDigest(
                        CONSUME_COMMAND, binding.bindingDigest(), correlation.idempotencyKey()),
                consumptionMetadata(binding, consumedAt), consumedAt
        ));
        return new ExactPilotBindingConsumption(
                binding.id(), binding.bindingDigest(), consumedAt, false, false);
    }

    private Optional<ExactPilotBinding> findCreated(UUID sessionId) {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT metadata::TEXT
                FROM live_session_events
                WHERE session_id = ? AND command = ?
                ORDER BY sequence_no
                """, String.class, sessionId, CREATE_COMMAND);
        if (rows.size() > 1) {
            throw corrupted("session has multiple exact binding create events");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(parseBinding(rows.getFirst()));
    }

    private String createMetadata(ExactPilotBinding binding) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", EVENT_SCHEMA);
        node.put("lifecycle", ExactPilotBinding.Lifecycle.VERIFIED.name());
        node.put("bindingId", binding.id().toString());
        node.put("bindingDigest", binding.bindingDigest());
        node.put("canonicalBinding", ExactPilotBindingCanonicalEncoder.encode(binding));
        return writeJson(node);
    }

    private String consumptionMetadata(ExactPilotBinding binding, Instant consumedAt) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", EVENT_SCHEMA);
        node.put("lifecycle", ExactPilotBinding.Lifecycle.CONSUMED.name());
        node.put("bindingId", binding.id().toString());
        node.put("bindingDigest", binding.bindingDigest());
        node.put("consumedAt", consumedAt.toString());
        node.put("tradingAuthorized", false);
        node.put("exchangeMutation", false);
        return writeJson(node);
    }

    private ExactPilotBinding parseBinding(String metadata) {
        try {
            JsonNode stored = objectMapper.readTree(metadata);
            requireText(stored, "schemaVersion", EVENT_SCHEMA);
            requireText(stored, "lifecycle", ExactPilotBinding.Lifecycle.VERIFIED.name());
            String canonical = text(stored, "canonicalBinding");
            String storedDigest = text(stored, "bindingDigest");
            JsonNode value = objectMapper.readTree(canonical);
            String schemaVersion = text(value, "schemaVersion");
            boolean operatorPilot = ExactPilotBinding.OPERATOR_PILOT_SCHEMA_VERSION.equals(schemaVersion);
            if (!operatorPilot && !ExactPilotBinding.SCHEMA_VERSION.equals(schemaVersion)) {
                throw new IllegalArgumentException("schemaVersion is unsupported");
            }
            ExactPilotBinding.RiskPolicyIdentity risk = operatorPilot ? null
                    : new ExactPilotBinding.RiskPolicyIdentity(
                    uuid(value, "riskLimitSetId"), intValue(value, "riskPolicyVersion"),
                    text(value, "riskPolicyDigest"), text(value, "killSwitchState"));
            ExactPilotBinding.OperatorPilotAuthorityIdentity operatorAuthority = operatorPilot
                    ? new ExactPilotBinding.OperatorPilotAuthorityIdentity(
                    uuid(value, "operatorPilotAuthorityId"),
                    text(value, "operatorPilotAuthorityDigest"),
                    text(value, "operatorPilotInstrument"),
                    ExactPilotBinding.Side.valueOf(text(value, "operatorPilotSide")),
                    ExactPilotBinding.OrderType.valueOf(text(value, "operatorPilotOrderType")),
                    decimal(value, "operatorPilotMaxNotional"),
                    intValue(value, "operatorPilotMaxPlaceCount"),
                    intValue(value, "operatorPilotMaxCancelCount"),
                    booleanValue(value, "operatorPilotTransferAllowed"),
                    booleanValue(value, "operatorPilotWithdrawAllowed"),
                    text(value, "killSwitchState"))
                    : null;
            ExactPilotBinding binding = new ExactPilotBinding(
                    uuid(value, "bindingId"), uuid(value, "sessionId"), uuid(value, "pilotScopeId"),
                    uuid(value, "observationSetId"),
                    new ExactPilotBinding.DeploymentIdentity(
                            text(value, "sourceCommit"), text(value, "releaseId"),
                            text(value, "manifestSha256"), text(value, "serverIdentity"),
                            text(value, "runtimeProfile")),
                    new ExactPilotBinding.AccountIdentity(
                            text(value, "exchange"), text(value, "environment"),
                            longValue(value, "ownerId"), longValue(value, "exchangeAccountId"),
                            longValue(value, "credentialReferenceId")),
                    new ExactPilotBinding.OrderEnvelope(
                            longValue(value, "instrumentId"), text(value, "exchangeInstrumentId"),
                            ExactPilotBinding.Side.valueOf(text(value, "side")),
                            ExactPilotBinding.OrderType.valueOf(text(value, "orderType")),
                            decimal(value, "price"), decimal(value, "quantity"), decimal(value, "notional")),
                    new ExactPilotBinding.ObservationIdentities(
                            uuid(value, "instrumentSnapshotIdentity"), uuid(value, "feeSnapshotIdentity"),
                            uuid(value, "balanceSnapshotIdentity"), uuid(value, "exchangeTimeSnapshotIdentity"),
                            uuid(value, "marketSnapshotIdentity"), text(value, "marketSnapshotDigest")),
                    risk, operatorAuthority,
                    instant(value, "pilotWindowStart"), instant(value, "pilotWindowEnd"),
                    new ExactPilotBinding.Correlation(
                            text(value, "requestId"), text(value, "traceId"), text(value, "idempotencyKey")),
                    instant(value, "bindingCreatedAt"), instant(value, "bindingExpiresAt"), storedDigest
            );
            if (!binding.id().toString().equals(text(stored, "bindingId"))
                    || !binding.hasCanonicalDigest()
                    || !ExactPilotBindingCanonicalEncoder.encode(binding).equals(canonical)) {
                throw corrupted("stored canonical binding or digest was tampered");
            }
            return binding;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw corrupted("stored exact binding metadata is invalid", exception);
        }
    }

    private static void requireLockedSession(ExactPilotBinding binding, LiveSession lockedSession) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(lockedSession, "lockedSession must not be null");
        if (!binding.sessionId().equals(lockedSession.id())
                || binding.account().ownerId() != lockedSession.ownerId()
                || binding.account().exchangeAccountId() != lockedSession.exchangeAccountId()
                || binding.account().credentialReferenceId() != lockedSession.credentialReference()) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_SESSION_MISMATCH", "binding differs from the locked session identity");
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw corrupted("exact binding metadata serialization failed", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " is missing or invalid");
        }
        return value.textValue();
    }

    private static void requireText(JsonNode node, String field, String expected) {
        if (!expected.equals(text(node, field))) {
            throw new IllegalArgumentException(field + " is unsupported");
        }
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalArgumentException(field + " is missing or invalid");
        }
        return value.longValue();
    }

    private static int intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " is missing or invalid");
        }
        return value.intValue();
    }

    private static boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " is missing or invalid");
        }
        return value.booleanValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }

    private static Instant instant(JsonNode node, String field) {
        return Instant.parse(text(node, field));
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(text(node, field));
    }

    private static LiveControlException corrupted(String message) {
        return corrupted(message, null);
    }

    private static LiveControlException corrupted(String message, Throwable cause) {
        LiveControlException exception = new LiveControlException("EXACT_PILOT_BINDING_FACT_CORRUPTED", message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
