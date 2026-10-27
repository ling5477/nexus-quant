package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.research.*;
import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.*;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.*;
import com.guidinglight.nexusquant.research.application.backtest.*;
import com.guidinglight.nexusquant.research.application.backtest.command.*;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.strategy.application.*;
import com.guidinglight.nexusquant.strategy.application.command.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/** 真实公开720根样本在全新PG16/Servlet内执行；损失和无交易都是合法研究结果。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class WalkForwardPostgresIntegrationTest {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test @Timeout(240) void chronologicalSelectionIndependentWindowsStressReplayIsolationAndRecovery() throws Exception {
        try(var db=TenantIsolationTestDatabase.create("48")) {
            var owner=db.local("experiment-owner");var other=db.local("validation-other");var tenant=db.demo("WF_OTHER","validation-demo");
            db.migrate(null);db.runtime();db.migration(null).validate();
            assertEquals(16,db.admin.queryForObject("SELECT current_setting('server_version_num')::int/10000",Integer.class));
            db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
            UUID recoverySource=null;UUID recoveryValidation=null;String recoveryRun=null;
            try(var app=StrategyExperimentPostgresIntegrationTest.start(db.url(),db.runtime,db.runtimePassword);
                    var http=new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var experiments=app.getBean(StrategyExperimentService.class);var validations=app.getBean(ExperimentValidationService.class);
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var templates=db.as(owner,()->seed(app));
                String token=http.login("experiment-owner"),otherToken=http.login("validation-other");
                for(String type:List.of("SPOT_SMA_TARGET_V1","SPOT_CHANNEL_BREAKOUT_V1")) {
                    var request=new StrategyExperimentService.Request(templates.get(type),null,JSON.readTree(type.contains("SMA")?
                            "{\"window\":[4,20],\"investedExposure\":[\"1\"]}":"{\"entryLookbackBars\":[10,20],\"exitLookbackBars\":[4],\"investedExposure\":[\"1\"]}"));
                    var source=db.as(owner,()->experiments.create(UUID.randomUUID(),request,owner.userId()));
                    advance(app,source.experimentId());advance(app,source.experimentId());
                    assertEquals("SUCCEEDED",db.as(owner,()->experiments.get(source.experimentId(),owner.userId())).status());
                    UUID operation=UUID.randomUUID();
                    String endpoint="/api/strategy-experiments/"+source.experimentId()+"/validations/operations/"+operation;
                    JsonNode created=http.ok("POST",endpoint,token,null);UUID id=UUID.fromString(created.path("validationId").asText());
                    assertEquals(id.toString(),http.ok("POST",endpoint,token,null).path("validationId").asText());
                    assertTrue(java.util.stream.StreamSupport.stream(http.ok("GET","/api/strategy-experiments",token,null).spliterator(),false)
                            .noneMatch(row->row.path("experimentId").asText().equals(created.path("steps").get(0).path("experimentId").asText())));
                    assertEquals(404,http.send("GET","/api/strategy-experiments/validations/"+id,otherToken,null).statusCode());
                    assertEquals(404,http.send("POST","/api/strategy-experiments/validations/"+id+"/cancel",otherToken,null).statusCode());
                    assertThrows(RuntimeException.class,()->db.as(tenant,()->validations.get(id,tenant.userId())));
                    assertEquals(0,db.as(tenant,()->db.jdbc.queryForObject("SELECT count(*) FROM experiment_validation_steps WHERE validation_id=?",Integer.class,id)));
                    // 同一训练组两线程推进，原worker的全局执行锁和Run锁必须防止重复经济事实。
                    UUID training=UUID.fromString(created.path("steps").get(0).path("experimentId").asText());
                    try(var pool=Executors.newFixedThreadPool(2)) {
                        var one=pool.submit(()->advance(app,training));var two=pool.submit(()->advance(app,training));
                        one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);
                    }
                    drain(app,db,owner,id,source.experimentId());
                    var result=db.as(owner,()->validations.get(id,owner.userId()));
                    assertEquals("SUCCEEDED",result.status());assertEquals(16,result.steps().size());assertEquals(20,result.results().size());
                    assertEquals(3,result.parameterStability().path("completedOosFolds").asInt());assertEquals(8,result.costSensitivity().size());
                    assertEquals(8,java.util.stream.StreamSupport.stream(result.costSensitivity().spliterator(),false).filter(x->x.path("status").asText().equals("AVAILABLE")).count());
                    assertEquals(2,result.frozenPolicy().path("candidates").size());
                    for(var step:result.steps()) {
                        if(step.role().equals("IN_SAMPLE")) {
                            var selected=step.selection().path("selected");
                            assertEquals(2,step.selection().path("rankings").size());
                            var independent=result.results().stream().filter(r->r.step()==step.number()+1).findFirst().orElseThrow();
                            assertEquals(selected.path("versionId").asText(),independent.strategyVersionId());
                            assertFalse(step.selectedAt().isAfter(independent.evaluatedAt()));
                        }
                    }
                    verifyWindowFacts(db,result);
                    var test=result.results().stream().filter(r->r.step()==1).findFirst().orElseThrow();
                    var config=app.getBean(BacktestConfigService.class);var runs=app.getBean(BacktestRunService.class);
                    var clone=db.as(owner,()->runs.createForValidation(new BacktestRunStartRequest(test.backtestConfigId()),source.frozenInput().path("referenceRunId").asText()));
                    db.as(owner,()->app.getBean(BacktestExecutionService.class).startRun(clone.backtestRunId()));
                    var repeated=db.as(owner,()->app.getBean(BacktestEvaluationService.class).evaluate(clone.backtestRunId()));
                    assertEquals(0,new BigDecimal(test.netPnl()).compareTo(repeated.netPnl()));
                    assertEquals(0,new BigDecimal(test.totalReturn()).compareTo(repeated.totalReturn()));
                    assertThrows(RuntimeException.class,()->db.as(owner,()->db.jdbc.update("UPDATE experiment_validation_steps SET selection='{}'::jsonb WHERE validation_id=? AND step_number=0",id)));
                    assertThrows(RuntimeException.class,()->db.as(owner,()->db.jdbc.update("UPDATE experiment_validations SET frozen_policy='{}'::jsonb WHERE validation_id=?",id)));
                    UUID view=UUID.randomUUID();assertThrows(RuntimeException.class,()->db.as(other,()->validations.get(id,other.userId())));
                    var viewed=db.as(owner,()->validations.revealHoldout(id,view,owner.userId()));
                    assertEquals(1,viewed.holdoutViews().size());
                    assertEquals(1,db.as(owner,()->validations.revealHoldout(id,view,owner.userId())).holdoutViews().size());
                    int count=db.admin.queryForObject("SELECT count(*) FROM sim_trades",Integer.class);
                    drain(app,db,owner,id,source.experimentId());assertEquals(count,db.admin.queryForObject("SELECT count(*) FROM sim_trades",Integer.class));
                    // 新验证创建重放由同一回执处理；不能复用历史选择记录覆盖新结果。
                    UUID concurrent=UUID.randomUUID();
                    try(var pool=Executors.newFixedThreadPool(2)) {
                        var a=pool.submit(()->db.as(owner,()->validations.create(source.experimentId(),concurrent,owner.userId())));
                        var b=pool.submit(()->db.as(owner,()->validations.create(source.experimentId(),concurrent,owner.userId())));
                        assertEquals(a.get().validationId(),b.get().validationId());
                    }
                    var fresh=db.as(owner,()->validations.list(source.experimentId(),owner.userId())).getFirst();
                    if(type.contains("SMA")) {
                        var cancellation=db.as(owner,()->validations.cancel(fresh.validationId(),owner.userId()));
                        assertEquals("CANCELLED",cancellation.status());assertTrue(cancellation.results().stream().allMatch(r->r.status().equals("CANCELLED")));
                        assertFalse(advance(app,cancellation.steps().getFirst().experimentId()));
                        verifyInterruptedOutcomesAndCancellation(app,db,owner,source.experimentId());
                    } else {
                        recoverySource=source.experimentId();recoveryValidation=fresh.validationId();
                        var pending=db.as(owner,()->validations.get(fresh.validationId(),owner.userId()));
                        recoveryRun=pending.results().getFirst().backtestRunId();
                    }
                    Path output=Path.of("..","..","artifacts","nq-v102-walk-forward-"+type+".json");
                    Files.createDirectories(output.getParent());Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
                }
                assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM orders",Integer.class));
            }
            assertNotNull(recoverySource);assertNotNull(recoveryValidation);assertNotNull(recoveryRun);
            UUID child=db.admin.queryForObject("SELECT experiment_id FROM experiment_validation_steps WHERE validation_id=? AND step_number=0",UUID.class,recoveryValidation);
            StrategyExperimentPostgresIntegrationTest.interruptDuringPersistence(db,child,recoveryRun);
            restart(db,recoverySource,recoveryValidation);
            assertEquals("SUCCEEDED",db.admin.queryForObject("SELECT status FROM experiment_validations WHERE validation_id=?",String.class,recoveryValidation));
            assertEquals(20,db.admin.queryForObject("SELECT count(*) FROM experiment_validation_steps s JOIN strategy_experiment_trials t ON t.experiment_id=s.experiment_id JOIN strategy_experiment_attempts a USING(trial_id) WHERE s.validation_id=?",Integer.class,recoveryValidation));
            db.migration(null).validate();
        }
    }

    static boolean advance(ConfigurableApplicationContext app,UUID id) {
        return app.getBean(TenantWorkAccess.class).execute(TenantWorkAccess.Kind.EXPERIMENT,id.toString(),()->app.getBean(StrategyExperimentWorker.class).advance(id));
    }
    static void drain(ConfigurableApplicationContext app,TenantIsolationTestDatabase db,TenantIdentity owner,UUID id,UUID source) {
        var validations=app.getBean(ExperimentValidationService.class);
        for(int n=0;n<100;n++) {
            var v=db.as(owner,()->validations.get(id,owner.userId()));
            if(!v.status().equals("RUNNING"))return;
            for(var step:v.steps())if(step.experimentId()!=null)advance(app,step.experimentId());
            advance(app,source);
        }
        fail("validation did not terminate within bounded work budget");
    }

    static void verifyWindowFacts(TenantIsolationTestDatabase db,ExperimentValidationService.View validation) {
        for(var result:validation.results()) {
            var window=validation.steps().stream().filter(s->s.number()==result.step()).findFirst().orElseThrow();
            Instant first=Instant.parse(result.researchValidity().path("full").path("startTime").asText());
            Instant last=Instant.parse(result.researchValidity().path("full").path("endTime").asText());
            assertEquals(window.endBar()-window.startBar(),db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots WHERE backtest_run_id=?",Integer.class,result.backtestRunId()));
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM sim_trades WHERE backtest_run_id=? AND (traded_at<? OR traded_at>?)",Integer.class,result.backtestRunId(),java.sql.Timestamp.from(first),java.sql.Timestamp.from(last)));
            assertEquals("FLAT",result.summary().path("initialPosition").asText());
            assertEquals("10000",result.summary().path("initialCapital").asText());
            assertEquals("NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT",result.researchValidity().path("assumptions").path("executionTimingPolicy").asText());
            assertEquals(window.startBar(),result.summary().path("warmupBarCount").asInt());
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM sim_orders WHERE backtest_run_id=? AND created_at<?",Integer.class,result.backtestRunId(),java.sql.Timestamp.from(first)));
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM sim_trades WHERE backtest_run_id=? AND traded_at<=?",Integer.class,result.backtestRunId(),java.sql.Timestamp.from(first.plusSeconds(3600))));
            var initial=db.admin.queryForMap("SELECT cash_balance,position_market_value FROM sim_pnl_snapshots WHERE backtest_run_id=? ORDER BY valuation_sequence LIMIT 1",result.backtestRunId());
            assertEquals(0,new BigDecimal("10000").compareTo((BigDecimal)initial.get("cash_balance")));
            assertEquals(0,((BigDecimal)initial.get("position_market_value")).signum());
        }
    }

    static void verifyInterruptedOutcomesAndCancellation(ConfigurableApplicationContext app,TenantIsolationTestDatabase db,TenantIdentity owner,UUID source) throws Exception {
        var validations=app.getBean(ExperimentValidationService.class);
        var unknown=db.as(owner,()->validations.create(source,UUID.randomUUID(),owner.userId()));
        var run=unknown.results().getFirst();
        db.as(owner,()->{
            new org.springframework.transaction.support.TransactionTemplate(app.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(s->{
                var jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
                String attempt=jdbc.queryForObject("SELECT attempt_id::text FROM strategy_experiment_attempts WHERE backtest_run_id=?",String.class,run.backtestRunId());
                jdbc.queryForObject("SELECT set_config('nq.experiment_attempt',?,true)",String.class,attempt);
                app.getBean(BacktestExecutionPersistenceService.class).markPreparing(run.backtestRunId(),Instant.now());
            });return true;
        });
        advance(app,unknown.steps().getFirst().experimentId());advance(app,source);
        var outcome=db.as(owner,()->validations.get(unknown.validationId(),owner.userId()));
        assertEquals("OUTCOME_UNKNOWN",outcome.status());assertEquals(1,outcome.steps().size());
        assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM sim_trades WHERE backtest_run_id=?",Integer.class,run.backtestRunId()));
        assertFalse(advance(app,source));
        assertEquals(2,db.as(owner,()->validations.get(unknown.validationId(),owner.userId())).results().size());

        var cancelled=db.as(owner,()->validations.create(source,UUID.randomUUID(),owner.userId()));
        String target=cancelled.results().getFirst().backtestRunId();
        db.admin.execute("CREATE FUNCTION validation_test_cancel() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.backtest_run_id=TG_ARGV[0] THEN PERFORM pg_advisory_xact_lock(748273); END IF; RETURN NEW; END; $$");
        db.admin.execute("CREATE TRIGGER zz_validation_test_cancel BEFORE INSERT ON sim_pnl_snapshots FOR EACH ROW EXECUTE FUNCTION validation_test_cancel('"+target+"')");
        try(var lock=db.database.source().getConnection();var statement=lock.createStatement();var pool=Executors.newFixedThreadPool(2)) {
            statement.execute("SELECT pg_advisory_lock(748273)");
            try {
                var execution=pool.submit(()->advance(app,cancelled.steps().getFirst().experimentId()));
                long deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();
                while(!Boolean.TRUE.equals(db.admin.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=748273 AND NOT granted)",Boolean.class))) {
                    assertTrue(System.nanoTime()<deadline,"validation did not reach persistence barrier");Thread.sleep(20);
                }
                var cancellation=pool.submit(()->db.as(owner,()->validations.cancel(cancelled.validationId(),owner.userId())));
                assertEquals("CANCELLED",cancellation.get(3,TimeUnit.SECONDS).status());
                statement.execute("SELECT pg_advisory_unlock(748273)");assertTrue(execution.get(10,TimeUnit.SECONDS));
                var completed=db.as(owner,()->validations.get(cancelled.validationId(),owner.userId()));
                assertEquals("CANCELLED",completed.status());assertEquals(1,completed.steps().size());
                assertEquals(1,completed.results().stream().filter(r->r.status().equals("SUCCEEDED")).count());
                assertEquals(1,completed.results().stream().filter(r->r.status().equals("CANCELLED")).count());
            } finally {statement.execute("SELECT pg_advisory_unlock(748273)");}
        } finally {db.admin.execute("DROP TRIGGER zz_validation_test_cancel ON sim_pnl_snapshots");db.admin.execute("DROP FUNCTION validation_test_cancel()");}

        var ineligible=db.as(owner,()->validations.create(source,UUID.randomUUID(),owner.userId()));
        db.as(owner,()->{
            new org.springframework.transaction.support.TransactionTemplate(app.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(s->{
                var jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
                for(var candidate:ineligible.results()) {
                    String attempt=jdbc.queryForObject("SELECT attempt_id::text FROM strategy_experiment_attempts WHERE backtest_run_id=?",String.class,candidate.backtestRunId());
                    jdbc.queryForObject("SELECT set_config('nq.experiment_attempt',?,true)",String.class,attempt);
                    var persistence=app.getBean(BacktestExecutionPersistenceService.class);
                    persistence.markPreparing(candidate.backtestRunId(),Instant.now());
                    persistence.markFailed(candidate.backtestRunId(),Instant.now(),Instant.now(),"INJECTED_LOCAL_FAILURE","受控训练失败注入","{}");
                }
            });return true;
        });
        advance(app,ineligible.steps().getFirst().experimentId());advance(app,ineligible.steps().getFirst().experimentId());advance(app,source);
        var rejected=db.as(owner,()->validations.get(ineligible.validationId(),owner.userId()));
        assertEquals("NO_ELIGIBLE_CANDIDATE",rejected.status());assertEquals(1,rejected.steps().size());
        assertFalse(rejected.steps().getFirst().selection().has("selected"));

        var shortTemplates=db.as(owner,()->StrategyExperimentPostgresIntegrationTest.seed(app));
        var experiments=app.getBean(StrategyExperimentService.class);
        var single=JSON.readTree("[{\"window\":4,\"investedExposure\":\"1\"}]");
        var small=db.as(owner,()->experiments.create(UUID.randomUUID(),new StrategyExperimentService.Request(shortTemplates.get("SPOT_SMA_TARGET_V1"),single,null),owner.userId()));
        advance(app,small.experimentId());
        var insufficient=db.as(owner,()->validations.create(small.experimentId(),UUID.randomUUID(),owner.userId()));
        assertEquals("INSUFFICIENT_DATA",insufficient.status());assertTrue(insufficient.steps().isEmpty());assertTrue(insufficient.results().isEmpty());
    }

    static Map<String,String> seed(ConfigurableApplicationContext app) {
        try {
            JsonNode dataset;
            try(var input=WalkForwardPostgresIntegrationTest.class.getResourceAsStream("/research/walk-forward-btc-usdt-720.json")) {dataset=JSON.readTree(input);}
            var replay=FrozenHistoricalInput.read(dataset,JSON);var bars=new ArrayList<HistoricalBar>();
            for(int i=0;i<replay.size();i++) {
                var b=replay.get(i);
                bars.add(new HistoricalBar(b.exchangeCode(),b.marketType(),b.symbol(),b.interval(),b.openTime(),b.closeTime(),b.openPrice(),b.highPrice(),b.lowPrice(),b.closePrice(),b.volume(),b.quoteVolume(),b.tradeCount(),b.qualityStatus(),b.rawPayloadJson(),Instant.parse(dataset.path("frozenInput").path("sourceObservedAt").get(i).asText())));
            }
            app.getBean(MarketdataBarRepository.class).upsertBars(bars,"WALK_FORWARD_PUBLIC_CAPTURE",Instant.now());
            var datasets=app.getBean(MarketdataDatasetService.class);
            var fresh=datasets.createDataset(new CreateMarketdataDatasetCommand("Frozen public BTC 720 bars","OKX","SPOT","BTC-USDT","1h",bars.getFirst().openTime(),bars.getLast().closeTime(),"experiment-owner"));
            String snapshot=datasets.buildDatasetSnapshot(fresh.datasetId());
            assertEquals(dataset.path("frozenInput").path("sha256"),JSON.readTree(snapshot).path("frozenInput").path("sha256"));
            var account=app.getBean(com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService.class).create(TenantContext.requireCurrent().userId(),new com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand("OKX","SIM","Validation fixture",null));
            var configs=app.getBean(BacktestConfigService.class);var result=new HashMap<String,String>();
            for(String type:List.of("SPOT_SMA_TARGET_V1","SPOT_CHANNEL_BREAKOUT_V1")) {
                var strategy=app.getBean(StrategyDefinitionService.class).create(new StrategyDefinitionCreateRequest("walk-forward-"+type,"Walk Forward "+type,type,"OKX",account.legacyAccountId(),"SIM","{}"));
                var version=app.getBean(StrategyVersionService.class).create(new StrategyVersionCreateRequest(strategy.strategyCode(),"seed","DRAFT",type.contains("SMA")?"{\"window\":20,\"investedExposure\":\"1\"}":"{\"entryLookbackBars\":20,\"exitLookbackBars\":4,\"investedExposure\":\"1\"}","{}","{\"executable\":\""+type+"\"}","experiment-owner"));
                var research=app.getBean(ResearchConfigService.class).create(new ResearchConfigCreateRequest(strategy.strategyId(),"Walk Forward research",null,"{}","{}",snapshot));
                var config=configs.create(new BacktestConfigCreateRequest(research.researchConfigId(),"Walk Forward template "+type,null,bars.getFirst().openTime(),bars.getLast().closeTime(),new BigDecimal("10000"),StrategyExperimentPostgresIntegrationTest.COST,"{}"));
                configs.bindStrategyVersion(config.backtestConfigId(),version.strategyVersionId());configs.bindDataset(config.backtestConfigId(),fresh.datasetId().toString(),snapshot);
                result.put(type,config.backtestConfigId());
            }
            return result;
        } catch(java.io.IOException e) {throw new IllegalStateException(e);}
    }

    static void restart(TenantIsolationTestDatabase db,UUID source,UUID validation) throws Exception {
        Path log=Files.createTempFile("nq-walk-forward-restart-",".log");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),WalkForwardRestartProbeMain.class.getName(),source.toString(),validation.toString());
        builder.environment().put("TASK_TENANT_URL",db.url());builder.environment().put("TASK_TENANT_USER",db.runtime);builder.environment().put("TASK_TENANT_PASSWORD",db.runtimePassword);
        var child=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {assertTrue(child.waitFor(90,TimeUnit.SECONDS),"validation restart timed out");assertEquals(0,child.exitValue(),Files.readString(log));}
        finally {if(child.isAlive())child.destroyForcibly();}
    }
}
