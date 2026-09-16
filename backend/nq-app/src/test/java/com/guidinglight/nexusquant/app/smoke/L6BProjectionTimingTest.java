package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 缺槽不伪造600秒增长窗口，容量及库存安全条件保持拒绝。 */
class L6BProjectionTimingTest {
    private ObjectNode row(long at,long capacity,long orders,String status) {
        var r=new ObjectMapper().createObjectNode().put("sampleType","PERIODIC").put("status","MEASURED")
                .put("scheduledElapsedMillis",at).put("slotStatus",status);
        r.putObject("sources").putObject("postgres").putObject("values").put("pgTmpfsCapacityBytes",capacity)
                .put("pgTmpfsUsedBytes",48_910_336).put("pgTmpfsFreeBytes",capacity-48_910_336).put("orders",orders).put("backlog",0);
        return r;
    }
    @Test void isolatedOverrunAndGapContinueWithoutChangingModelOrInventorySafety() throws Exception {
        var c=L6BContract.inspectFormalCandidate();long capacity=c.deriveCapacity(48_910_336);
        var guard=new L6BProjectionGuard(c,capacity);
        guard.observe(row(0,capacity,0,"VALID"),1_000_000_000L);
        guard.observe(row(10_000,capacity,1,"SLOT_OVERRUN"),21_000_000_000L);
        guard.observe(row(30_000,capacity,2,"VALID"),31_000_000_000L);
        assertEquals(1,guard.evidence().path("samples").get(2).path("growthWindowPoints").asInt());
        assertFalse(guard.stopped());assertEquals(c.base.rate(0),guard.evidence().path("samples").get(2).path("warmupRate").asLong());
        assertThrows(IllegalStateException.class,()->guard.observe(row(40_000,capacity,1,"VALID"),41_000_000_000L));
        assertThrows(IllegalStateException.class,()->guard.observe(row(40_000,capacity+1,3,"VALID"),41_000_000_000L));
    }
    @Test void lastSlotOverrunDoesNotExpandDurationOrNeedDomain() throws Exception {
        var c=L6BContract.inspectFormalCandidate();long capacity=c.deriveCapacity(48_910_336);
        var guard=new L6BProjectionGuard(c,capacity);
        guard.observe(row(0,capacity,0,"VALID"),1_000_000_000L);
        guard.observe(row(10_790_000,capacity,1000,"SLOT_OVERRUN"),10_801_000_000_000L);
        assertEquals(guard.need(10_790_000_000_000L,1000,0),guard.evidence().path("samples").get(1).path("projectedRemainingStorageNeed").asLong());
    }
}
