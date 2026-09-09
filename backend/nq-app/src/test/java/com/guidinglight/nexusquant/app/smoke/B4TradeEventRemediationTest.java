package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 旧 producer 制造真实历史缺口；候选只通过普通 reconciliation 修复，禁止 SQL 删除/补写业务事实。 */
@EnabledIfSystemProperty(named="nq.b4.remediation", matches="true")
class B4TradeEventRemediationTest {
    enum Cut { NORMAL, LEGACY_TRADE_GAP, LEGACY_LEDGER_GAP, ATOMIC_DEATH, LEGACY_COMPLETE }
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final B2RealProcessProofTest b2 = new B2RealProcessProofTest();

    @Test void durableFanoutSurvivesRealProcessDeathAndRepeatedRestart() throws Exception {
        Path root = B0Processes.root().resolve("backend/nq-app/target/b4-remediation/"+UUID.randomUUID());
        Files.createDirectories(root); System.out.println("B4_REMEDIATION " + root);
        Path legacy = B4LegacyClasspath.compile(root.resolve("legacy"));
        int run=0;
        try (var pg=B0Processes.Pg.start()) {
            for (String env : new String[]{"SIM","LIVE"}) for (Cut cut : Cut.values()) {
                if (!"ALL".equals(System.getProperty("nq.b4.case","ALL"))
                        && !cut.name().equals(System.getProperty("nq.b4.case"))) continue;
                scenario(pg, root.resolve(env+"-"+cut), legacy, cut, env, ++run);
            }
        }
    }

