package com.guidinglight.nexusquant.validationreview.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.audit.infra.jdbc.JdbcAuditLogRepository;
import com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewAction;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewActor;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewOperationalAuditService;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewOperationsService;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewTransitionService;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCaseQuery;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewException;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewSeverity;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionCommand;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GateV-1 repository 与 migration 的真实 PostgreSQL integration test。
 *
 * <p>测试只连接显式提供的本地/CI PostgreSQL，使用脱敏 fixture，并只写 users 与两张 review 表；
 * 不启动应用、scheduler、交易 adapter、credential service 或任何 Paper/Shadow/risk/ledger 写侧。
 */
class ValidationReviewRepositoryPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.postgres.smoke.required";
    private static final String URL_PROPERTY = "nq.postgres.smoke.url";
    private static final String USER_PROPERTY = "nq.postgres.smoke.user";
    private static final String PASSWORD_PROPERTY = "nq.postgres.smoke.password";

    @Test
    void shouldVerifySchemaScopeLockingIdempotencyAndAtomicEvents() throws Exception {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "PostgreSQL validation review integration is disabled");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(config.url());
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        JdbcValidationReviewRepository repository = new JdbcValidationReviewRepository(
                jdbcTemplate,
                new ObjectMapper()
        );

        assertMigratedSchema(jdbcTemplate);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long ownerOne = insertUser(jdbcTemplate, "gatev_owner_one_" + suffix);
        long ownerTwo = insertUser(jdbcTemplate, "gatev_owner_two_" + suffix);
        UUID caseOneId = UUID.randomUUID();
        UUID caseTwoId = UUID.randomUUID();
        UUID otherTenantCaseId = UUID.randomUUID();
        UUID atomicCaseId = UUID.randomUUID();
        UUID concurrentCaseId = UUID.randomUUID();
        UUID auditCaseId = UUID.randomUUID();
        UUID auditRollbackCaseId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2026-07-11T01:00:00Z");

        try {
            repository.createCase(openCase(caseOneId, ownerOne, "source-one", baseTime));
            repository.createCase(openCase(caseTwoId, ownerTwo, "source-two", baseTime.plusSeconds(1)));
            insertOtherTenantCase(jdbcTemplate, otherTenantCaseId, ownerTwo, baseTime.plusSeconds(2));
            assertIllegalAcceptedEventRejected(jdbcTemplate, caseOneId, ownerOne, baseTime, suffix);

            assertEquals(caseOneId, repository.findOwnedCase(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerOne,
                    caseOneId).orElseThrow().id());
            assertTrue(repository.findOwnedCase(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerTwo,
                    caseOneId).isEmpty());
            assertTrue(repository.findTenantCase(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    otherTenantCaseId).isEmpty());
            assertEquals(1, repository.listOwnedCases(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerOne,
                    10).size());
            assertEquals(2, repository.listTenantCases(ValidationReviewCase.LOCAL_TENANT_KEY, 10).size());
            assertEquals(2, repository.listTenantCases(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    new ValidationReviewCaseQuery(
                            ValidationReviewState.OPEN,
                            ValidationReviewSeverity.WARNING,
                            null,
                            100,
                            0
                    )).size());
            assertEquals(caseOneId, repository.listTenantCases(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    new ValidationReviewCaseQuery(null, null, null, 1, 1)
            ).getFirst().id());
            assertEquals(1, repository.listTenantCases(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    new ValidationReviewCaseQuery(null, null, ownerOne, 100, 0)
            ).size());
            assertTrue(repository.listTenantCases(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    new ValidationReviewCaseQuery(null, ValidationReviewSeverity.HIGH, null, 100, 0)
            ).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> repository.listTenantCases("OTHER_TENANT", 10));
            assertThrows(IllegalArgumentException.class, () -> repository.listOwnedCases(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerOne,
                    201));

            ValidationReviewTransitionCommand acknowledge = command(
                    caseOneId,
                    ownerOne,
                    ownerOne,
                    ValidationReviewState.ACKNOWLEDGED,
                    0,
                    "idem-ack-" + suffix,
                    "sha256-ack-" + suffix,
                    baseTime.plusSeconds(60)
            );
            ValidationReviewTransitionResult first = transactions.execute(
                    status -> repository.transitionOwned(acknowledge)
            );
            assertNotNull(first);
            assertFalse(first.idempotentReplay());
            assertEquals(ValidationReviewState.ACKNOWLEDGED, first.reviewCase().state());
            assertEquals(1, first.reviewCase().version());
            assertEquals(1, repository.listOwnedEvents(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerOne,
                    caseOneId,
                    20).size());

            ValidationReviewTransitionResult replay = transactions.execute(
                    status -> repository.transitionOwned(acknowledge)
            );
            assertNotNull(replay);
            assertTrue(replay.idempotentReplay());
            assertEquals(first.event().id(), replay.event().id());
            assertEquals(1, repository.listOwnedEvents(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerOne,
                    caseOneId,
                    20).size());

            ValidationReviewException reused = assertThrows(
                    ValidationReviewException.class,
                    () -> transactions.execute(status -> repository.transitionOwned(new ValidationReviewTransitionCommand(
                            acknowledge.reviewCaseId(),
                            acknowledge.tenantKey(),
                            acknowledge.ownerId(),
                            acknowledge.targetState(),
                            acknowledge.expectedVersion(),
                            acknowledge.actorId(),
                            acknowledge.idempotencyKey(),
                            "sha256-different-" + suffix,
                            acknowledge.requestId(),
                            acknowledge.traceId(),
                            acknowledge.metadata(),
                            acknowledge.occurredAt()
                    )))
            );
            assertEquals("IDEMPOTENCY_KEY_REUSED", reused.errorCode());

            ValidationReviewException conflict = assertThrows(
                    ValidationReviewException.class,
                    () -> transactions.execute(status -> repository.transitionOwned(command(
                            caseOneId,
                            ownerOne,
                            ownerOne,
                            ValidationReviewState.ESCALATED,
                            0,
                            "idem-conflict-" + suffix,
                            "sha256-conflict-" + suffix,
                            baseTime.plusSeconds(120)
                    )))
            );
            assertEquals("REVIEW_CASE_VERSION_CONFLICT", conflict.errorCode());

            ValidationReviewException illegal = assertThrows(
                    ValidationReviewException.class,
                    () -> transactions.execute(status -> repository.transitionOwned(command(
                            caseOneId,
                            ownerOne,
                            ownerOne,
                            ValidationReviewState.CLOSED,
                            1,
                            "idem-illegal-" + suffix,
                            "sha256-illegal-" + suffix,
                            baseTime.plusSeconds(120)
                    )))
            );
            assertEquals("REVIEW_STATE_TRANSITION_INVALID", illegal.errorCode());
            assertEquals(1, repository.listOwnedEvents(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerOne,
                    caseOneId,
                    20).size());

            ValidationReviewException forbidden = assertThrows(
                    ValidationReviewException.class,
                    () -> transactions.execute(status -> repository.transitionOwned(command(
                            caseOneId,
                            ownerOne,
                            ownerTwo,
                            ValidationReviewState.ESCALATED,
                            1,
                            "idem-forbidden-" + suffix,
                            "sha256-forbidden-" + suffix,
                            baseTime.plusSeconds(120)
                    )))
            );
            assertEquals("REVIEW_ACTION_FORBIDDEN", forbidden.errorCode());

            String sharedKey = "idem-shared-" + suffix;
            ValidationReviewTransitionResult caseOneEscalated = transactions.execute(
                    status -> repository.transitionOwned(command(
                            caseOneId,
                            ownerOne,
                            ownerOne,
                            ValidationReviewState.ESCALATED,
                            1,
                            sharedKey,
                            "sha256-shared-one-" + suffix,
                            baseTime.plusSeconds(180)))
            );
            ValidationReviewTransitionResult caseTwoEscalated = transactions.execute(
                    status -> repository.transitionInTenant(command(
                            caseTwoId,
                            ownerTwo,
                            ownerOne,
                            ValidationReviewState.ESCALATED,
                            0,
                            sharedKey,
                            "sha256-shared-two-" + suffix,
                            baseTime.plusSeconds(181)))
            );
            assertNotNull(caseOneEscalated);
            assertNotNull(caseTwoEscalated);
            assertEquals(ValidationReviewState.ESCALATED, caseTwoEscalated.reviewCase().state());
            assertEquals(ownerOne, caseTwoEscalated.event().actorId());

            ValidationReviewTransitionResult lateReplay = transactions.execute(
                    status -> repository.transitionOwned(acknowledge)
            );
            assertNotNull(lateReplay);
            assertTrue(lateReplay.idempotentReplay());
            assertEquals(first.event().id(), lateReplay.event().id());
            assertEquals(ValidationReviewState.ACKNOWLEDGED, lateReplay.reviewCase().state());
            assertEquals(1, lateReplay.reviewCase().version());

            List<ValidationReviewEvent> orderedEvents = repository.listTenantEvents(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    caseOneId,
                    20
            );
            assertEquals(List.of(
                    ValidationReviewState.ACKNOWLEDGED,
                    ValidationReviewState.ESCALATED
            ), orderedEvents.stream().map(ValidationReviewEvent::toState).toList());
            assertEquals("reviewed", orderedEvents.getFirst().metadata().get("result").asText());

            assertAtomicRollbackOnEventFailure(
                    jdbcTemplate,
                    transactions,
                    repository,
                    atomicCaseId,
                    ownerOne,
                    baseTime.plusSeconds(300),
                    suffix
            );
            assertConcurrentIdempotency(
                    transactions,
                    repository,
                    concurrentCaseId,
                    ownerOne,
                    baseTime.plusSeconds(400),
                    suffix
            );
            assertOperationalAuditTransaction(
                    jdbcTemplate,
                    transactions,
                    repository,
                    auditCaseId,
                    auditRollbackCaseId,
                    ownerOne,
                    baseTime.plusSeconds(500),
                    suffix
            );
        } finally {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE trace_id IN (?, ?)",
                    "trc-audit-" + suffix, "trc-audit-rollback-" + suffix);
            jdbcTemplate.update(
                    "DELETE FROM validation_review_events WHERE review_case_id IN (?, ?, ?, ?, ?, ?, ?)",
                    caseOneId,
                    caseTwoId,
                    otherTenantCaseId,
                    atomicCaseId,
                    concurrentCaseId,
                    auditCaseId,
                    auditRollbackCaseId
            );
            jdbcTemplate.update(
                    "DELETE FROM validation_review_cases WHERE id IN (?, ?, ?, ?, ?, ?, ?)",
                    caseOneId,
                    caseTwoId,
                    otherTenantCaseId,
                    atomicCaseId,
                    concurrentCaseId,
                    auditCaseId,
                    auditRollbackCaseId
            );
            jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", ownerOne, ownerTwo);
        }
    }

    private static void assertOperationalAuditTransaction(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions,
            JdbcValidationReviewRepository repository,
            UUID auditCaseId,
            UUID auditRollbackCaseId,
            long ownerId,
            Instant baseTime,
            String suffix
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcAuditLogRepository auditRepository = new JdbcAuditLogRepository(jdbcTemplate, objectMapper);
        ValidationReviewOperationsService operations = new ValidationReviewOperationsService(
                repository,
                new ValidationReviewTransitionService(repository),
                auditRepository,
                new ValidationReviewOperationalAuditService(auditRepository),
                objectMapper
        );
        ValidationReviewActor operator = new ValidationReviewActor(ownerId, java.util.Set.of("OPERATOR"));
        repository.createCase(openCase(auditCaseId, ownerId, "audit-source", baseTime));

        ValidationReviewTransitionResult accepted = transactions.execute(status -> operations.transition(
                operator,
                auditCaseId,
                ValidationReviewAction.ACKNOWLEDGE,
                0L,
                "local audit review",
                JsonNodeFactory.instance.objectNode().put("result", "reviewed"),
                "idem-audit-" + suffix,
                "req-audit-" + suffix,
                "trc-audit-" + suffix
        ));
        assertNotNull(accepted);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_review_events WHERE review_case_id = ?",
                Integer.class,
                auditCaseId
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE trace_id = ? AND domain = 'VALIDATION_REVIEW'",
                Integer.class,
                "trc-audit-" + suffix
        ));

        repository.createCase(openCase(auditRollbackCaseId, ownerId, "audit-rollback-source", baseTime.plusSeconds(1)));
        AuditLogRepository failingAudit = (domain, action, actorId, traceId, detail) -> {
            throw new IllegalStateException("forced operational audit failure");
        };
        ValidationReviewOperationsService failingOperations = new ValidationReviewOperationsService(
                repository,
                new ValidationReviewTransitionService(repository),
                failingAudit,
                new ValidationReviewOperationalAuditService(auditRepository),
                objectMapper
        );
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> failingOperations.transition(
                operator,
                auditRollbackCaseId,
                ValidationReviewAction.ACKNOWLEDGE,
                0L,
                "rollback review",
                JsonNodeFactory.instance.objectNode(),
                "idem-audit-rollback-" + suffix,
                "req-audit-rollback-" + suffix,
                "trc-audit-rollback-" + suffix
        )));
        assertEquals(ValidationReviewState.OPEN, repository.findOwnedCase(
                ValidationReviewCase.LOCAL_TENANT_KEY,
                ownerId,
                auditRollbackCaseId
        ).orElseThrow().state());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_review_events WHERE review_case_id = ?",
                Integer.class,
                auditRollbackCaseId
        ));
    }

    private static void assertIllegalAcceptedEventRejected(
            JdbcTemplate jdbcTemplate,
            UUID caseId,
            long actorId,
            Instant createdAt,
            String suffix
    ) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                        INSERT INTO validation_review_events (
                            id, review_case_id, tenant_key, event_type, from_state, to_state,
                            case_version, actor_id, idempotency_key, request_hash, trace_id,
                            metadata, created_at
                        ) VALUES (?, ?, 'NQ_LOCAL', 'CLOSED', 'OPEN', 'CLOSED',
                                  1, ?, ?, ?, ?, '{}'::jsonb, ?)
                        """,
                UUID.randomUUID(),
                caseId,
                actorId,
                "idem-illegal-db-" + suffix,
                "sha256-illegal-db-" + suffix,
                "trace-illegal-db-" + suffix,
                Timestamp.from(createdAt.plusSeconds(30))
        ));
    }

    /**
     * 使用两个独立事务同时提交相同 command，验证 case row lock 会把重复请求串行化，
     * 最终只追加一个 accepted event，另一请求返回同一首次结果。
     */
    private static void assertConcurrentIdempotency(
            TransactionTemplate transactions,
            JdbcValidationReviewRepository repository,
            UUID caseId,
            long ownerId,
            Instant baseTime,
            String suffix
    ) throws Exception {
        repository.createCase(openCase(caseId, ownerId, "concurrent-source", baseTime));
        ValidationReviewTransitionCommand concurrentCommand = command(
                caseId,
                ownerId,
                ownerId,
                ValidationReviewState.ACKNOWLEDGED,
                0,
                "idem-concurrent-" + suffix,
                "sha256-concurrent-" + suffix,
                baseTime.plusSeconds(60)
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ValidationReviewTransitionResult> firstFuture = executor.submit(
                    () -> executeConcurrentTransition(transactions, repository, concurrentCommand, ready, start)
            );
            Future<ValidationReviewTransitionResult> secondFuture = executor.submit(
                    () -> executeConcurrentTransition(transactions, repository, concurrentCommand, ready, start)
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS), "concurrent workers did not become ready");
            start.countDown();

            ValidationReviewTransitionResult firstResult = firstFuture.get(10, TimeUnit.SECONDS);
            ValidationReviewTransitionResult secondResult = secondFuture.get(10, TimeUnit.SECONDS);
            assertEquals(1, List.of(firstResult, secondResult).stream()
                    .filter(ValidationReviewTransitionResult::idempotentReplay)
                    .count());
            assertEquals(firstResult.event().id(), secondResult.event().id());
            assertEquals(firstResult.reviewCase(), secondResult.reviewCase());
            assertEquals(1, repository.listOwnedEvents(
                    ValidationReviewCase.LOCAL_TENANT_KEY,
                    ownerId,
                    caseId,
                    20).size());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "concurrent executor did not terminate");
        }
    }

    private static ValidationReviewTransitionResult executeConcurrentTransition(
            TransactionTemplate transactions,
            JdbcValidationReviewRepository repository,
            ValidationReviewTransitionCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "concurrent start signal timed out");
        ValidationReviewTransitionResult result = transactions.execute(
                status -> repository.transitionOwned(command)
        );
        assertNotNull(result);
        return result;
    }

    private static void assertAtomicRollbackOnEventFailure(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions,
            JdbcValidationReviewRepository repository,
            UUID caseId,
            long ownerId,
            Instant baseTime,
            String suffix
    ) {
        repository.createCase(openCase(caseId, ownerId, "atomic-source", baseTime));
        String functionName = "gatev_fail_event_" + suffix;
        String triggerName = "gatev_fail_event_trigger_" + suffix;
        jdbcTemplate.execute("""
                CREATE FUNCTION %s() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced validation review event failure';
                END;
                $$ LANGUAGE plpgsql
                """.formatted(functionName));
        jdbcTemplate.execute("""
                CREATE TRIGGER %s BEFORE INSERT ON validation_review_events
                FOR EACH ROW WHEN (NEW.review_case_id = '%s'::uuid)
                EXECUTE FUNCTION %s()
                """.formatted(triggerName, caseId, functionName));
        try {
            assertThrows(RuntimeException.class, () -> transactions.execute(status -> repository.transitionOwned(command(
                    caseId,
                    ownerId,
                    ownerId,
                    ValidationReviewState.ACKNOWLEDGED,
                    0,
                    "idem-atomic-" + suffix,
                    "sha256-atomic-" + suffix,
                    baseTime.plusSeconds(60)
            ))));
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT state, version FROM validation_review_cases WHERE id = ?",
                    caseId
            );
            assertEquals("OPEN", row.get("state"));
            assertEquals(0L, ((Number) row.get("version")).longValue());
            Integer events = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM validation_review_events WHERE review_case_id = ?",
                    Integer.class,
                    caseId
            );
            assertEquals(0, events);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + triggerName + " ON validation_review_events");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + functionName + "()");
        }
    }

    private static ValidationReviewCase openCase(UUID id, long ownerId, String source, Instant createdAt) {
        return new ValidationReviewCase(
                id,
                ValidationReviewCase.LOCAL_TENANT_KEY,
                ownerId,
                "INCIDENT_REPLAY_REVIEW",
                source,
                JsonNodeFactory.instance.objectNode()
                        .put("sourceId", source)
                        .put("diagnosticOnly", true)
                        .put("notTradingAuthorization", true),
                ValidationReviewSeverity.WARNING,
                ValidationReviewState.OPEN,
                "Local validation review fixture",
                "Sanitized PostgreSQL integration fixture",
                0,
                ownerId,
                createdAt,
                createdAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static ValidationReviewTransitionCommand command(
            UUID caseId,
            long ownerId,
            long actorId,
            ValidationReviewState target,
            long expectedVersion,
            String idempotencyKey,
            String requestHash,
            Instant occurredAt
    ) {
        return new ValidationReviewTransitionCommand(
                caseId,
                ValidationReviewCase.LOCAL_TENANT_KEY,
                ownerId,
                target,
                expectedVersion,
                actorId,
                idempotencyKey,
                requestHash,
                "req-" + idempotencyKey,
                "trace-" + idempotencyKey,
                JsonNodeFactory.instance.objectNode().put("result", "reviewed"),
                occurredAt
        );
    }

    private static long insertUser(JdbcTemplate jdbcTemplate, String username) {
        Long userId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO users (username, password_hash, enabled, created_at, updated_at)
                        VALUES (?, ?, TRUE, NOW(), NOW())
                        RETURNING id
                        """,
                Long.class,
                username,
                "disabled-fixture-hash"
        );
        assertNotNull(userId);
        return userId;
    }

    private static void insertOtherTenantCase(
            JdbcTemplate jdbcTemplate,
            UUID caseId,
            long ownerId,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO validation_review_cases (
                            id, tenant_key, owner_id, evidence_type, evidence_source, evidence_anchor,
                            severity, state, title, version, created_by, created_at, updated_at
                        ) VALUES (?, 'OTHER_TENANT', ?, 'LOCAL_FIXTURE', 'other-tenant-source',
                                  '{}'::jsonb, 'INFO', 'OPEN', 'Other tenant fixture', 0, ?, ?, ?)
                        """,
                caseId,
                ownerId,
                ownerId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private static void assertMigratedSchema(JdbcTemplate jdbcTemplate) {
        assertEquals("validation_review_cases", jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.validation_review_cases')::text",
                String.class));
        assertEquals("validation_review_events", jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.validation_review_events')::text",
                String.class));
        for (String constraint : List.of(
                "chk_validation_review_cases_state",
                "chk_validation_review_cases_severity",
                "chk_validation_review_cases_version",
                "chk_validation_review_cases_state_times",
                "chk_validation_review_events_legal_transition",
                "fk_validation_review_events_case_tenant",
                "uq_validation_review_events_case_idempotency"
        )) {
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
                    Integer.class,
                    constraint));
        }
        for (String index : List.of(
                "idx_validation_review_cases_tenant_owner_state_updated",
                "idx_validation_review_cases_tenant_state_severity_updated",
                "idx_validation_review_cases_tenant_owner_updated",
                "idx_validation_review_cases_tenant_updated",
                "idx_validation_review_cases_evidence_type_source",
                "idx_validation_review_events_case_created",
                "idx_validation_review_events_tenant_actor_created",
                "idx_validation_review_events_trace_id"
        )) {
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class,
                    index));
        }
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "SELECT obj_description('validation_review_cases'::regclass, 'pg_class') IS NOT NULL",
                Boolean.class));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "SELECT obj_description('validation_review_events'::regclass, 'pg_class') IS NOT NULL",
                Boolean.class));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) >= 12
                        FROM pg_attribute a
                        WHERE a.attrelid IN (
                            'validation_review_cases'::regclass,
                            'validation_review_events'::regclass
                        )
                          AND a.attnum > 0
                          AND col_description(a.attrelid, a.attnum) IS NOT NULL
                        """,
                Boolean.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '33' AND success = TRUE",
                Integer.class));
    }

    private record SmokeConfig(String url, String user, String password, boolean required) {
        static SmokeConfig fromSystemProperties() {
            return new SmokeConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
            );
        }

        boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }
    }

    private static String property(String name) {
        return System.getProperty(name, "").trim();
    }
}
