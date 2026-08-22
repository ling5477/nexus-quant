package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeAuthorizationCommand;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotScopeAuthorization;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotScopeAuthorizationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotScopeAuthorizationRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 复用 V39 append-only session events 的 exact operator scope authorization adapter。 */
@Repository
public class JdbcExactPilotScopeAuthorizationRepository implements ExactPilotScopeAuthorizationRepository {

    static final String AUTHORIZE_COMMAND = "AUTHORIZE_EXACT_PILOT_SCOPE";
    static final String APPROVE_COMMAND = "APPROVE_EXACT_PILOT_SCOPE";
    private static final String EVENT_SCHEMA = "exact-pilot-scope-authorization-event.v1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LiveControlRepository liveControlRepository;

    public JdbcExactPilotScopeAuthorizationRepository(
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
    public ExactPilotScopeAuthorization recordApproved(
            ExactPilotScopeAuthorization authorization,
            LiveSession lockedSession,
            ExactPilotBinding.Correlation creatorCorrelation,
            ExactPilotBinding.Correlation approverCorrelation,
            Instant approvedAt,
            Instant expiresAt
    ) {
        requireSession(authorization, lockedSession);
        Objects.requireNonNull(creatorCorrelation, "creatorCorrelation must not be null");
        Objects.requireNonNull(approverCorrelation, "approverCorrelation must not be null");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!authorization.hasCanonicalDigest() || !expiresAt.isAfter(approvedAt)
                || expiresAt.isAfter(authorization.pilotWindowEnd())) {
            throw rejected("exact scope authorization facts are invalid");
        }
        List<StoredEvent> existing = findEvents(authorization.sessionId(), authorization.bindingId());
        if (!existing.isEmpty()) {
            requireStoredApproval(
                    existing, authorization, creatorCorrelation, approverCorrelation, approvedAt, expiresAt);
            return authorization;
        }
        String metadata = metadata(authorization, approvedAt, expiresAt);
        appendEvent(
                authorization, lockedSession, AUTHORIZE_COMMAND, authorization.creatorPrincipal(),
                creatorCorrelation, "EXACT_PILOT_SCOPE_AUTHORIZED", metadata, approvedAt, expiresAt);
        appendEvent(
                authorization, lockedSession, APPROVE_COMMAND, authorization.approverPrincipal(),
                approverCorrelation, ExactPilotScopeAuthorizationCommand.REQUIRED_REASON,
                metadata, approvedAt, expiresAt);
        return authorization;
    }

