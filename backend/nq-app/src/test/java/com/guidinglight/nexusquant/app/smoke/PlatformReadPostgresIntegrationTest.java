package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.auth.domain.AuthSession;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/** 真实受限角色证明分页、Session 检查及原始经济事实不可读写；不使用管理身份切换业务租户。 */
@EnabledIfSystemProperty(named="nq.demo.pg.required",matches="true")
class PlatformReadPostgresIntegrationTest {
    // 合成小时窗口按实际测试起点滚动，避免只有每小时前十五分钟才能获得新鲜信号的时间依赖。
    static final Instant CURRENT_START=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).minusSeconds(21660);

    static List<HistoricalBar> currentBars() {
        var bars=new ArrayList<HistoricalBar>();
        for(int i=0;i<5;i++)bars.add(PublicDemoTwoUserFlowIntegrationTest.hour(i,CURRENT_START));
        return bars;
    }

    static ContinuousSimRunService registerCurrentDriver(ServletWebServerApplicationContext app) {
        Instant observed=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        ClosedBarMarketFeed feed=(first,max)->new ClosedBarMarketFeed.Observation(observed,observed,
                List.of(PublicDemoTwoUserFlowIntegrationTest.hour(3,CURRENT_START),PublicDemoTwoUserFlowIntegrationTest.hour(4,CURRENT_START),
                        PublicDemoTwoUserFlowIntegrationTest.hour(5,CURRENT_START))
                    .stream().filter(bar->!bar.openTime().isBefore(first)).toList(),
                new ClosedBarMarketFeed.Rule(observed,"LIVE",new BigDecimal("0.01"),new BigDecimal("0.0001"),new BigDecimal("0.0001")),
                new ClosedBarMarketFeed.Quote(observed.plusMillis(1),new BigDecimal("105")));
        var driver=new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class),app.getBean(StrategySimRunService.class),
                app.getBean(PaperTradingRunService.class),feed,app.getBean(JdbcTemplate.class),Clock.fixed(observed.plusMillis(2),ZoneOffset.UTC));
        ReflectionTestUtils.setField(driver,"tenantWork",app.getBean(TenantWorkAccess.class));
        app.getBeanFactory().registerSingleton("platformReadFixtureDriver",driver);
        return driver;
    }

    @Test void migrationOwnerCannotAcquireUnscopedReadBeforeOrAfterProjectionRoleInstallation() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null)) {
            var tenant=db.demo("PROJECTION_INSTALL_BOUNDARY","projection-install-user");
            TenantIsolationPostgresIntegrationTest.account(db,tenant,"projection-install-account");
            assertEquals(1,db.admin.queryForObject("SELECT count(*) FROM accounts",Integer.class));
            assertUnscopedMigrationOwnerCannotRead(db);
            try(var roles=PlatformReadTestRoles.create(db)) {
                assertUnscopedMigrationOwnerCannotRead(db);
                long actor=db.user("projection-install-admin","PLATFORM_ADMIN");
                Instant now=Instant.now();String session=UUID.randomUUID().toString();
                new JdbcAuthUserRepository(db.admin).createSession(new AuthSession(session,actor,1,now,now,
                        now.plusSeconds(3600)),now.minusSeconds(1800));
                // 安装后受控调用仍可用；reader 本身不能因函数所有权获得经济表 SELECT。
                assertNotNull(roles.jdbc.queryForObject("SELECT nq_platform_tenants(?,?,?,0,20,'',NULL,false)::text",
                        String.class,actor,session,1));
                assertThrows(DataAccessException.class,()->roles.jdbc.queryForList("SELECT * FROM accounts"));
            }
        }
    }

    private static void assertUnscopedMigrationOwnerCannotRead(TenantIsolationTestDatabase db) {
        db.admin.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(var statement=connection.createStatement()) {
                statement.execute("SET ROLE "+db.owner);
                try {
                    try(var result=statement.executeQuery("SELECT nq_is_platform_projection_owner(),count(*) FROM accounts")) {
                        assertTrue(result.next());assertFalse(result.getBoolean(1));assertEquals(0,result.getInt(2));
                    }
                } finally {statement.execute("RESET ROLE");}
            }
            return null;
        });
    }

    @Test void platformReadsTheExistingCanonicalFrozenPerformanceWithoutMutatingEconomicFacts() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null);var roles=PlatformReadTestRoles.create(db)) {
            var local=db.local("platform-read-market-fixture");
            db.user("platform-read-admin","PLATFORM_ADMIN");
            try(var app=PublicDemoTestHttp.start(db,roles.arguments());
                    var http=new PublicDemoTestHttp(app.getWebServer().getPort())) {
                var user=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"platform-read-tenant");
                var other=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"platform-read-other");
                String platform=http.ok("POST","/api/auth/login",null,Map.of("username","platform-read-admin",
                        "password",TenantIsolationTestDatabase.PASSWORD)).path("accessToken").asText();
                String endpoint="/api/platform/tenants/"+user.identity().tenantId().value()+"/sim-performance";
                var noValuation=http.ok("GET",endpoint,platform,null);
                assertEquals("UNAVAILABLE",noValuation.path("status").asText());
                assertTrue(noValuation.path("equity").isNull()); assertTrue(noValuation.path("netPnl").isNull());
                var bars=currentBars();
                db.as(local,()->{app.getBean(MarketdataBarRepository.class).upsertBars(bars,"FIXTURE_SYNC",Instant.now());return null;});
                UUID dataset=PublicDemoTwoUserFlowIntegrationTest.sharedDataset(db,bars);
                String datasetSnapshot=db.as(user.identity(),()->app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset));
                var chain=PublicDemoTwoUserFlowIntegrationTest.chain(http,user,dataset,datasetSnapshot,CURRENT_START);
                var driver=registerCurrentDriver(app);
                String run=http.ok("POST",PublicDemoTwoUserFlowIntegrationTest.OPERATIONS,user.token(),
                        PublicDemoTwoUserFlowIntegrationTest.create(UUID.randomUUID(),chain.publish())).path("paperRunId").asText();
                var polled=driver.pollOnce(run);
                assertEquals(1,app.getBean(PaperMatchingService.class).matchOnce(64),"poll="+polled+" decisions="
                        +db.admin.queryForList("SELECT status FROM strategy_sim_decisions"));
                var performance=db.as(user.identity(),()->app.getBean(StrategySimAnalysisQuery.class)
                        .readPerformance(user.identity().userId(),run,null,null).orElseThrow());
                var mark=performance.samples().getLast();
                var frozen=db.as(user.identity(),()->app.getBean(PaperEquitySnapshotService.class)
                        .generate(user.identity().userId(),run,mark.decisionId(),mark.eventAt()));
                assertEquals("VERIFIED",frozen.status());
                var before=TenantIsolationHttpIntegrationTest.economicSnapshot(db);
                var result=http.ok("GET",endpoint,platform,null);
                assertEquals("AVAILABLE",result.path("status").asText());
                assertEquals(0,frozen.snapshot().totalEquity().compareTo(new BigDecimal(result.path("equity").asText())));
                BigDecimal pnl=new BigDecimal(result.path("netPnl").asText());
                assertEquals(0,frozen.snapshot().totalEquity().subtract(new BigDecimal("10000")).compareTo(pnl));
                assertTrue(pnl.abs().compareTo(new BigDecimal("1000"))<0);
                assertEquals("UNAVAILABLE",http.ok("GET","/api/platform/tenants/"+other.identity().tenantId().value()
                        +"/sim-performance",platform,null).path("status").asText());
                assertEquals(before,TenantIsolationHttpIntegrationTest.economicSnapshot(db));
            }
        }
    }

    @Test void boundedProjectionRequiresCurrentPlatformSessionAndHasNoRawFactPrivileges() throws Exception {
        try(var db=TenantIsolationTestDatabase.create(null);var roles=PlatformReadTestRoles.create(db)) {
            long actor=db.user("projection-admin","PLATFORM_ADMIN");
            var users=new JdbcAuthUserRepository(db.admin);
            var now=Instant.now(); String session=UUID.randomUUID().toString();
            users.createSession(new AuthSession(session,actor,1,now,now,now.plusSeconds(3600)),now.minusSeconds(1800));
            String hash=new BCryptPasswordEncoder().encode("Synthetic-read-only-fixture-2026!");
            for(String name:new String[]{"reader-alice","reader-bob","reader-charlie"})
                db.jdbc.queryForObject("SELECT nq_create_demo_pending(?,?,?,?,?)",Long.class,
                        UUID.randomUUID(),name,name+"@example.invalid",hash,"a".repeat(64));
            var mapper=new ObjectMapper();
            var result=mapper.readTree(roles.jdbc.queryForObject(
                    "SELECT nq_platform_tenants(?,?,?,0,2,'reader-',NULL,false)::text",String.class,actor,session,1));
            assertEquals(3,result.path("total").asInt()); assertEquals(2,result.path("items").size());
            assertFalse(result.toString().contains("password")); assertFalse(result.toString().contains("session"));
            assertTrue(result.path("items").get(0).path("tenantId").isNull());
            var filtered=mapper.readTree(roles.jdbc.queryForObject(
                    "SELECT nq_platform_tenants(?,?,?,0,20,'alice',false,true)::text",String.class,actor,session,1));
            assertEquals(1,filtered.path("items").size());
            assertEquals("reader-alice",filtered.path("items").get(0).path("username").asText());
            assertThrows(DataAccessException.class,()->roles.jdbc.queryForObject(
                    "SELECT nq_platform_tenants(?,?,?,0,101,'',NULL,false)::text",String.class,actor,session,1));
            assertThrows(DataAccessException.class,()->roles.jdbc.queryForObject(
                    "SELECT nq_platform_tenants(?,?,?,0,20,'',NULL,false)::text",String.class,actor,"forged-session",1));
            assertThrows(DataAccessException.class,()->roles.jdbc.queryForList("SELECT * FROM ledger_entries"));
            assertThrows(DataAccessException.class,()->roles.jdbc.queryForList("SELECT password_hash FROM users"));
            assertThrows(DataAccessException.class,()->roles.jdbc.execute("UPDATE orders SET status=status"));
            assertThrows(DataAccessException.class,()->roles.jdbc.execute("UPDATE tenant_execution_controls SET suspended=false"));
            assertThrows(DataAccessException.class,()->db.jdbc.queryForObject(
                    "SELECT nq_platform_tenants(?,?,?,0,20,'',NULL,false)::text",String.class,actor,session,1));
            assertEquals(0,db.admin.queryForObject("""
                    SELECT count(*) FROM pg_proc p JOIN pg_roles r ON r.oid=p.proowner
                    WHERE p.proname IN ('nq_platform_tenants','nq_platform_tenant_summary','nq_platform_sim_facts')
                        AND (r.rolcanlogin OR r.rolbypassrls OR r.rolsuper OR r.rolcreaterole OR r.rolcreatedb)
                    """,Integer.class));
            db.admin.update("UPDATE auth_sessions SET revoked_at=now() WHERE session_id=?",session);
            assertThrows(DataAccessException.class,()->roles.jdbc.queryForObject(
                    "SELECT nq_platform_tenants(?,?,?,0,20,'',NULL,false)::text",String.class,actor,session,1));
        }
    }
}
