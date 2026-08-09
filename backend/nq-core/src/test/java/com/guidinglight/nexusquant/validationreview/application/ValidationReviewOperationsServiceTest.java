package com.guidinglight.nexusquant.validationreview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCaseQuery;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEventType;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewException;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewSeverity;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewStateMachine;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionCommand;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionResult;
import com.guidinglight.nexusquant.validationreview.domain.port.ValidationReviewRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** GateV-2 service 的 query scope、action mapping、audit 与 replay 回归。 */
class ValidationReviewOperationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T08:00:00Z");
    private static final UUID CASE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FakeRepository repository;
    private RecordingAuditRepository auditRepository;
    private ValidationReviewOperationsService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        auditRepository = new RecordingAuditRepository();
        ValidationReviewOperationalAuditService rejectedAudit = new ValidationReviewOperationalAuditService(
                auditRepository
        );
        service = new ValidationReviewOperationsService(
                repository,
                new ValidationReviewTransitionService(repository),
                auditRepository,
                rejectedAudit,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldEnforceOperatorAndAdminListScope() {
        ValidationReviewActor operator = new ValidationReviewActor(11L, Set.of("OPERATOR"));
        ValidationReviewActor admin = new ValidationReviewActor(99L, Set.of("ADMIN"));
        ValidationReviewCaseQuery operatorQuery = new ValidationReviewCaseQuery(
                ValidationReviewState.OPEN,
                ValidationReviewSeverity.WARNING,
                null,
                50,
                0
        );
        ValidationReviewCaseQuery adminQuery = new ValidationReviewCaseQuery(null, null, 12L, 100, 3);

        service.listCases(operator, operatorQuery);
        assertEquals("OWNED", repository.lastListScope);
        assertEquals(11L, repository.lastOwnerId);
        assertEquals(operatorQuery, repository.lastQuery);
        service.listCases(admin, adminQuery);
        assertEquals("TENANT", repository.lastListScope);
        assertEquals(adminQuery, repository.lastQuery);

        assertThrows(ValidationReviewException.class, () -> service.listCases(
                operator,
                new ValidationReviewCaseQuery(null, null, 12L, 10, 0)
        ));
    }

    @Test
    void shouldFailClosedForOtherOwnerAndUnknownRoleWithoutUnscopedRead() {
        repository.visible = false;
        ValidationReviewException hidden = assertThrows(ValidationReviewException.class, () -> service.detail(
                new ValidationReviewActor(11L, Set.of("OPERATOR")),
                CASE_ID
        ));
        assertEquals("REVIEW_CASE_NOT_FOUND", hidden.errorCode());
        assertEquals("OWNED", repository.lastFindScope);

        repository.lastFindScope = null;
        assertThrows(ValidationReviewException.class, () -> service.detail(
                new ValidationReviewActor(13L, Set.of("VIEWER")),
                CASE_ID
        ));
        assertEquals(null, repository.lastFindScope);
    }

    @ParameterizedTest
    @EnumSource(ValidationReviewAction.class)
    void shouldMapAllFourActionsAndAuditOnlyFirstAcceptedTransition(ValidationReviewAction action) {
        repository.current = currentFor(action);
        ValidationReviewTransitionResult result = service.transition(
                new ValidationReviewActor(11L, Set.of("OPERATOR")),
                CASE_ID,
                action,
                repository.current.version(),
                "local review",
                JsonNodeFactory.instance.objectNode().put("note", "safe"),
                "idem-" + action.name(),
                "req-1",
                "trc-1"
        );

        assertEquals(action.targetState(), result.reviewCase().state());
        assertEquals(action.targetState(), repository.lastCommand.targetState());
        assertEquals(11L, repository.lastCommand.ownerId());
        assertEquals(11L, repository.lastCommand.actorId());
        assertEquals(64, repository.lastCommand.requestHash().length());
        assertEquals(1, auditRepository.calls.size());
        RecordingAuditRepository.AuditCall audit = auditRepository.calls.getFirst();
        assertEquals("VALIDATION_REVIEW_ACCEPTED", audit.action());
        assertEquals(Set.of("caseId", "action", "fromState", "toState", "actorId", "requestId", "traceId"),
                audit.detail().keySet());
    }

    @Test
    void shouldNotAppendAcceptedAuditForIdempotentReplay() {
        repository.current = currentFor(ValidationReviewAction.ACKNOWLEDGE);
        repository.replay = true;
        ValidationReviewTransitionResult replay = service.transition(
                new ValidationReviewActor(11L, Set.of("OPERATOR")), CASE_ID,
                ValidationReviewAction.ACKNOWLEDGE, 0L, "review", JsonNodeFactory.instance.objectNode(),
                "idem-replay", "req-replay", "trc-replay"
        );

        assertTrue(replay.idempotentReplay());
        assertTrue(auditRepository.calls.isEmpty());
    }

    @Test
    void shouldAuditRejectedRoleAndInvalidPayloadWithoutLifecycleEvent() {
        assertThrows(ValidationReviewException.class, () -> service.transition(
                new ValidationReviewActor(15L, Set.of("VIEWER")), CASE_ID,
                ValidationReviewAction.ACKNOWLEDGE, 0L, "review", JsonNodeFactory.instance.objectNode(),
                "idem-denied", "req-denied", "trc-denied"
        ));
        assertEquals("VALIDATION_REVIEW_REJECTED", auditRepository.calls.getFirst().action());
        assertEquals("REVIEW_ACTION_FORBIDDEN", auditRepository.calls.getFirst().detail().get("errorCode"));
        assertEquals(0, repository.transitionCalls);

        auditRepository.calls.clear();
        repository.current = openCase();
        assertThrows(IllegalArgumentException.class, () -> service.transition(
                new ValidationReviewActor(11L, Set.of("OPERATOR")), CASE_ID,
                ValidationReviewAction.ACKNOWLEDGE, null, null, null,
                null, "req-invalid", "trc-invalid"
        ));
        assertEquals("REVIEW_REQUEST_INVALID", auditRepository.calls.getFirst().detail().get("errorCode"));
        assertEquals(0, repository.transitionCalls);
    }

    private static ValidationReviewCase currentFor(ValidationReviewAction action) {
        ValidationReviewCase open = openCase();
        ValidationReviewStateMachine machine = new ValidationReviewStateMachine();
        return switch (action) {
            case ACKNOWLEDGE, ESCALATE -> open;
            case RESOLVE -> machine.transition(open, ValidationReviewState.ACKNOWLEDGED, 11L, NOW.minusSeconds(30));
            case CLOSE -> {
                ValidationReviewCase acknowledged = machine.transition(
                        open, ValidationReviewState.ACKNOWLEDGED, 11L, NOW.minusSeconds(40));
                yield machine.transition(acknowledged, ValidationReviewState.RESOLVED, 11L, NOW.minusSeconds(20));
            }
        };
    }

    private static ValidationReviewCase openCase() {
        return new ValidationReviewCase(
                CASE_ID, ValidationReviewCase.LOCAL_TENANT_KEY, 11L, "LOCAL", "source",
                JsonNodeFactory.instance.objectNode().put("id", "evidence-1"),
                ValidationReviewSeverity.WARNING, ValidationReviewState.OPEN, "title", "summary", 0L, 11L,
                NOW.minusSeconds(60), NOW.minusSeconds(60), null, null, null, null, null, null, null, null,
                NOW.plusSeconds(86400)
        );
    }

    private static final class FakeRepository implements ValidationReviewRepository {
        private ValidationReviewCase current = openCase();
        private ValidationReviewTransitionCommand lastCommand;
        private ValidationReviewCaseQuery lastQuery;
        private String lastListScope;
        private String lastFindScope;
        private long lastOwnerId;
        private int transitionCalls;
        private boolean visible = true;
        private boolean replay;

        @Override
        public ValidationReviewCase createCase(ValidationReviewCase reviewCase) {
            current = reviewCase;
            return reviewCase;
        }

        @Override
        public Optional<ValidationReviewCase> findOwnedCase(String tenantKey, long ownerId, UUID reviewCaseId) {
            lastFindScope = "OWNED";
            return visible && current.ownerId() == ownerId ? Optional.of(current) : Optional.empty();
        }

        @Override
        public Optional<ValidationReviewCase> findTenantCase(String tenantKey, UUID reviewCaseId) {
            lastFindScope = "TENANT";
            return visible ? Optional.of(current) : Optional.empty();
        }

        @Override
        public List<ValidationReviewCase> listOwnedCases(
                String tenantKey,
                long ownerId,
                ValidationReviewCaseQuery query
        ) {
            lastListScope = "OWNED";
            lastOwnerId = ownerId;
            lastQuery = query;
            return List.of(current);
        }

        @Override
        public List<ValidationReviewCase> listTenantCases(String tenantKey, ValidationReviewCaseQuery query) {
            lastListScope = "TENANT";
            lastQuery = query;
            return List.of(current);
        }

        @Override
        public List<ValidationReviewEvent> listOwnedEvents(
                String tenantKey, long ownerId, UUID reviewCaseId, int limit
        ) {
            return List.of();
        }

        @Override
        public List<ValidationReviewEvent> listTenantEvents(String tenantKey, UUID reviewCaseId, int limit) {
            return List.of();
        }

        @Override
        public Optional<ValidationReviewEvent> findOwnedEventByIdempotencyKey(
                String tenantKey, long ownerId, UUID reviewCaseId, String idempotencyKey
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<ValidationReviewEvent> findTenantEventByIdempotencyKey(
                String tenantKey, UUID reviewCaseId, String idempotencyKey
        ) {
            return Optional.empty();
        }

        @Override
        public ValidationReviewTransitionResult transitionOwned(ValidationReviewTransitionCommand command) {
            return transition(command);
        }

        @Override
        public ValidationReviewTransitionResult transitionInTenant(ValidationReviewTransitionCommand command) {
            return transition(command);
        }

        private ValidationReviewTransitionResult transition(ValidationReviewTransitionCommand command) {
            transitionCalls++;
            lastCommand = command;
            ValidationReviewCase updated = new ValidationReviewStateMachine().transition(
                    current, command.targetState(), command.actorId(), command.occurredAt());
            ValidationReviewEvent event = new ValidationReviewEvent(
                    UUID.randomUUID(), CASE_ID, ValidationReviewCase.LOCAL_TENANT_KEY,
                    ValidationReviewEventType.fromTargetState(command.targetState()), current.state(),
                    command.targetState(), updated.version(), command.actorId(), command.idempotencyKey(),
                    command.requestHash(), command.requestId(), command.traceId(), command.metadata(), command.occurredAt()
            );
            current = updated;
            return new ValidationReviewTransitionResult(updated, event, replay);
        }
    }

    private static final class RecordingAuditRepository implements AuditLogRepository {
        private final List<AuditCall> calls = new ArrayList<>();

        @Override
        public void append(String domain, String action, String actorId, String traceId, Map<String, Object> detail) {
            calls.add(new AuditCall(domain, action, actorId, traceId, detail));
        }

        private record AuditCall(
                String domain,
                String action,
                String actorId,
                String traceId,
                Map<String, Object> detail
        ) {
        }
    }
}
