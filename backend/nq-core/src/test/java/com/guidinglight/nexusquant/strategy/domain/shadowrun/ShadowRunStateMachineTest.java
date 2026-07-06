package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShadowRunStateMachineTest {

    private final ShadowRunStateMachine stateMachine = new ShadowRunStateMachine();

    @Test
    void shouldAllowGateRHappyPathTransitions() {
        assertEquals(ShadowRunStatus.PRECHECKING,
                stateMachine.transition(ShadowRunStatus.CREATED, ShadowRunStatus.PRECHECKING));
        assertEquals(ShadowRunStatus.READY,
                stateMachine.transition(ShadowRunStatus.PRECHECKING, ShadowRunStatus.READY));
        assertEquals(ShadowRunStatus.RUNNING,
                stateMachine.transition(ShadowRunStatus.READY, ShadowRunStatus.RUNNING));
        assertEquals(ShadowRunStatus.COMPLETED,
                stateMachine.transition(ShadowRunStatus.RUNNING, ShadowRunStatus.COMPLETED));
    }

    @Test
    void shouldAllowStopAndFailClosedTransitions() {
        assertEquals(ShadowRunStatus.STOP_REQUESTED,
                stateMachine.transition(ShadowRunStatus.RUNNING, ShadowRunStatus.STOP_REQUESTED));
        assertEquals(ShadowRunStatus.STOPPED,
                stateMachine.transition(ShadowRunStatus.STOP_REQUESTED, ShadowRunStatus.STOPPED));
        assertEquals(ShadowRunStatus.BLOCKED,
                stateMachine.transition(ShadowRunStatus.PRECHECKING, ShadowRunStatus.BLOCKED));
        assertEquals(ShadowRunStatus.BLOCKED,
                stateMachine.transition(ShadowRunStatus.RUNNING, ShadowRunStatus.BLOCKED));
        assertEquals(ShadowRunStatus.CANCELLED,
                stateMachine.transition(ShadowRunStatus.READY, ShadowRunStatus.CANCELLED));
    }

    @Test
    void shouldRejectTerminalStateBackToRunningOrReady() {
        for (ShadowRunStatus terminalStatus : ShadowRunStatus.values()) {
            if (!terminalStatus.terminal()) {
                continue;
            }
            ShadowRunStateTransitionException ex = assertThrows(
                    ShadowRunStateTransitionException.class,
                    () -> stateMachine.transition(terminalStatus, ShadowRunStatus.RUNNING)
            );
            assertEquals("SHADOW_RUN_TERMINAL_STATE_LOCKED", ex.reasonCode());
            assertFalse(stateMachine.canTransition(terminalStatus, ShadowRunStatus.READY));
        }
    }

    @Test
    void shouldRejectCompletedBackToRunning() {
        ShadowRunStateTransitionException ex = assertThrows(
                ShadowRunStateTransitionException.class,
                () -> stateMachine.transition(ShadowRunStatus.COMPLETED, ShadowRunStatus.RUNNING)
        );

        assertEquals(ShadowRunStatus.COMPLETED, ex.fromStatus());
        assertEquals(ShadowRunStatus.RUNNING, ex.toStatus());
        assertEquals("SHADOW_RUN_TERMINAL_STATE_LOCKED", ex.reasonCode());
    }

    @Test
    void shouldRejectIllegalNonTerminalTransitionWithExplicitError() {
        ShadowRunStateTransitionException ex = assertThrows(
                ShadowRunStateTransitionException.class,
                () -> stateMachine.transition(ShadowRunStatus.CREATED, ShadowRunStatus.RUNNING)
        );

        assertEquals("SHADOW_RUN_ILLEGAL_STATE_TRANSITION", ex.reasonCode());
        assertTrue(ex.getMessage().contains("CREATED -> RUNNING"));
    }
}
