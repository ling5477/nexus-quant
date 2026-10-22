package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.scheduler.paper.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 三个真实 HTTP 主体、隔离 PostgreSQL 与原 canonical SIM 链共同证明平台边界。 */
@EnabledIfSystemProperty(named="nq.demo.pg.required",matches="true")
class PlatformAdminHttpIntegrationTest {
    static long platform(TenantIsolationTestDatabase db,String username) {
        long id = db.user(username,"PLATFORM_ADMIN");
        db.admin.update("UPDATE users SET password_hash=?,must_change_password=false WHERE id=?",
                new BCryptPasswordEncoder().encode(PublicDemoTestHttp.PASSWORD),id);
        return id;
    }

    @Test void independentPlatformSessionAndUserOperationsCannotMutateEconomicFacts() throws Exception {
        try (var db=TenantIsolationTestDatabase.create(null); var reader=PlatformReadTestRoles.create(db)) {
            long admin=platform(db,"platform-chief"); var local=db.local("platform-fixture");
            String run;
            try (var app=PublicDemoTestHttp.start(db,reader.arguments()); var http=new PublicDemoTestHttp(app.getWebServer().getPort())) {
                String token=http.login("platform-chief");
                var health=http.ok("GET","/actuator/health",null,null);
                assertTrue(health.has("status"));
                assertTrue(Set.of("status","groups").containsAll(PublicDemoTestHttp.JSON.convertValue(health,Map.class).keySet()));
                assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM tenant_memberships WHERE user_id=?",Integer.class,admin));
                assertEquals(List.of("PLATFORM_ADMIN"),PublicDemoTestHttp.JSON.convertValue(
                        http.ok("GET","/api/auth/me",token,null).path("roles"),List.class));
                var a=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"platform-user-a");
                var b=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"platform-user-b");
                long target=a.identity().userId(); UUID tenant=a.identity().tenantId().value();
                assertEquals(401,http.send("GET","/api/platform/tenants",null,null).statusCode());
                assertEquals(403,http.send("GET","/api/platform/tenants",a.token(),null).statusCode());
                for(String path:List.of("/api/strategies","/api/research-configs","/api/demo/sim-account",
                        "/api/paper-trading/strategy-sim/analysis/runs","/api/exchange-accounts","/api/unknown"))
                    assertEquals(403,http.send("GET",path,token,null).statusCode(),path);
                assertEquals(403,http.send("POST","/api/strategies",token,Map.of()).statusCode());
                assertEquals(403,http.send("GET","/api/platform/tenants?tenantId="+tenant,token,null).statusCode());
                assertEquals(403,http.send("HEAD","/api/platform/tenants",token,null).statusCode());
                assertEquals(403,http.send("GET","/api/platform/tenants/unregistered",token,null).statusCode());
                var listing=http.ok("GET","/api/platform/tenants?size=1&sort=registeredAt,asc",token,null);
                assertEquals(2,listing.path("total").asInt());assertEquals(1,listing.path("items").size());
                assertEquals(2,listing.path("overview").path("registeredUsers").asInt());
                for(String forbidden:List.of("password_hash","accessToken","verificationToken","passwordHash","credential"))
                    assertFalse(listing.toString().contains(forbidden));
                assertEquals(400,http.send("GET","/api/platform/tenants?size=101",token,null).statusCode());
                assertEquals(1,http.ok("GET","/api/platform/tenants?search=platform-user-a",token,null).path("total").asInt());
                var emptyPerf=http.ok("GET","/api/platform/tenants/"+tenant+"/sim-performance",token,null);
                assertTrue(emptyPerf.path("netPnl").isNull()); assertTrue(emptyPerf.path("equity").isNull());
                assertEquals(0,new BigDecimal("10000").compareTo(new BigDecimal(emptyPerf.path("initialCapital").asText())),emptyPerf.toString());
                var bars=new ArrayList<HistoricalBar>();for(int i=0;i<5;i++)bars.add(PublicDemoTwoUserFlowIntegrationTest.hour(i));
                db.as(local,()->{app.getBean(MarketdataBarRepository.class).upsertBars(bars,"FIXTURE_SYNC",Instant.now());return null;});
                UUID dataset=PublicDemoTwoUserFlowIntegrationTest.sharedDataset(db,bars);
                String snapshot=db.as(a.identity(),()->app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset));
                var chain=PublicDemoTwoUserFlowIntegrationTest.chain(http,a,dataset,snapshot);
                PublicDemoTwoUserFlowIntegrationTest.registerDriver(app);
                run=http.ok("POST",PublicDemoTwoUserFlowIntegrationTest.OPERATIONS,a.token(),
                        PublicDemoTwoUserFlowIntegrationTest.create(UUID.randomUUID(),chain.publish())).path("paperRunId").asText();
                var driver=app.getBean("demoFixtureContinuousDriver",ContinuousSimRunService.class);
                driver.pollOnce(run);assertEquals(1,app.getBean(PaperMatchingService.class).matchOnce(64));
                assertEquals(1,db.admin.queryForObject("SELECT count(*) FROM trades",Integer.class));
                var before=economicFacts(db);
                // 行情调用期间不持数据库许可；禁用完成后返回的行情不得继续提交。
                var networkEntered=new CountDownLatch(1);var networkReturn=new CountDownLatch(1);
                var originalFeed=(ClosedBarMarketFeed)ReflectionTestUtils.getField(driver,"feed");
                ReflectionTestUtils.setField(driver,"feed",(ClosedBarMarketFeed)(first,max)->{
                    networkEntered.countDown();
                    try {if(!networkReturn.await(10,TimeUnit.SECONDS))throw new IllegalStateException("network barrier timeout");}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    return originalFeed.observe(first,max);
                });
                var inFlight=Executors.newSingleThreadExecutor();
                var poll=inFlight.submit(()->driver.pollOnce(run));
                assertTrue(networkEntered.await(10,TimeUnit.SECONDS));
                UUID op=UUID.randomUUID();var body=operation(op);
                com.fasterxml.jackson.databind.JsonNode disabled;
                try {disabled=http.ok("POST","/api/platform/users/"+target+"/disable",token,body);}
                finally {networkReturn.countDown();}
                try {
                    try {poll.get(20,TimeUnit.SECONDS);}catch(ExecutionException rejected) {
                        assertTrue(rejected.getCause() instanceof org.springframework.dao.DataAccessException,rejected.toString());
                    }
                }finally{inFlight.shutdownNow();ReflectionTestUtils.setField(driver,"feed",originalFeed);}
                assertTrue(disabled.path("beforeEnabled").asBoolean());assertFalse(disabled.path("afterEnabled").asBoolean());
                assertEquals(disabled.path("beforeAuthVersion").asLong()+1,disabled.path("afterAuthVersion").asLong());
                assertEquals(disabled.path("beforeExecutionEpoch").asLong()+1,disabled.path("afterExecutionEpoch").asLong());
                assertEquals(disabled,http.ok("POST","/api/platform/users/"+target+"/disable",token,body));
                assertEquals(409,http.send("POST","/api/platform/users/"+b.identity().userId()+"/disable",token,body).statusCode());
                assertEquals(409,http.send("POST","/api/platform/users/"+target+"/enable",token,body).statusCode());
                assertEquals(409,http.send("POST","/api/platform/users/"+target+"/disable",token,Map.of("operationId",op,"reason","changed")).statusCode());
                assertEquals(403,http.send("POST","/api/platform/users/"+admin+"/disable",token,operation(UUID.randomUUID())).statusCode());
                assertEquals(400,http.send("POST","/api/platform/users/"+target+"/enable",token,
                        Map.of("operationId",UUID.randomUUID(),"reason","audit","balance",10000)).statusCode());
                for(String path:List.of("/api/auth/me","/api/demo/sim-account"))assertEquals(401,http.send("GET",path,a.token(),null).statusCode());
                assertEquals(401,http.send("POST","/api/auth/renew",a.token(),null).statusCode());
                assertEquals(401,http.send("POST","/api/auth/login",null,Map.of("username","platform-user-a","password",PublicDemoTestHttp.PASSWORD)).statusCode());
                assertThrows(org.springframework.dao.DataAccessException.class,()->driver.pollOnce(run));
                assertEquals(0,app.getBean(PaperMatchingService.class).matchOnce(64));
                assertEquals(before,economicFacts(db));
                assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NULL",Integer.class,target));
                restart(db,run);
                assertEquals(before,economicFacts(db));
                var enabled=http.ok("POST","/api/platform/users/"+target+"/enable",token,operation(UUID.randomUUID()));
                assertTrue(enabled.path("afterEnabled").asBoolean());
                assertEquals(disabled.path("afterAuthVersion"),enabled.path("afterAuthVersion"));
                assertEquals(disabled.path("afterExecutionEpoch"),enabled.path("afterExecutionEpoch"));
                String renewed=http.login("platform-user-a");
                assertEquals(401,http.send("POST","/api/auth/renew",a.token(),null).statusCode());
                assertThrows(org.springframework.dao.DataAccessException.class,()->driver.pollOnce(run));
                assertEquals(before,economicFacts(db));
                var summary=http.ok("GET","/api/platform/tenants/"+tenant+"/summary",token,null);
                assertTrue(summary.path("requiresManualResume").asBoolean());assertTrue(summary.path("suspended").asBoolean());
                // 尚未 Resume 时再次启停，也必须保留可解释的平台暂停来源和人工恢复路径。
                http.ok("POST","/api/platform/users/"+target+"/disable",token,operation(UUID.randomUUID()));
                http.ok("POST","/api/platform/users/"+target+"/enable",token,operation(UUID.randomUUID()));
                renewed=http.login("platform-user-a");
                http.ok("POST",PublicDemoTwoUserFlowIntegrationTest.OPERATIONS,renewed,
                        Map.of("operationId",UUID.randomUUID().toString(),"type","RESUME","paperRunId",run));
                assertFalse(db.admin.queryForObject("SELECT suspended OR requires_manual_resume FROM tenant_execution_controls WHERE tenant_id=?",Boolean.class,tenant));
                assertEquals(before,economicFacts(db));
                var pause=http.ok("POST","/api/platform/users/"+target+"/disable",token,operation(UUID.randomUUID()));
                db.admin.update("UPDATE tenant_memberships SET status='DISABLED' WHERE user_id=?",target);
                db.admin.update("UPDATE tenant_memberships SET status='ACTIVE' WHERE user_id=?",target);
                assertEquals(pause.path("afterExecutionEpoch").asLong()+1,
                        db.admin.queryForObject("SELECT execution_epoch FROM tenant_execution_controls WHERE tenant_id=?",Long.class,tenant));
                http.ok("POST","/api/platform/users/"+target+"/enable",token,operation(UUID.randomUUID()));
                renewed=http.login("platform-user-a");
                assertEquals(409,http.send("POST",PublicDemoTwoUserFlowIntegrationTest.OPERATIONS,renewed,
                        Map.of("operationId",UUID.randomUUID().toString(),"type","RESUME","paperRunId",run)).statusCode());
                assertTrue(db.admin.queryForObject("SELECT suspended AND requires_manual_resume FROM tenant_execution_controls WHERE tenant_id=?",Boolean.class,tenant));
                assertEquals(before,economicFacts(db));
                assertEquals(204,http.send("POST","/api/auth/logout",token,null).statusCode());
                assertEquals(401,http.send("GET","/api/platform/tenants",token,null).statusCode());
                assertEquals(401,http.send("POST","/api/auth/renew",token,null).statusCode());
            }
        }
    }

    @Test void concurrentDistinctCommandsSerializeAndDisableWaitsForCommittedExecutionPermit() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null);var reader=PlatformReadTestRoles.create(db)) {
            platform(db,"platform-race");
            try(var app=PublicDemoTestHttp.start(db,reader.arguments());var http=new PublicDemoTestHttp(app.getWebServer().getPort());var workers=Executors.newFixedThreadPool(4)) {
                var a=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"platform-race-user");String token=http.login("platform-race");
                long target=a.identity().userId();var before=economicFacts(db);
                long epoch=db.admin.queryForObject("SELECT execution_epoch FROM tenant_execution_controls WHERE tenant_id=?",Long.class,a.identity().tenantId().value());
                var held=new CountDownLatch(1);var release=new CountDownLatch(1);
                var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));tx.setTimeout(15);
                var permit=workers.submit(()->db.as(a.identity(),()->tx.execute(status->{
                    app.getBean(JdbcTemplate.class).queryForObject("SELECT nq_require_tenant_execution(?)",Object.class,a.identity().tenantId().value());
                    held.countDown();try {assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException interrupted){throw new IllegalStateException(interrupted);}return null;})));
                assertTrue(held.await(10,TimeUnit.SECONDS));
                var first=workers.submit(()->http.ok("POST","/api/platform/users/"+target+"/disable",token,operation(UUID.randomUUID())));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline){waiting=db.admin.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%nq_platform_user_operation%')",Boolean.class);if(waiting)break;Thread.sleep(25);}
                try {assertTrue(waiting,"disable must actually wait in PostgreSQL");assertFalse(first.isDone());}finally{release.countDown();}
                permit.get(15,TimeUnit.SECONDS);var original=first.get(20,TimeUnit.SECONDS);
                var repeat=new ArrayList<Future<com.fasterxml.jackson.databind.JsonNode>>();
                for(int i=0;i<3;i++)repeat.add(workers.submit(()->http.ok("POST","/api/platform/users/"+target+"/disable",token,operation(UUID.randomUUID()))));
                for(var call:repeat){var value=call.get(20,TimeUnit.SECONDS);assertEquals(original.path("afterAuthVersion"),value.path("afterAuthVersion"));assertEquals(epoch+1,value.path("afterExecutionEpoch").asLong());}
                assertEquals(4,db.admin.queryForObject("SELECT count(*) FROM platform_user_operations WHERE target_user_id=?",Integer.class,target));
                assertEquals(before,economicFacts(db));
                var denied=assertThrows(org.springframework.transaction.CannotCreateTransactionException.class,()->db.as(a.identity(),()->tx.execute(status->app.getBean(JdbcTemplate.class).queryForObject("SELECT nq_require_tenant_execution(?)",Object.class,a.identity().tenantId().value()))));
                assertTrue(denied.getMessage().contains("TENANT_ACCESS_DENIED"));
            }
        }
    }

    static Map<String,Object> operation(UUID operation){return Map.of("operationId",operation.toString(),"reason","isolated platform control test");}

    @Test void changedOrMixedDemoRolesCannotReuseAPlatformSuspensionPermit() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null);var roles=PlatformReadTestRoles.create(db)) {
            platform(db,"platform-role-check");var local=db.local("platform-role-market");
            try(var app=PublicDemoTestHttp.start(db,roles.arguments());var http=new PublicDemoTestHttp(app.getWebServer().getPort())) {
                String platform=http.login("platform-role-check");
                var bars=new ArrayList<HistoricalBar>();for(int i=0;i<5;i++)bars.add(PublicDemoTwoUserFlowIntegrationTest.hour(i));
                db.as(local,()->{app.getBean(MarketdataBarRepository.class).upsertBars(bars,"FIXTURE_SYNC",Instant.now());return null;});
                UUID dataset=PublicDemoTwoUserFlowIntegrationTest.sharedDataset(db,bars);
                PublicDemoTwoUserFlowIntegrationTest.registerDriver(app);
                for(String source:List.of("changed","mixed")) {
                    var user=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"platform-role-"+source);
                    long target=user.identity().userId();UUID tenant=user.identity().tenantId().value();
                    String snapshot=db.as(user.identity(),()->app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset));
                    var chain=PublicDemoTwoUserFlowIntegrationTest.chain(http,user,dataset,snapshot);
                    String run=http.ok("POST",PublicDemoTwoUserFlowIntegrationTest.OPERATIONS,user.token(),
                            PublicDemoTwoUserFlowIntegrationTest.create(UUID.randomUUID(),chain.publish())).path("paperRunId").asText();
                    var facts=economicFacts(db);
                    var disabled=http.ok("POST","/api/platform/users/"+target+"/disable",platform,operation(UUID.randomUUID()));
                    if(source.equals("changed")) {
                        db.admin.update("DELETE FROM user_roles WHERE user_id=?",target);
                        db.admin.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE role_code='DEMO_USER'",target);
                    } else {
                        db.admin.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE role_code='VIEWER'",target);
                        db.admin.update("DELETE FROM user_roles WHERE user_id=? AND role_id=(SELECT id FROM roles WHERE role_code='VIEWER')",target);
                    }
                    long epoch=db.admin.queryForObject("SELECT execution_epoch FROM tenant_execution_controls WHERE tenant_id=?",Long.class,tenant);
                    assertTrue(epoch>disabled.path("afterExecutionEpoch").asLong());
                    http.ok("POST","/api/platform/users/"+target+"/enable",platform,operation(UUID.randomUUID()));
                    String token=http.login("platform-role-"+source);
                    assertEquals(409,http.send("POST",PublicDemoTwoUserFlowIntegrationTest.OPERATIONS,token,
                            Map.of("operationId",UUID.randomUUID().toString(),"type","RESUME","paperRunId",run)).statusCode());
                    assertTrue(db.admin.queryForObject("SELECT suspended AND requires_manual_resume FROM tenant_execution_controls WHERE tenant_id=?",Boolean.class,tenant));
                    assertEquals(facts,economicFacts(db));
                }
            }
        }
    }

    static Map<String,List<String>> economicFacts(TenantIsolationTestDatabase db) {
        var result=new TreeMap<>(TenantIsolationHttpIntegrationTest.economicSnapshot(db));
        for(String table:List.of("equity_curve_snapshots","sim_asset_funding_receipts","sim_pnl_snapshots"))
            result.put(table,db.admin.queryForList("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text",String.class));
        return result;
    }

    private static void restart(TenantIsolationTestDatabase db,String run) throws Exception {
        Path dir=Path.of("target","platform-admin-evidence").toAbsolutePath();Files.createDirectories(dir);
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Path arguments=dir.resolve("restart.args"),log=dir.resolve("restart.log");
        Files.writeString(arguments,"-cp\n\""+cp.replace("\\","\\\\").replace("\"","\\\"")+"\"\n"+PlatformAdminRestartProbeMain.class.getName()+"\n");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"@"+arguments);
        builder.environment().put("TASK_DEMO_URL",db.url());builder.environment().put("TASK_DEMO_USER",db.runtime);
        builder.environment().put("TASK_DEMO_PASSWORD",db.runtimePassword);builder.environment().put("TASK_PLATFORM_DISABLED_RUN",run);
        var process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {assertTrue(process.waitFor(55,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),"restart log: "+log);
            assertTrue(Files.readString(log).contains("PLATFORM_DISABLE_RESTART_VERIFIED"));}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
}
