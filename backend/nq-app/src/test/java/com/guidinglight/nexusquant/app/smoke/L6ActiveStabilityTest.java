package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.net.URI;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.sample;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.facts;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.http;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.number;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.value;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.cursor;

/** 同一数据库和 Venue 全程存活；生产者、周期检查及最终 drain 有独立预算。 */
@EnabledIfSystemProperty(named = "nq.l6", matches = "true")
class L6ActiveStabilityTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final boolean diagnostic = Boolean.getBoolean("nq.l6.diagnostic");
    private final L6DurationContract duration = L6DurationContract.forMode(diagnostic);
    private final int activeSeconds = (int) TimeUnit.NANOSECONDS.toSeconds(duration.activeNanos());
    private final int warmupSeconds = (int) TimeUnit.NANOSECONDS.toSeconds(duration.warmupNanos());
    private long startedNanos;
    private Path dir;
    private int checks;
    private int iterations;
    private long lastCheckNanos;
    private L6ResourceSampler resourceSampler;
    private final List<B0Processes.Child> children = new ArrayList<>();
    private final ObjectNode proof = JSON.createObjectNode();

    @Test void activeSoak() throws Exception {
        if (diagnostic) { QualificationCapacity.formal().start(this::executeSoak); return; }
        boolean smoke = Boolean.getBoolean("nq.l6.formal.smoke");
        var timing = L6FormalManifest.timing(smoke);
        L6FormalManifest.start(B0Processes.root().resolve(L6FormalManifest.CANONICAL), timing,
                (manifest, capacity) -> new L6FormalRuntime(manifest, capacity, timing, smoke).run());
    }

    private void executeSoak() throws Exception {
        dir = B0Processes.root().resolve("backend/nq-app/target/l6-active/" + UUID.randomUUID());
        Files.createDirectories(dir);
        System.out.println("L6_ROOT " + dir);
        proof.put("diagnostic", diagnostic).put("activeSecondsRequired", activeSeconds).put("warmupSeconds", warmupSeconds)
                .put("mode", diagnostic ? "READINESS" : "FORMAL_60MIN")
                .put("drainRequiredSeconds", TimeUnit.NANOSECONDS.toSeconds(duration.drainNanos()))
                .put("totalRequiredSeconds", TimeUnit.NANOSECONDS.toSeconds(duration.total()))
                .put("checkpointSeconds", 30).put("resourceSampleSeconds", 10).put("drainDeadlineSeconds", 120)
                .put("qualificationMode", "L6_FORMAL").put("capacityPreflight", "PASS")
                .put("venueLogicalOrderCapacity", QualificationCapacity.formal().venueLogicalOrderCapacity())
                .put("globalStageSafetyCap", QualificationCapacity.formal().globalStageSafetyCap())
                .put("orderBudget", QualificationCapacity.formal().runOrderBudget()).put("workingSetBudget", 240).put("controllerPid", ProcessHandle.current().pid());
        Files.writeString(dir.resolve("parameters.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        int pgPort=0, venuePort=0; long venuePid=0; String container=null;
        try {
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(L6FormalVenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                container=pg.ownedContainerId(); pgPort=URI.create(pg.ownedUrl().substring(5)).getPort();
                venuePid=venue.process.pid(); venuePort=Integer.parseInt(venue.ready());
                String endpoint="http://127.0.0.1:"+venuePort;
                var env=B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB",fixture.url());env.put("NQ_B0_VENUE",endpoint);env.put("NQ_B0_PROFILE","b0-test");
                fixture.initialize(true, endpoint, env); seed(fixture);
                proof.put("database",fixture.name()).put("venuePid",venuePid).put("container",container);
                try {
                    for(int i=0;i<2;i++) children.add(new B0Processes.Child(L6NqProcessMain.class,dir,"nq-"+i,env).awaitReady());
                    proof.set("paper",JSON.readTree(children.get(0).send("L6_PAPER")));
                    http(endpoint,"L5_OPEN");
                    try(var reader=fixture.checker()) {
                        assertEquals("51",value(reader,"SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                        assertEquals("f",value(reader,"SELECT has_table_privilege(current_user,'orders','UPDATE')"));
                        reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);reader.setAutoCommit(false);
                        try (var resourceReader = fixture.checker();
                             var runtimeResources = new L6RuntimeResources(resourceReader, dir, children, venue, endpoint, container)) {
                            startedNanos=System.nanoTime();
                            resourceSampler = runtimeResources.sampler(duration, startedNanos, dir.resolve("resources.ndjson"));
                            try (var sampling = resourceSampler) {
                                sampling.start();
                                long warmStart=startedNanos;
                                runPhase(reader,endpoint,venue,duration.warmupNanos(),"warmup",true);
                                proof.put("warmupActualSeconds",(System.nanoTime()-warmStart)/1e9);
                                long activeStart=System.nanoTime();proof.put("activeStartMillis",System.currentTimeMillis());
                                runPhase(reader,endpoint,venue,duration.activeEnd(),"active",true);
                                proof.put("activeDurationSeconds",(System.nanoTime()-activeStart)/1e9).put("activeEndMillis",System.currentTimeMillis());
                                // 停止 scanner 生产者；正常 V51 timer 和对账保留，不能通过停消费者制造假静默。
                                long drainStart=System.nanoTime();
                                runPhase(reader,endpoint,venue,duration.total(),"drain",false);
                                ObjectNode end=checkpoint(reader,endpoint,venue,"final");
                                assertEquals(0,end.path("actionable").asInt());
                                assertEquals(0,number(reader,"SELECT count(*) FROM strategy_runs WHERE status<>'SUCCEEDED'"));reader.commit();
                                proof.put("drainSeconds",(System.nanoTime()-drainStart)/1e9).put("checks",checks).put("iterations",iterations);
                                proof.set("final",end);proof.set("facts",facts(reader));reader.commit();
                                proof.set("venue",http(endpoint,null));
                                assertTrue(System.nanoTime()-startedNanos >= duration.total());
                                for (String actor : List.of("nq0", "nq1")) {
                                    var resource = sampling.latest().path("sources").path(actor).path("values");
                                    L6Measurements.requireMandatory(resource);
                                    assertTrue(resource.has("validationQualification"), "validation timer must complete");
                                    assertEquals(0, resource.path("acquisitionTimeoutDelta").asDouble());
                                    assertEquals(0, resource.path("candidateAge").path("eligibleCandidateCount").asInt());
                                    assertTrue(resource.path("candidateAge").path("oldestCandidateAgeMillis").isNull());
                                }
                                assertTrue(proof.path("nonemptyAgeObserved").asBoolean(), "candidate age positive control required");
                                sampling.requireComplete();
                            }
                            proof.set("resourceSampling", resourceSampler.summary());
                        }

                    }
                } finally { B0Processes.closeChildren(children); }
                assertTrue(children.stream().noneMatch(c->c.process.isAlive()));
                // Venue 由 try-with-resources 正常持有至业务检查结束，随后只回收本轮进程。
            }
            assertTrue(ProcessHandle.of(venuePid).map(p->!p.isAlive()).orElse(true));
            released(pgPort);released(venuePort);
            assertTrue(B0Processes.command("docker","ps","-a","--filter","id="+container,"--format","{{.ID}}").isBlank());
            proof.put("cleanup",true).put("result",diagnostic?"READINESS_MEASURED":"MEASURED");
        } catch(Exception|AssertionError error) {
            proof.put("result","FAILED").put("failure",error.toString());throw error;
        } finally {
            if (resourceSampler != null) proof.set("resourceSampling", resourceSampler.summary());
            proof.put("ownedNqRemaining",children.stream().filter(c->c.process.isAlive()).count());
            Files.writeString(dir.resolve("proof.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }

    private void runPhase(Connection reader,String endpoint,B0Processes.Child venue,long phaseEndNanos,String phase,boolean produce) throws Exception {
        long phaseStart=System.nanoTime()-startedNanos;
        long until=startedNanos+phaseEndNanos;
        while(System.nanoTime()<until) {
            resourceSampler.checkHealthy();
            long tick=System.nanoTime();
            if(produce) {
                QualificationCapacity.formal().reserve(number(reader,"SELECT count(*) FROM orders"), 4); reader.commit();
                for(var c:children)c.startCommand("L6_SCAN");for(var c:children)c.result(); }
            ObjectNode before = JSON.createObjectNode().put("phase", phase).put("point", "BEFORE_FILL");
            var ages = before.putArray("actors");
            for (var c : children) {
                var resource = JSON.readTree(c.send("L6_METRICS"));
                ages.add(resource.path("candidateAge"));
                if (resource.path("candidateAge").path("eligibleCandidateCount").asLong() > 0
                        && resource.path("candidateAge").path("oldestCandidateAgeMillis").isNumber()) proof.put("nonemptyAgeObserved", true);
            }
            append("candidate-age.ndjson", before);
            http(endpoint,"FILL");
            for(var c:children)c.startCommand("L6_RECONCILE");for(var c:children)c.result();iterations++;
            ObjectNode current=sample(reader);reader.commit();
            assertTrue(current.path("orders").asInt()<=240,"finite total order budget");
            assertTrue(current.path("actionable").asInt()<=240,"bounded working set");
            assertTrue(current.path("transactions").asLong()<=1000000,"transaction budget");
            current.put("phase",phase).put("iteration",iterations);
            append("progress.ndjson",current);
            if((lastCheckNanos == 0 || System.nanoTime()-lastCheckNanos>=Duration.ofSeconds(30).toNanos())
                    && current.path("orders").asInt()>0) {
                checkpoint(reader,endpoint,venue,phase);lastCheckNanos=System.nanoTime();
            }
            long remaining=Math.min(until,tick+Duration.ofSeconds(5).toNanos())-System.nanoTime();
            if(remaining>0)TimeUnit.NANOSECONDS.sleep(remaining);
        }
        resourceSampler.checkHealthy();
        ObjectNode timing=JSON.createObjectNode().put("phase",phase).put("startElapsedNanos",phaseStart)
                .put("endElapsedNanos",System.nanoTime()-startedNanos).put("scheduledEndElapsedNanos",phaseEndNanos);
        timing.put("durationNanos",timing.path("endElapsedNanos").asLong()-phaseStart);
        proof.withArray("phases").add(timing);
    }

    private ObjectNode checkpoint(Connection reader,String endpoint,B0Processes.Child venue,String phase) throws Exception {
        return L6BusinessCheckpoint.verify(reader, endpoint, children, dir, phase, ++checks, false);
    }

    private void append(String name,JsonNode value) throws Exception {
        Files.writeString(dir.resolve(name),JSON.writeValueAsString(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    static void released(int port) throws Exception {
        try(var socket=new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1",port),500);throw new AssertionError("owned port remains open"); }
        catch(IOException expected) { }
    }
    static void seed(B0Fixture fixture) throws Exception { seed(fixture, false); }

    static void seed(B0Fixture fixture, boolean calibration) throws Exception {
        // 所有定义/研究 fixture 在 NQ 启动前初始化；运行期 controller 只拥有 SELECT。
        try(var owner=DriverManager.getConnection(fixture.url(),"postgres","");var s=owner.createStatement()) {
            // 仅owned fixture授权统计可见性，防止其他角色的state被PG隐藏后误记idle-in-transaction=0。
            s.execute("GRANT pg_read_all_stats TO nq_b0_reader");
            for(int index=1;index<=2;index++) {
                s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot) SELECT 'l6-strategy-"+index+"','l6-strategy-"+index+"','L6 fixture','TEST','OKX',account_id,'SIM',true,'{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"0.1005\"}'::jsonb FROM accounts WHERE account_code='b0-account'");
                s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,dedup_scope,exchange_code,account_id,trade_env,created_at) SELECT 'l6-schedule-"+index+"','l6-strategy-"+index+"','"+(calibration ? "*/5 * * * * *" : "0 * * * * *")+"','UTC',true,'SCHEDULE_WINDOW','OKX',account_id,'SIM',CURRENT_TIMESTAMP-INTERVAL '60 seconds' FROM accounts WHERE account_code='b0-account'");
            }
            L6ValidationFixture.seed(owner);
            s.execute("INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES('l6-research','l6-strategy-1','L6 Paper fixture','{}')");
            s.execute("INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES('l6-config','l6-research','L6 Paper fixture')");
            s.execute("INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at) VALUES('l6-backtest','l6-config','l6-research','l6-strategy-1','SUCCEEDED','{}','{}',CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name) VALUES('l6-publish','l6-backtest','l6-research','l6-config','l6-strategy-1','SUCCEEDED','L6 Paper fixture')");
        }
    }
}