    @Override
    public void requireApproved(
            long creatorPrincipal,
            ExactPilotBindingCommand command,
            ExactPilotBinding.AuthoritativeFacts currentFacts,
            Instant decisionAt
    ) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(currentFacts, "currentFacts must not be null");
        Objects.requireNonNull(decisionAt, "decisionAt must not be null");
        List<StoredEvent> events = findEvents(command.sessionId(), command.bindingId());
        if (events.size() != 2) {
            throw rejected("exact scope authorization and independent approval are required");
        }
        StoredEvent creator = event(events, AUTHORIZE_COMMAND);
        StoredEvent approver = event(events, APPROVE_COMMAND);
        if (creator.actorId() != creatorPrincipal || approver.actorId() == creatorPrincipal) {
            throw rejected("creator and approver separation is invalid");
        }
        Metadata creatorMetadata = parseMetadata(creator.metadataJson());
        Metadata approverMetadata = parseMetadata(approver.metadataJson());
        if (!creatorMetadata.equals(approverMetadata)
                || creatorMetadata.creatorPrincipal() != creatorPrincipal
                || creatorMetadata.approverPrincipal() != approver.actorId()
                || !"EXACT_PILOT_SCOPE_AUTHORIZED".equals(creator.reasonCode())
                || !ExactPilotScopeAuthorizationCommand.REQUIRED_REASON.equals(approver.reasonCode())
                || !creator.createdAt().equals(creatorMetadata.approvedAt())
                || !approver.createdAt().equals(creatorMetadata.approvedAt())
                || !decisionAt.isBefore(creatorMetadata.expiresAt())
                || creatorMetadata.expiresAt().isAfter(command.bindingExpiresAt())
                || !ExactPilotScopeAuthorizationCommand.REQUIRED_REASON.equals(creatorMetadata.reason())
                || !creator.hasCanonicalPayload(
                creatorMetadata.scopeDigest(), creatorMetadata.expiresAt())
                || !approver.hasCanonicalPayload(
                creatorMetadata.scopeDigest(), creatorMetadata.expiresAt())) {
            throw rejected("exact scope approval facts are expired or inconsistent");
        }
        ExactPilotScopeAuthorization expected = ExactPilotScopeAuthorization.approved(
                currentFacts, command, creatorPrincipal, approver.actorId());
        if (!expected.scopeDigest().equals(creatorMetadata.scopeDigest())
                || !ExactPilotScopeAuthorizationCanonicalEncoder.encode(expected)
                .equals(creatorMetadata.canonicalScope())) {
            throw rejected("exact scope changed after independent approval");
        }
    }

    private void appendEvent(
            ExactPilotScopeAuthorization authorization,
            LiveSession session,
            String command,
            long actorId,
            ExactPilotBinding.Correlation correlation,
            String reason,
            String metadata,
            Instant createdAt,
            Instant expiresAt
    ) {
        liveControlRepository.appendSessionEvent(new LiveSessionEvent(
                UUID.randomUUID(), session.id(), 1, session.state(), session.state(), command, actorId,
                correlation.requestId(), correlation.traceId(), reason, correlation.idempotencyKey(),
                ExactPilotScopeAuthorizationCanonicalEncoder.eventDigest(
                        command, authorization.scopeDigest(), correlation.idempotencyKey(), expiresAt),
                metadata, createdAt
        ));
    }

    private List<StoredEvent> findEvents(UUID sessionId, UUID bindingId) {
        return jdbcTemplate.query("""
                SELECT command, actor_id, request_id, trace_id, reason_code, idempotency_key,
                       command_payload_hash, metadata::TEXT, created_at
                FROM live_session_events
                WHERE session_id = ?
                  AND command IN (?, ?)
                  AND metadata ->> 'bindingId' = ?
                ORDER BY sequence_no
                """, this::mapEvent, sessionId, AUTHORIZE_COMMAND, APPROVE_COMMAND, bindingId.toString());
    }

    private void requireStoredApproval(
            List<StoredEvent> events,
            ExactPilotScopeAuthorization authorization,
            ExactPilotBinding.Correlation creatorCorrelation,
            ExactPilotBinding.Correlation approverCorrelation,
            Instant approvedAt,
            Instant expiresAt
    ) {
        if (events.size() != 2) {
            throw rejected("stored exact scope approval event cardinality is invalid");
        }
        StoredEvent creator = event(events, AUTHORIZE_COMMAND);
        StoredEvent approver = event(events, APPROVE_COMMAND);
        Metadata expected = new Metadata(
                authorization.bindingId(), authorization.scopeDigest(),
                ExactPilotScopeAuthorizationCanonicalEncoder.encode(authorization),
                authorization.creatorPrincipal(), authorization.approverPrincipal(),
                ExactPilotScopeAuthorizationCommand.REQUIRED_REASON, approvedAt, expiresAt);
        if (creator.actorId() != authorization.creatorPrincipal()
                || approver.actorId() != authorization.approverPrincipal()
                || !"EXACT_PILOT_SCOPE_AUTHORIZED".equals(creator.reasonCode())
                || !ExactPilotScopeAuthorizationCommand.REQUIRED_REASON.equals(approver.reasonCode())
                || !creator.correlation().equals(creatorCorrelation)
                || !approver.correlation().equals(approverCorrelation)
                || !creator.createdAt().equals(approvedAt)
                || !approver.createdAt().equals(approvedAt)
                || !expected.equals(parseMetadata(creator.metadataJson()))
                || !expected.equals(parseMetadata(approver.metadataJson()))
                || !creator.hasCanonicalPayload(expected.scopeDigest(), expected.expiresAt())
                || !approver.hasCanonicalPayload(expected.scopeDigest(), expected.expiresAt())) {
            throw rejected("stored exact scope approval conflicts with the requested facts");
        }
    }

    private String metadata(
            ExactPilotScopeAuthorization authorization,
            Instant approvedAt,
            Instant expiresAt
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", EVENT_SCHEMA);
        node.put("bindingId", authorization.bindingId().toString());
        node.put("scopeDigest", authorization.scopeDigest());
        node.put("canonicalScope", ExactPilotScopeAuthorizationCanonicalEncoder.encode(authorization));
        node.put("creatorPrincipal", authorization.creatorPrincipal());
        node.put("approverPrincipal", authorization.approverPrincipal());
        node.put("approvalReason", ExactPilotScopeAuthorizationCommand.REQUIRED_REASON);
        node.put("approvedAt", approvedAt.toString());
        node.put("expiresAt", expiresAt.toString());
        node.put("tradingAuthorized", false);
        node.put("liveAuthorized", false);
        return writeJson(node);
    }

    private Metadata parseMetadata(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!EVENT_SCHEMA.equals(text(node, "schemaVersion"))
                    || booleanValue(node, "tradingAuthorized")
                    || booleanValue(node, "liveAuthorized")) {
                throw new IllegalArgumentException("authorization metadata schema is invalid");
            }
            String canonicalScope = text(node, "canonicalScope");
            String scopeDigest = text(node, "scopeDigest");
            if (!scopeDigest.equals(ExactPilotScopeAuthorizationCanonicalEncoder
                    .digestCanonical(canonicalScope))) {
                throw new IllegalArgumentException("authorization canonical scope digest mismatch");
            }
            return new Metadata(
                    UUID.fromString(text(node, "bindingId")), scopeDigest, canonicalScope,
                    longValue(node, "creatorPrincipal"), longValue(node, "approverPrincipal"),
                    text(node, "approvalReason"), Instant.parse(text(node, "approvedAt")),
                    Instant.parse(text(node, "expiresAt")));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw rejected("stored exact scope authorization metadata is invalid", exception);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw rejected("exact scope authorization metadata serialization failed", exception);
        }
    }

    private StoredEvent mapEvent(ResultSet row, int rowNumber) throws SQLException {
        return new StoredEvent(
                row.getString("command"), row.getLong("actor_id"), row.getString("request_id"),
                row.getString("trace_id"), row.getString("reason_code"), row.getString("idempotency_key"),
                row.getString("command_payload_hash"), row.getString("metadata"),
                row.getTimestamp("created_at").toInstant());
    }

    private static StoredEvent event(List<StoredEvent> events, String command) {
        return events.stream().filter(value -> command.equals(value.command())).findFirst()
                .orElseThrow(() -> rejected("required exact scope authorization event is missing"));
    }

    private static void requireSession(
            ExactPilotScopeAuthorization authorization,
            LiveSession session
    ) {
        Objects.requireNonNull(authorization, "authorization must not be null");
        Objects.requireNonNull(session, "lockedSession must not be null");
        if (!authorization.sessionId().equals(session.id())
                || authorization.creatorPrincipal() != session.ownerId()
                || authorization.account().exchangeAccountId() != session.exchangeAccountId()
                || authorization.account().credentialReferenceId() != session.credentialReference()) {
            throw rejected("exact scope authorization differs from the locked session");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " is missing or invalid");
        }
        return value.textValue();
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalArgumentException(field + " is missing or invalid");
        }
        return value.longValue();
    }

    private static boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " is missing or invalid");
        }
        return value.booleanValue();
    }

    private static LiveControlException rejected(String message) {
        return rejected(message, null);
    }

    private static LiveControlException rejected(String message, Throwable cause) {
        LiveControlException exception = new LiveControlException(
                "EXACT_PILOT_SCOPE_APPROVAL_REJECTED", message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private record StoredEvent(
            String command,
            long actorId,
            String requestId,
            String traceId,
            String reasonCode,
            String idempotencyKey,
            String commandPayloadHash,
            String metadataJson,
            Instant createdAt
    ) {
        private ExactPilotBinding.Correlation correlation() {
            return new ExactPilotBinding.Correlation(requestId, traceId, idempotencyKey);
        }

        private boolean hasCanonicalPayload(String scopeDigest, Instant expiresAt) {
            return commandPayloadHash.equals(ExactPilotScopeAuthorizationCanonicalEncoder.eventDigest(
                    command, scopeDigest, idempotencyKey, expiresAt));
        }
    }

    private record Metadata(
            UUID bindingId,
            String scopeDigest,
            String canonicalScope,
            long creatorPrincipal,
            long approverPrincipal,
            String reason,
            Instant approvedAt,
            Instant expiresAt
    ) {
    }
}
