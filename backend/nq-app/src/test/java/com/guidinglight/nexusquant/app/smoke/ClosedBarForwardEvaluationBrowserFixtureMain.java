package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.research.StrategyObservationService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 浏览器使用正式服务的独立合成观察；固定身份及全表指纹用于验证读取没有业务副作用。 */
public final class ClosedBarForwardEvaluationBrowserFixtureMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("fresh ready and stop paths required");
        Path ready = Path.of(args[0]).toAbsolutePath(), stop = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(ready) || Files.exists(stop) || !ready.getParent().equals(stop.getParent()))
            throw new IllegalArgumentException("fresh task-owned fixture identity required");
        try (var db = TenantIsolationTestDatabase.create(null, System.getenv("TASK_EXPERIMENT_PG_URL"), "postgres",
                System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner = db.local("experiment-owner"); db.local("forward-other");
            try (var app = ResearchSimComparisonFixture.start(db)) {
                var initial = ClosedBarForwardEvaluationFixture.prepare(app, db, owner, "SPOT_SMA_TARGET_V1");
                var from = initial.from().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(1));
                var to = from.plus(Duration.ofDays(1));
                var scenario = new ForwardSimEvaluationFixture.Scenario(initial.binding(), owner, from, to, to.plusSeconds(5), initial.market());
                ForwardSimEvaluationFixture.clock(app, from.minusSeconds(3600));
                var binding = scenario.binding();
                var observations = app.getBean(StrategyObservationService.class);
                long account = ForwardSimEvaluationFixture.account(db, binding.paperRunId());
                var oldRequest = new StrategyObservationService.CreateRequest(binding.paperRunId(), account,
                        binding.strategyVersionId(), binding.publishId(), binding.validationId(), binding.selectedTrialId(),
                        "FORWARD", from, to, null);
                var legacy = db.as(owner, () -> observations.create(UUID.randomUUID(), oldRequest, owner.userId()));
                var newRequest = new StrategyObservationService.CreateRequest(binding.paperRunId(), account,
                        binding.strategyVersionId(), binding.publishId(), binding.validationId(), binding.selectedTrialId(),
                        "FORWARD", from, to, null, ClosedBarForwardEvaluationFixture.METHOD);
                var trusted = db.as(owner, () -> observations.create(UUID.randomUUID(), newRequest, owner.userId()));
                ClosedBarForwardEvaluationFixture.advance(app, db, scenario);
                var evaluation = db.as(owner, () -> app.getBean(StrategySimAnalysisQuery.class).readForwardEvaluation(
                        owner.userId(), binding.paperRunId(), from, to, scenario.asOf(), ClosedBarForwardEvaluationFixture.METHOD).orElseThrow());
                if (!"AVAILABLE".equals(evaluation.status())) throw new AssertionError("browser trusted evaluation unavailable " + evaluation);
                var reviewRequest = new StrategyObservationService.ReviewRequest(0, "CONTINUE_OBSERVATION",
                        "隔离浏览器夹具的人工研究历史", scenario.asOf(), evaluation.checksum());
                var review = db.as(owner, () -> observations.review(trusted.observation().observationId(), UUID.randomUUID(), reviewRequest, owner.userId()));
                var values = new LinkedHashMap<String, Object>();
                values.put("port", app.getWebServer().getPort()); values.put("pid", ProcessHandle.current().pid());
                values.put("source", "SYNTHETIC_HOURLY_RECEIPT_CANONICAL_SIM"); values.put("syntheticClock", true);
                values.put("experimentId", binding.sourceExperimentId()); values.put("validationId", binding.validationId());
                values.put("selectedTrialId", binding.selectedTrialId()); values.put("strategyVersionId", binding.strategyVersionId());
                values.put("publishId", binding.publishId()); values.put("paperRunId", binding.paperRunId());
                values.put("canonicalAccountId", account); values.put("observationId", trusted.observation().observationId());
                values.put("legacyObservationId", legacy.observation().observationId());
                values.put("reviewId", review.review().reviewId()); values.put("methodVersion", ClosedBarForwardEvaluationFixture.METHOD);
                values.put("observationFrom", from); values.put("observationTo", to); values.put("observedAt", scenario.asOf());
                values.put("forwardFrom", to.plus(Duration.ofDays(1))); values.put("forwardTo", to.plus(Duration.ofDays(2)));
                values.put("owner", "experiment-owner"); values.put("otherUsername", "forward-other");
                values.put("baseFingerprint", ForwardSimEvaluationFixture.allFacts(db)); values.put("evaluation", evaluation);
                Files.createDirectories(ready.getParent());
                Files.writeString(ready, ForwardSimEvaluationFixture.JSON.writeValueAsString(values), StandardOpenOption.CREATE_NEW);
                long deadline = System.nanoTime() + Duration.ofMinutes(45).toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(200);
                Files.writeString(ready.getParent().resolve("browser-final-facts.json"), ForwardSimEvaluationFixture.JSON.writeValueAsString(Map.of(
                        "source", "SYNTHETIC_HOURLY_RECEIPT_CANONICAL_SIM", "finalFingerprint", ForwardSimEvaluationFixture.allFacts(db),
                        "observations", db.admin.queryForList("SELECT observation_id,kind,status,revision FROM strategy_observations ORDER BY observation_id"),
                        "reviews", db.admin.queryForList("SELECT review_id,decision,revision,evaluation_checksum FROM strategy_observation_reviews ORDER BY review_id"))));
            }
        }
    }
}
