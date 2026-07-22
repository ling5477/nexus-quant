package com.guidinglight.nexusquant.strategyrelease.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateMachine;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateTransitionException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** PRE-GATEX lifecycle test-only prototype；release 与现有 Shadow Run 状态机严格隔离。 */
class StrategyReleaseLifecyclePrototypeTest {

    @Test
    void shouldAllowDeclaredStrategyReleaseLifecycle() {
        StrategyReleasePrototypeStateMachine release = new StrategyReleasePrototypeStateMachine();

        assertAccepted(release.apply("action-candidate", StrategyReleaseState.CANDIDATE), "SUBMIT_CANDIDATE");
        assertAccepted(release.apply("action-verify", StrategyReleaseState.VERIFIED), "VERIFY_MANIFEST");
        assertAccepted(release.apply("action-publish", StrategyReleaseState.PUBLISHED), "PUBLISH_RELEASE");
        assertAccepted(release.apply("action-retire", StrategyReleaseState.RETIRED), "RETIRE_RELEASE");

        assertEquals(StrategyReleaseState.RETIRED, release.state());
        assertEquals(4, release.auditEvents().size());
        assertTrue(release.state().terminal());
    }

    @Test
    void shouldRejectPublishBeforeVerificationWithExplicitReasonAndNoMutation() {
        StrategyReleasePrototypeStateMachine release = new StrategyReleasePrototypeStateMachine();
        release.apply("action-candidate", StrategyReleaseState.CANDIDATE);

        ReleaseTransitionResult result = release.apply("action-publish-too-early", StrategyReleaseState.PUBLISHED);

        assertFalse(result.accepted());
        assertEquals("RELEASE_NOT_VERIFIED", result.reasonCode());
        assertEquals(StrategyReleaseState.CANDIDATE, release.state());
        assertEquals("ILLEGAL_TRANSITION_REJECTED", result.auditEvent());
    }

    @Test
    void shouldKeepReleaseVerificationAndShadowExecutionAsIndependentLifecycles() {
        StrategyReleasePrototypeStateMachine release = new StrategyReleasePrototypeStateMachine();
        release.apply("action-candidate", StrategyReleaseState.CANDIDATE);
        release.apply("action-verify", StrategyReleaseState.VERIFIED);
        ShadowRunStateMachine shadow = new ShadowRunStateMachine();
        ShadowRunStatus shadowStatus = ShadowRunStatus.CREATED;

        assertEquals(StrategyReleaseState.VERIFIED, release.state());
        assertEquals(ShadowRunStatus.CREATED, shadowStatus, "release verified must not start Shadow Run");

        shadowStatus = shadow.transition(shadowStatus, ShadowRunStatus.PRECHECKING);
        shadowStatus = shadow.transition(shadowStatus, ShadowRunStatus.READY);
        shadowStatus = shadow.transition(shadowStatus, ShadowRunStatus.RUNNING);
        shadowStatus = shadow.transition(shadowStatus, ShadowRunStatus.COMPLETED);

        assertEquals(ShadowRunStatus.COMPLETED, shadowStatus);
        assertEquals(StrategyReleaseState.VERIFIED, release.state(), "Shadow completion must not publish release");
        assertFalse(PrototypeTradingBoundary.liveAuthorized(release.state(), shadowStatus));
    }

    @Test
    void shouldLockReleaseAndShadowTerminalStates() {
        StrategyReleasePrototypeStateMachine release = new StrategyReleasePrototypeStateMachine();
        release.apply("action-reject", StrategyReleaseState.REJECTED);

        ReleaseTransitionResult releaseResult = release.apply("action-reopen", StrategyReleaseState.CANDIDATE);
        assertFalse(releaseResult.accepted());
        assertEquals("RELEASE_TERMINAL_STATE_LOCKED", releaseResult.reasonCode());
        assertEquals(StrategyReleaseState.REJECTED, release.state());

        ShadowRunStateMachine shadow = new ShadowRunStateMachine();
        for (ShadowRunStatus status : ShadowRunStatus.values()) {
            if (!status.terminal()) {
                continue;
            }
            ShadowRunStateTransitionException exception = assertThrows(
                    ShadowRunStateTransitionException.class,
                    () -> shadow.transition(status, ShadowRunStatus.RUNNING)
            );
            assertEquals("SHADOW_RUN_TERMINAL_STATE_LOCKED", exception.reasonCode());
        }
    }

    @Test
    void shouldReturnCachedResultForRepeatedActionIdAndRejectConflictingReuse() {
        StrategyReleasePrototypeStateMachine release = new StrategyReleasePrototypeStateMachine();

        ReleaseTransitionResult first = release.apply("action-001", StrategyReleaseState.CANDIDATE);
        ReleaseTransitionResult repeated = release.apply("action-001", StrategyReleaseState.CANDIDATE);
        ReleaseTransitionResult conflict = release.apply("action-001", StrategyReleaseState.VERIFIED);

        assertSame(first, repeated);
        assertEquals(1, release.auditEvents().size(), "idempotent replay must not append a second success event");
        assertFalse(conflict.accepted());
        assertEquals("RELEASE_ACTION_ID_CONFLICT", conflict.reasonCode());
        assertEquals(StrategyReleaseState.CANDIDATE, release.state());
    }

    @Test
    void shouldFailClosedForUndeclaredTransitionWithExplicitReason() {
        StrategyReleasePrototypeStateMachine release = new StrategyReleasePrototypeStateMachine();

        ReleaseTransitionResult result = release.apply("action-illegal", StrategyReleaseState.RETIRED);

        assertFalse(result.accepted());
        assertEquals("RELEASE_ILLEGAL_STATE_TRANSITION", result.reasonCode());
        assertEquals(StrategyReleaseState.DRAFT, release.state());
    }

