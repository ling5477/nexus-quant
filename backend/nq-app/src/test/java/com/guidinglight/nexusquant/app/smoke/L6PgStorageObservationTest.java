package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class L6PgStorageObservationTest {
    @TempDir Path directory;

    @Test void wholeFilesystemUnitsAndWalAllocationRemainDistinct() {
        var stat = L6PgStorageObservation.parseStat("tmpfs 4096 65536 8192 8192\n");
        assertEquals(268435456, stat.path("pgTmpfsCapacityBytes").asLong());
        assertEquals(33554432, stat.path("pgTmpfsFreeBytes").asLong());
        assertEquals(234881024, stat.path("pgTmpfsUsedBytes").asLong());
        assertEquals(0.875, stat.path("pgTmpfsUsageRatio").asDouble());
        var allocated = L6PgStorageObservation.parseAllocated("""
                8192 /var/lib/postgresql/data/base
                16777216 /var/lib/postgresql/data/pg_wal
                4096 /var/lib/postgresql/data/global
                16805888 /var/lib/postgresql/data
                """);
        assertEquals(16777216, allocated.path("pgWalAllocatedBytes").asLong());
        assertEquals(16384, allocated.path("pgOtherAllocatedBytes").asLong());
        assertFalse(allocated.has("pgTmpfsUsedBytes"));
    }

    @Test void missingWrongFilesystemInconsistentAndOverflowCountersReject() {
        for (String value : List.of("", "tmpfs", "ext2/ext3 4096 65536 1 1", "tmpfs 0 65536 1 1",
                "tmpfs 4096 0 0 0", "tmpfs 4096 65536 -1 -1", "tmpfs 4096 65536 65537 65537",
                "tmpfs 4096 65536 2 1", "tmpfs 4096 65536 unavailable unavailable",
                "tmpfs 9223372036854775807 65536 1 1", "tmpfs 4096 65536 1 1 trailing")) {
            assertEquals(L6PgStorageObservation.UNAVAILABLE,
                    assertThrows(IllegalStateException.class, () -> L6PgStorageObservation.parseStat(value)).getMessage());
        }
        assertThrows(IllegalStateException.class, () -> L6PgStorageObservation.parseAllocated("1 /unexpected"));
        assertThrows(IllegalStateException.class, () -> L6PgStorageObservation.parseAllocated("1 /var/lib/postgresql/data\n1 /var/lib/postgresql/data"));
    }

    @Test void unavailableStorageRejectsExistingSamplerAndRetainsFailureWithoutZero() throws Exception {
        var clock = new AtomicLong();
        var sampler = new L6ResourceSampler(Map.of("postgres", stamp -> {
            var value = L6PgStorageObservation.parseStat("tmpfs 4096 65536 unavailable unavailable");
            return L6RuntimeResources.measured(stamp, value, L6PgStorageObservation.FIELDS);
        }), Map.of("postgres", L6PgStorageObservation.FIELDS), clock::get, () -> Instant.EPOCH,
                0, L6DurationContract.forMode(false), directory.resolve("storage.ndjson"));
        assertThrows(IllegalStateException.class, sampler::sample);
        assertThrows(IllegalStateException.class, sampler::close);
        var raw = new ObjectMapper().readTree(Files.readString(directory.resolve("storage.ndjson")));
        assertEquals("UNAVAILABLE", raw.path("status").asText());
        assertEquals("UNAVAILABLE", raw.path("sources").path("postgres").path("status").asText());
        assertTrue(raw.toString().contains(L6PgStorageObservation.UNAVAILABLE));
        assertFalse(raw.path("sources").path("postgres").path("values").has("pgTmpfsUsedBytes"));
    }
}
