package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;

/** 浏览器只使用任务专属PG和正式服务建立的SIM；合成时钟推进不代表真实长期观察。 */
public final class ForwardSimEvaluationBrowserFixtureMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("fresh ready and stop paths required");
        Path ready = Path.of(args[0]).toAbsolutePath(), stop = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(ready) || Files.exists(stop) || !ready.getParent().equals(stop.getParent()))
            throw new IllegalArgumentException("fresh task-owned fixture identity required");
        try (var db = TenantIsolationTestDatabase.create(null, System.getenv("TASK_EXPERIMENT_PG_URL"), "postgres",
                System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner = db.local("experiment-owner"); db.local("forward-other");
            try (var app = ResearchSimComparisonFixture.start(db)) {
                var scenario = ForwardSimEvaluationFixture.seed(app, db, owner, "SPOT_SMA_TARGET_V1");
                var binding = scenario.binding();
                var values = new LinkedHashMap<String, Object>();
                values.put("port", app.getWebServer().getPort()); values.put("pid", ProcessHandle.current().pid());
                values.put("source", ForwardSimEvaluationFixture.SOURCE); values.put("syntheticClock", true);
                values.put("experimentId", binding.sourceExperimentId()); values.put("validationId", binding.validationId());
                values.put("selectedTrialId", binding.selectedTrialId()); values.put("strategyVersionId", binding.strategyVersionId());
                values.put("publishId", binding.publishId()); values.put("paperRunId", binding.paperRunId());
                values.put("canonicalAccountId", ForwardSimEvaluationFixture.account(db, binding.paperRunId()));
                values.put("observationFrom", scenario.from()); values.put("observationTo", scenario.to()); values.put("observedAt", scenario.asOf());
                var future = scenario.asOf().plusSeconds(3600).truncatedTo(ChronoUnit.HOURS);
                values.put("forwardFrom", future); values.put("forwardTo", future.plus(Duration.ofDays(1)));
                values.put("owner", "experiment-owner");
                values.put("otherUsername", "forward-other");
                values.put("baseFingerprint", ForwardSimEvaluationFixture.allFacts(db));
                Files.createDirectories(ready.getParent());
                Files.writeString(ready, ForwardSimEvaluationFixture.JSON.writeValueAsString(values), StandardOpenOption.CREATE_NEW);
                long deadline = System.nanoTime() + Duration.ofMinutes(45).toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(200);
                // 保留业务结果指纹供浏览器证明只读/元数据写入边界，绝不输出数据库口令或会话令牌。
                ForwardSimEvaluationFixture.evidence("browser-final-facts.json", java.util.Map.of(
                        "source", ForwardSimEvaluationFixture.SOURCE, "finalFingerprint", ForwardSimEvaluationFixture.allFacts(db),
                        "observations", db.admin.queryForList("SELECT observation_id,kind,status,revision FROM strategy_observations ORDER BY observation_id"),
                        "reviews", db.admin.queryForList("SELECT review_id,decision,revision,evaluation_checksum FROM strategy_observation_reviews ORDER BY review_id")));
            }
        }
    }
}
