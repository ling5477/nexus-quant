package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class L6PgProjectionGuardTest {
    static ObjectNode sample(long at, long free, long orders, long backlog) {
        var n = new ObjectMapper().createObjectNode().put("sampleType", "PERIODIC").put("status", "MEASURED").put("scheduledElapsedMillis", at);
        n.putObject("sources").putObject("postgres").putObject("values")
                .put("pgTmpfsCapacityBytes", L6PgCapacityContractTest.run(L6PgCapacityContractTest.contract()).capacity())
                .put("pgTmpfsUsedBytes", L6PgCapacityContractTest.run(L6PgCapacityContractTest.contract()).capacity()-free).put("pgTmpfsFreeBytes", free)
                .put("orders", orders).put("backlog", backlog);
        return n;
    }
    @Test void freeEqualityContinuesOneByteBelowStopsBothAdmissionAndDispatch() throws Exception {
        var c = L6PgCapacityContractTest.contract(); var safe = new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c)); long need = safe.need(0, 0, 0);
        safe.observe(sample(0, need, 0, 0), 0); assertTrue(safe.producerAllowed());
        var risky = new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c)); risky.observe(sample(0, need-1, 0, 0), 0);
        assertFalse(risky.producerAllowed()); var dispatched = new AtomicBoolean();
        assertThrows(L6PgProjectionGuard.ProducerStopped.class, () -> risky.produce(() -> dispatched.set(true)));
        assertFalse(dispatched.get()); assertFalse(risky.drainExpired(19_999_999_999L)); assertTrue(risky.drainExpired(20_000_000_000L));
        var pacer = new L6DeterministicPacer(() -> 0, 0, c.interval(), L6DurationContract.forMode(false), risky::producerAllowed);
        pacer.poll(false, slot -> { dispatched.set(true); return "unexpected"; }, slot -> assertEquals("SKIPPED_PHASE", slot.decision()));
        assertFalse(dispatched.get());
        assertEquals(c.reserve()+20*c.rate(2), risky.need(0, 0, 0));
        assertEquals(L6PgProjectionGuard.RESULT, risky.evidence().path("result").asText());
    }
    @Test void drainUsesExistingInventoryAndBacklogOnly() {
        var c = L6PgCapacityContractTest.contract(); var guard = new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c));
        assertEquals(c.reserve()+600*11*c.rate(2), guard.need(3_000_000_000_000L, 10, 0));
        assertEquals(c.backlogUnit()*2, guard.need(3_000_000_000_000L, 10, 2)-guard.need(3_000_000_000_000L, 10, 0));
        assertEquals(c.reserve(), guard.need(3_600_000_000_000L, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> guard.need(1, 0, 1));
    }
    @Test void noSpaceForEmergencyDrainDisablesFurtherWritesImmediately() {
        var c=L6PgCapacityContractTest.contract(); var guard=new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c));
        guard.observe(sample(0,0,0,0),0);
        assertFalse(guard.producerAllowed()); assertTrue(guard.drainExpired(0));
        assertTrue(guard.evidence().path("drainBudgetUnavailable").asBoolean());
    }
    @Test void singleDeltaDoesNotChangeRateButCompleteComparableWindowCanRaiseIt() {
        var c = L6PgCapacityContractTest.contract(); var guard = new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c));
        for (int i=0;i<=120;i++) {
            long used = i < 60 ? 0 : (i-60)*20_000;
            guard.observe(sample(i*10_000L, L6PgCapacityContractTest.run(c).capacity()-used, 0, 0), i*10_000_000_000L);
            if (i==61) assertEquals(c.rate(1), guard.evidence().path("samples").get(i).path("activeRate").asLong());
        }
        assertEquals(2000, guard.evidence().path("samples").get(120).path("activeRate").asLong());
    }
    @Test void missingCadenceCapacityAndStaleSamplesFailClosed() {
        var c = L6PgCapacityContractTest.contract();
        var missing = sample(0, L6PgCapacityContractTest.run(c).capacity(), 0, 0); ((ObjectNode)missing.path("sources").path("postgres").path("values")).remove("pgTmpfsFreeBytes");
        assertThrows(IllegalStateException.class, () -> new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c)).observe(missing,0));
        assertThrows(IllegalStateException.class, () -> new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c)).observe(sample(10_000,L6PgCapacityContractTest.run(c).capacity(),0,0),10_000_000_000L));
        assertThrows(IllegalStateException.class, () -> new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c)).observe(sample(0,L6PgCapacityContractTest.run(c).capacity(),0,0),11_000_000_000L));
        var drift = sample(0,L6PgCapacityContractTest.run(c).capacity(),0,0); ((ObjectNode)drift.path("sources").path("postgres").path("values")).put("pgTmpfsCapacityBytes",L6PgCapacityContractTest.run(c).capacity()+1);
        assertThrows(IllegalStateException.class, () -> new L6PgProjectionGuard(c, L6PgCapacityContractTest.run(c)).observe(drift,0));
    }
}
