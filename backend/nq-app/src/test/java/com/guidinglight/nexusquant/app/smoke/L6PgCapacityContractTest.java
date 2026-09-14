package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class L6PgCapacityContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;
    static L6PgCapacityContract contract() { return L6PgCapacityContract.read(B0Processes.root().resolve(L6PgCapacityContract.CANONICAL)); }
    @Test void rawReplayGoldenIsDeterministicAndMatchesEntireCanonicalContract() throws Exception {
        String script = B0Processes.root().resolve("backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/l6_capacity_calculator.py").toString();
        String first = B0Processes.command("python", "-X", "utf8", script, B0Processes.root().toString(), L6FormalManifest.CANONICAL.toString(), contract().sourceRelative());
        assertEquals(first, B0Processes.command("python", "-X", "utf8", script, B0Processes.root().toString(), L6FormalManifest.CANONICAL.toString(), contract().sourceRelative()));
        var calculated = JSON.readTree(first);
        assertEquals(JSON.readTree(B0Processes.root().resolve(L6PgCapacityContract.CANONICAL).toFile()), calculated);
        var p = calculated.path("projection");
        assertEquals(1_304_428_544L, p.path("requiredCapacityBytes").asLong());
        assertEquals(1_000_225_764L, p.path("projectedFormalEndPeakBytes").asLong());
        assertEquals(304_052_400, p.path("reserveBytes").asLong());
        assertEquals(731_429_368, p.path("projected40minActiveGrowthBytes").asLong());
        assertEquals(532_197_125, p.path("crossChecks").path("inventoryTimeAdjustedPerChainActiveBytes").asLong());
        assertEquals(p.path("crossChecks").path("inventoryTimeAdjustedPerChainActiveBytes"), p.path("crossChecks").path("inventoryTimeAdjustedPhaseActiveBytes"));
    }
    @Test void replayRejectsCorruptionIncompleteSourceAndManifestDrift() throws Exception {
        assertTrue(B0Processes.command("python", "-X", "utf8", B0Processes.root().resolve(
                "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/l6_capacity_calculator_test.py").toString(),
                B0Processes.root().toString(), L6FormalManifest.CANONICAL.toString(), contract().sourceRelative()).contains("OK"));
    }
    @Test void missingMalformedExtraFieldsWrongScopeAndInconsistentFormulaReject() throws Exception {
        assertThrows(IllegalStateException.class, () -> L6PgCapacityContract.read(temporary.resolve("missing")));
        String original = Files.readString(B0Processes.root().resolve(L6PgCapacityContract.CANONICAL));
        for (String invalid : new String[]{"{}", "null", original + "{}", original.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1"),
                original.replace("L6_A_60MIN", "L6_B_180MIN"), original.replace("1304428544", "1304428545"),
                original.replace("7117650000", "7117650001"), original.replace("\"maximumFraction\": 0.6", "\"maximumFraction\": 0.7"),
                original.replace("\"status\": \"ACCEPTED\"", "\"status\": \"DRAFT\""), original.replace("\"schemaVersion\": 1", "\"unexpected\": 1, \"schemaVersion\": 1")}) {
            Path path = temporary.resolve("invalid.json"); Files.writeString(path, invalid);
            assertThrows(IllegalStateException.class, () -> L6PgCapacityContract.read(path));
        }
    }
    @Test void localReadDoesNotGrantCommittedAuthorityAndExitDriftRejects() throws Exception {
        Path copy = temporary.resolve("capacity.json"); Files.copy(B0Processes.root().resolve(L6PgCapacityContract.CANONICAL), copy);
        var c = L6PgCapacityContract.read(copy);
        assertThrows(Exception.class, () -> L6PgCapacityContract.requireCommitted(copy));
        Files.writeString(copy, "{}"); assertThrows(Exception.class, c::verifyUnchanged);
    }
    @Test void exitIdentityFailureRetainsGuardAndCleanupEvidenceAndStillRejects() throws Exception {
        var proof = JSON.createObjectNode().put("cleanup", "PASS").put("result", "FORMAL_MEASURED_PENDING_QUALIFICATION");
        proof.putObject("storageProjectionGuard").put("triggered",true);
        Path output = temporary.resolve("exit-proof.json");
        assertThrows(IllegalStateException.class, () -> L6FormalRuntime.persistExit(output,proof,
                () -> { throw new IllegalStateException("identity drift"); },0));
        var saved = JSON.readTree(output.toFile());
        assertEquals("BLOCKED / L6_FROZEN_INPUT_IDENTITY_DRIFT",saved.path("result").asText());
        assertTrue(saved.path("storageProjectionGuard").path("triggered").asBoolean());
        assertEquals("PASS",saved.path("cleanup").asText()); assertEquals(0,saved.path("ownedNqRemaining").asLong());
    }
    @Test void boundedL5AndFormalScopeRemainSeparate() {
        assertEquals(256L*1_048_576, B0Processes.Pg.defaultTmpfsBytes());
        assertEquals(1244L*1_048_576, contract().capacity());
        assertEquals(2012L*1_048_576, contract().pgMemory());
        assertEquals(7_117_650_000L, contract().interval());
        assertEquals(10_000, L6ResourceSampler.INTERVAL_MILLIS);
        assertEquals(3_600_000_000_000L, L6DurationContract.forMode(false).total());
    }
    @Test void sixtyPercentBoundaryAndNegativeNeverReachRuntime() throws Exception {
        var c = contract(); long budget = L6HostMemoryPreflight.budget(c);
        assertEquals(6364L*1_048_576, budget);
        long minimumAvailable = Math.ceilDiv(budget*5, 3);
        var called = new AtomicBoolean();
        L6HostMemoryPreflight.beforeRuntime(c, new L6HostMemoryPreflight.Entry(minimumAvailable, 512L*1_048_576, 512L*1_048_576), p -> {
            assertFalse(p.path("pgStarted").asBoolean()); assertEquals(0, p.path("orders").asInt()); called.set(true);
        });
        assertTrue(called.get()); called.set(false);
        var error = assertThrows(IllegalStateException.class, () -> L6HostMemoryPreflight.beforeRuntime(c,
                new L6HostMemoryPreflight.Entry(minimumAvailable-1, 512L*1_048_576, 512L*1_048_576), p -> called.set(true)));
        assertEquals(L6HostMemoryPreflight.EXCEEDED, error.getMessage()); assertFalse(called.get());
        ObjectNode divisible = (ObjectNode) JSON.readTree(B0Processes.root().resolve(L6PgCapacityContract.CANONICAL).toFile());
        ((ObjectNode) divisible.path("hostMemory")).put("nativeAndToolsBudgetBytes", c.memory("nativeAndToolsBudgetBytes") + (3-budget%3)%3);
        Path path = temporary.resolve("exact.json"); Files.writeString(path, divisible.toString()); var exact = L6PgCapacityContract.read(path);
        long exactBudget = L6HostMemoryPreflight.budget(exact);
        assertEquals(0, exactBudget%3);
        assertEquals("PASS", L6HostMemoryPreflight.verify(exact, new L6HostMemoryPreflight.Entry(exactBudget/3*5, 512L*1_048_576, 512L*1_048_576)).path("status").asText());
    }
    @Test void unboundedOrUnavailableHeapsFailBeforeRuntime() {
        var c = contract();
        for (var entry : new L6HostMemoryPreflight.Entry[]{new L6HostMemoryPreflight.Entry(Long.MAX_VALUE, 0, 1),
                new L6HostMemoryPreflight.Entry(Long.MAX_VALUE, 1, 1024L*1_048_576), new L6HostMemoryPreflight.Entry(0, 1, 1)})
            assertThrows(IllegalStateException.class, () -> L6HostMemoryPreflight.verify(c, entry));
    }
}
