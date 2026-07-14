package com.guidinglight.nexusquant.trading.application.safety;

import static com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyFactBundle.HUMAN_REVIEW_EVIDENCE_TYPE;
import static com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyFactBundle.HUMAN_REVIEW_SUBJECT;
import static com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyStatus.BLOCKED;
import static com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyStatus.NOT_EVALUATED;
import static com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyStatus.PASS;
import static com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyStatus.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyFactBundle.HumanReviewEvidence;
import com.guidinglight.nexusquant.trading.application.safety.GateW4OperationalSafetyFactBundle.HumanReviewEvidenceStatus;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEventType;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewSeverity;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** GateW-4 incident matrix、human-review binding 与 10,000 次 no-egress soak 回归。 */
class GateW4OperationalSafetyAssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final GateW4OperationalSafetyAssessmentService SERVICE =
            new GateW4OperationalSafetyAssessmentService();

    @Test
    void engagedBaselineRemainsBlockedAndNeverAuthorizesTrading() {
        GateW4OperationalSafetyResult result = SERVICE.assess(request(
                snapshot(KillSwitchStatus.ENGAGED, "OPERATOR_ENGAGE"),
                facts(PASS, PASS, PASS, PASS, NOT_EVALUATED, Set.of())
        ));

        assertEquals(BLOCKED, result.killSwitchStatus());
        assertEquals(BLOCKED, result.overallStatus());
        assertTrue(result.blockers().contains(GateW4OperationalSafetyFindingCode.KILL_SWITCH_ENGAGED));
        assertTrue(result.warnings().contains(
                GateW4OperationalSafetyFindingCode.REAL_READONLY_SOAK_CREDENTIAL_REQUIRED));
        assertTrue(result.diagnosticOnly());
        assertTrue(result.readOnly());
        assertTrue(result.noSideEffect());
        assertTrue(result.liveDisabled());
        assertFalse(result.orderSubmitted());
        assertFalse(result.tradingAuthorized());
    }

    @ParameterizedTest
    @MethodSource("incidentScenarios")
    void incidentDrillAlwaysReturnsBlockedOrUnknown(
            GateW4OperationalSafetyFindingCode scenario,
            KillSwitchSnapshot snapshot,
            GateW4OperationalSafetyStatus incidentStatus
    ) {
        GateW4OperationalSafetyResult result = SERVICE.assess(request(
                snapshot,
                facts(PASS, PASS, incidentStatus, PASS, NOT_EVALUATED, EnumSet.of(scenario))
        ));

        assertTrue(result.overallStatus() == BLOCKED || result.overallStatus() == UNKNOWN);
        assertTrue(result.blockers().contains(scenario) || result.unknowns().contains(scenario));
        assertFalse(result.orderSubmitted());
        assertFalse(result.tradingAuthorized());
        assertTrue(result.liveDisabled());
    }

    @Test
    void unknownKillSwitchNeverBecomesPass() {
        GateW4OperationalSafetyResult result = SERVICE.assess(request(
                snapshot(KillSwitchStatus.UNKNOWN, "KILL_SWITCH_STATE_READ_FAILED"),
                facts(PASS, PASS, PASS, PASS, NOT_EVALUATED, Set.of())
        ));

        assertEquals(UNKNOWN, result.killSwitchStatus());
        assertEquals(UNKNOWN, result.overallStatus());
        assertTrue(result.unknowns().contains(
                GateW4OperationalSafetyFindingCode.KILL_SWITCH_STORAGE_FAILURE));
    }

    @Test
    void missingStaleAndConflictingHumanReviewEvidenceFailClosed() {
        List<HumanReviewEvidence> invalid = List.of(
                HumanReviewEvidence.missing(NOW),
                humanReview(HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_STALE, true, NOW.plusSeconds(60)),
                humanReview(HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_CONFLICT, false, NOW.plusSeconds(60))
        );

        for (HumanReviewEvidence evidence : invalid) {
            GateW4OperationalSafetyFactBundle facts = new GateW4OperationalSafetyFactBundle(
                    evidence, PASS, PASS, PASS, PASS, NOT_EVALUATED, Set.of());
            GateW4OperationalSafetyResult result = SERVICE.assess(request(
                    snapshot(KillSwitchStatus.DISENGAGED, "TEST_ONLY_DISENGAGED"), facts));
            assertEquals(BLOCKED, result.humanReviewEvidenceStatus());
            assertFalse(result.tradingAuthorized());
        }
    }

    @Test
    void derivesPresentHumanReviewOnlyFromCompleteDurableEventChain() {
        List<ValidationReviewEvent> events = reviewEvents();
        HumanReviewEvidence present = HumanReviewEvidence.bind(
                Optional.of(reviewCase()), events, NOW);
        assertEquals(HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_PRESENT, present.status());
        assertTrue(present.eventChainComplete());

        HumanReviewEvidence conflict = HumanReviewEvidence.bind(
                Optional.of(reviewCase()), events.subList(0, events.size() - 1), NOW);
        assertEquals(HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_CONFLICT, conflict.status());
        assertFalse(conflict.eventChainComplete());
        assertEquals(HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_MISSING,
                HumanReviewEvidence.bind(Optional.empty(), List.of(), NOW).status());
    }

    @Test
    void boundedConcurrentNoEgressSoakIsDeterministicAndReleasesExecutor() throws Exception {
        GateW4OperationalSafetyRequest request = request(
                snapshot(KillSwitchStatus.ENGAGED, "OPERATOR_ENGAGE"),
                facts(PASS, PASS, PASS, PASS, NOT_EVALUATED, Set.of())
        );
        GateW4OperationalSafetyResult expected = SERVICE.assess(request);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<GateW4OperationalSafetyResult>> workers = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                workers.add(() -> {
                    GateW4OperationalSafetyResult last = null;
                    for (int iteration = 0; iteration < 1_250; iteration++) {
                        last = SERVICE.assess(request);
                        assertEquals(expected, last);
                    }
                    return last;
                });
            }
            List<Future<GateW4OperationalSafetyResult>> futures = executor.invokeAll(workers);
            for (Future<GateW4OperationalSafetyResult> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertTrue(executor.isTerminated());
        assertEquals(0, GateW4OperationalSafetyAssessmentService.class.getDeclaredFields().length);
        assertEquals(BLOCKED, expected.overallStatus());
        assertTrue(expected.noSideEffect());
    }

    private static Stream<Arguments> incidentScenarios() {
        KillSwitchSnapshot engaged = snapshot(KillSwitchStatus.ENGAGED, "OPERATOR_ENGAGE");
        KillSwitchSnapshot unknown = snapshot(KillSwitchStatus.UNKNOWN, "KILL_SWITCH_STATE_MISSING");
        KillSwitchSnapshot storageFailure = snapshot(
                KillSwitchStatus.UNKNOWN, "KILL_SWITCH_STATE_READ_FAILED");
        KillSwitchSnapshot disengaged = snapshot(
                KillSwitchStatus.DISENGAGED, "TEST_ONLY_DISENGAGED");
        return Stream.of(
                Arguments.of(GateW4OperationalSafetyFindingCode.KILL_SWITCH_ENGAGED, engaged, PASS),
                Arguments.of(GateW4OperationalSafetyFindingCode.KILL_SWITCH_UNKNOWN, unknown, UNKNOWN),
                Arguments.of(GateW4OperationalSafetyFindingCode.KILL_SWITCH_STORAGE_FAILURE,
                        storageFailure, UNKNOWN),
                Arguments.of(GateW4OperationalSafetyFindingCode.DATABASE_UNAVAILABLE, disengaged, BLOCKED),
                Arguments.of(GateW4OperationalSafetyFindingCode.RECONCILIATION_STALE, disengaged, BLOCKED),
                Arguments.of(GateW4OperationalSafetyFindingCode.RECONCILIATION_PARTIAL, disengaged, BLOCKED),
                Arguments.of(GateW4OperationalSafetyFindingCode.PRIVATE_PROBE_FAILURE, disengaged, BLOCKED),
                Arguments.of(GateW4OperationalSafetyFindingCode.CREDENTIAL_UNAVAILABLE, disengaged, BLOCKED),
                Arguments.of(GateW4OperationalSafetyFindingCode.CREDENTIAL_CONFLICT, disengaged, BLOCKED),
                Arguments.of(GateW4OperationalSafetyFindingCode.RESTORE_FAILURE, disengaged, BLOCKED),
                Arguments.of(GateW4OperationalSafetyFindingCode.MARKETDATA_STALE, disengaged, BLOCKED)
        );
    }

    private static GateW4OperationalSafetyRequest request(
            KillSwitchSnapshot snapshot,
            GateW4OperationalSafetyFactBundle facts
    ) {
        return new GateW4OperationalSafetyRequest(snapshot, facts, NOW);
    }

    private static GateW4OperationalSafetyFactBundle facts(
            GateW4OperationalSafetyStatus persistence,
            GateW4OperationalSafetyStatus backup,
            GateW4OperationalSafetyStatus incident,
            GateW4OperationalSafetyStatus soak,
            GateW4OperationalSafetyStatus realSoak,
            Set<GateW4OperationalSafetyFindingCode> incidentFindings
    ) {
        return new GateW4OperationalSafetyFactBundle(
                humanReview(HumanReviewEvidenceStatus.HUMAN_REVIEW_EVIDENCE_PRESENT,
                        true, NOW.plusSeconds(86_400)),
                persistence,
                backup,
                incident,
                soak,
                realSoak,
                incidentFindings
        );
    }

    private static HumanReviewEvidence humanReview(
            HumanReviewEvidenceStatus status,
            boolean eventChainComplete,
            Instant retentionUntil
    ) {
        return new HumanReviewEvidence(
                UUID.fromString("00000000-0000-0000-0000-000000004004"),
                2,
                HUMAN_REVIEW_EVIDENCE_TYPE,
                HUMAN_REVIEW_SUBJECT,
                "gatew4-local-evidence-v1",
                ValidationReviewState.CLOSED,
                eventChainComplete,
                retentionUntil,
                NOW,
                status
        );
    }

    private static KillSwitchSnapshot snapshot(KillSwitchStatus status, String reasonCode) {
        if (status == KillSwitchStatus.UNKNOWN) {
            return new KillSwitchSnapshot(
                    KillSwitchScope.GLOBAL_TRADING,
                    status,
                    0,
                    reasonCode,
                    "DURABLE_STORE",
                    null,
                    NOW,
                    "trace-gatew4-test"
            );
        }
        return new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING,
                status,
                1,
                reasonCode,
                "GATEW4_TEST_FIXTURE",
                NOW.minusSeconds(1),
                NOW,
                "trace-gatew4-test"
        );
    }

    private static ValidationReviewCase reviewCase() {
        Instant created = NOW.minusSeconds(40);
        Instant acknowledged = NOW.minusSeconds(30);
        Instant resolved = NOW.minusSeconds(20);
        Instant closed = NOW.minusSeconds(10);
        com.fasterxml.jackson.databind.node.ObjectNode anchor =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        anchor.put("subject", HUMAN_REVIEW_SUBJECT);
        anchor.put("reference", "gatew4-local-evidence-v1");
        return new ValidationReviewCase(
                UUID.fromString("00000000-0000-0000-0000-000000004004"),
                ValidationReviewCase.LOCAL_TENANT_KEY,
                1,
                HUMAN_REVIEW_EVIDENCE_TYPE,
                "LOCAL_GATEW4_TEST",
                anchor,
                ValidationReviewSeverity.INFO,
                ValidationReviewState.CLOSED,
                "GateW-4 human review evidence",
                "Diagnostic review only",
                3,
                1,
                created,
                closed,
                1L,
                acknowledged,
                null,
                null,
                1L,
                resolved,
                1L,
                closed,
                NOW.plusSeconds(86_400)
        );
    }

    private static List<ValidationReviewEvent> reviewEvents() {
        UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000004004");
        Instant created = NOW.minusSeconds(40);
        com.fasterxml.jackson.databind.node.ObjectNode metadata =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        return List.of(
                reviewEvent(caseId, 1, ValidationReviewState.OPEN,
                        ValidationReviewState.ACKNOWLEDGED, created.plusSeconds(10), metadata),
                reviewEvent(caseId, 2, ValidationReviewState.ACKNOWLEDGED,
                        ValidationReviewState.RESOLVED, created.plusSeconds(20), metadata),
                reviewEvent(caseId, 3, ValidationReviewState.RESOLVED,
                        ValidationReviewState.CLOSED, created.plusSeconds(30), metadata)
        );
    }

    private static ValidationReviewEvent reviewEvent(
            UUID caseId,
            long version,
            ValidationReviewState from,
            ValidationReviewState to,
            Instant createdAt,
            com.fasterxml.jackson.databind.JsonNode metadata
    ) {
        return new ValidationReviewEvent(
                UUID.nameUUIDFromBytes(("gatew4-event-" + version).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                caseId,
                ValidationReviewCase.LOCAL_TENANT_KEY,
                ValidationReviewEventType.valueOf(to.name()),
                from,
                to,
                version,
                1,
                "gatew4-idempotency-" + version,
                "gatew4-request-hash-" + version,
                "gatew4-request-" + version,
                "trace-gatew4-review",
                metadata,
                createdAt
        );
    }
}
