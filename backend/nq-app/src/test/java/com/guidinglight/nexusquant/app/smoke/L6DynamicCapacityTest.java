package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class L6DynamicCapacityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void sameModelDifferentBaselinesDeterministicCapacityAndEntryMismatchRejects() {
        var model = L6PgCapacityContractTest.contract();
        var a = L6PgRunCapacity.derive(model, 48_910_336);
        var b = L6PgRunCapacity.derive(model, 48_910_336 + 4*L6PgCapacityContract.MIB);
        assertEquals(a.capacity()+4*L6PgCapacityContract.MIB, b.capacity());
        assertEquals(a.evidence(model), L6PgRunCapacity.derive(model, 48_910_336).evidence(model));
        assertEquals(a.evidence(model).path("modelContractSha"), b.evidence(model).path("modelContractSha"));
        assertEquals("PASS", a.confirmEntry(model, 48_910_336, a.capacity()).path("preflightResult").asText());
        assertThrows(IllegalStateException.class, () -> a.confirmEntry(model, 48_910_336+2*L6PgCapacityContract.MIB, a.capacity()));
        assertThrows(IllegalStateException.class, () -> a.confirmEntry(model, 48_910_336, a.capacity()+L6PgCapacityContract.MIB));
        assertThrows(IllegalArgumentException.class, () -> L6PgRunCapacity.derive(model, 0));
        assertThrows(ArithmeticException.class, () -> L6PgRunCapacity.derive(model, Long.MAX_VALUE));
    }

    @Test void originalTenSecondRawTriggerReproducesExactlyAndDerivedBurstAllocationClosesIt() throws Exception {
        var model = L6PgCapacityContractTest.contract();
        var path = B0Processes.root().resolve(L6PgCapacityContract.CANONICAL)
                .resolveSibling("runs").resolve("L6_DYNAMIC_PG_TMPFS_20260914/original-trigger-replay.zip");
        try (var zip = new ZipFile(path.toFile())) {
            var identity = JSON.readTree(zip.getInputStream(zip.getEntry("identity.json")));
            byte[] bytes = zip.getInputStream(zip.getEntry("resources.ndjson")).readAllBytes();
            assertEquals(identity.path("resources.ndjson").asText(), L6PgCapacityContract.hash(bytes));
            var rows = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).lines().map(line -> {
                try { return (ObjectNode) JSON.readTree(line); } catch (Exception error) { throw new IllegalStateException(error); }
            }).toList();
            var first = rows.getFirst().path("sources").path("postgres").path("values");
            assertEquals(48_910_336, first.path("pgTmpfsUsedBytes").asLong());
            var run = L6PgRunCapacity.derive(model, first.path("pgTmpfsUsedBytes").asLong());
            var guard = new L6PgProjectionGuard(model, run);
            long previousNeed = guard.need(0, 0, 0);
            assertEquals(150_380, first.path("pgTmpfsFreeBytes").asLong()-previousNeed);
            var trigger = rows.get(1).path("sources").path("postgres").path("values");
            long need = guard.need(10_000_000_000L, trigger.path("orders").asLong(), trigger.path("backlog").asLong());
            assertEquals(1_255_256_871, need);
            assertEquals(-279_335, trigger.path("pgTmpfsFreeBytes").asLong()-need);
            assertEquals(110_957, previousNeed-need);
            assertEquals(540_672, trigger.path("pgTmpfsUsedBytes").asLong()-first.path("pgTmpfsUsedBytes").asLong());
            assertEquals(17_473_536, model.burst());
            for (int i=0; i<2; i++) {
                var row = rows.get(i).deepCopy();
                var values = (ObjectNode) row.path("sources").path("postgres").path("values");
                values.put("pgTmpfsCapacityBytes", run.capacity());
                values.put("pgTmpfsFreeBytes", run.capacity()-values.path("pgTmpfsUsedBytes").asLong());
                guard.observe(row, i == 0 ? 534_675_800L : 10_442_928_500L);
                assertFalse(guard.stopped());
            }
        }
    }

    @Test void remainingNeedNeverIncludesBaselineOrAlreadyElapsedGrowth() {
        var c = L6PgCapacityContractTest.contract();
        var a = new L6PgProjectionGuard(c, L6PgRunCapacity.derive(c, 48_910_336));
        var b = new L6PgProjectionGuard(c, L6PgRunCapacity.derive(c, 80_000_000));
        assertEquals(c.growth(0)+c.growth(1)+c.growth(2)+c.reserve(), a.need(0,0,0));
        for (long at : new long[]{0, 600_000_000_000L, 1_200_000_000_000L, 3_000_000_000_000L}) {
            assertEquals(a.need(at, 10, 0), b.need(at, 10, 0));
        }
        assertTrue(a.need(600_000_000_000L, 10, 0) > a.need(1_200_000_000_000L, 10, 0));
        assertEquals(c.backlogUnit(), a.need(1_200_000_000_000L, 10, 1)-a.need(1_200_000_000_000L, 10, 0));
    }
}