    private void assertAccepted(ReleaseTransitionResult result, String triggerAction) {
        assertTrue(result.accepted(), result.reasonCode());
        assertEquals("OK", result.reasonCode());
        assertEquals(triggerAction, result.triggerAction());
        assertEquals("STATE_TRANSITION_ACCEPTED", result.auditEvent());
    }
}

enum StrategyReleaseState {
    DRAFT(false),
    CANDIDATE(false),
    VERIFIED(false),
    PUBLISHED(false),
    REJECTED(true),
    RETIRED(true);

    private final boolean terminal;

    StrategyReleaseState(boolean terminal) {
        this.terminal = terminal;
    }

    boolean terminal() {
        return terminal;
    }
}

record ReleaseTransitionResult(
        boolean accepted,
        StrategyReleaseState fromState,
        StrategyReleaseState requestedState,
        StrategyReleaseState resultingState,
        String reasonCode,
        String triggerAction,
        String auditEvent
) {
}

record ReleaseAuditEvent(
        String actionId,
        StrategyReleaseState fromState,
        StrategyReleaseState requestedState,
        StrategyReleaseState resultingState,
        String reasonCode,
        String eventType
) {
}

final class StrategyReleasePrototypeStateMachine {

    private static final Map<StrategyReleaseState, Set<StrategyReleaseState>> TRANSITIONS = transitions();
    private final Map<String, CachedAction> actions = new LinkedHashMap<>();
    private final List<ReleaseAuditEvent> auditEvents = new ArrayList<>();
    private StrategyReleaseState state = StrategyReleaseState.DRAFT;

    StrategyReleaseState state() {
        return state;
    }

    List<ReleaseAuditEvent> auditEvents() {
        return List.copyOf(auditEvents);
    }

    ReleaseTransitionResult apply(String actionId, StrategyReleaseState requestedState) {
        if (actionId == null || actionId.isBlank() || requestedState == null) {
            throw new IllegalArgumentException("actionId and requestedState are required");
        }
        CachedAction cached = actions.get(actionId);
        if (cached != null) {
            if (cached.requestedState() == requestedState) {
                return cached.result();
            }
            return new ReleaseTransitionResult(
                    false,
                    state,
                    requestedState,
                    state,
                    "RELEASE_ACTION_ID_CONFLICT",
                    triggerFor(requestedState),
                    "IDEMPOTENCY_CONFLICT_REJECTED"
            );
        }

        StrategyReleaseState fromState = state;
        boolean accepted = TRANSITIONS.getOrDefault(fromState, Set.of()).contains(requestedState);
        String reasonCode = accepted ? "OK" : illegalReason(fromState, requestedState);
        if (accepted) {
            state = requestedState;
        }
        String eventType = accepted ? "STATE_TRANSITION_ACCEPTED" : "ILLEGAL_TRANSITION_REJECTED";
        ReleaseTransitionResult result = new ReleaseTransitionResult(
                accepted,
                fromState,
                requestedState,
                state,
                reasonCode,
                triggerFor(requestedState),
                eventType
        );
        actions.put(actionId, new CachedAction(requestedState, result));
        auditEvents.add(new ReleaseAuditEvent(
                actionId,
                fromState,
                requestedState,
                state,
                reasonCode,
                eventType
        ));
        return result;
    }

    private static String illegalReason(StrategyReleaseState from, StrategyReleaseState to) {
        if (from.terminal()) {
            return "RELEASE_TERMINAL_STATE_LOCKED";
        }
        if (to == StrategyReleaseState.PUBLISHED && from != StrategyReleaseState.VERIFIED) {
            return "RELEASE_NOT_VERIFIED";
        }
        return "RELEASE_ILLEGAL_STATE_TRANSITION";
    }

    private static String triggerFor(StrategyReleaseState requestedState) {
        return switch (requestedState) {
            case DRAFT -> "CREATE_DRAFT";
            case CANDIDATE -> "SUBMIT_CANDIDATE";
            case VERIFIED -> "VERIFY_MANIFEST";
            case PUBLISHED -> "PUBLISH_RELEASE";
            case REJECTED -> "REJECT_RELEASE";
            case RETIRED -> "RETIRE_RELEASE";
        };
    }

    private static Map<StrategyReleaseState, Set<StrategyReleaseState>> transitions() {
        Map<StrategyReleaseState, Set<StrategyReleaseState>> transitions =
                new EnumMap<>(StrategyReleaseState.class);
        transitions.put(
                StrategyReleaseState.DRAFT,
                EnumSet.of(StrategyReleaseState.CANDIDATE, StrategyReleaseState.REJECTED)
        );
        transitions.put(
                StrategyReleaseState.CANDIDATE,
                EnumSet.of(StrategyReleaseState.VERIFIED, StrategyReleaseState.REJECTED)
        );
        transitions.put(
                StrategyReleaseState.VERIFIED,
                EnumSet.of(StrategyReleaseState.PUBLISHED, StrategyReleaseState.REJECTED)
        );
        transitions.put(StrategyReleaseState.PUBLISHED, EnumSet.of(StrategyReleaseState.RETIRED));
        return Map.copyOf(transitions);
    }

    private record CachedAction(StrategyReleaseState requestedState, ReleaseTransitionResult result) {
    }
}

final class PrototypeTradingBoundary {

    private PrototypeTradingBoundary() {
    }

    static boolean liveAuthorized(StrategyReleaseState releaseState, ShadowRunStatus shadowStatus) {
        // PRE-GATEX 固定边界：任何 release/shadow 状态组合都不能产生 LIVE authorization。
        return false;
    }
}
