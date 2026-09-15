package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** busy只接纳精确数据库拒绝；期限和slot消耗保持fail-closed及无追赶。 */
class L6BAdmissionTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void diagnosticTraceRejectsFormalParameters() {
        var p = JSON.createObjectNode().put("mode", L6BContract.MODE).put("diagnosticOnly", true)
                .put("formalTimerStarted", false).put("probe", false);
        assertThrows(IllegalStateException.class, () -> L6BRecoveryTrace.requireDiagnostic(p));
        p.put("probe", true); L6BRecoveryTrace.requireDiagnostic(p);
        p.put("formalTimerStarted", true);
        assertThrows(IllegalStateException.class, () -> L6BRecoveryTrace.requireDiagnostic(p));
    }

    @Test void onlyExactBusyRollbackIsClassified() {
        assertTrue(L6BAdmission.isConfirmedBusy(new IllegalStateException(sql("55000", "strategy_run_active"))));
        assertFalse(L6BAdmission.isConfirmedBusy(sql("23505", "strategy_run_active")));
        assertFalse(L6BAdmission.isConfirmedBusy(sql("55000", "strategy_run_active_other")));
        assertFalse(L6BAdmission.isConfirmedBusy(new IllegalStateException("strategy_run_active")));
        assertFalse(L6BAdmission.isConfirmedBusy(new java.sql.SQLException("strategy_run_active", "55000")));
    }

    @Test void busyRunCannotOutliveBoundAndTerminalAllowsAdmission() throws Exception {
        var rows = JSON.createArrayNode();
        rows.addObject().put("strategy_run_id", "run-a").put("strategy_id", "l6-strategy-2").put("status", "RUNNING");
        var gate = new L6BAdmission(row -> { });
        assertTrue(gate.observe(rows, 0, 395, 0));
        long bound = L6BAdmission.recoveryBoundNanos(395);
        assertTrue(gate.observe(rows, 0, 395, bound - 1));
        assertEquals("RESTART_CONTINUITY_NOT_CONVERGED", assertThrows(IllegalStateException.class,
                () -> gate.observe(rows, 0, 395, bound)).getMessage());
        assertThrows(IllegalStateException.class, () -> gate.observe(JSON.createArrayNode(), 0, 395, bound + 1));
        assertFalse(new L6BAdmission(row -> { }).observe(JSON.createArrayNode(), 0, 395, bound + 1));
    }

    @Test void busySlotIsConsumedWithoutResendOrCatchUp() throws Exception {
        var now = new AtomicLong(0);
        long interval = 7_117_650_000L;
        var duration = new L6DurationContract(10_000_000_000L, 40_000_000_000L, 10_000_000_000L);
        var pacer = new L6DeterministicPacer(now::get, 0, interval, duration);
        var rows = new ArrayList<L6DeterministicPacer.Slot>();
        var attempted = new ArrayList<Long>();
        pacer.poll(false, slot -> { attempted.add(slot); throw new L6DeterministicPacer.AdmissionBusy(); }, rows::add);
        assertEquals("PAUSED_BACKPRESSURE", rows.getFirst().decision());
        assertEquals("STRATEGY_RUN_ACTIVE", rows.getFirst().reason());
        now.set(interval - 1); pacer.poll(false, slot -> { throw new AssertionError("same slot resent"); }, rows::add);
        now.set(interval); pacer.poll(false, slot -> { attempted.add(slot); return "order-b"; }, rows::add);
        assertEquals(java.util.List.of(0L, 1L), attempted);
        now.set(4 * interval); pacer.poll(false, slot -> "order-c", rows::add);
        assertEquals(2, rows.stream().filter(r -> "EMITTED".equals(r.decision())).count());
        assertEquals(4, rows.getLast().slotIndex());
        assertTrue(rows.getLast().actualElapsed() - rows.get(1).actualElapsed() >= interval);
    }

    private static PSQLException sql(String state, String message) {
        return new PSQLException(new ServerErrorMessage("SERROR\0C" + state + "\0M" + message + "\0\0"));
    }
}
