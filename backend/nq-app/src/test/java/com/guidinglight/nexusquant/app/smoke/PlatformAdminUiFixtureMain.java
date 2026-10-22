package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.scheduler.paper.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 本轮浏览器使用独占数据库与三个真实身份；停止标记或超时后释放应用和数据库角色。 */
public final class PlatformAdminUiFixtureMain {
    public static void main(String[] args) throws Exception {
        String url=required("TASK_DEMO_URL");
        Path output=Path.of(required("TASK_DEMO_UI_FILE")).toAbsolutePath().normalize();
        if(args.length!=0 || !url.startsWith("jdbc:postgresql://127.0.0.1:")
                || !output.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()))
            throw new IllegalArgumentException("isolated local browser fixture required");
        Path stop=output.resolveSibling("platform-fixture-stop");
        if(Files.exists(stop))throw new IllegalStateException("stale fixture marker");
        try(var db=TenantIsolationTestDatabase.create(null,url,required("TASK_DEMO_USER"),required("TASK_DEMO_PASSWORD"));
                var roles=PlatformReadTestRoles.create(db)) {
            PlatformAdminHttpIntegrationTest.platform(db,"ui-platform-admin");
            var local=db.local("ui-platform-market-fixture");
            try(var app=PublicDemoTestHttp.start(db,roles.arguments());var http=new PublicDemoTestHttp(app.getWebServer().getPort())) {
                var a=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"ui-platform-user-a");
                var b=PublicDemoTwoUserFlowIntegrationTest.register(db,http,"ui-platform-user-b");
                var bars=PlatformReadPostgresIntegrationTest.currentBars();
                db.as(local,()->{app.getBean(MarketdataBarRepository.class).upsertBars(bars,"FIXTURE_SYNC",Instant.now());return null;});
                UUID dataset=PublicDemoTwoUserFlowIntegrationTest.sharedDataset(db,bars);
                String snapshot=db.as(a.identity(),()->app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset));
                var chain=PublicDemoTwoUserFlowIntegrationTest.chain(http,a,dataset,snapshot,PlatformReadPostgresIntegrationTest.CURRENT_START);
                PlatformReadPostgresIntegrationTest.registerCurrentDriver(app);
                String run=http.ok("POST",PublicDemoTwoUserFlowIntegrationTest.OPERATIONS,a.token(),
                        PublicDemoTwoUserFlowIntegrationTest.create(UUID.randomUUID(),chain.publish())).path("paperRunId").asText();
                app.getBean("platformReadFixtureDriver",ContinuousSimRunService.class).pollOnce(run);
                if(app.getBean(PaperMatchingService.class).matchOnce(64)!=1)throw new AssertionError("canonical UI fixture did not fill");
                var view=db.as(a.identity(),()->app.getBean(StrategySimAnalysisQuery.class).readPerformance(a.identity().userId(),run,null,null).orElseThrow());
                var mark=view.samples().getLast();
                var frozen=db.as(a.identity(),()->app.getBean(PaperEquitySnapshotService.class).generate(a.identity().userId(),run,mark.decisionId(),mark.eventAt()));
                if(!"VERIFIED".equals(frozen.status()))throw new AssertionError("canonical UI valuation not verified");
                Files.createDirectories(output.getParent());
                Files.writeString(output,PublicDemoTestHttp.JSON.writeValueAsString(Map.of(
                        "port",app.getWebServer().getPort(),"platformUsername","ui-platform-admin",
                        "usernameA","ui-platform-user-a","usernameB","ui-platform-user-b",
                        "tenantIdA",a.identity().tenantId().value().toString(),"tenantIdB",b.identity().tenantId().value().toString(),
                        "userIdA",a.identity().userId(),"runIdA",run)));
                System.out.println("PLATFORM_UI_FIXTURE_READY");
                long deadline=System.nanoTime()+Duration.ofMinutes(45).toNanos();
                while(!Files.exists(stop) && System.nanoTime()<deadline)Thread.sleep(250);
            }
        }
    }
    private static String required(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("missing fixture input");return value;}
}
