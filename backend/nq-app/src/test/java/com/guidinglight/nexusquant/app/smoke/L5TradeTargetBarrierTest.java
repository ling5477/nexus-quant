package com.guidinglight.nexusquant.app.smoke;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 身份负例不能消耗目标的一次性认领权；等待超时不产生任何进程终止动作。 */
class L5TradeTargetBarrierTest {
    @Test void exactIdentityAndOwnerClaimOnce() {
        var barrier = new L5TradeTargetBarrier(12, "fill-85", "trace-85");
        assertFalse(barrier.claim(12, "fill-84", "trace-85"));
        assertFalse(barrier.claim(12, "fill-85", "trace-84"));
        assertFalse(barrier.claim(13, "fill-85", "trace-85"));
        assertTrue(barrier.claim(12, "fill-85", "trace-85"));
        assertFalse(barrier.claim(12, "fill-85", "trace-85"));
        barrier.release();
        assertFalse(barrier.claim(12, "fill-85", "trace-85"));
    }

    @Test void boundedWaitAndIdempotentRelease() throws Exception {
        var barrier = new L5TradeTargetBarrier(12, "fill-85", "trace-85");
        assertFalse(barrier.awaitRelease(1, TimeUnit.MILLISECONDS));
        barrier.release(); barrier.release();
        assertTrue(barrier.awaitRelease(0, TimeUnit.MILLISECONDS));
    }

    @Test void futureBoundaryIdentityCanBeScopedWithoutInjectingFaults() {
        // 只证明身份表达，不证明未来连接/提交边界已接线，更不计F3/F4资格。
        for (String boundary : List.of("DB_CONNECTION_LOSS", "COMMIT_RESPONSE_LOSS", "RECOVERY_PRESSURE")) {
            var barrier = new L5TradeTargetBarrier(12, "fill-85", boundary + ":trace-85");
            assertFalse(barrier.claim(12, "fill-85", "OTHER:trace-85"));
            assertTrue(barrier.claim(12, "fill-85", boundary + ":trace-85"));
            assertFalse(barrier.claim(12, "fill-85", boundary + ":trace-85"));
            barrier.release();
        }
    }

    @Test void cleanEntryRejectsTargetControls() {
        boolean previous = L5QualificationControls.repeatedFault;
        try {
            for (String command : List.of("L5_TARGET_ARM", "L5_TARGET_RELEASE", "L5C_PAUSE", "L5C_RESUME", "L5C_TARGET_STEP", "L5C_TARGET_STATUS")) {
                L5QualificationControls.repeatedFault = false;
                assertThrows(IllegalArgumentException.class, () -> L5QualificationControls.validateCommand(command));
                L5QualificationControls.repeatedFault = true;
                L5QualificationControls.validateCommand(command);
            }
        } finally { L5QualificationControls.repeatedFault = previous; }
    }
}
