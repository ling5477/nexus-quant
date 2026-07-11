package com.guidinglight.nexusquant.validationreview.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEventType;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewException;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewSeverity;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewStateMachine;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionCommand;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionResult;
import com.guidinglight.nexusquant.validationreview.domain.port.ValidationReviewRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable validation review fact model 的 PostgreSQL JDBC adapter。
 *
 * <p>所有 case/event 查询都在 SQL 层携带 tenant/owner scope。Adapter 只写两张 GateV-1
 * 本地 review 表，不访问交易所、credential、Paper、Shadow、risk、account、order 或 ledger 写侧。
 */
@Repository
public class JdbcValidationReviewRepository implements ValidationReviewRepository {

    private static final int MAX_LIST_LIMIT = 200;

    private static final String CASE_SELECT = """
            SELECT id, tenant_key, owner_id, evidence_type, evidence_source,
                   evidence_anchor::text AS evidence_anchor, severity, state, title, summary,
                   version, created_by, created_at, updated_at, acknowledged_by, acknowledged_at,
                   escalated_by, escalated_at, resolved_by, resolved_at, closed_by, closed_at,
                   retention_until
            FROM validation_review_cases
            """;

    private static final String EVENT_SELECT = """
            SELECT e.id, e.review_case_id, e.tenant_key, e.event_type, e.from_state, e.to_state,
                   e.case_version, e.actor_id, e.idempotency_key, e.request_hash, e.request_id,
                   e.trace_id, e.metadata::text AS metadata, e.created_at
            FROM validation_review_events e
            JOIN validation_review_cases c
              ON c.id = e.review_case_id AND c.tenant_key = e.tenant_key
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ValidationReviewStateMachine stateMachine;

    /**
     * 创建 production adapter。
     *
     * @param jdbcTemplate PostgreSQL JDBC template
     * @param objectMapper 仅用于脱敏 JSONB 序列化和反序列化
     */
    @Autowired
    public JdbcValidationReviewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, new ValidationReviewStateMachine());
    }

    JdbcValidationReviewRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ValidationReviewStateMachine stateMachine
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public ValidationReviewCase createCase(ValidationReviewCase reviewCase) {
        Objects.requireNonNull(reviewCase, "reviewCase must not be null");
        if (reviewCase.state() != ValidationReviewState.OPEN || reviewCase.version() != 0) {
            throw new IllegalArgumentException("new review case must be OPEN with version 0");
        }
        jdbcTemplate.update(
                """
                        INSERT INTO validation_review_cases (
                            id, tenant_key, owner_id, evidence_type, evidence_source, evidence_anchor,
                            severity, state, title, summary, version, created_by, created_at, updated_at,
                            acknowledged_by, acknowledged_at, escalated_by, escalated_at, resolved_by,
                            resolved_at, closed_by, closed_at, retention_until
                        ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                reviewCase.id(),
                reviewCase.tenantKey(),
                reviewCase.ownerId(),
                reviewCase.evidenceType(),
                reviewCase.evidenceSource(),
                writeJson(reviewCase.evidenceAnchor()),
                reviewCase.severity().name(),
                reviewCase.state().name(),
                reviewCase.title(),
                reviewCase.summary(),
                reviewCase.version(),
                reviewCase.createdBy(),
                timestamp(reviewCase.createdAt()),
                timestamp(reviewCase.updatedAt()),
                reviewCase.acknowledgedBy(),
                timestamp(reviewCase.acknowledgedAt()),
                reviewCase.escalatedBy(),
                timestamp(reviewCase.escalatedAt()),
                reviewCase.resolvedBy(),
                timestamp(reviewCase.resolvedAt()),
                reviewCase.closedBy(),
                timestamp(reviewCase.closedAt()),
                timestamp(reviewCase.retentionUntil())
        );
        return reviewCase;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ValidationReviewCase> findOwnedCase(String tenantKey, long ownerId, UUID reviewCaseId) {
        requireScope(tenantKey, ownerId, reviewCaseId);
        return first(jdbcTemplate.query(
                CASE_SELECT + " WHERE tenant_key = ? AND owner_id = ? AND id = ?",
                caseRowMapper(),
                tenantKey,
                ownerId,
                reviewCaseId
        ));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ValidationReviewCase> findTenantCase(String tenantKey, UUID reviewCaseId) {
        requireTenantCase(tenantKey, reviewCaseId);
        return first(jdbcTemplate.query(
                CASE_SELECT + " WHERE tenant_key = ? AND id = ?",
                caseRowMapper(),
                tenantKey,
                reviewCaseId
        ));
    }

    /** {@inheritDoc} */
    @Override
    public List<ValidationReviewCase> listOwnedCases(String tenantKey, long ownerId, int limit) {
        ValidationReviewCase.requireTenant(tenantKey);
        ValidationReviewCase.requirePositive(ownerId, "ownerId");
        int boundedLimit = requireLimit(limit);
        return jdbcTemplate.query(
                CASE_SELECT + " WHERE tenant_key = ? AND owner_id = ? ORDER BY updated_at DESC, id DESC LIMIT ?",
                caseRowMapper(),
                tenantKey,
                ownerId,
                boundedLimit
        );
    }

    /** {@inheritDoc} */
    @Override
    public List<ValidationReviewCase> listTenantCases(String tenantKey, int limit) {
        ValidationReviewCase.requireTenant(tenantKey);
        int boundedLimit = requireLimit(limit);
        return jdbcTemplate.query(
                CASE_SELECT + " WHERE tenant_key = ? ORDER BY updated_at DESC, id DESC LIMIT ?",
                caseRowMapper(),
                tenantKey,
                boundedLimit
        );
    }

    /** {@inheritDoc} */
    @Override
    public List<ValidationReviewEvent> listOwnedEvents(
            String tenantKey,
            long ownerId,
            UUID reviewCaseId,
            int limit
    ) {
        requireScope(tenantKey, ownerId, reviewCaseId);
        return jdbcTemplate.query(
                EVENT_SELECT + """
                        WHERE e.tenant_key = ? AND c.owner_id = ? AND e.review_case_id = ?
                        ORDER BY e.created_at ASC, e.id ASC LIMIT ?
                        """,
                eventRowMapper(),
                tenantKey,
                ownerId,
                reviewCaseId,
                requireLimit(limit)
        );
    }

    /** {@inheritDoc} */
    @Override
    public List<ValidationReviewEvent> listTenantEvents(String tenantKey, UUID reviewCaseId, int limit) {
        requireTenantCase(tenantKey, reviewCaseId);
        return jdbcTemplate.query(
                EVENT_SELECT + """
                        WHERE e.tenant_key = ? AND e.review_case_id = ?
                        ORDER BY e.created_at ASC, e.id ASC LIMIT ?
                        """,
                eventRowMapper(),
                tenantKey,
                reviewCaseId,
                requireLimit(limit)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ValidationReviewEvent> findOwnedEventByIdempotencyKey(
            String tenantKey,
            long ownerId,
            UUID reviewCaseId,
            String idempotencyKey
    ) {
        requireScope(tenantKey, ownerId, reviewCaseId);
        ValidationReviewCase.requireText(idempotencyKey, "idempotencyKey", 128);
        return first(jdbcTemplate.query(
                EVENT_SELECT + """
                        WHERE e.tenant_key = ? AND c.owner_id = ? AND e.review_case_id = ?
                          AND e.idempotency_key = ?
                        """,
                eventRowMapper(),
                tenantKey,
                ownerId,
                reviewCaseId,
                idempotencyKey
        ));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ValidationReviewEvent> findTenantEventByIdempotencyKey(
            String tenantKey,
            UUID reviewCaseId,
            String idempotencyKey
    ) {
        requireTenantCase(tenantKey, reviewCaseId);
        ValidationReviewCase.requireText(idempotencyKey, "idempotencyKey", 128);
        return first(jdbcTemplate.query(
                EVENT_SELECT + """
                        WHERE e.tenant_key = ? AND e.review_case_id = ? AND e.idempotency_key = ?
                        """,
                eventRowMapper(),
                tenantKey,
                reviewCaseId,
                idempotencyKey
        ));
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public ValidationReviewTransitionResult transitionOwned(ValidationReviewTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.actorId() != command.ownerId()) {
            throw forbidden(command);
        }
        return transition(command, true);
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public ValidationReviewTransitionResult transitionInTenant(ValidationReviewTransitionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return transition(command, false);
    }

    private ValidationReviewTransitionResult transition(
            ValidationReviewTransitionCommand command,
            boolean ownerScoped
    ) {
        ValidationReviewCase current = lockCase(command, ownerScoped);
        if (current.ownerId() != command.ownerId()) {
            throw notFound(command);
        }
        Optional<ValidationReviewEvent> existing = ownerScoped
                ? findOwnedEventByIdempotencyKey(
                        command.tenantKey(),
                        command.ownerId(),
                        command.reviewCaseId(),
                        command.idempotencyKey())
                : findTenantEventByIdempotencyKey(
                        command.tenantKey(),
                        command.reviewCaseId(),
                        command.idempotencyKey());
        if (existing.isPresent()) {
            ValidationReviewEvent event = existing.get();
            if (!event.requestHash().equals(command.requestHash())) {
                throw new ValidationReviewException(
                        "IDEMPOTENCY_KEY_REUSED",
                        "idempotency key was already used by a different review transition request",
                        command.reviewCaseId(),
                        current.state(),
                        command.targetState()
                );
            }
            return new ValidationReviewTransitionResult(snapshotAtEvent(current, event), event, true);
        }
        if (current.version() != command.expectedVersion()) {
            throw new ValidationReviewException(
                    "REVIEW_CASE_VERSION_CONFLICT",
                    "validation review case version does not match expectedVersion",
                    command.reviewCaseId(),
                    current.state(),
                    command.targetState()
            );
        }

        ValidationReviewCase updated = stateMachine.transition(
                current,
                command.targetState(),
                command.actorId(),
                command.occurredAt()
        );
        int updatedRows = updateCase(updated, current, ownerScoped);
        if (updatedRows != 1) {
            throw new ValidationReviewException(
                    "REVIEW_CASE_VERSION_CONFLICT",
                    "validation review case changed concurrently",
                    command.reviewCaseId(),
                    current.state(),
                    command.targetState()
            );
        }

        ValidationReviewEvent event = new ValidationReviewEvent(
                UUID.randomUUID(),
                updated.id(),
                updated.tenantKey(),
                ValidationReviewEventType.fromTargetState(updated.state()),
                current.state(),
                updated.state(),
                updated.version(),
                command.actorId(),
                command.idempotencyKey(),
                command.requestHash(),
                command.requestId(),
                command.traceId(),
                command.metadata(),
                command.occurredAt()
        );
        appendEvent(event);
        return new ValidationReviewTransitionResult(updated, event, false);
    }

    /**
     * 重建首次 accepted transition 的 case snapshot。
     *
     * <p>Why：幂等 key 可能在 case 已继续流转后重放；返回最新 case 会把首次 event 与后续状态
     * 混为一个结果。Lifecycle 字段只由状态机单向追加，因此可从当前事实安全裁剪后续字段，
     * 且不需要新增 snapshot 表或修改 migration。
     */
    private static ValidationReviewCase snapshotAtEvent(
            ValidationReviewCase current,
            ValidationReviewEvent event
    ) {
        if (!current.id().equals(event.reviewCaseId())
                || !current.tenantKey().equals(event.tenantKey())
                || current.version() < event.caseVersion()) {
            throw new IllegalStateException("idempotency event does not belong to current review case history");
        }
        boolean keepAcknowledged = event.toState() != ValidationReviewState.ESCALATED
                || current.acknowledgedAt() != null;
        boolean keepEscalated = event.toState() == ValidationReviewState.ESCALATED
                || event.toState() == ValidationReviewState.RESOLVED
                || event.toState() == ValidationReviewState.CLOSED;
        boolean keepResolved = event.toState() == ValidationReviewState.RESOLVED
                || event.toState() == ValidationReviewState.CLOSED;
        boolean keepClosed = event.toState() == ValidationReviewState.CLOSED;
        return new ValidationReviewCase(
                current.id(),
                current.tenantKey(),
                current.ownerId(),
                current.evidenceType(),
                current.evidenceSource(),
                current.evidenceAnchor(),
                current.severity(),
                event.toState(),
                current.title(),
                current.summary(),
                event.caseVersion(),
                current.createdBy(),
                current.createdAt(),
                event.createdAt(),
                keepAcknowledged ? current.acknowledgedBy() : null,
                keepAcknowledged ? current.acknowledgedAt() : null,
                keepEscalated ? current.escalatedBy() : null,
                keepEscalated ? current.escalatedAt() : null,
                keepResolved ? current.resolvedBy() : null,
                keepResolved ? current.resolvedAt() : null,
                keepClosed ? current.closedBy() : null,
                keepClosed ? current.closedAt() : null,
                current.retentionUntil()
        );
    }

    private ValidationReviewCase lockCase(ValidationReviewTransitionCommand command, boolean ownerScoped) {
        List<ValidationReviewCase> rows = ownerScoped
                ? jdbcTemplate.query(
                        CASE_SELECT + " WHERE tenant_key = ? AND owner_id = ? AND id = ? FOR UPDATE",
                        caseRowMapper(),
                        command.tenantKey(),
                        command.ownerId(),
                        command.reviewCaseId())
                : jdbcTemplate.query(
                        CASE_SELECT + " WHERE tenant_key = ? AND id = ? FOR UPDATE",
                        caseRowMapper(),
                        command.tenantKey(),
                        command.reviewCaseId());
        return first(rows).orElseThrow(() -> notFound(command));
    }

    private int updateCase(
            ValidationReviewCase updated,
            ValidationReviewCase current,
            boolean ownerScoped
    ) {
        String ownerPredicate = ownerScoped ? " AND owner_id = ?" : "";
        String sql = """
                UPDATE validation_review_cases
                SET state = ?, version = ?, updated_at = ?,
                    acknowledged_by = ?, acknowledged_at = ?,
                    escalated_by = ?, escalated_at = ?,
                    resolved_by = ?, resolved_at = ?,
                    closed_by = ?, closed_at = ?
                WHERE id = ? AND tenant_key = ? AND state = ? AND version = ?
                """ + ownerPredicate;
        Object[] base = {
                updated.state().name(),
                updated.version(),
                timestamp(updated.updatedAt()),
                updated.acknowledgedBy(),
                timestamp(updated.acknowledgedAt()),
                updated.escalatedBy(),
                timestamp(updated.escalatedAt()),
                updated.resolvedBy(),
                timestamp(updated.resolvedAt()),
                updated.closedBy(),
                timestamp(updated.closedAt()),
                updated.id(),
                updated.tenantKey(),
                current.state().name(),
                current.version()
        };
        if (!ownerScoped) {
            return jdbcTemplate.update(sql, base);
        }
        Object[] scoped = new Object[base.length + 1];
        System.arraycopy(base, 0, scoped, 0, base.length);
        scoped[base.length] = updated.ownerId();
        return jdbcTemplate.update(sql, scoped);
    }

    private void appendEvent(ValidationReviewEvent event) {
        jdbcTemplate.update(
                """
                        INSERT INTO validation_review_events (
                            id, review_case_id, tenant_key, event_type, from_state, to_state,
                            case_version, actor_id, idempotency_key, request_hash, request_id,
                            trace_id, metadata, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                        """,
                event.id(),
                event.reviewCaseId(),
                event.tenantKey(),
                event.eventType().name(),
                event.fromState().name(),
                event.toState().name(),
                event.caseVersion(),
                event.actorId(),
                event.idempotencyKey(),
                event.requestHash(),
                event.requestId(),
                event.traceId(),
                writeJson(event.metadata()),
                timestamp(event.createdAt())
        );
    }

    private RowMapper<ValidationReviewCase> caseRowMapper() {
        return this::mapCase;
    }

    private ValidationReviewCase mapCase(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ValidationReviewCase(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("tenant_key"),
                resultSet.getLong("owner_id"),
                resultSet.getString("evidence_type"),
                resultSet.getString("evidence_source"),
                readJson(resultSet.getString("evidence_anchor")),
                ValidationReviewSeverity.valueOf(resultSet.getString("severity")),
                ValidationReviewState.valueOf(resultSet.getString("state")),
                resultSet.getString("title"),
                resultSet.getString("summary"),
                resultSet.getLong("version"),
                resultSet.getLong("created_by"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                nullableLong(resultSet, "acknowledged_by"),
                instant(resultSet, "acknowledged_at"),
                nullableLong(resultSet, "escalated_by"),
                instant(resultSet, "escalated_at"),
                nullableLong(resultSet, "resolved_by"),
                instant(resultSet, "resolved_at"),
                nullableLong(resultSet, "closed_by"),
                instant(resultSet, "closed_at"),
                instant(resultSet, "retention_until")
        );
    }

    private RowMapper<ValidationReviewEvent> eventRowMapper() {
        return this::mapEvent;
    }

    private ValidationReviewEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ValidationReviewEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("review_case_id", UUID.class),
                resultSet.getString("tenant_key"),
                ValidationReviewEventType.valueOf(resultSet.getString("event_type")),
                ValidationReviewState.valueOf(resultSet.getString("from_state")),
                ValidationReviewState.valueOf(resultSet.getString("to_state")),
                resultSet.getLong("case_version"),
                resultSet.getLong("actor_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("request_hash"),
                resultSet.getString("request_id"),
                resultSet.getString("trace_id"),
                readJson(resultSet.getString("metadata")),
                instant(resultSet, "created_at")
        );
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to read validation review JSONB", ex);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to write validation review JSONB", ex);
        }
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static int requireLimit(int limit) {
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        return limit;
    }

    private static void requireScope(String tenantKey, long ownerId, UUID reviewCaseId) {
        ValidationReviewCase.requireTenant(tenantKey);
        ValidationReviewCase.requirePositive(ownerId, "ownerId");
        Objects.requireNonNull(reviewCaseId, "reviewCaseId must not be null");
    }

    private static void requireTenantCase(String tenantKey, UUID reviewCaseId) {
        ValidationReviewCase.requireTenant(tenantKey);
        Objects.requireNonNull(reviewCaseId, "reviewCaseId must not be null");
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private static ValidationReviewException notFound(ValidationReviewTransitionCommand command) {
        return new ValidationReviewException(
                "REVIEW_CASE_NOT_FOUND",
                "validation review case was not found in the requested scope",
                command.reviewCaseId(),
                null,
                command.targetState()
        );
    }

    private static ValidationReviewException forbidden(ValidationReviewTransitionCommand command) {
        return new ValidationReviewException(
                "REVIEW_ACTION_FORBIDDEN",
                "operator actor must match review case owner",
                command.reviewCaseId(),
                null,
                command.targetState()
        );
    }
}
