package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** 定向浏览器验证用独占本地夹具；停止标记或超时后关闭应用并销毁本例数据库和角色。 */
public final class PublicDemoUiFixtureMain {
    public static void main(String[] args) throws Exception {
        String url = required("TASK_DEMO_URL");
        Path output = Path.of(required("TASK_DEMO_UI_FILE")).toAbsolutePath().normalize();
        Path temporary = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (args.length != 0 || !url.startsWith("jdbc:postgresql://127.0.0.1:") || !output.startsWith(temporary))
            throw new IllegalArgumentException("isolated local browser fixture required");
        Path stop = output.resolveSibling("fixture-stop");
        if (Files.exists(stop)) throw new IllegalStateException("stale browser fixture stop marker");
        try (var db = TenantIsolationTestDatabase.create(null, url, required("TASK_DEMO_USER"), required("TASK_DEMO_PASSWORD"))) {
            var local = db.local("demo-browser-fixture");
            try (var app = PublicDemoTestHttp.start(db);
                    var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
                var bars = new ArrayList<HistoricalBar>();
                for (int i = 0; i < 5; i++) bars.add(PublicDemoTwoUserFlowIntegrationTest.hour(i));
                db.as(local, () -> { app.getBean(MarketdataBarRepository.class).upsertBars(bars, "FIXTURE_SYNC", Instant.now()); return null; });
                UUID dataset = PublicDemoTwoUserFlowIntegrationTest.sharedDataset(db, bars);
                var user = PublicDemoTwoUserFlowIntegrationTest.register(db, http, "ui-bound-demo");
                String snapshot = db.as(user.identity(), () -> app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset));
                var chain = PublicDemoTwoUserFlowIntegrationTest.chain(http, user, dataset, snapshot);
                PublicDemoTwoUserFlowIntegrationTest.registerDriver(app);
                var bound = http.ok("POST", PublicDemoTwoUserFlowIntegrationTest.OPERATIONS, user.token(),
                        PublicDemoTwoUserFlowIntegrationTest.create(UUID.randomUUID(), chain.publish()));
                String run = bound.path("paperRunId").asText();
                app.getBean(ContinuousSimRunService.class).pollOnce(run);
                if (app.getBean(PaperMatchingService.class).matchOnce(64) != 1) throw new AssertionError("browser canonical fixture did not fill");
                Files.createDirectories(output.getParent());
                Files.writeString(output, PublicDemoTestHttp.JSON.writeValueAsString(Map.of(
                        "port", app.getWebServer().getPort(), "datasetId", dataset.toString(), "boundUsername", "ui-bound-demo",
                        "boundRunId", run, "mailApi", "http://127.0.0.1:18025")));
                System.out.println("PUBLIC_DEMO_UI_FIXTURE_READY");
                long deadline = System.nanoTime() + Duration.ofHours(2).toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(250);
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing local fixture input");
        return value;
    }
}
