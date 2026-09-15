package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** 独立进程单位成本实验；不运行正式时钟，也不产生qualification资格。 */
@EnabledIfSystemProperty(named = "nq.l6.transactions", matches = "true")
class L6TransactionProcessTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Test void realTransactionsAndReplayUnitCosts() throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/l6-transactions/" + UUID.randomUUID());
        Files.createDirectories(dir); System.out.println("L6_TRANSACTION_PROOF " + dir);
        var manifest = L6FormalManifest.read(B0Processes.root().resolve(L6FormalManifest.CANONICAL));
        var parameters = JSON.createObjectNode().put("mode", "FORMAL_L6_A").put("shortSmoke", false)
                .put("diagnosticUnitCostOnly", true).put("formalTimerStarted", false);
        parameters.set("manifestEntry", manifest.identity()); Files.writeString(dir.resolve("parameters.json"), parameters.toString());
        var proof = JSON.createObjectNode().put("formalTimerStarted", false).put("scope", "TRANSACTION_UNIT_COST_ONLY");
        var actors = new ArrayList<B0Processes.Child>();
        try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(L6FormalVenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            L6PgBaselinePreflight.initialize(fixture, endpoint, env);
            try (var statistics = java.sql.DriverManager.getConnection(pg.ownedUrl(), "postgres", "");
                 var rawReader = fixture.checker(); var reader = L6TransactionAccounting.wrap(rawReader, "CONTROLLER_HELPER")) {
                String statisticsSql = "SELECT xact_commit+xact_rollback FROM pg_stat_database WHERE datname='" + fixture.name() + "'";
                Thread.sleep(1200);
                proof.put("serverBeforeActors", number(statistics, statisticsSql));
                proof.set("controllerBeforeActors", L6TransactionAccounting.snapshot());
                try {
                    for (int i = 0; i < 2; i++) actors.add(new B0Processes.Child(L6NqProcessMain.class, dir, "nq-" + i, env).awaitReady());
                    http(endpoint, "L5_OPEN");
                    proof.set("before", snapshot(reader, actors));
                    proof.set("paper", JSON.readTree(actors.getFirst().send("L6_PAPER")));
                    var slots = new ArrayList<L6DeterministicPacer.Slot>();
                    for (int goal : List.of(1, 2, 10)) {
                        while (slots.size() < goal) {
                            int index = slots.size(); var actor = actors.get(index % 2);
                            long clock = Long.parseLong(actor.send("L6_CLOCK")); long begin = System.nanoTime();
                            JsonNode emitted = JSON.readTree(actor.send("L6_EMIT " + index + " " + (clock + 60_000_000_000L)));
                            slots.add(new L6DeterministicPacer.Slot(index, begin, begin, "EMITTED", 0, emitted.path("logicalOrderId").asText(), System.nanoTime(), "UNIT_COST_DIAGNOSTIC"));
                            http(endpoint, "FILL");
                            for (var a : actors) a.send("L6_RECONCILE");
                            long deadline = System.nanoTime() + 15_000_000_000L;
                            while (number(reader, "SELECT count(*) FROM strategy_runs WHERE status<>'SUCCEEDED'") > 0) {
                                assertTrue(System.nanoTime() < deadline, "diagnostic strategy recovery deadline");
                                Thread.sleep(250);
                            }
                        }
                        var row = proof.withArray("units").addObject().put("orders", goal);
                        row.set("before", snapshot(reader, actors));
                        for (int i = 0; i < 3; i++) for (var a : actors) a.send("L6_RECONCILE");
                        row.set("after", snapshot(reader, actors));
                        long reconciliationDelta = groupTotal(row.path("after"), "QUALIFICATION_RECONCILIATION/")
                                - groupTotal(row.path("before"), "QUALIFICATION_RECONCILIATION/");
                        assertEquals(6L * (7L * goal + 2), reconciliationDelta, "真实终态重放与游标事务单位成本");
                        row.put("reconciliationTransactions", reconciliationDelta);
                        for (var a : actors) a.send("L6_OBSERVER_SCAN");
                        reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); reader.setAutoCommit(false);
                        row.set("oracle", L6TransactionAccounting.within("CHECKPOINT_ORACLE", () ->
                                L6BusinessCheckpoint.verify(reader, endpoint, actors, dir, "DIAGNOSTIC", goal, false, 422, slots)));
                        reader.commit(); reader.setAutoCommit(true);
                    }
                    try (var resourceRaw = fixture.checker(); var resourceReader = L6TransactionAccounting.wrap(resourceRaw, "SAMPLER_PG");
                         var resources = new L6RuntimeResources(resourceReader, dir, actors, venue, endpoint, pg.ownedContainerId(), true);
                         var sampler = resources.sampler(new L6DurationContract(10_000_000_000L, 10_000_000_000L, 20_000_000_000L), System.nanoTime(), dir.resolve("resources.ndjson"))) {
                        sampler.start();
                        Thread.sleep(40_000); sampler.requireComplete(); proof.set("sampling", sampler.summary());
                    }
                    proof.set("after", snapshot(reader, actors));
                } finally { B0Processes.closeChildren(actors); }
                Thread.sleep(1200);
                proof.put("serverAfterChildExit", number(statistics, statisticsSql));
                proof.set("controllerAfter", L6TransactionAccounting.snapshot());
                proof.put("ownedActorSurvivors", actors.stream().filter(a -> a.process.isAlive()).count());
                assertEquals(0, proof.path("ownedActorSurvivors").asLong());
                proof.put("result", "PASS");
            }
        } finally { Files.writeString(dir.resolve("proof.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof)); }
    }
    private static long groupTotal(JsonNode snapshot, String prefix) {
        long total = 0;
        for (var actor : snapshot.path("actors")) {
            var fields = actor.path("transactionsByOriginAndOwner").fields();
            while (fields.hasNext()) { var field = fields.next(); if (field.getKey().startsWith(prefix)) total += field.getValue().asLong(); }
        }
        return total;
    }
    private static ObjectNode snapshot(Connection reader, List<B0Processes.Child> actors) throws Exception {
        var result = JSON.createObjectNode();
        var array = result.putArray("actors");
        for (var actor : actors) array.add(JSON.readTree(actor.send("L6_METRICS")));
        result.put("serverTransactions", number(reader, "SELECT xact_commit+xact_rollback FROM pg_stat_database WHERE datname=current_database()"));
        result.set("controller", L6TransactionAccounting.snapshot()); return result;
    }
}
