package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.research.*;
import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.*;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.*;
import com.guidinglight.nexusquant.research.application.backtest.command.*;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.strategy.application.*;
import com.guidinglight.nexusquant.strategy.application.command.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/** 隔离PG16、完整Servlet容器和正式业务服务；只有合成行情，不访问交易所。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class StrategyExperimentPostgresIntegrationTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final Instant START=Instant.parse("2026-09-01T00:00:00Z");
    static final String COST="{\"quantityStep\":\"0.0001\",\"priceTick\":\"0.01\",\"minimumQuantity\":\"0.0001\",\"minimumNotional\":\"1\",\"feeRate\":\"0.001\",\"slippageBps\":\"10\"}";

    @Test @Timeout(180) void realBatchesReplayConcurrencyCancellationIsolationAndProcessRecovery() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            assertEquals(16,db.admin.queryForObject("SELECT current_setting('server_version_num')::int/10000",Integer.class));
            var owner=db.local("experiment-owner");var other=db.local("experiment-other");var tenant=db.demo("EXPERIMENT_OTHER","experiment-demo");
            db.admin.update("UPDATE scheduled_job_controls SET enabled=false");
            UUID restartExperiment;
            String interruptedRun;
            try(var app=start(db.url(),db.runtime,db.runtimePassword);var http=new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL WHERE job_key='STRATEGY_EXPERIMENTS'");
                var service=app.getBean(StrategyExperimentService.class);
                var worker=app.getBean(StrategyExperimentWorker.class);
                var work=app.getBean(TenantWorkAccess.class);
                String token=http.login("experiment-owner"),otherToken=http.login("experiment-other");
                var templates=db.as(owner,()->seed(app));
                for(String type:List.of("SPOT_SMA_TARGET_V1","SPOT_CHANNEL_BREAKOUT_V1")) {
                    String config=templates.get(type);
                    var request=new StrategyExperimentService.Request(config,null,JSON.readTree(type.contains("SMA")?
                            "{\"window\":[4,8],\"investedExposure\":[\"0.5\",\"1\"]}":
                            "{\"entryLookbackBars\":[4,8],\"exitLookbackBars\":[2],\"investedExposure\":[\"0.5\",\"1\"]}"));
                    UUID operation=UUID.randomUUID();
                    var first=http.ok("POST","/api/strategy-experiments/operations/"+operation,token,request);
                    UUID id=UUID.fromString(first.path("experimentId").asText());
                    assertEquals(4,first.path("trials").size());
                    assertEquals(first.path("trials"),http.ok("POST","/api/strategy-experiments/operations/"+operation,token,request).path("trials"));
                    var changed=new StrategyExperimentService.Request(config,JSON.readTree("[{\"window\":2,\"investedExposure\":\"1\"}]"),null);
                    assertEquals(409,http.send("POST","/api/strategy-experiments/operations/"+operation,token,changed).statusCode());
                    assertEquals(404,http.send("GET","/api/strategy-experiments/"+id,otherToken,null).statusCode());
                    assertEquals(404,http.send("POST","/api/strategy-experiments/"+id+"/cancel",otherToken,null).statusCode());
                    assertEquals(404,http.send("POST","/api/strategy-experiments/"+id+"/trials/"+first.path("trials").get(0).path("trialId").asText()+"/attempts/"+UUID.randomUUID(),otherToken,null).statusCode());
                    assertThrows(RuntimeException.class,()->db.as(tenant,()->service.get(id,tenant.userId())));
                    UUID concurrent=UUID.randomUUID();
                    try(var pool=Executors.newFixedThreadPool(2)) {
                        var futures=new ArrayList<Future<StrategyExperimentService.View>>();
                        for(int n=0;n<2;n++)futures.add(pool.submit(()->db.as(owner,()->service.create(concurrent,request,owner.userId()))));
                        assertEquals(futures.get(0).get().experimentId(),futures.get(1).get().experimentId());
                    }
                    try(var pool=Executors.newFixedThreadPool(2)) {
                        var one=pool.submit(()->work.execute(TenantWorkAccess.Kind.EXPERIMENT,id.toString(),()->worker.advance(id)));
                        var two=pool.submit(()->work.execute(TenantWorkAccess.Kind.EXPERIMENT,id.toString(),()->worker.advance(id)));
                        one.get(10,TimeUnit.SECONDS);two.get(10,TimeUnit.SECONDS);
                    }
                    for(int n=0;n<4;n++) work.execute(TenantWorkAccess.Kind.EXPERIMENT,id.toString(),()->worker.advance(id));
                    var completed=db.as(owner,()->service.get(id,owner.userId()));
                    assertEquals("SUCCEEDED",completed.status());
                    assertEquals(4,completed.trials().stream().map(StrategyExperimentService.Trial::strategyVersionId).distinct().count());
                    for(var trial:completed.trials()) {
                        assertTrue(trial.comparable());assertEquals("SUCCEEDED",trial.runStatus());assertNotNull(trial.evaluationId());
                        assertNotNull(trial.metrics().path("netPnl").textValue());
                        assertTrue(trial.metrics().path("metrics").has("researchValidity"));
                        assertFalse(db.as(tenant,()->app.getBean(com.guidinglight.nexusquant.strategy.application.StrategyVersionService.class).listByStrategyCode("experiment-"+type)).stream().anyMatch(v->v.strategyVersionId().equals(trial.strategyVersionId())));
                        assertThrows(RuntimeException.class,()->db.as(tenant,()->app.getBean(BacktestRunService.class).getByBacktestRunId(trial.backtestRunId())));
                    }
                    var second=db.as(owner,()->service.create(UUID.randomUUID(),request,owner.userId()));
                    for(int n=0;n<4;n++)work.execute(TenantWorkAccess.Kind.EXPERIMENT,second.experimentId().toString(),()->worker.advance(second.experimentId()));
                    var recomputed=db.as(owner,()->service.get(second.experimentId(),owner.userId()));
                    for(int n=0;n<4;n++) for(String metric:List.of("netPnl","totalReturn","maxDrawdown","sharpe","tradeCount","fee","slippage"))
                        assertEquals(completed.trials().get(n).metrics().path(metric),recomputed.trials().get(n).metrics().path(metric));
                    var cancelled=db.as(owner,()->service.create(UUID.randomUUID(),request,owner.userId()));
                    db.as(owner,()->service.cancel(cancelled.experimentId(),owner.userId()));
                    assertEquals("CANCELLED",db.as(owner,()->service.get(cancelled.experimentId(),owner.userId())).status());
                    assertFalse(work.execute(TenantWorkAccess.Kind.EXPERIMENT,cancelled.experimentId().toString(),()->worker.advance(cancelled.experimentId())));
                    assertThrows(RuntimeException.class,()->db.as(owner,()->db.jdbc.update("UPDATE strategy_experiments SET trial_count=1 WHERE experiment_id=?",id)));
                }
                var request=new StrategyExperimentService.Request(templates.get("SPOT_SMA_TARGET_V1"),null,JSON.readTree("{\"window\":[4,8],\"investedExposure\":[\"0.5\",\"1\"]}"));
                var inFlight=db.as(owner,()->service.create(UUID.randomUUID(),request,owner.userId()));
                cancelDuringPersistence(db,owner,service,worker,work,inFlight);
                // 正式执行拒绝非法执行规则，原失败Run与每次新尝试均须永久保留。
                var badTemplate=db.as(owner,()->{
                    var configs=app.getBean(BacktestConfigService.class);var original=configs.getByBacktestConfigId(templates.get("SPOT_SMA_TARGET_V1"));
                    var bad=configs.create(new BacktestConfigCreateRequest(original.researchConfigId(),"Invalid execution assumption",null,original.startTime(),original.endTime(),original.initialCapital(),COST.replace("\"minimumNotional\":\"1\"","\"minimumNotional\":\"-1\""),"{}"));
                    configs.bindStrategyVersion(bad.backtestConfigId(),original.strategyVersionId());configs.bindDataset(bad.backtestConfigId(),original.datasetId(),original.datasetSnapshotJson());return bad.backtestConfigId();
                });
                var failedRequest=new StrategyExperimentService.Request(badTemplate,JSON.readTree("[{\"window\":4,\"investedExposure\":\"1\"}]"),null);
                var failing=db.as(owner,()->service.create(UUID.randomUUID(),failedRequest,owner.userId()));
                UUID failedId=failing.experimentId(),trial=failing.trials().getFirst().trialId();
                for(int attempt=1;attempt<=3;attempt++) {
                    work.execute(TenantWorkAccess.Kind.EXPERIMENT,failedId.toString(),()->worker.advance(failedId));
                    var failed=db.as(owner,()->service.get(failedId,owner.userId()));assertEquals("FAILED",failed.status());
                    assertEquals(attempt,failed.trials().size());assertTrue(failed.trials().stream().allMatch(t->t.runStatus().equals("FAILED")));
                    if(attempt<3) {
                        UUID retryId=UUID.randomUUID();
                        db.as(owner,()->service.retry(failedId,trial,retryId,owner.userId()));
                        var replay=db.as(owner,()->service.retry(failedId,trial,retryId,owner.userId()));assertEquals(attempt+1,replay.trials().size());
                    }
                }
                assertThrows(RuntimeException.class,()->db.as(owner,()->service.retry(failedId,trial,UUID.randomUUID(),owner.userId())));
                var recovery=db.as(owner,()->service.create(UUID.randomUUID(),request,owner.userId()));restartExperiment=recovery.experimentId();
                interruptedRun=recovery.trials().getFirst().backtestRunId();
                assertEquals(0,db.as(owner,()->db.jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)));
            }
            interruptDuringPersistence(db,restartExperiment,interruptedRun);
            restart(db,restartExperiment);
            assertEquals(4,db.admin.queryForObject("SELECT count(*) FROM strategy_experiment_trials t JOIN strategy_experiment_attempts a USING(trial_id) WHERE t.experiment_id=? AND a.status='SUCCEEDED'",Integer.class,restartExperiment));
            db.migration(null).validate();
        }
    }
    static void cancelDuringPersistence(TenantIsolationTestDatabase db,TenantIdentity owner,StrategyExperimentService service,
            StrategyExperimentWorker worker,TenantWorkAccess work,StrategyExperimentService.View view) throws Exception {
        String run=view.trials().getFirst().backtestRunId();assertTrue(run.matches("brn-[a-f0-9-]{36}"));
        db.admin.execute("CREATE FUNCTION experiment_test_cancel() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.backtest_run_id=TG_ARGV[0] THEN PERFORM pg_advisory_xact_lock(748272); END IF; RETURN NEW; END; $$");
        db.admin.execute("CREATE TRIGGER zz_experiment_test_cancel BEFORE INSERT ON sim_pnl_snapshots FOR EACH ROW EXECUTE FUNCTION experiment_test_cancel('"+run+"')");
        try(var lock=db.database.source().getConnection();var statement=lock.createStatement();var pool=Executors.newFixedThreadPool(2)) {
            statement.execute("SELECT pg_advisory_lock(748272)");
            try {
                var execution=pool.submit(()->work.execute(TenantWorkAccess.Kind.EXPERIMENT,view.experimentId().toString(),()->worker.advance(view.experimentId())));
                long deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();
                while(!Boolean.TRUE.equals(db.admin.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=748272 AND NOT granted)",Boolean.class))) {
                    assertTrue(System.nanoTime()<deadline,"worker did not reach persistence");Thread.sleep(20);
                }
                var cancellation=pool.submit(()->db.as(owner,()->service.cancel(view.experimentId(),owner.userId())));
                var cancelled=cancellation.get(2,TimeUnit.SECONDS);
                assertEquals(3,cancelled.trials().stream().filter(t->t.status().equals("CANCELLED")).count());
                statement.execute("SELECT pg_advisory_unlock(748272)");assertTrue(execution.get(10,TimeUnit.SECONDS));
                var completed=db.as(owner,()->service.get(view.experimentId(),owner.userId()));
                assertEquals("CANCELLED",completed.status());assertEquals(1,completed.trials().stream().filter(t->t.status().equals("SUCCEEDED")).count());
            } finally {statement.execute("SELECT pg_advisory_unlock(748272)");}
        } finally {db.admin.execute("DROP TRIGGER zz_experiment_test_cancel ON sim_pnl_snapshots");db.admin.execute("DROP FUNCTION experiment_test_cancel()");}
    }
    static ProcessBuilder child(TenantIsolationTestDatabase db,UUID id) {
        var builder=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),StrategyExperimentRestartProbeMain.class.getName(),id.toString());
        builder.environment().put("TASK_TENANT_URL",db.url());builder.environment().put("TASK_TENANT_USER",db.runtime);builder.environment().put("TASK_TENANT_PASSWORD",db.runtimePassword);
        return builder;
    }
    static void interruptDuringPersistence(TenantIsolationTestDatabase db,UUID id,String run) throws Exception {
        assertTrue(run.matches("brn-[a-f0-9-]{36}"));
        db.admin.execute("CREATE FUNCTION experiment_test_interrupt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.backtest_run_id=TG_ARGV[0] THEN PERFORM pg_advisory_xact_lock(748271); END IF; RETURN NEW; END; $$");
        db.admin.execute("CREATE TRIGGER zz_experiment_test_interrupt BEFORE INSERT ON sim_pnl_snapshots FOR EACH ROW EXECUTE FUNCTION experiment_test_interrupt('"+run+"')");
        var log=java.nio.file.Files.createTempFile("nq-experiment-interrupt-",".log");
        try(var lock=db.database.source().getConnection();var statement=lock.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(748271)");
            var process=child(db,id).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                long deadline=System.nanoTime()+java.time.Duration.ofSeconds(30).toNanos();
                while(!Boolean.TRUE.equals(db.admin.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=748271 AND NOT granted)",Boolean.class))) {
                    assertTrue(process.isAlive(),java.nio.file.Files.readString(log));assertTrue(System.nanoTime()<deadline,"worker never reached actual persistence barrier");Thread.sleep(50);
                }
                process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));
            } finally {if(process.isAlive())process.destroyForcibly();}
            assertEquals("CREATED",db.admin.queryForObject("SELECT status FROM backtest_runs WHERE backtest_run_id=?",String.class,run));
            assertEquals("RUNNING",db.admin.queryForObject("SELECT status FROM strategy_experiment_attempts WHERE backtest_run_id=?",String.class,run));
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots WHERE backtest_run_id=?",Integer.class,run));
        } finally {db.admin.execute("DROP TRIGGER zz_experiment_test_interrupt ON sim_pnl_snapshots");db.admin.execute("DROP FUNCTION experiment_test_interrupt()");}
    }

    static Map<String,String> seed(ConfigurableApplicationContext app) {
        var bars=new ArrayList<HistoricalBar>();
        for(int i=0;i<32;i++) {
            var at=START.plusSeconds(i*3600L);var price=BigDecimal.valueOf(100+(i%12<7?i%12:14-i%12));
            bars.add(new HistoricalBar("OKX","SPOT","BTC-USDT",BarInterval.ONE_HOUR,at,at.plusSeconds(3600).minusMillis(1),price,price.add(BigDecimal.ONE),price.subtract(BigDecimal.ONE),price,BigDecimal.ONE,null,null,"OK","{}",at.plusSeconds(3600)));
        }
        app.getBean(MarketdataBarRepository.class).upsertBars(bars,"SYNTHETIC_EXPERIMENT",Instant.now());
        var datasets=app.getBean(MarketdataDatasetService.class);
        var dataset=datasets.createDataset(new CreateMarketdataDatasetCommand("Synthetic experiment","OKX","SPOT","BTC-USDT","1h",START,bars.getLast().closeTime(),"experiment-owner"));
        String snapshot=datasets.buildDatasetSnapshot(dataset.datasetId());
        var result=new HashMap<String,String>();
        var account=app.getBean(com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService.class)
                .create(TenantContext.requireCurrent().userId(),new com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand("OKX","SIM","Experiment fixture",null));
        for(String type:List.of("SPOT_SMA_TARGET_V1","SPOT_CHANNEL_BREAKOUT_V1")) {
            var strategy=app.getBean(StrategyDefinitionService.class).create(new StrategyDefinitionCreateRequest("experiment-"+type,"Experiment "+type,type,"OKX",account.legacyAccountId(),"SIM","{}"));
            var version=app.getBean(StrategyVersionService.class).create(new StrategyVersionCreateRequest(strategy.strategyCode(),"seed","DRAFT",type.contains("SMA")?"{\"window\":4,\"investedExposure\":\"1\"}":"{\"entryLookbackBars\":4,\"exitLookbackBars\":2,\"investedExposure\":\"1\"}","{}","{\"executable\":\""+type+"\"}","experiment-owner"));
            var research=app.getBean(ResearchConfigService.class).create(new ResearchConfigCreateRequest(strategy.strategyId(),"Experiment research",null,"{}","{}",snapshot));
            var configs=app.getBean(BacktestConfigService.class);
            var config=configs.create(new BacktestConfigCreateRequest(research.researchConfigId(),"Experiment template "+type,null,START,bars.getLast().closeTime(),new BigDecimal("10000"),COST,"{}"));
            configs.bindStrategyVersion(config.backtestConfigId(),version.strategyVersionId());configs.bindDataset(config.backtestConfigId(),dataset.datasetId().toString(),snapshot);
            result.put(type,config.backtestConfigId());
        }
        return result;
    }
    static org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext start(String url,String username,String password) {
        PublicDemoTestHttp.isolatedProcessConfiguration();
        var application=new org.springframework.boot.SpringApplication(com.guidinglight.nexusquant.app.NexusQuantApplication.class,FixtureRules.class);
        application.setRegisterShutdownHook(false);
        return (org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)application.run(
                "--server.address=127.0.0.1","--server.port=0","--spring.profiles.active=ci","--spring.main.banner-mode=off","--logging.level.root=WARN",
                "--spring.datasource.url="+url,"--spring.datasource.username="+username,"--spring.datasource.password="+password,
                "--spring.datasource.hikari.maximum-pool-size=6","--spring.flyway.enabled=false","--spring.sql.init.mode=never",
                "--nq.env-safety.no-outbound=true","--nq.env-safety.live-enabled=false","--nq.strategy-sim.enabled=false",
                "--nq.continuous-sim.enabled=false","--nq.instrument.catalog-sync.enabled=false","--nq.okx.ws.enabled=false","--nq.binance.ws.enabled=false",
                "--nq.okx.recovery.enabled=false","--nq.auth.bootstrap-admin.enabled=false","--nq.public-news.enabled=false","--nq.account.performance.sampling-enabled=false",
                "--nq.account.credentials.master-key=synthetic-experiment-key-1234567890123456789",
                "--nq.security.issuer=experiment-test","--nq.security.secret=synthetic-experiment-signing-secret-123456789");
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class FixtureRules {
        @org.springframework.context.annotation.Bean(destroyMethod="close") RuleServer ruleServer() throws Exception {return new RuleServer();}
        @org.springframework.context.annotation.Bean com.guidinglight.nexusquant.app.marketdata.PublicMarketReplayCaptureService syntheticRules(
                RuleServer server,ObjectMapper mapper,org.springframework.jdbc.core.JdbcTemplate jdbc,org.springframework.transaction.support.TransactionTemplate transaction) {
            return new com.guidinglight.nexusquant.app.marketdata.PublicMarketReplayCaptureService(java.net.http.HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:"+server.server.getAddress().getPort()),mapper,jdbc,transaction,java.time.Clock.systemUTC());
        }
    }
    static class RuleServer implements AutoCloseable {
        final com.sun.net.httpserver.HttpServer server;
        RuleServer() throws Exception {
            server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            byte[] raw;
            try(var input=getClass().getResourceAsStream("/public-market-replay/okx-btc-usdt-public-instrument-20260925.json")) {raw=input.readAllBytes();}
            server.createContext("/api/v5/public/instruments",exchange->{exchange.sendResponseHeaders(200,raw.length);try(var output=exchange.getResponseBody()){output.write(raw);}});
            server.start();
        }
        public void close() {server.stop(0);}
    }
    static void restart(TenantIsolationTestDatabase db,UUID id) throws Exception {
        var log=java.nio.file.Files.createTempFile("nq-experiment-restart-",".log");
        var builder=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),StrategyExperimentRestartProbeMain.class.getName(),id.toString());
        builder.environment().put("TASK_TENANT_URL",db.url());builder.environment().put("TASK_TENANT_USER",db.runtime);builder.environment().put("TASK_TENANT_PASSWORD",db.runtimePassword);
        var child=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {assertTrue(child.waitFor(55,TimeUnit.SECONDS),"restart timed out");assertEquals(0,child.exitValue(),java.nio.file.Files.readString(log));}
        finally {if(child.isAlive())child.destroyForcibly();}
    }
}
