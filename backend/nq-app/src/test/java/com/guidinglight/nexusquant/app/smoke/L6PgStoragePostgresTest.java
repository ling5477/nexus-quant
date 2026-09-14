package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 只证明原256MiB owned PG的真实观测，不伪造未知的L6派生容量或正式运行资格。 */
@EnabledIfSystemProperty(named = "nq.l6.storage", matches = "true")
class L6PgStoragePostgresTest {
    @Test void defaultTmpfsReadWriteAndFreshTenSecondMeasurements() throws Exception {
        var json = new ObjectMapper();
        var dir = B0Processes.root().resolve("backend/nq-app/target/l6-pg-storage/" + UUID.randomUUID());
        Files.createDirectories(dir);
        var proof = json.createObjectNode().put("scope", "DEFAULT_256M_OBSERVATION_SMOKE_ONLY")
                .put("derivedFormalCapacityAccepted", false).put("formalTimerStarted", false).put("ordersCreated", 0);
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long available = os.getFreeMemorySize();
        // tmpfs 已受 cgroup memory 约束；这里再次计入其最大值，采用保守的重复预算。
        long budget = Math.addExact(Runtime.getRuntime().maxMemory(), (768L + 256L) * 1024 * 1024);
        proof.put("availableHostMemoryAtEntry", available).put("controllerMaxHeap", Runtime.getRuntime().maxMemory())
                .put("smokeOwnedBudgetBytes", budget).put("hostBudgetIncludesTmpfsAgainConservatively", true);
        assertTrue(available > 0 && budget <= available / 5 * 3, "smoke host memory exceeds 60 percent");
        String container = null;
        try {
            try (var pg = B0Processes.Pg.startBounded()) {
                container = pg.ownedContainerId(); proof.put("container", container);
                try (var writer = DriverManager.getConnection(pg.ownedUrl(), "postgres", ""); var statement = writer.createStatement()) {
                    statement.execute("CREATE ROLE l6_storage_reader LOGIN");
                    statement.execute("GRANT CONNECT ON DATABASE postgres TO l6_storage_reader");
                    try (var reader = DriverManager.getConnection(pg.ownedUrl(), "l6_storage_reader", "")) {
                        proof.put("measurementRole", "l6_storage_reader").put("measurementRoleSuperuser", false);
                        var initial = L6PgStorageObservation.collect(reader, container, B0Processes::command);
                        assertEquals(256L * 1024 * 1024, initial.path("pgTmpfsCapacityBytes").asLong());
                        proof.set("before", initial);
                        statement.execute("CREATE TABLE l6_storage_probe(id integer PRIMARY KEY, payload text)");
                        statement.execute("ALTER TABLE l6_storage_probe ALTER COLUMN payload SET STORAGE EXTERNAL");
                        statement.execute("INSERT INTO l6_storage_probe SELECT i, repeat(md5(i::text),256) FROM generate_series(1,16) i");
                        statement.execute("GRANT SELECT ON l6_storage_probe TO l6_storage_reader");
                        try (var check = reader.createStatement(); var permissions = check.executeQuery(
                                "SELECT has_table_privilege(current_user,'l6_storage_probe','SELECT'), "
                                        + "has_table_privilege(current_user,'l6_storage_probe','INSERT'), "
                                        + "(SELECT rolsuper FROM pg_roles WHERE rolname=current_user)")) {
                            assertTrue(permissions.next()); assertTrue(permissions.getBoolean(1));
                            assertFalse(permissions.getBoolean(2)); assertFalse(permissions.getBoolean(3));
                        }
                        try (var rows = statement.executeQuery("SELECT count(*),min(length(payload)) FROM l6_storage_probe")) {
                            assertTrue(rows.next()); assertEquals(16, rows.getInt(1)); assertEquals(8192, rows.getInt(2));
                        }
                        String owned = container; long start = System.nanoTime();
                        try (var sampler = new L6ResourceSampler(Map.of("postgres", stamp ->
                                L6RuntimeResources.measured(stamp, L6PgStorageObservation.collect(reader, owned, B0Processes::command),
                                        L6PgStorageObservation.FIELDS)), Map.of("postgres", L6PgStorageObservation.FIELDS),
                                System::nanoTime, Instant::now, start, new L6DurationContract(10_000_000_000L, 10_000_000_000L, 10_000_000_000L),
                                dir.resolve("resources.ndjson"))) {
                            sampler.start();
                            while (System.nanoTime() - start < 30_000_000_000L) {
                                sampler.checkHealthy(); TimeUnit.MILLISECONDS.sleep(100);
                            }
                            sampler.requireComplete(); proof.set("sampling", sampler.summary());
                            var last = sampler.latest().path("sources").path("postgres").path("values");
                            assertTrue(last.path("pgTmpfsUsedBytes").asLong() > initial.path("pgTmpfsUsedBytes").asLong());
                            assertTrue(last.path("pgRelationStorage").findValuesAsText("relation").contains("l6_storage_probe"));
                            proof.set("after", last);
                        }
                    }
                }
            }
            assertTrue(B0Processes.command("docker", "ps", "-a", "--filter", "id=" + container, "--format", "{{.ID}}").isBlank());
            proof.put("cleanup", "PASS").put("ownedPgRemaining", 0).put("result", "OBSERVATION_SMOKE_PASS");
        } finally {
            Files.writeString(dir.resolve("proof.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            System.out.println("L6_PG_STORAGE_ROOT " + dir);
        }
    }
}
