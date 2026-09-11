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
    private final int activeSeconds = diagnostic ? 90 : 3600;
    private final int warmupSeconds = diagnostic ? 15 : 120;
    private Path dir;
    private int checks;
    private int iterations;
    private long lastCheck;
    private final List<B0Processes.Child> children = new ArrayList<>();
    private final ObjectNode proof = JSON.createObjectNode();

    @Test void activeSoak() throws Exception {
        dir = B0Processes.root().resolve("backend/nq-app/target/l6-active/" + UUID.randomUUID());
        Files.createDirectories(dir);
        System.out.println("L6_ROOT " + dir);
        proof.put("diagnostic", diagnostic).put("activeSecondsRequired", activeSeconds).put("warmupSeconds", warmupSeconds)
                .put("sampleSeconds", 30).put("resourceSampleSeconds", 10).put("drainDeadlineSeconds", 120)
                .put("orderBudget", 240).put("workingSetBudget", 240).put("controllerPid", ProcessHandle.current().pid());
        Files.writeString(dir.resolve("parameters.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        int pgPort=0, venuePort=0; long venuePid=0; String container=null;
        try {
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(L5VenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
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
                        long warmStart=System.nanoTime();
                        runPhase(reader,endpoint,venue,warmupSeconds,"warmup",true);
                        proof.put("warmupActualSeconds",(System.nanoTime()-warmStart)/1e9);
                        long activeStart=System.nanoTime();proof.put("activeStartMillis",System.currentTimeMillis());
                        runPhase(reader,endpoint,venue,activeSeconds,"active",true);
                        proof.put("activeDurationSeconds",(System.nanoTime()-activeStart)/1e9).put("activeEndMillis",System.currentTimeMillis());
                        // 停止 scanner 生产者；正常 V51 timer 和对账保留，不能通过停消费者制造假静默。
                        long drainStart=System.nanoTime();
                        runPhase(reader,endpoint,venue,diagnostic?10:30,"drain",false);
                        ObjectNode end=checkpoint(reader,endpoint,venue,"final");
                        assertEquals(0,end.path("actionable").asInt());
                        assertEquals(0,number(reader,"SELECT count(*) FROM strategy_runs WHERE status<>'SUCCEEDED'"));reader.commit();
                        proof.put("drainSeconds",(System.nanoTime()-drainStart)/1e9).put("checks",checks).put("iterations",iterations);
                        proof.set("final",end);proof.set("facts",facts(reader));reader.commit();
                        proof.set("venue",http(endpoint,null));
                        if(!diagnostic) assertTrue(proof.path("activeDurationSeconds").asDouble()>=3600);
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
            proof.put("ownedNqRemaining",children.stream().filter(c->c.process.isAlive()).count());
            Files.writeString(dir.resolve("proof.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }

    private void runPhase(Connection reader,String endpoint,B0Processes.Child venue,int seconds,String phase,boolean produce) throws Exception {
        long until=System.nanoTime()+Duration.ofSeconds(seconds).toNanos();
        while(System.nanoTime()<until) {
            long tick=System.nanoTime();
            if(produce) { for(var c:children)c.startCommand("L6_SCAN");for(var c:children)c.result(); }
            http(endpoint,"FILL");
            for(var c:children)c.startCommand("L6_RECONCILE");for(var c:children)c.result();iterations++;
            ObjectNode current=sample(reader);reader.commit();
            assertTrue(current.path("orders").asInt()<=240,"finite total order budget");
            assertTrue(current.path("actionable").asInt()<=240,"bounded working set");
            assertTrue(current.path("transactions").asLong()<=1000000,"transaction budget");
            current.put("phase",phase).put("iteration",iterations);
            append("progress.ndjson",current);
            if(System.currentTimeMillis()-lastCheck>=30000 && current.path("orders").asInt()>0) {
                checkpoint(reader,endpoint,venue,phase);lastCheck=System.currentTimeMillis();
            }
            long remaining=TimeUnit.NANOSECONDS.toMillis(tick+Duration.ofSeconds(5).toNanos()-System.nanoTime());
            if(remaining>0)Thread.sleep(remaining);
        }
    }

    private ObjectNode checkpoint(Connection reader,String endpoint,B0Processes.Child venue,String phase) throws Exception {
        // 真实完成后才用完整源账务重建；短暂在途状态不能被误报成投影损坏。
        long deadline=System.nanoTime()+Duration.ofSeconds(120).toNanos();
        while(true) {
            long busy=number(reader,"SELECT count(*) FROM strategy_runs WHERE status<>'SUCCEEDED'");
            ObjectNode sample=sample(reader);reader.commit();
            if(busy==0 && sample.path("backlog").asInt()==0)break;
            if(System.nanoTime()>deadline)throw new AssertionError("L6 work failed bounded convergence");
            http(endpoint,"FILL");
            for(var c:children)c.send("L6_RECONCILE");Thread.sleep(500);
        }
        ObjectNode point=sample(reader);point.put("phase",phase).put("check",++checks);
        point.put("nonActionable",number(reader,"SELECT count(*) FROM orders WHERE status IN ('FILLED','CANCELLED','RISK_REJECTED')"));
        point.set("cursor",cursor(reader));
        point.put("auditRows",number(reader,"SELECT count(*) FROM audit_logs"));
        point.put("eventRows",number(reader,"SELECT count(*) FROM event_store"));
        point.put("databaseBytes",number(reader,"SELECT pg_database_size(current_database())"));
        ObjectNode check=JSON.createObjectNode();check.set("facts",facts(reader));
        check.put("expectedStrategyRuns",number(reader,"SELECT count(*) FROM strategy_runs"));reader.commit();
        check.set("venue",http(endpoint,null));
        Path file=dir.resolve(String.format("checkpoint-%03d.json",checks));
        Files.writeString(file,JSON.writeValueAsString(check));
        String oracle=B0Processes.command("python","-X","utf8",B0Processes.root().resolve(
                "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/l6_oracle.py").toString(),file.toString());
        point.set("oracle",JSON.readTree(oracle));
        var resources=point.putArray("resources");
        for(var c:children) { resources.add(JSON.readTree(c.send("L6_METRICS"))); }
        point.set("venueExecutor",http(endpoint,"METRICS"));
        point.put("logBytes",Files.size(venue.log)+Files.size(children.get(0).log)+Files.size(children.get(1).log));
        long bytes=0,count=0;
        try(var files=Files.walk(dir)) {for(Path p:files.filter(Files::isRegularFile).toList()){bytes+=Files.size(p);count++;}}
        point.put("rawBytes",bytes).put("rawFiles",count);
        assertTrue(bytes<1024L*1024*1024,"raw budget");
        assertTrue(Files.getFileStore(dir).getUsableSpace()>2L*1024*1024*1024,"disk floor");
        String ids=children.get(0).process.pid()+","+children.get(1).process.pid()+","+venue.process.pid();
        String os=B0Processes.command("powershell","-NoProfile","-Command","Get-Process -Id "+ids+" | Select-Object Id,HandleCount,WorkingSet64 | ConvertTo-Json -Compress");
        point.set("osProcesses",JSON.readTree(os));
        append("checkpoints.ndjson",point);
        System.out.println("L6_PROGRESS phase="+phase+" checks="+checks+" orders="+point.path("orders")+" backlog="+point.path("actionable"));
        System.out.flush();return point;
    }

    private void append(String name,JsonNode value) throws Exception {
        Files.writeString(dir.resolve(name),JSON.writeValueAsString(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private static void released(int port) throws Exception {
        try(var socket=new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1",port),500);throw new AssertionError("owned port remains open"); }
        catch(IOException expected) { }
    }
    private static void seed(B0Fixture fixture) throws Exception {
        // 所有定义/研究 fixture 在 NQ 启动前初始化；运行期 controller 只拥有 SELECT。
        try(var owner=DriverManager.getConnection(fixture.url(),"postgres","");var s=owner.createStatement()) {
            for(int index=1;index<=2;index++) {
                s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot) SELECT 'l6-strategy-"+index+"','l6-strategy-"+index+"','L6 fixture','TEST','OKX',account_id,'SIM',true,'{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"0.1005\"}'::jsonb FROM accounts WHERE account_code='b0-account'");
                s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,enabled,dedup_scope,exchange_code,account_id,trade_env,created_at) SELECT 'l6-schedule-"+index+"','l6-strategy-"+index+"','0 * * * * *','UTC',true,'SCHEDULE_WINDOW','OKX',account_id,'SIM',CURRENT_TIMESTAMP-INTERVAL '60 seconds' FROM accounts WHERE account_code='b0-account'");
            }
            s.execute("INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES('l6-research','l6-strategy-1','L6 Paper fixture','{}')");
            s.execute("INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES('l6-config','l6-research','L6 Paper fixture')");
            s.execute("INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at) VALUES('l6-backtest','l6-config','l6-research','l6-strategy-1','SUCCEEDED','{}','{}',CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name) VALUES('l6-publish','l6-backtest','l6-research','l6-config','l6-strategy-1','SUCCEEDED','L6 Paper fixture')");
        }
    }
}