    private void scenario(B0Processes.Pg pg, Path directory, Path legacy, Cut cut, String environment, int run) throws Exception {
        Files.createDirectories(directory);
        ObjectNode proof=mapper.createObjectNode().put("scenario",cut.name()).put("environment",environment)
                .put("controllerPid",ProcessHandle.current().pid()).put("legacyHead",B4LegacyClasspath.HEAD);
        try {
            String db;
            try (var fixture=B0Fixture.create(pg);
                 var venue=new B0Processes.Child(B2SyntheticVenueMain.class,directory,"venue",B0Processes.cleanEnvironment())) {
                String endpoint="http://127.0.0.1:"+venue.ready(); db=fixture.name();
                var env=B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB",fixture.url());env.put("NQ_B0_VENUE",endpoint);env.put("NQ_B0_PROFILE","b0-test");
                fixture.initialize(true,endpoint,env,true);
                proof.put("database",db).put("venuePid",venue.process.pid());
                boolean old=cut.name().startsWith("LEGACY");
                try (var reader=fixture.checker();
                     var nq=new B0Processes.Child(B0NqProcessMain.class,directory,"nq-a",env,old?legacy:null)) {
                    nq.ready();proof.put("nqPid",nq.process.pid());
                    assertEquals("48",value(reader,"SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                    proof.put("postgres",value(reader,"SHOW server_version")).put("schema","V48");
                    assertTrue(nq.send(environment.equals("LIVE")?"PLACE_B2_LIVE":"PLACE_B2").endsWith("ACCEPTED"));
                    control(endpoint,"FILL 10 0.01");
                    if (cut==Cut.NORMAL || cut==Cut.LEGACY_COMPLETE) {
                        assertEquals("RECOVER 1",nq.send("RECOVER"));
                        assertEquals("1",events(reader)); b2.assertAccounting(reader,facts(endpoint));
                    } else {
                        assertEquals("ARMED AFTER_TRADE_COMMIT",nq.send("ARM_B4_TRADE_COMMIT"));
                        nq.startCommand("RECOVER"); awaitCut(nq);
                        assertEquals("1",value(reader,"SELECT count(*) FROM trades"));
                        assertEquals(cut==Cut.ATOMIC_DEATH?"1":"0",events(reader));
                        assertEquals("0",value(reader,"SELECT count(*) FROM ledger_entries"));
                    }
                    proof.set("atCut",snapshot(reader));
                    nq.kill();
                    if (cut==Cut.LEGACY_LEDGER_GAP) {
                        try (var oldRecovery=new B0Processes.Child(B0NqProcessMain.class,directory,"nq-old-recovery",env,legacy)) {
                            oldRecovery.ready();proof.put("legacyRecoveryPid",oldRecovery.process.pid());
                            assertEquals("RECOVER 0",oldRecovery.send("RECOVER"));
                            assertEquals("0",events(reader));b2.assertAccounting(reader,facts(endpoint));
                            proof.set("legacyLedgerWithoutEvent",snapshot(reader));
                        }
                    }
                    try (var restarted=new B0Processes.Child(B0NqProcessMain.class,directory,"nq-b",env)) {
                        restarted.ready();proof.put("restartPid",restarted.process.pid());
                        assertNotEquals(nq.process.pid(),restarted.process.pid());
                        assertTrue(restarted.send("ENGAGE").startsWith("ENGAGE ENGAGED"));
                        assertEquals("ENGAGED",value(reader,"SELECT status FROM kill_switch_states"));
                        for (int i=0;i<3;i++) assertEquals("RECOVER 0",restarted.send("RECOVER"));
                        assertEquals("1",events(reader));b2.assertAccounting(reader,facts(endpoint));
                        JsonNode stable=snapshot(reader);
                        assertEquals("RECOVER 0",restarted.send("RECOVER"));
                        assertEquals(stable,snapshot(reader));
                        proof.set("afterRecovery",stable);
                    }
                    try (var again=new B0Processes.Child(B0NqProcessMain.class,directory,"nq-c",env)) {
                        again.ready();proof.put("secondRestartPid",again.process.pid());
                        assertNotEquals(proof.path("restartPid").asLong(),again.process.pid());
                        assertEquals("RECOVER 0",again.send("RECOVER"));
                        assertEquals(proof.path("afterRecovery"),snapshot(reader));
                    }
                    assertEquals(environment,value(reader,"SELECT trade_env FROM trades"));
                    assertEquals(environment,value(reader,"SELECT trade_env FROM orders"));
                    if (cut!=Cut.LEGACY_COMPLETE) assertEquals(environment,value(reader,
                            "SELECT payload_json->'payload'->>'trade_env' FROM event_store WHERE event_type='TradeExecuted'"));
                    assertEquals("1",value(reader,"SELECT count(*) FROM trades"));
                    JsonNode truth=facts(endpoint);proof.set("venue",truth);
                    assertEquals(1,truth.path("placeRequests").asInt());assertEquals(1,truth.path("places").asInt());
                    proof.put("blindRetries",0).put("result","PASS");
                }
            }
            assertTrue(pg.databaseAbsent(db));proof.put("cleanup",true);
            System.out.println("B4_REMEDIATION_PASS "+environment+" "+cut);
        } catch (Exception | AssertionError failure) {
            proof.put("result","FAIL").put("failure",failure.getClass().getSimpleName());throw failure;
        } finally {
            Path raw=directory.resolve("raw-proof.json");
            Files.writeString(raw,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw,"B4-R1",run);
        }
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        ObjectNode snapshot=b2.snapshot(reader);
        snapshot.set("tradeEvents",mapper.readTree(value(reader,"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY event_id)::text,'[]') FROM event_store t WHERE event_type='TradeExecuted'")));
        return snapshot;
    }
    private String events(Connection reader) throws Exception {return value(reader,"SELECT count(*) FROM event_store WHERE event_type='TradeExecuted' AND topic='trade.event.v1'");}
    private String value(Connection c,String sql) throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    private void awaitCut(B0Processes.Child nq) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
        while(!Files.readString(nq.log).contains("B4_CUT AFTER_TRADE_COMMIT transactionActive=false")){
            assertTrue(nq.process.isAlive());assertTrue(System.nanoTime()<deadline,"B4_HARNESS_CONTROL_GAP");Thread.sleep(20);
        }
    }
    private JsonNode facts(String endpoint) throws Exception {
        var r=http.send(HttpRequest.newBuilder(URI.create(endpoint+"/facts")).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,r.statusCode());return mapper.readTree(r.body());
    }
    private void control(String endpoint,String command) throws Exception {
        var r=http.send(HttpRequest.newBuilder(URI.create(endpoint+"/control")).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(command)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,r.statusCode());
    }
}
