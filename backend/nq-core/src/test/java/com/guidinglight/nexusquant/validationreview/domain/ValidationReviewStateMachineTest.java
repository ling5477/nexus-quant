package com.guidinglight.nexusquant.validationreview.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ValidationReviewStateMachineTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-11T00:00:00Z");
    private static final long OWNER_ID = 101L;
    private static final Set<String> LEGAL = Set.of(
            "OPEN->ACKNOWLEDGED",
            "OPEN->ESCALATED",
            "ACKNOWLEDGED->ESCALATED",
            "ACKNOWLEDGED->RESOLVED",
            "ESCALATED->RESOLVED",
            "RESOLVED->CLOSED"
    );

    private final ValidationReviewStateMachine stateMachine = new ValidationReviewStateMachine();

    @Test
    void shouldAllowEveryDeclaredTransitionAndIncrementVersion() {
        for (String transition : LEGAL) {
            String[] parts = transition.split("->");
            ValidationReviewState from = ValidationReviewState.valueOf(parts[0]);
            ValidationReviewState to = ValidationReviewState.valueOf(parts[1]);
            ValidationReviewCase current = caseIn(from);

            ValidationReviewCase updated = stateMachine.transition(
                    current,
                    to,
                    OWNER_ID,
                    current.updatedAt().plusSeconds(60)
            );

            assertEquals(to, updated.state(), transition);
            assertEquals(current.version() + 1, updated.version(), transition);
            assertTrue(stateMachine.canTransition(from, to), transition);
        }
    }

    @Test
    void shouldRejectEveryUndeclaredTransitionWithExplicitCode() {
        for (ValidationReviewState from : ValidationReviewState.values()) {
            for (ValidationReviewState to : ValidationReviewState.values()) {
                String transition = from + "->" + to;
                if (LEGAL.contains(transition)) {
                    continue;
                }
                ValidationReviewCase current = caseIn(from);
                ValidationReviewException exception = assertThrows(
                        ValidationReviewException.class,
                        () -> stateMachine.transition(current, to, OWNER_ID, current.updatedAt().plusSeconds(60)),
                        transition
                );
                assertEquals(
                        from == ValidationReviewState.CLOSED
                                ? "REVIEW_CASE_TERMINAL_STATE_LOCKED"
                                : "REVIEW_STATE_TRANSITION_INVALID",
                        exception.errorCode(),
                        transition
                );
                assertFalse(stateMachine.canTransition(from, to), transition);
            }
        }
    }

    @Test
    void shouldRejectSelfLoopWithoutChangingCase() {
        ValidationReviewCase current = caseIn(ValidationReviewState.ACKNOWLEDGED);

        ValidationReviewException exception = assertThrows(
                ValidationReviewException.class,
                () -> stateMachine.transition(
                        current,
                        ValidationReviewState.ACKNOWLEDGED,
                        OWNER_ID,
                        current.updatedAt().plusSeconds(60)
                )
        );

        assertEquals("REVIEW_STATE_TRANSITION_INVALID", exception.errorCode());
        assertEquals(ValidationReviewState.ACKNOWLEDGED, current.state());
        assertEquals(1, current.version());
    }

    @Test
    void shouldFailClosedForSensitiveEvidenceOrMetadataFieldNames() {
        assertThrows(IllegalArgumentException.class, () -> openCase(
                JsonNodeFactory.instance.objectNode().put("secret", "fixture-redacted")
        ));
        ValidationReviewCase safe = openCase(JsonNodeFactory.instance.objectNode().put("sourceId", "fixture-1"));
        assertThrows(IllegalArgumentException.class, () -> new ValidationReviewCase(
                safe.id(),
                safe.tenantKey(),
                safe.ownerId(),
                safe.evidenceType(),
                safe.evidenceSource(),
                safe.evidenceAnchor(),
                safe.severity(),
                safe.state(),
                "Contains privateKey marker",
                safe.summary(),
                safe.version(),
                safe.createdBy(),
                safe.createdAt(),
                safe.updatedAt(),
                safe.acknowledgedBy(),
                safe.acknowledgedAt(),
                safe.escalatedBy(),
                safe.escalatedAt(),
                safe.resolvedBy(),
                safe.resolvedAt(),
                safe.closedBy(),
                safe.closedAt(),
                safe.retentionUntil()
        ));
        assertThrows(IllegalArgumentException.class, () -> new ValidationReviewTransitionCommand(
                UUID.randomUUID(),
                ValidationReviewCase.LOCAL_TENANT_KEY,
                OWNER_ID,
                ValidationReviewState.ACKNOWLEDGED,
                0,
                OWNER_ID,
                "idem-sensitive-fixture",
                "sha256-sensitive-fixture",
                "req-sensitive-fixture",
                "trace-sensitive-fixture",
                JsonNodeFactory.instance.objectNode().put("realOrderId", "redacted-fixture"),
                CREATED_AT.plusSeconds(60)
        ));
    }

    @Test
    void shouldDefensivelyCopyEvidenceAndTransitionMetadata() {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode().put("sourceId", "fixture-1");
        ValidationReviewCase reviewCase = openCase(evidence);
        evidence.put("secret", "mutated-after-validation");
        ((ObjectNode) reviewCase.evidenceAnchor()).put("secret", "mutated-accessor-copy");

        ObjectNode metadata = JsonNodeFactory.instance.objectNode().put("result", "reviewed");
        ValidationReviewTransitionCommand command = new ValidationReviewTransitionCommand(
                reviewCase.id(),
                reviewCase.tenantKey(),
                reviewCase.ownerId(),
                ValidationReviewState.ACKNOWLEDGED,
                0,
                OWNER_ID,
                "idem-defensive-copy",
                "sha256-defensive-copy",
                "req-defensive-copy",
                "trace-defensive-copy",
                metadata,
                CREATED_AT.plusSeconds(60)
        );
        metadata.put("secret", "mutated-after-validation");
        ((ObjectNode) command.metadata()).put("secret", "mutated-accessor-copy");

        assertEquals("fixture-1", reviewCase.evidenceAnchor().get("sourceId").asText());
        assertFalse(reviewCase.evidenceAnchor().has("secret"));
        assertEquals("reviewed", command.metadata().get("result").asText());
        assertFalse(command.metadata().has("secret"));
    }

    private ValidationReviewCase caseIn(ValidationReviewState state) {
        ValidationReviewCase current = openCase(JsonNodeFactory.instance.objectNode().put("sourceId", "fixture-1"));
        if (state == ValidationReviewState.OPEN) {
            return current;
        }
        ValidationReviewState[] path = switch (state) {
            case ACKNOWLEDGED -> new ValidationReviewState[]{ValidationReviewState.ACKNOWLEDGED};
            case ESCALATED -> new ValidationReviewState[]{ValidationReviewState.ESCALATED};
            case RESOLVED -> new ValidationReviewState[]{
                    ValidationReviewState.ACKNOWLEDGED,
                    ValidationReviewState.RESOLVED
            };
            case CLOSED -> new ValidationReviewState[]{
                    ValidationReviewState.ACKNOWLEDGED,
                    ValidationReviewState.RESOLVED,
                    ValidationReviewState.CLOSED
            };
            case OPEN -> throw new IllegalStateException("OPEN handled above");
        };
        for (ValidationReviewState target : path) {
            current = stateMachine.transition(current, target, OWNER_ID, current.updatedAt().plusSeconds(60));
        }
        return current;
    }

    private ValidationReviewCase openCase(com.fasterxml.jackson.databind.JsonNode evidenceAnchor) {
        return new ValidationReviewCase(
                UUID.randomUUID(),
                ValidationReviewCase.LOCAL_TENANT_KEY,
                OWNER_ID,
                "INCIDENT_REPLAY_REVIEW",
                "fixture-source",
                evidenceAnchor,
                ValidationReviewSeverity.WARNING,
                ValidationReviewState.OPEN,
                "Fixture review case",
                "Local diagnostic fixture only",
                0,
                OWNER_ID,
                CREATED_AT,
                CREATED_AT,
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
}
