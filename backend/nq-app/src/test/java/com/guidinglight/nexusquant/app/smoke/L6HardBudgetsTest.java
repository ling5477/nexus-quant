package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 完整3600秒投影只推进模型时钟；真实run另行授权，异常放大不能靠增加总cap绕过。 */
class L6HardBudgetsTest {
    static ArrayNode actors() {
        var actors = new ObjectMapper().createArrayNode();
        for (int i = 0; i < 2; i++) actors.addObject().put("tickStarted", 3).put("commands", 4).put("poolMax", 10);
        return actors;
    }
    static L6HardBudgets budget(long baseline) {
        return new L6HardBudgets(L6DurationContract.forMode(false), 7_117_650_000L,
                new QualificationCapacity(QualificationCapacity.Mode.L6_FORMAL, 422, 422, 422), baseline, 100L << 30, actors());
    }
    @Test void entireFormalScheduleFitsWithoutChangingCapsAtRuntime() {
        var b = budget(500_000); var frozen = b.evidence();
        long total = 500_000;
        for (int seconds = 0; seconds <= 3600; seconds += 10) {
            // 满100候选的最坏双actor扫描，另加订单、timer、只读观测；包括完整DRAIN。
            if (seconds > 0) total += 4 * 701 + (seconds <= 3000 ? 80 : 0) + 32;
            b.check(seconds * 1000L, total);
        }
        assertTrue(total - 500_000 <= frozen.path("transactionHardCap").asLong());
        assertEquals(frozen.path("transactionHardCap"), b.evidence().path("transactionHardCap"));
        assertTrue(frozen.path("projectedRawBytes").asLong() + frozen.path("rawReserveBytes").asLong() < L6HardBudgets.RAW_CAP);
        assertEquals(422, frozen.path("orders").asInt()); assertEquals(360, frozen.path("resourceSamples").asInt());
        assertEquals(61, frozen.path("checkpointCountUpper").asInt());
    }
    @Test void trueAmplificationRejectedWellBeforeTotalCap() {
        var b = budget(100);
        for (int i = 0; i < 5; i++) b.check(i * 10_000L, 100 + i * 10_000);
        var failure = assertThrows(IllegalStateException.class, () -> b.check(50_000, 50_100));
        assertTrue(failure.getMessage().contains("RATE_AMPLIFICATION"));
        assertThrows(IllegalStateException.class, () -> b.check(60_000, 50_101));
    }
    @Test void totalCapBaselineRegressionAndMissingInputsReject() {
        var b = budget(900_000);
        b.check(0, 900_000);
        assertThrows(IllegalStateException.class, () -> b.check(10_000, 899_999));
        var cap = budget(100);
        assertThrows(IllegalStateException.class, () -> cap.check(0, 101 + cap.evidence().path("transactionHardCap").asLong()));
        assertThrows(IllegalStateException.class, () -> budget(-1));
        assertThrows(IllegalStateException.class, () -> budget(0).admission(new ObjectMapper().createObjectNode(), new ObjectMapper().createObjectNode()));
        assertThrows(IllegalStateException.class, () -> new L6HardBudgets(L6DurationContract.forMode(false), 7_117_650_000L,
                new QualificationCapacity(QualificationCapacity.Mode.L6_FORMAL, 422, 422, 422), 0, 1L << 30, actors()));
        var late = actors(); ((com.fasterxml.jackson.databind.node.ObjectNode) late.get(0)).put("tickStarted", 300);
        assertThrows(IllegalStateException.class, () -> new L6HardBudgets(L6DurationContract.forMode(false), 7_117_650_000L,
                new QualificationCapacity(QualificationCapacity.Mode.L6_FORMAL, 422, 422, 422), 0, 100L << 30, late));
    }
    @Test void finalOracleCannotReorderIndependentSamplerClock() {
        var b = budget(100);
        b.check(50_000, 200); b.checkFinal(300); b.check(60_000, 290);
        assertTrue(b.evidence().path("failure").isNull());
        assertEquals(300, b.evidence().path("finalAbsoluteTransactions").asLong());
    }
}
