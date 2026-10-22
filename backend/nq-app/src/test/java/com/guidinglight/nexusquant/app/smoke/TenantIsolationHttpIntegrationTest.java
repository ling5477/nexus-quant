package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.*;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.scheduler.paper.*;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/** 完整 Servlet、真实认证、非 owner PostgreSQL 与原业务服务共同证明两租户闭环。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class TenantIsolationHttpIntegrationTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final Instant START=Instant.parse("2026-09-25T00:00:00Z");
    static final String COST="""
            {"quantityStep":"0.0001","priceTick":"0.01","minimumQuantity":"0.0001",
             "minimumNotional":"5","feeRate":"0.001","slippageBps":"10"}
            """;

    @Test void researchHttpAndCanonicalSimAreIsolatedForTwoTenantsAndLocal() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var a=db.demo("HTTP_A","http-a"); var b=db.demo("HTTP_B","http-b"); var local=db.local("http-local");
            long aa=sourceAccount(db,a,"source-a"), ab=sourceAccount(db,b,"source-b"), al=sourceAccount(db,local,"source-local");
            String runA,runB;
            try(var app=start(db); var http=new Http(app.getWebServer().getPort())) {
                var bars=new ArrayList<HistoricalBar>();
                for(int i=0;i<5;i++) {
                    var time=START.plusSeconds(i*60L); var price=new BigDecimal(100+i);
                    bars.add(new HistoricalBar("OKX","SPOT","BTC-USDT",BarInterval.ONE_MINUTE,time,time.plusSeconds(59),
                            price,price,price,price,BigDecimal.ONE,null,null,"OK","{}",time.plusSeconds(59)));
                }
                db.as(local,()->{app.getBean(MarketdataBarRepository.class).upsertBars(bars,"FIXTURE_SYNC",Instant.now());return null;});
                UUID dataset=sharedSyntheticDataset(db,bars);
                String snapshot=db.as(a,()->app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset));
                String ta=http.login("http-a"), tb=http.login("http-b"), tl=http.login("http-local");
                Chain ca=chain(http,ta,aa,dataset,snapshot), cb=chain(http,tb,ab,dataset,snapshot), cl=chain(http,tl,al,dataset,snapshot);
                assertNotEquals(ca.strategy(),cb.strategy());
                for(var pair:List.of(new String[]{ta,ca.strategy()},new String[]{tb,cb.strategy()},new String[]{tl,cl.strategy()})) {
                    var rows=http.ok("GET","/api/strategies",pair[0],null);
                    assertEquals(2,rows.size()); assertTrue(rows.toString().contains(pair[1]));
                    assertEquals(1,http.ok("GET","/api/strategies/tenant-sma/versions",pair[0],null).size());
                    assertEquals(1,http.ok("GET","/api/research-configs",pair[0],null).size());
                    assertEquals(1,http.ok("GET","/api/backtest-configs",pair[0],null).size());
                    assertEquals(2,http.ok("GET","/api/evaluations",pair[0],null).size());
                    assertEquals(2,http.ok("GET","/api/strategies/page",pair[0],null).path("total").asInt());
                    assertEquals(1,http.ok("GET","/api/research-configs/page",pair[0],null).path("total").asInt());
                    assertEquals(1,http.ok("GET","/api/backtest-configs/page",pair[0],null).path("total").asInt());
                    assertEquals(2,http.ok("GET","/api/evaluations/page",pair[0],null).path("total").asInt());
                    assertEquals(1,http.ok("GET","/api/publishes/page",pair[0],null).path("total").asInt());
                }
                assertEquals(0,http.ok("GET","/api/research-configs/page?sourceStrategyId="+cb.strategy(),ta,null).path("total").asInt());
                for(String path:List.of("/api/research-configs/"+cb.research(),"/api/backtest-configs/"+cb.config(),
                        "/api/backtest-runs/"+cb.run(),"/api/evaluations/"+cb.evaluation(),"/api/publishes/"+cb.publish(),
                        "/api/strategies/tenant-sma/versions/"+cb.version())) {
                    var foreign=http.send("GET",path,ta,null);
                    assertTrue(foreign.statusCode()==404 || foreign.statusCode()==400,path+": "+foreign.body());
                    assertEquals(foreign.statusCode(),http.send("GET",path.substring(0,path.lastIndexOf('/')+1)+"absent-object",ta,null).statusCode());
                }
                assertRejected(http.send("POST","/api/research-configs",ta,research(cb.strategy(),snapshot)));
                assertRejected(http.send("POST","/api/research-configs",ta,research(ca.strategy(),"{\"provider\":\"fixture\",\"datasetId\":\"unlicensed-file\"}")));
                assertRejected(http.send("POST","/api/strategies",ta,Map.of("strategyCode","unsafe","strategyName","unsafe","strategyType","UNREVIEWED_EXECUTABLE",
                        "exchangeCode","PAPER","accountId",aa,"tradeEnv","SIM","configSnapshot","{}")));
                assertRejected(http.send("POST","/api/backtest-runs",ta,Map.of("backtestConfigId",cb.config())));
                assertRejected(http.send("PATCH","/api/backtest-configs/"+ca.config()+"/strategy-version",ta,Map.of("strategyVersionId",cb.version())));
                assertRejected(http.send("POST","/api/backtest-runs/"+cb.run()+"/evaluate",ta,null));
                assertRejected(http.send("POST","/api/backtest-runs/"+ca.run()+"/publish",ta,Map.of("strategyVersionId",cb.version())));
                UUID privateDataset=UUID.randomUUID();
                db.as(b,()->db.jdbc.update("""
                        INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,symbol,"interval",start_time,end_time,status,quality_status,bar_count,source,created_by)
                        SELECT ?,'Private test descriptor',exchange_code,market_type,symbol,"interval",start_time,end_time,status,quality_status,bar_count,source,created_by
                        FROM marketdata_datasets WHERE dataset_id=?
                        """,privateDataset,dataset));
                assertEquals(0,db.as(a,()->db.jdbc.queryForObject("SELECT count(*) FROM marketdata_datasets WHERE dataset_id=?",Integer.class,privateDataset)));
                assertRejected(http.send("PATCH","/api/backtest-configs/"+ca.config()+"/dataset",ta,Map.of("datasetId",privateDataset.toString())));
                assertEquals(0,db.jdbc.queryForObject("SELECT count(*) FROM marketdata_bars",Integer.class));
                assertEquals(0,db.as(a,()->db.jdbc.update("UPDATE marketdata_bars SET close_price=close_price+1")));
                for(String path:List.of("/api/accounts","/api/scheduler/jobs","/api/trading/orders","/api/paper-trading/runs",
                        "/api/marketdata/datasets","/api/strategy-releases/x/admission-preview","/api/research-creation-operations/"+UUID.randomUUID()))
                    assertEquals(403,http.send("GET",path,ta,null).statusCode(),path);
                assertEquals(403,http.send("POST","/api/paper-trading/strategy-sim/runs",ta,Map.of("publishId",ca.publish(),"budget","100")).statusCode());
                var sim=app.getBean(StrategySimRunService.class);
                var paper=app.getBean(PaperTradingRunService.class);
                var sa=db.as(a,()->sim.create(ca.publish(),new BigDecimal("100"),"http-a"));
                var sb=db.as(b,()->sim.create(cb.publish(),new BigDecimal("200"),"http-b"));
                runA=sa.paperRunId();runB=sb.paperRunId();
                assertNotEquals(sa.canonicalAccountId(),sb.canonicalAccountId());
                db.as(a,()->{paper.start(sa.paperRunId());sim.advance(sa.paperRunId());sim.advance(sa.paperRunId());
                    var decision=sim.advance(sa.paperRunId()); assertEquals("ACCEPTED",decision.status(),decision.toString());return null;});
                raceMatchingAndPause(db,app,a,sa.paperRunId(),sa.canonicalAccountId());
                assertEquals(0,app.getBean(PaperMatchingService.class).matchOnce(64));
                db.as(b,()->{paper.start(sb.paperRunId());sim.advance(sb.paperRunId());sim.advance(sb.paperRunId());
                    var decision=sim.advance(sb.paperRunId()); assertEquals("ACCEPTED",decision.status(),decision.toString());return null;});
                var fa=db.as(a,()->sim.facts(sa.paperRunId())); var fb=db.as(b,()->sim.facts(sb.paperRunId()));
                rejectCrossTenantDefiners(db,a,sb.canonicalAccountId(),fb.orders().getFirst());
                assertEquals(1,fa.orders().size());assertEquals(1,fa.trades().size());
                assertEquals(1,fb.orders().size());assertEquals(0,fb.trades().size());
                assertTrue(fa.positionQuantity().signum()>0);assertEquals(0,fb.positionQuantity().signum());
                assertEquals(2,db.as(a,()->db.jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type LIKE 'SIM_FUNDING_%'",Integer.class)));
                assertEquals(2,db.as(b,()->db.jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type LIKE 'SIM_FUNDING_%'",Integer.class)));
                for(String token:List.of(ta,tb)) {
                    String own=token.equals(ta)?sa.paperRunId():sb.paperRunId();String other=token.equals(ta)?sb.paperRunId():sa.paperRunId();
                    http.ok("GET","/api/paper-trading/strategy-sim/runs/"+own+"/facts",token,null);
                    http.ok("GET","/api/paper-trading/strategy-sim/analysis/runs/"+own+"/performance",token,null);
                    assertEquals(404,http.send("GET","/api/paper-trading/strategy-sim/runs/"+other+"/facts",token,null).statusCode());
                }
                assertEquals(0,db.jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class));
                db.admin.update("UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true WHERE tenant_id=?",a.tenantId().value());
                assertTrue(TenantContext.currentTenantId().isEmpty());
            }
            restart(db,runA,runB,1);
            var facts=economicSnapshot(db);
            restart(db,runA,runB,0);
            assertEquals(facts,economicSnapshot(db));
            assertEquals(2,db.admin.queryForObject("SELECT count(*) FROM trades",Integer.class));
            assertEquals(4,db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type LIKE 'SIM_FUNDING_%'",Integer.class));
            assertTrue(db.admin.queryForObject("SELECT suspended AND requires_manual_resume FROM tenant_execution_controls WHERE tenant_id=?",Boolean.class,a.tenantId().value()));
        }
    }

    static long sourceAccount(TenantIsolationTestDatabase db,TenantIdentity identity,String code) {
        return db.as(identity,()->{
            long account=db.jdbc.queryForObject("INSERT INTO accounts(account_code,venue) VALUES(?,'PAPER') RETURNING account_id",Long.class,code);
            if(identity.kind()==TenantKind.LOCAL) {
                long exchange=db.jdbc.queryForObject("INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias) VALUES(?,'OKX','SIM',?) RETURNING exchange_account_id",Long.class,identity.userId(),code);
                db.jdbc.update("UPDATE accounts SET account_code=?,venue='OKX' WHERE account_id=?","nq-okx-sim-"+exchange,account);
                db.jdbc.update("UPDATE exchange_accounts SET legacy_account_id=? WHERE exchange_account_id=?",account,exchange);
            }
            return account;
        });
    }

    static void rejectCrossTenantDefiners(TenantIsolationTestDatabase db,TenantIdentity identity,long account,Map<String,Object> order) {
        var before=economicSnapshot(db);
        var functions=db.admin.queryForList("""
                SELECT p.oid,p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE n.nspname='public' AND p.proname IN ('nq_admit_strategy_work','nq_begin_strategy_dispatch','nq_project_strategy_run',
                    'nq_bind_strategy_effective','nq_create_ordinary_place_order','nq_decide_ordinary_place','nq_finish_strategy_cancel')
                """);
        assertEquals(7,functions.size());
        for(var function:functions) {
            var arguments=db.admin.queryForList("SELECT p.proargnames[i+1] name,format_type(p.proargtypes[i],NULL) type FROM pg_proc p CROSS JOIN LATERAL generate_series(0,p.pronargs-1) i WHERE p.oid=?",function.get("oid"));
            var placeholders=new ArrayList<String>();var values=new ArrayList<Object>();
            for(var argument:arguments) {
                placeholders.add("CAST(? AS "+argument.get("type")+")");
                values.add(switch(argument.get("name").toString()) {
                    case "p_account"->account;
                    case "p_run"->order.get("strategy_run_id");
                    case "p_id"->order.get("order_id");
                    default->null;
                });
            }
            String sql="SELECT "+function.get("proname")+"("+String.join(",",placeholders)+")";
            var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->db.as(identity,()->db.jdbc.queryForObject(sql,Object.class,values.toArray())),sql);
            assertTrue(failure.getMostSpecificCause().getMessage().contains("TENANT_ACCESS_DENIED"),failure.getMessage());
        }
        assertEquals(before,economicSnapshot(db));
    }

    static void raceMatchingAndPause(TenantIsolationTestDatabase db,ServletWebServerApplicationContext app,
            TenantIdentity tenant,String run,long account) throws Exception {
        var work=app.getBean(TenantWorkAccess.class);var old=work.restore(TenantWorkAccess.Kind.PAPER,run);
        long key=Math.abs(UUID.randomUUID().getMostSignificantBits()>>>1);
        db.admin.execute("CREATE FUNCTION tenant_test_trade_barrier() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.account_id="+account+
                " THEN PERFORM pg_advisory_xact_lock("+key+"); END IF; RETURN NEW; END $$");
        db.admin.execute("CREATE TRIGGER zz_tenant_test_trade_barrier BEFORE INSERT ON trades FOR EACH ROW EXECUTE FUNCTION tenant_test_trade_barrier()");
        try(var lock=db.database.source().getConnection();var statement=lock.createStatement();var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            statement.execute("SELECT pg_advisory_lock("+key+")");
            try {
            var matching=workers.submit(()->app.getBean(PaperMatchingService.class).matchOnce(64));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while(!Boolean.TRUE.equals(db.admin.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory')",Boolean.class))) {
                assertTrue(System.nanoTime()<deadline,"matching did not reach actual Trade write");Thread.sleep(20);
            }
            var pause=workers.submit(()->db.admin.update("UPDATE tenants SET status='DISABLED' WHERE tenant_id=?",tenant.tenantId().value()));
            assertThrows(java.util.concurrent.TimeoutException.class,()->pause.get(200,java.util.concurrent.TimeUnit.MILLISECONDS),
                    "pause must wait for the already admitted economic transaction");
            statement.execute("SELECT pg_advisory_unlock("+key+")");
            assertEquals(1,matching.get(15,java.util.concurrent.TimeUnit.SECONDS));pause.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(org.springframework.dao.DataAccessException.class,()->work.restore(TenantWorkAccess.Kind.PAPER,run));
            db.admin.update("UPDATE tenants SET status='ACTIVE' WHERE tenant_id=?",tenant.tenantId().value());
            assertThrows(org.springframework.dao.DataAccessException.class,()->work.restore(TenantWorkAccess.Kind.PAPER,run),"reenabling must not resume automatically");
            db.as(tenant,()->db.jdbc.queryForObject("SELECT nq_resume_tenant_execution(?)",Object.class,run));
            try(var ignored=TenantContext.openExecution(old)) {
                var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->db.jdbc.queryForObject("SELECT nq_require_tenant_execution(?)",Object.class,tenant.tenantId().value()));
                assertTrue(failure.getMostSpecificCause().getMessage().contains("TENANT_EXECUTION_EPOCH_STALE"));
            }
            assertTrue(work.restore(TenantWorkAccess.Kind.PAPER,run).executionEpoch()>old.executionEpoch());
            } finally { statement.execute("SELECT pg_advisory_unlock("+key+")"); }
        } finally {
            db.admin.execute("DROP TRIGGER zz_tenant_test_trade_barrier ON trades");db.admin.execute("DROP FUNCTION tenant_test_trade_barrier()");
        }
    }

    static UUID sharedSyntheticDataset(TenantIsolationTestDatabase db,List<HistoricalBar> bars) {
        UUID id=UUID.randomUUID();
        db.admin.update("""
                INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,symbol,"interval",start_time,end_time,
                    status,quality_status,bar_count,source,created_by,tenant_id,visibility)
                VALUES(?,'Synthetic licensed test bars','OKX','SPOT','BTC-USDT','1m',?,?,'READY','OK',5,'marketdata_bars','synthetic',NULL,'SHARED_READONLY')
                """,id,java.sql.Timestamp.from(bars.getFirst().openTime()),java.sql.Timestamp.from(bars.getLast().closeTime()));
        db.admin.update("""
                INSERT INTO marketdata_dataset_publications(dataset_id,source_reference,license_reference,permitted_from,permitted_until,expires_at,status)
                VALUES(?,'GENERATED_TEST_ONLY','SYNTHETIC_TEST_PERMISSION',?,?,now()+interval '1 hour','APPROVED')
                """,id,java.sql.Timestamp.from(bars.getFirst().openTime()),java.sql.Timestamp.from(bars.getLast().closeTime()));
        return id;
    }

    static Map<String,Object> research(String strategy,String snapshot) { return Map.of("sourceStrategyId",strategy,"name","Tenant research",
            "description","synthetic","parameterSchema","{}","parameterDefaults","{}","datasetSpec",snapshot); }
    static Chain chain(Http http,String token,long account,UUID dataset,String snapshot) throws Exception {
        var definition=http.ok("POST","/api/strategies",token,Map.of("strategyCode","tenant-sma","strategyName","Tenant SMA",
                "strategyType","SPOT_SMA_TARGET_V1","exchangeCode",token.equals(http.localToken)?"OKX":"PAPER","accountId",account,"tradeEnv","SIM","configSnapshot","{}"));
        var version=http.ok("POST","/api/strategies/tenant-sma/versions",token,Map.of("versionName","v1","status","ACTIVE",
                "paramSnapshotJson","{\"window\":3,\"investedExposure\":\"1\"}","configSnapshotJson","{}","sourceSnapshotJson","{\"executable\":\"SPOT_SMA_TARGET_V1\"}"));
        String strategy=definition.path("strategyId").asText(), vid=version.path("strategyVersionId").asText();
        String research=http.ok("POST","/api/research-configs",token,research(strategy,snapshot)).path("researchConfigId").asText();
        String config=http.ok("POST","/api/backtest-configs",token,Map.of("researchConfigId",research,"name","Tenant backtest",
                "startTime",START.toString(),"endTime",START.plusSeconds(299).toString(),"initialCapital","100","executionSpec",COST,"evaluationSpec","{}")).path("backtestConfigId").asText();
        http.ok("PATCH","/api/backtest-configs/"+config+"/dataset",token,Map.of("datasetId",dataset.toString()));
        http.ok("PATCH","/api/backtest-configs/"+config+"/strategy-version",token,Map.of("strategyVersionId",vid));
        String run=http.ok("POST","/api/backtest-runs",token,Map.of("backtestConfigId",config)).path("backtestRunId").asText();
        assertEquals("SUCCEEDED",http.ok("POST","/api/backtest-runs/"+run+"/start",token,null).path("status").asText());
        String eval=http.ok("POST","/api/backtest-runs/"+run+"/evaluate",token,null).path("evalReportId").asText();
        String again=http.ok("POST","/api/backtest-runs/"+run+"/evaluate",token,null).path("evalReportId").asText();
        assertNotEquals(eval,again);
        String publication=http.ok("POST","/api/backtest-runs/"+run+"/publish",token,Map.of("strategyVersionId",vid,"displayName","Tenant publish")).path("publishRecordId").asText();
        return new Chain(strategy,vid,research,config,run,eval,publication);
    }

    static ServletWebServerApplicationContext start(TenantIsolationTestDatabase db) {
        return start(db.database.url(),db.runtime,db.runtimePassword);
    }

    static ServletWebServerApplicationContext start(String url,String username,String password) {
        var application=new SpringApplication(NexusQuantApplication.class); application.setRegisterShutdownHook(false);
        return (ServletWebServerApplicationContext)application.run("--server.address=127.0.0.1","--server.port=0",
                "--spring.profiles.active=ci","--spring.main.banner-mode=off","--logging.level.root=WARN",
                "--spring.datasource.url="+url,"--spring.datasource.username="+username,"--spring.datasource.password="+password,
                "--spring.datasource.hikari.maximum-pool-size=4","--spring.flyway.enabled=false","--spring.sql.init.mode=never",
                "--nq.env-safety.no-outbound=true","--nq.env-safety.live-enabled=false","--nq.demo.tenant-business-enabled=true",
                "--nq.strategy-sim.enabled=true","--nq.continuous-sim.enabled=false","--nq.instrument.catalog-sync.enabled=false",
                "--nq.okx.ws.enabled=false","--nq.binance.ws.enabled=false","--nq.okx.recovery.enabled=false",
                "--nq.auth.bootstrap-admin.enabled=false","--nq.public-news.enabled=false","--nq.account.performance.sampling-enabled=false",
                "--nq.account.credentials.master-key=synthetic-tenant-key-1234567890123456789",
                "--nq.security.issuer=tenant-core-test","--nq.security.secret=synthetic-tenant-core-signing-secret-123456789");
    }

    static Map<String,List<String>> economicSnapshot(TenantIsolationTestDatabase db) {
        var result=new TreeMap<String,List<String>>();
        for(String table:List.of("orders","trades","ledger_entries","ledger_events","positions","account_snapshots"))
            result.put(table,db.admin.queryForList("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text",String.class));
        return result;
    }

    static void restart(TenantIsolationTestDatabase db,String runA,String runB,int expected) throws Exception {
        var directory=java.nio.file.Path.of("target","tenant-isolation-evidence").toAbsolutePath();java.nio.file.Files.createDirectories(directory);
        var args=directory.resolve("restart-"+expected+".args");var log=directory.resolve("restart-"+expected+".log");
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        java.nio.file.Files.writeString(args,"-Duser.timezone=UTC\n-cp\n\""+cp.replace("\\","\\\\").replace("\"","\\\"")+"\"\n"+TenantIsolationRestartProbeMain.class.getName()+"\n");
        String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
        var builder=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin",executable).toString(),"@"+args);
        builder.environment().put("TASK_TENANT_URL",db.database.url());builder.environment().put("TASK_TENANT_USER",db.runtime);
        builder.environment().put("TASK_TENANT_PASSWORD",db.runtimePassword);builder.environment().put("TASK_TENANT_RUN_A",runA);
        builder.environment().put("TASK_TENANT_RUN_B",runB);builder.environment().put("TASK_TENANT_MATCHES",Integer.toString(expected));
        var process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(55,java.util.concurrent.TimeUnit.SECONDS),"tenant application restart timeout");
            assertEquals(0,process.exitValue(),"restart evidence: "+log);
            assertTrue(java.nio.file.Files.readString(log).contains("TENANT_RESTART_VERIFIED"));
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
    record Chain(String strategy,String version,String research,String config,String run,String evaluation,String publish) {}
    static void assertRejected(HttpResponse<String> response) { assertTrue(Set.of(400,403,404,409).contains(response.statusCode()),response.body()); }
    static final class Http implements AutoCloseable {
        final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); final String base;
        String localToken;
        Http(int port) { base="http://127.0.0.1:"+port; }
        HttpResponse<String> send(String method,String path,String token,Object body,String... headers) throws Exception {
            var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(40)).header("Content-Type","application/json");
            if(token!=null)request.header("Authorization","Bearer "+token);
            for(int i=0;i<headers.length;i+=2)request.header(headers[i],headers[i+1]);
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode ok(String method,String path,String token,Object body,String... headers) throws Exception {
            var response=send(method,path,token,body,headers); assertEquals(200,response.statusCode(),method+" "+path+": "+response.body());
            return JSON.readTree(response.body());
        }
        String login(String user) throws Exception {
            String token=ok("POST","/api/auth/login",null,Map.of("username",user,"password",TenantIsolationTestDatabase.PASSWORD)).path("accessToken").asText();
            if(user.equals("http-local"))localToken=token;
            return token;
        }
        public void close() {client.close();}
    }
}
