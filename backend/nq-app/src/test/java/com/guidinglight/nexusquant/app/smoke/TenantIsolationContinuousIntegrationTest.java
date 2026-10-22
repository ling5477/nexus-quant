package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.marketdata.domain.*;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.*;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.strategy.domain.PublicReplayAssumptionIdentity;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** 冻结输入仅作合成夹具；经济事实由原 Continuous/Risk/Order/Matching/Ledger 服务形成。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class TenantIsolationContinuousIntegrationTest {
    private JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper();
    private static final Instant START=Instant.parse("2026-09-25T00:00:00Z");

    @Test void networkReadCannotCarryOldExecutionEligibilityAcrossPauseAndExplicitResume() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            jdbc=db.jdbc;var a=db.demo("POLL_A","poll-a");
            db.local("poll-local");
            db.demo("POLL_B","poll-b");
            Fixture fixture=db.as(a,()->seed(hours(),true));
            try(var app=TenantIsolationHttpIntegrationTest.start(db)) {
                var observed=new CountDownLatch(1);var release=new CountDownLatch(1);
                var blocking=new java.util.concurrent.atomic.AtomicBoolean(true);
                ClosedBarMarketFeed feed=(first,max)->{
                    if(blocking.getAndSet(false)) {
                        observed.countDown();
                        try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("fixture feed barrier timeout");}
                        catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    }
                    return observation(first);
                };
                var driver=driver(app,feed);
                app.getBeanFactory().registerSingleton("tenantFixtureContinuousDriver",driver);
                var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
                String run=db.as(a,()->tx.execute(status->driver.start(fixture.publishId(),new BigDecimal("100"),"poll-a"))).paperRunId();
                var before=TenantIsolationHttpIntegrationTest.economicSnapshot(db);
                try(var worker=Executors.newSingleThreadExecutor()) {
                    var poll=worker.submit(()->driver.pollOnce(run));
                    try {
                        assertTrue(observed.await(10,TimeUnit.SECONDS));
                        db.admin.update("UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true WHERE tenant_id=?",a.tenantId().value());
                    } finally {release.countDown();}
                    poll.get(15,TimeUnit.SECONDS);
                }
                assertEquals(before,TenantIsolationHttpIntegrationTest.economicSnapshot(db));
                assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM orders",Integer.class));
                db.admin.update("UPDATE tenant_execution_controls SET suspended=false WHERE tenant_id=?",a.tenantId().value());
                assertThrows(org.springframework.dao.DataAccessException.class,()->driver.pollOnce(run));
                db.as(a,()->tx.execute(status->driver.resume(run)));
                driver.pollOnce(run);
                assertEquals(1,app.getBean(PaperMatchingService.class).matchOnce(64));
                assertEquals(0,app.getBean(PaperMatchingService.class).matchOnce(64));
                assertEquals(1,db.admin.queryForObject("SELECT count(*) FROM orders",Integer.class));
                assertEquals(1,db.admin.queryForObject("SELECT count(*) FROM trades",Integer.class));
                assertEquals(2,db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type LIKE 'SIM_FUNDING_%'",Integer.class));
                try(var http=new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                    String token=http.login("poll-a"),other=http.login("poll-b");
                    String base="/api/paper-trading/strategy-sim/runs/"+run+"/continuous/";
                    String operation=UUID.randomUUID().toString();
                    var stopped=http.ok("POST",base+"stop",token,null,"Idempotency-Key",operation);
                    assertEquals("STOPPED",stopped.path("status").asText());
                    assertEquals(stopped,http.ok("POST",base+"stop",token,null,"Idempotency-Key",operation));
                    TenantIsolationHttpIntegrationTest.assertRejected(http.send("POST",base+"resume",other,null,"Idempotency-Key",UUID.randomUUID().toString()));
                    http.ok("POST",base+"resume",token,null,"Idempotency-Key",UUID.randomUUID().toString());
                    assertEquals(1,db.admin.queryForObject("SELECT count(*) FROM tenant_continuous_slots WHERE active",Integer.class));
                    assertEquals(2,db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type LIKE 'SIM_FUNDING_%'",Integer.class));
                }
                assertTrue(TenantContext.currentTenantId().isEmpty());
            }
        }
    }

    @Test void concurrentTenantsCannotExceedGlobalEightOrOneActiveRunPerTenant() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            jdbc=db.jdbc;var tenants=new ArrayList<TenantIdentity>();var fixtures=new ArrayList<Fixture>();
            for(int i=0;i<9;i++) {var t=db.demo("CAPACITY_"+i,"capacity-"+i);tenants.add(t);fixtures.add(db.as(t,()->seed(hours(),true)));}
            try(var app=TenantIsolationHttpIntegrationTest.start(db);var workers=Executors.newFixedThreadPool(9)) {
                var driver=driver(app,(first,max)->observation(first));var ready=new CountDownLatch(1);
                var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
                var tasks=new ArrayList<Future<Boolean>>();
                for(int i=0;i<9;i++) {int index=i;tasks.add(workers.submit(()->{
                    ready.await();
                    try {db.as(tenants.get(index),()->tx.execute(status->driver.start(fixtures.get(index).publishId(),new BigDecimal("100"),"capacity-"+index)));return true;}
                    catch(org.springframework.dao.DataAccessException failure) {
                        if(!failure.getMostSpecificCause().getMessage().contains("CAPACITY_LIMIT"))throw failure;return false;
                    }
                }));}
                ready.countDown();int accepted=0;
                for(var task:tasks)if(task.get(30,TimeUnit.SECONDS))accepted++;
                assertEquals(8,accepted);
                assertEquals(8,db.admin.queryForObject("SELECT count(*) FROM tenant_continuous_slots WHERE active",Integer.class));
                assertEquals(8,db.admin.queryForObject("SELECT count(DISTINCT tenant_id) FROM tenant_continuous_slots WHERE active",Integer.class));
                assertEquals(16,db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type LIKE 'SIM_FUNDING_%'",Integer.class));
                int index=0;while(!tasks.get(index).get())index++;
                int other=index+1;while(other<9 && !tasks.get(other).get())other++;
                if(other==9) {other=0;while(other==index || !tasks.get(other).get())other++;}
                var stoppedTenant=tenants.get(other);
                String stoppedRun=db.admin.queryForObject("SELECT paper_run_id FROM tenant_continuous_slots WHERE tenant_id=? AND active",String.class,stoppedTenant.tenantId().value());
                db.as(stoppedTenant,()->tx.execute(status->driver.stop(stoppedRun)));
                assertEquals(7,db.admin.queryForObject("SELECT count(*) FROM tenant_continuous_slots WHERE active",Integer.class));
                var tenant=tenants.get(index);var fixture=fixtures.get(index);var before=TenantIsolationHttpIntegrationTest.economicSnapshot(db);
                assertThrows(org.springframework.dao.DataAccessException.class,()->db.as(tenant,()->tx.execute(status->driver.start(fixture.publishId(),new BigDecimal("100"),"capacity"))));
                assertEquals(before,TenantIsolationHttpIntegrationTest.economicSnapshot(db));
            }
        }
    }

    private ContinuousSimRunService driver(org.springframework.context.ApplicationContext app,ClosedBarMarketFeed feed) {
        var result=new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class),app.getBean(StrategySimRunService.class),
                app.getBean(PaperTradingRunService.class),feed,app.getBean(JdbcTemplate.class),Clock.fixed(START.plusSeconds(21662),ZoneOffset.UTC));
        ReflectionTestUtils.setField(result,"tenantWork",app.getBean(TenantWorkAccess.class));return result;
    }
    private List<HistoricalBar> hours() {var result=new ArrayList<HistoricalBar>();for(int i=0;i<5;i++)result.add(hour(i));return result;}
    private HistoricalBar hour(int value) {
        Instant open=START.plusSeconds(value*3600L);BigDecimal price=new BigDecimal(100+value);
        return new HistoricalBar("OKX","SPOT","BTC-USDT",BarInterval.ONE_HOUR,open,open.plusSeconds(3600).minusMillis(1),price,price,price,price,
                BigDecimal.ONE,null,null,"OK","{}",open.plusSeconds(3600).minusMillis(1));
    }
    private ClosedBarMarketFeed.Observation observation(Instant first) {
        Instant now=START.plusSeconds(21661);
        return new ClosedBarMarketFeed.Observation(now,now,List.of(hour(3),hour(4),hour(5)).stream().filter(bar->!bar.openTime().isBefore(first)).toList(),
                new ClosedBarMarketFeed.Rule(now,"LIVE",new BigDecimal("0.01"),new BigDecimal("0.0001"),new BigDecimal("0.0001")),
                new ClosedBarMarketFeed.Quote(now.plusSeconds(1),new BigDecimal("105")));
    }
    private JsonNode read(String text) {try{return mapper.readTree(text);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private record Fixture(String publishId,String versionId,String digest) {}
    private Fixture seed(List<HistoricalBar> bars, boolean publicCapture) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String strategyId = "str-gz-" + id;
        String strategyCode = "gz-" + id;
        String versionId = "sv-gz-" + id;
        String researchId = "rc-gz-" + id;
        String configId = "bc-gz-" + id;
        String backtestId = "br-gz-" + id;
        String publishId = "pub-gz-" + id;
        String checksum = "a".repeat(64);
        String params = "{\"window\":3,\"investedExposure\":\"1\"}";
        String source = "{\"executable\":\"SPOT_SMA_TARGET_V1\"}";
        String backtestVersion = "{\"strategyVersionId\":\"" + versionId
                + "\",\"checksum\":\"" + checksum + "\",\"paramSnapshotJson\":"
                + params + ",\"sourceSnapshotJson\":" + source + "}";
        String publishVersion = "{\"strategyVersionId\":\"" + versionId
                + "\",\"checksum\":\"" + checksum + "\",\"paramSnapshot\":"
                + params + ",\"sourceSnapshot\":" + source + "}";
        Long account = jdbc.queryForObject("""
                INSERT INTO accounts(account_code,venue,status) VALUES (?,'PAPER','ACTIVE') RETURNING account_id
                """, Long.class, "STRATEGY-SIM-TEST-SOURCE-" + id);
        jdbc.update("""
                INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,
                    exchange_code,account_id,trade_env,enabled,config_snapshot)
                VALUES (?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}'::jsonb)
                """, strategyId, strategyCode, "Strategy SIM synthetic", account);
        jdbc.update("""
                INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,
                    param_snapshot_json,source_snapshot_json,checksum)
                VALUES (?,?,1,'v1','ACTIVE',?::jsonb,?::jsonb,?)
                """, versionId, strategyCode, params, source, checksum);
        jdbc.update("""
                INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)
                VALUES (?,?,?,'{}'::jsonb)
                """, researchId, strategyId, "Strategy SIM synthetic");
        jdbc.update("""
                INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,
                    strategy_version_id,strategy_version_snapshot_json,param_snapshot_json)
                VALUES (?,?,?, ?,?::jsonb,?::jsonb)
                """, configId, researchId, "Strategy SIM synthetic", versionId, backtestVersion, params);
        var identity = SpotBarIdentity.capture(bars, mapper);
        ObjectNode summary = mapper.createObjectNode();
        summary.put("strategyVersionId", versionId);
        summary.put("exchangeCode", "OKX");
        summary.put("symbol", "BTC-USDT");
        summary.put("interval", publicCapture ? "1h" : "1m");
        summary.put("barCount", bars.size());
        summary.put("barContentSha256", identity.sha256());
        summary.set("consumedBars", read(identity.canonicalJson()));
        ObjectNode assumptions = (ObjectNode) read("""
                {"quantityStep":"0.0001","priceTick":"0.01","minimumQuantity":"0.0001",
                 "minimumNotional":"5","feeRate":"0.001","slippageBps":"10"}
                """);
        String dataset;
        if (publicCapture) {
            assumptions.put("ruleSha256", "b".repeat(64));
            assumptions.put("rulePolicy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
            summary.put("datasetProvider", "public-capture");
            summary.put("costAndRuleSha256", PublicReplayAssumptionIdentity.sha256(assumptions));
            dataset = "{\"provider\":\"public-capture\",\"source\":\"OKX_PUBLIC_CAPTURE\",\"exchangeCode\":\"OKX\",\"symbol\":\"BTC-USDT\","
                    + "\"interval\":\"1h\",\"barCount\":" + bars.size() + ",\"capture\":{"
                    + "\"consumedSha256\":\"" + identity.sha256() + "\","
                    + "\"replayVisibilityVersion\":\"CLOSED_HOURLY_BOUNDARY_V1\","
                    + "\"replayVisibilitySource\":\"EXPERIMENT_ASSUMPTION\","
                    + "\"ruleSha256\":\"" + "b".repeat(64) + "\"}}";
        } else {
            dataset = "{\"provider\":\"synthetic\",\"datasetId\":\"gz-" + id
                    + "\",\"exchangeCode\":\"OKX\",\"symbol\":\"BTC-USDT\",\"interval\":\"1m\"}";
        }
        summary.set("costAndRuleAssumptions", assumptions);
        jdbc.update("""
                INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,
                    source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,summary_json,
                    requested_at,strategy_version_id,strategy_version_snapshot_json,param_snapshot_json,
                    dataset_snapshot_json)
                VALUES (?,?,?,?,'SUCCEEDED','{}'::jsonb,'{}'::jsonb,?::jsonb,now(),?,?::jsonb,
                    ?::jsonb,?::jsonb)
                """, backtestId, configId, researchId, strategyId, summary.toString(),
                versionId, backtestVersion, params, dataset);
        jdbc.update("""
                INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,
                    backtest_config_id,source_strategy_id,target_strategy_definition_id,publish_status,
                    publish_name,publish_snapshot_json,strategy_version_id,version_snapshot_json)
                VALUES (?,?,?,?,?,?,'SUCCEEDED',?,?::jsonb,?,?::jsonb)
                """, publishId, backtestId, researchId, configId, strategyId, strategyId,
                "Strategy SIM synthetic", "{\"strategyCode\":\"" + strategyCode + "\"}", versionId, publishVersion);
        return new Fixture(publishId, versionId, identity.sha256());
    }

}
