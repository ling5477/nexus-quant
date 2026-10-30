package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.research.ExperimentValidationService;
import com.guidinglight.nexusquant.app.research.StrategyExperimentService;
import com.guidinglight.nexusquant.app.research.StrategyExperimentWorker;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataIngestionJobCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataIngestionService;
import com.guidinglight.nexusquant.marketdata.domain.FrozenHistoricalInput;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 隔离PG16的正式长输入/执行/WF证明；全部行情是显式合成数据，不能宣称真实市场表现。 */
@EnabledIfSystemProperty(named = "nq.tenant.pg.required", matches = "true")
class LongHistoricalResearchPostgresIntegrationTest {
    private static final Instant START = LongHistoricalResearchBrowserFixtureMain.START;

    @Test @Timeout(240)
    void boundedAdapterIngestionRetains3000PrefixAndResumes4320WithoutDuplicateIdentity() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = LongHistoricalResearchBrowserFixtureMain.start(db)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var ingestion = app.getBean(MarketdataIngestionService.class);
                var server = app.getBean(LongHistoricalResearchBrowserFixtureMain.MarketServer.class);
                var command = new CreateMarketdataIngestionJobCommand("OKX", "SPOT", "BTC-USDT", "1h",
                        START, START.plusSeconds(4320L * 3600).minusMillis(1), "experiment-owner");
                UUID job = UUID.randomUUID(), firstId = UUID.randomUUID();
                db.as(owner, () -> ingestion.createJob(job, command));
                assertEquals(job, db.as(owner, () -> ingestion.createJob(job, command)).jobId());
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> ingestion.createJob(job,
                        new CreateMarketdataIngestionJobCommand("OKX", "SPOT", "BTC-USDT", "1h", START,
                                command.endTime().minusSeconds(3600), "experiment-owner"))));
                var first = db.as(owner, () -> ingestion.runOnce(job, firstId));
                assertEquals(MarketdataIngestionStatus.PARTIAL, first.status());
                assertEquals("INGESTION_BATCH_LIMIT_CONTINUE", first.errorMessage());
                assertEquals(3000, first.insertedBars());
                assertEquals(3000, db.admin.queryForObject("SELECT count(*) FROM marketdata_bars WHERE symbol='BTC-USDT'", Integer.class));
                assertEquals(10, server.historyRequests.get());
                assertEquals(first, db.as(owner, () -> ingestion.runOnce(job, firstId)));
                assertEquals(10, server.historyRequests.get());

                // 一次来源故障耗尽原Adapter重试后，已提交3000根前缀仍可由新的显式运行恢复。
                server.failingRequests.set(3);
                UUID failedId = UUID.randomUUID();
                var failed = db.as(owner, () -> ingestion.runOnce(job, failedId));
                assertEquals(MarketdataIngestionStatus.FAILED, failed.status());
                assertEquals(0, failed.insertedBars());
                assertEquals(3000, db.admin.queryForObject("SELECT count(*) FROM marketdata_bars WHERE symbol='BTC-USDT'", Integer.class));
                assertEquals(failed, db.as(owner, () -> ingestion.runOnce(job, failedId)));
                UUID resumedId = UUID.randomUUID();
                var resumed = db.as(owner, () -> ingestion.runOnce(job, resumedId));
                assertEquals(MarketdataIngestionStatus.SUCCEEDED, resumed.status());
                assertEquals(START.plusSeconds(3000L * 3600), resumed.requestedStartTime());
                assertEquals(1320, resumed.insertedBars());
                assertEquals(0, resumed.updatedBars());
                assertEquals(4320, db.admin.queryForObject("SELECT count(*) FROM marketdata_bars WHERE symbol='BTC-USDT'", Integer.class));
                assertEquals(4320, db.admin.queryForObject("SELECT count(DISTINCT open_time) FROM marketdata_bars WHERE symbol='BTC-USDT'", Integer.class));
                assertEquals(resumed, db.as(owner, () -> ingestion.runOnce(job, resumedId)));
                assertEquals(18, server.historyRequests.get());
                var datasets = app.getBean(MarketdataDatasetService.class);
                var dataset = db.as(owner, () -> datasets.createDataset(new CreateMarketdataDatasetCommand(
                        "Synthetic adapter 180 UTC days", "OKX", "SPOT", "BTC-USDT", "1h", START,
                        command.endTime(), "experiment-owner")));
                assertEquals(4320, dataset.barCount()); assertEquals(0, dataset.gapCount());
                var snapshot = db.as(owner, () -> json(datasets.buildDatasetSnapshot(dataset.datasetId())));
                assertEquals("HISTORICAL_RESEARCH_INPUT_V2", snapshot.path("frozenInput").path("version").asText());
                assertEquals(4320, db.as(owner, () -> datasets.readFrozenInput(snapshot)).size());
                evidence("ingestion", Map.of("dataSource", "SYNTHETIC_LONG_HISTORY", "bars", 4320,
                        "confirmedPrefix", 3000, "resumedBars", 1320, "actualAdapterRequests", server.historyRequests.get(),
                        "jobId", job.toString(), "sourceRequestsBoundedAt", 300));
            }
            db.migration(null).validate();
        }
    }

    @Test @Timeout(480)
    void upgradeFreezeReplayOwnershipCorruptionAndLegacy720RemainCompatible() throws Exception {
        try (var db = TenantIsolationTestDatabase.create("49")) {
            var owner = db.local("experiment-owner");
            var other = db.local("history-other");
            var tenant = db.demo("LONG_HISTORY_OTHER", "history-demo");
            int users = db.admin.queryForObject("SELECT count(*) FROM users", Integer.class);
            db.migrate(null); db.runtime();
            assertEquals(users, db.admin.queryForObject("SELECT count(*) FROM users", Integer.class));
            assertEquals(16, db.admin.queryForObject("SELECT current_setting('server_version_num')::int/10000", Integer.class));
            try (var app = LongHistoricalResearchBrowserFixtureMain.start(db)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                resetPeakHeap();
                long freezeStarted = System.nanoTime();
                var template = db.as(owner, () -> LongHistoricalResearchBrowserFixtureMain.seedTemplate(
                        app, "BTC-USDT", 4320, "SPOT_SMA_TARGET_V1", "replay"));
                long freezeNanos = System.nanoTime() - freezeStarted;
                var datasets = app.getBean(MarketdataDatasetService.class);
                JsonNode snapshot = json(template.snapshot());
                assertEquals("HISTORICAL_RESEARCH_INPUT_V2", snapshot.path("frozenInput").path("version").asText());
                assertFalse(snapshot.path("frozenInput").has("bars"));
                long readStarted = System.nanoTime();
                var input = db.as(owner, () -> datasets.readFrozenInput(snapshot));
                long readNanos = System.nanoTime() - readStarted;
                assertEquals(4320, input.size());
                assertEquals(START, input.getFirst().openTime());
                assertEquals(START.plusSeconds(4320L * 3600).minusMillis(1), input.getLast().closeTime());
                assertEquals(snapshot.path("frozenInput").path("sha256").asText(), SpotBarIdentity.captureResearch(input,
                        LongHistoricalResearchBrowserFixtureMain.JSON).sha256());
                assertEquals(template.snapshot(), db.as(owner, () -> datasets.buildDatasetSnapshot(template.datasetId())));
                assertThrows(RuntimeException.class, () -> db.as(other, () -> datasets.readFrozenInput(snapshot)));
                assertThrows(RuntimeException.class, () -> db.as(tenant, () -> datasets.readFrozenInput(snapshot)));
                assertEquals(0, db.as(owner, () -> db.jdbc.update(
                        "UPDATE research_dataset_versions SET dataset_id=dataset_id WHERE dataset_id=?", template.datasetId())));
                assertEquals(0, db.as(owner, () -> db.jdbc.update("DELETE FROM research_dataset_chunks")));
                assertThrows(RuntimeException.class, () -> db.admin.update(
                        "UPDATE research_dataset_versions SET dataset_id=dataset_id WHERE dataset_id=?", template.datasetId()));
                ObjectNode corrupt = snapshot.deepCopy();
                ((ObjectNode) corrupt.path("frozenInput")).put("sha256", "0".repeat(64));
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> datasets.readFrozenInput(corrupt)));
                ObjectNode absent = snapshot.deepCopy();
                ((ObjectNode) absent.path("frozenInput")).put("datasetVersionId", UUID.randomUUID().toString());
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> datasets.readFrozenInput(absent)));
                verifyCorruptedAndMissingChunk(db, owner, datasets, snapshot);

                var runs = app.getBean(BacktestRunService.class);
                var execution = app.getBean(BacktestExecutionService.class);
                var evaluations = app.getBean(BacktestEvaluationService.class);
                var first = db.as(owner, () -> runs.create(new BacktestRunStartRequest(template.configId())));
                long executionStarted = System.nanoTime();
                db.as(owner, () -> execution.startRun(first.backtestRunId()));
                var firstReport = db.as(owner, () -> evaluations.evaluate(first.backtestRunId()));
                long executionNanos = System.nanoTime() - executionStarted;
                assertEquals(BacktestRunStatus.SUCCEEDED, db.as(owner, () -> runs.getByBacktestRunId(first.backtestRunId())).status());
                assertEquals(4320, db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots WHERE backtest_run_id=?", Integer.class, first.backtestRunId()));
                String economicFacts = economicFacts(db, first.backtestRunId());
                assertLedgerOracle(db, first.backtestRunId(), firstReport);
                assertTrue(executionNanos < Duration.ofSeconds(30).toNanos(), "4320-bar run and evaluation exceeded planned normal budget");

                db.admin.update("UPDATE marketdata_bars SET volume=volume+100 WHERE symbol='BTC-USDT' AND open_time=?", Timestamp.from(START.plusSeconds(4000L * 3600)));
                assertEquals(template.snapshot(), db.as(owner, () -> datasets.buildDatasetSnapshot(template.datasetId())));
                var repeated = db.as(owner, () -> runs.createWithFrozenAssumptions(
                        new BacktestRunStartRequest(template.configId()), first.backtestRunId()));
                db.as(owner, () -> execution.startRun(repeated.backtestRunId()));
                var repeatedReport = db.as(owner, () -> evaluations.evaluate(repeated.backtestRunId()));
                assertEquals(economicFacts, economicFacts(db, repeated.backtestRunId()));
                for (BigDecimal[] metric : List.of(new BigDecimal[]{firstReport.netPnl(), repeatedReport.netPnl()},
                        new BigDecimal[]{firstReport.totalReturn(), repeatedReport.totalReturn()},
                        new BigDecimal[]{firstReport.maxDrawdownRate(), repeatedReport.maxDrawdownRate()},
                        new BigDecimal[]{firstReport.totalFee(), repeatedReport.totalFee()},
                        new BigDecimal[]{firstReport.totalSlippage(), repeatedReport.totalSlippage()}))
                    assertEquals(0, metric[0].compareTo(metric[1]));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM orders", Integer.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class));

                var legacy = db.as(owner, () -> LongHistoricalResearchBrowserFixtureMain.seedTemplate(
                        app, "ETH-USDT", 720, "SPOT_CHANNEL_BREAKOUT_V1", "legacy"));
                var legacySnapshot = json(legacy.snapshot());
                assertEquals(FrozenHistoricalInput.VERSION, legacySnapshot.path("frozenInput").path("version").asText());
                assertEquals(720, FrozenHistoricalInput.read(legacySnapshot, LongHistoricalResearchBrowserFixtureMain.JSON).size());
                var legacyRun = db.as(owner, () -> runs.create(new BacktestRunStartRequest(legacy.configId())));
                db.as(owner, () -> execution.startRun(legacyRun.backtestRunId()));
                var legacyReport = db.as(owner, () -> evaluations.evaluate(legacyRun.backtestRunId()));
                assertEquals("SUCCEEDED", legacyReport.evaluationStatus().name());
                assertEquals(720, db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots WHERE backtest_run_id=?", Integer.class, legacyRun.backtestRunId()));

                // 未冻结的新Dataset不能将采集时间晚于当前冻结点的来源伪装成已确认输入。
                db.admin.update("UPDATE marketdata_bars SET available_at=? WHERE symbol='ETH-USDT' AND open_time=?",
                        Timestamp.from(Instant.now().plusSeconds(86400)), Timestamp.from(START));
                var future = db.as(owner, () -> datasets.createDataset(new CreateMarketdataDatasetCommand("Future observation rejected",
                        "OKX", "SPOT", "ETH-USDT", "1h", START, START.plusSeconds(720L * 3600).minusMillis(1), "experiment-owner")));
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> datasets.buildDatasetSnapshot(future.datasetId())));

                var measured = new LinkedHashMap<String, Object>();
                measured.put("dataSource", "SYNTHETIC_LONG_HISTORY"); measured.put("bars", 4320);
                measured.put("canonicalInputBytes", SpotBarIdentity.captureResearch(input, LongHistoricalResearchBrowserFixtureMain.JSON)
                        .canonicalJson().getBytes(StandardCharsets.UTF_8).length);
                measured.put("referenceSnapshotBytes", template.snapshot().getBytes(StandardCharsets.UTF_8).length);
                measured.put("seedAndFreezeMillis", freezeNanos / 1_000_000d);
                measured.put("queryAndVerifyMillis", readNanos / 1_000_000d);
                measured.put("backtestAndEvaluationMillis", executionNanos / 1_000_000d);
                measured.put("peakHeapPoolUpperBoundBytes", peakHeap());
                measured.put("testJvmPid", ProcessHandle.current().pid());
                measured.put("heapUsedAtMeasurementBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                measured.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
                measured.put("databaseBytes", db.admin.queryForObject("SELECT pg_database_size(current_database())", Long.class));
                measured.put("gcCollections", ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum());
                measured.put("queryCount", "NOT_INSTRUMENTED"); measured.put("run", first.backtestRunId());
                evidence("frozen-replay-resources", measured);
            }
            db.migration(null).validate();
        }
    }

    @Test @Timeout(1200)
    void ninetyAndOneHundredEightyDayFormalWalkForwardUseFrozenWindowsAndBoundedFourCandidates() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = LongHistoricalResearchBrowserFixtureMain.start(db)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var experiments = app.getBean(StrategyExperimentService.class);
                var validations = app.getBean(ExperimentValidationService.class);
                var resources = new ArrayList<Map<String, Object>>();
                for (int count : List.of(4320, 2160)) {
                    String type = count == 4320 ? "SPOT_SMA_TARGET_V1" : "SPOT_CHANNEL_BREAKOUT_V1";
                    var template = db.as(owner, () -> LongHistoricalResearchBrowserFixtureMain.seedTemplate(app,
                            count == 4320 ? "BTC-USDT" : "ETH-USDT", count, type, "wf-" + count));
                    var grid = json(type.contains("SMA") ? "{\"window\":[4,20],\"investedExposure\":[\"0.5\",\"1\"]}"
                            : "{\"entryLookbackBars\":[10,20],\"exitLookbackBars\":[4],\"investedExposure\":[\"0.5\",\"1\"]}");
                    long started = System.nanoTime(); resetPeakHeap();
                    UUID operation = UUID.randomUUID();
                    var request = new StrategyExperimentService.Request(template.configId(), null, grid);
                    var source = db.as(owner, () -> experiments.create(operation, request, owner.userId()));
                    assertEquals(source.experimentId(), db.as(owner, () -> experiments.create(operation, request, owner.userId())).experimentId());
                    assertEquals(4, source.trials().size());
                    Map<String, Object> recovery = Map.of();
                    if (count == 4320) {
                        recovery = interruptAndResumeLongSource(db, source.experimentId(), source.trials().getFirst().backtestRunId());
                    } else {
                        for (int i = 0; i < 4; i++) advance(app, source.experimentId());
                    }
                    assertEquals("SUCCEEDED", db.as(owner, () -> experiments.get(source.experimentId(), owner.userId())).status());
                    var preview = db.as(owner, () -> validations.preview(source.experimentId(), owner.userId()));
                    assertEquals(count, preview.path("barCount").asInt());
                    assertEquals("EXPANDING_WALK_FORWARD_V2", preview.path("version").asText());
                    assertEquals("SOURCE_EXPERIMENT_ALREADY_EVALUATED_FULL_DATASET_NOT_PRISTINE", preview.path("holdoutExposure").asText());
                    UUID validationOperation = UUID.randomUUID();
                    var validation = db.as(owner, () -> validations.create(source.experimentId(), validationOperation, owner.userId()));
                    assertEquals(validation.validationId(), db.as(owner, () -> validations.create(source.experimentId(), validationOperation, owner.userId())).validationId());
                    WalkForwardPostgresIntegrationTest.drain(app, db, owner, validation.validationId(), source.experimentId());
                    var finished = db.as(owner, () -> validations.get(validation.validationId(), owner.userId()));
                    assertEquals("SUCCEEDED", finished.status(), finished.failureReason());
                    assertEquals(16, finished.steps().size()); assertEquals(28, finished.results().size());
                    assertEquals(8, finished.costSensitivity().size());
                    assertEquals(8, java.util.stream.StreamSupport.stream(finished.costSensitivity().spliterator(), false)
                            .filter(row -> "AVAILABLE".equals(row.path("status").asText())).count());
                    int unit = count / 6;
                    for (var step : finished.steps()) {
                        int end = unit * (2 + step.number() / 4);
                        assertEquals(step.number() % 4 == 0 ? 0 : end, step.startBar());
                        assertEquals(step.number() % 4 == 0 ? end : end + unit, step.endBar());
                        if (step.number() % 4 == 0) {
                            assertEquals(4, step.selection().path("rankings").size());
                            var selected = step.selection().path("selected");
                            var independent = finished.results().stream().filter(row -> row.step() == step.number() + 1).findFirst().orElseThrow();
                            assertEquals(selected.path("versionId").asText(), independent.strategyVersionId());
                            assertFalse(step.selectedAt().isAfter(independent.evaluatedAt()));
                        }
                    }
                    WalkForwardPostgresIntegrationTest.verifyWindowFacts(db, finished);
                    if (count == 4320) verifyFutureTrainingIsolation(app, db, owner, finished);
                    verifyInvalidPolicyAndWindowRejected(app, db, owner, source.experimentId(), finished);
                    assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM strategy_experiments WHERE octet_length(frozen_input::text)>2097152", Integer.class));
                    assertEquals(1L, finished.datasetValidationUses());
                    int economicCount = db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots", Integer.class);
                    advance(app, source.experimentId());
                    assertEquals(economicCount, db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots", Integer.class));
                    resources.add(Map.of("bars", count, "sourceRuns", 4, "validationRuns", finished.results().size(),
                            "durationMillis", (System.nanoTime() - started) / 1_000_000d,
                            "peakHeapPoolUpperBoundBytes", peakHeap(), "source", source.experimentId().toString(),
                            "validation", finished.validationId().toString(), "crossJvmRecovery", recovery));
                }
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM orders", Integer.class));
                evidence("walk-forward-resources", Map.of("dataSource", "SYNTHETIC_LONG_HISTORY", "scenarios", resources,
                        "testJvmPid", ProcessHandle.current().pid(), "heapUsedAtMeasurementBytes",
                        ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()));
            }
        }
    }

    private static boolean advance(ConfigurableApplicationContext app, UUID id) {
        return app.getBean(TenantWorkAccess.class).execute(TenantWorkAccess.Kind.EXPERIMENT, id.toString(),
                () -> app.getBean(StrategyExperimentWorker.class).advance(id));
    }

    /** 新建反例父对象并在同事务插入首步骤；失败必须整体回滚，不改写原冻结政策或历史选择。 */
    private static void verifyInvalidPolicyAndWindowRejected(ConfigurableApplicationContext app,
            TenantIsolationTestDatabase db, TenantIdentity owner, UUID source, ExperimentValidationService.View original) {
        var policies = new ArrayList<ObjectNode>();
        ObjectNode missing = original.frozenPolicy().deepCopy(); missing.remove("version"); policies.add(missing);
        ObjectNode nil = original.frozenPolicy().deepCopy(); nil.putNull("version"); policies.add(nil);
        ObjectNode unknown = original.frozenPolicy().deepCopy(); unknown.put("version", "UNKNOWN_RESEARCH_POLICY"); policies.add(unknown);
        for (ObjectNode policy : policies)
            assertInvalidStep(app, db, owner, source, policy, 0, original.steps().getFirst().endBar());
        ObjectNode valid = original.frozenPolicy().deepCopy();
        assertInvalidStep(app, db, owner, source, valid, 1, original.steps().getFirst().endBar());
        assertInvalidStep(app, db, owner, source, valid, 0, original.steps().getFirst().endBar() + 1);
        assertInvalidStep(app, db, owner, source, valid, 0, 4321);
    }

    private static void assertInvalidStep(ConfigurableApplicationContext app, TenantIsolationTestDatabase db,
            TenantIdentity owner, UUID source, ObjectNode policy, int start, int end) {
        UUID invalid = UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> db.as(owner, () -> {
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
                jdbc.update("INSERT INTO experiment_validations(validation_id,owner_user_id,source_experiment_id,frozen_policy,status) VALUES(?,?,?,?::jsonb,'RUNNING')",
                        invalid, owner.userId(), source, policy.toString());
                jdbc.update("INSERT INTO experiment_validation_steps(validation_id,step_number,fold_number,role,start_bar,end_bar,unavailable_reason) VALUES(?,0,1,'IN_SAMPLE',?,?,'CONTROLLED_INVALID_POLICY')",
                        invalid, start, end);
            });
            return true;
        }));
        assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM experiment_validations WHERE validation_id=?", Integer.class, invalid));
    }

    /** 新Dataset只改变第一训练窗后的OHLCV；正式训练Run及选择不能读取未来段，即使源实验已看完整输入。 */
    private static void verifyFutureTrainingIsolation(ConfigurableApplicationContext app, TenantIsolationTestDatabase db,
            TenantIdentity owner, ExperimentValidationService.View original) {
        int trainingEnd = original.steps().getFirst().endBar();
        var template = db.as(owner, () -> LongHistoricalResearchBrowserFixtureMain.seedTemplate(app,
                "BTC-USDT", 4320, "SPOT_SMA_TARGET_V1", "future-perturbed", trainingEnd));
        var experiments = app.getBean(StrategyExperimentService.class);
        var validations = app.getBean(ExperimentValidationService.class);
        var request = new StrategyExperimentService.Request(template.configId(), null,
                json("{\"window\":[4,20],\"investedExposure\":[\"0.5\",\"1\"]}"));
        var source = db.as(owner, () -> experiments.create(UUID.randomUUID(), request, owner.userId()));
        for (int i = 0; i < 4; i++) advance(app, source.experimentId());
        assertEquals("SUCCEEDED", db.as(owner, () -> experiments.get(source.experimentId(), owner.userId())).status());
        var variant = db.as(owner, () -> validations.create(source.experimentId(), UUID.randomUUID(), owner.userId()));
        UUID training = variant.steps().getFirst().experimentId();
        for (int i = 0; i < 4; i++) advance(app, training);
        advance(app, source.experimentId());
        var measured = db.as(owner, () -> validations.get(variant.validationId(), owner.userId()));
        assertNotEquals(original.frozenPolicy().path("datasetSha256"), measured.frozenPolicy().path("datasetSha256"));
        var oldRanks = original.steps().getFirst().selection().path("rankings");
        var newRanks = measured.steps().getFirst().selection().path("rankings");
        assertEquals(4, newRanks.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(oldRanks.get(i).path("ordinal"), newRanks.get(i).path("ordinal"));
            assertEquals(0, new BigDecimal(oldRanks.get(i).path("totalReturn").asText())
                    .compareTo(new BigDecimal(newRanks.get(i).path("totalReturn").asText())));
            assertEquals(0, new BigDecimal(oldRanks.get(i).path("maxDrawdown").asText())
                    .compareTo(new BigDecimal(newRanks.get(i).path("maxDrawdown").asText())));
        }
        for (var before : original.results().stream().filter(result -> result.step() == 0).toList()) {
            var after = measured.results().stream().filter(result -> result.step() == 0 && result.ordinal() == before.ordinal()).findFirst().orElseThrow();
            assertEquals(economicFacts(db, before.backtestRunId()), economicFacts(db, after.backtestRunId()));
            assertEquals(before.summary().path("barContentSha256"), after.summary().path("barContentSha256"));
        }
        db.as(owner, () -> validations.cancel(variant.validationId(), owner.userId()));
    }

    /** 在真实权益INSERT切点强杀512MiB JVM，再由另一JVM恢复同一四候选实验；不以Bean重建代替进程恢复。 */
    private static Map<String, Object> interruptAndResumeLongSource(TenantIsolationTestDatabase db, UUID experiment, String run) throws Exception {
        assertTrue(run.matches("brn-[a-f0-9-]{36}"));
        Path directory = evidenceDirectory();
        Path interruptedLog = directory.resolve("interrupted-" + experiment + ".log");
        Path restartedLog = directory.resolve("restarted-" + experiment + ".log");
        db.admin.execute("CREATE FUNCTION long_history_test_interrupt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.backtest_run_id=TG_ARGV[0] THEN PERFORM pg_advisory_xact_lock(748274); END IF; RETURN NEW; END; $$");
        db.admin.execute("CREATE TRIGGER zz_long_history_test_interrupt BEFORE INSERT ON sim_pnl_snapshots FOR EACH ROW EXECUTE FUNCTION long_history_test_interrupt('" + run + "')");
        long interruptedPid;
        try (var lock = db.database.source().getConnection(); var statement = lock.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(748274)");
            var builder = StrategyExperimentPostgresIntegrationTest.child(db, experiment);
            builder.command().add(1, "-Xmx512m");
            var child = builder.redirectErrorStream(true).redirectOutput(interruptedLog.toFile()).start();
            interruptedPid = child.pid();
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
                while (!Boolean.TRUE.equals(db.admin.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=748274 AND NOT granted)", Boolean.class))) {
                    assertTrue(child.isAlive(), Files.readString(interruptedLog));
                    assertTrue(System.nanoTime() < deadline, "long input child never reached actual persistence barrier");
                    Thread.sleep(50);
                }
                child.destroyForcibly(); assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            } finally {
                if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
            }
            assertEquals("CREATED", db.admin.queryForObject("SELECT status FROM backtest_runs WHERE backtest_run_id=?", String.class, run));
            assertEquals("RUNNING", db.admin.queryForObject("SELECT status FROM strategy_experiment_attempts WHERE backtest_run_id=?", String.class, run));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots WHERE backtest_run_id=?", Integer.class, run));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM sim_trades WHERE backtest_run_id=?", Integer.class, run));
        } finally {
            db.admin.execute("DROP TRIGGER zz_long_history_test_interrupt ON sim_pnl_snapshots");
            db.admin.execute("DROP FUNCTION long_history_test_interrupt()");
        }
        var builder = StrategyExperimentPostgresIntegrationTest.child(db, experiment);
        builder.command().add(1, "-Xmx512m");
        var child = builder.redirectErrorStream(true).redirectOutput(restartedLog.toFile()).start();
        long restartedPid = child.pid();
        try {
            assertTrue(child.waitFor(150, TimeUnit.SECONDS), "long source recovery exceeded bounded four-run budget");
            assertEquals(0, child.exitValue(), Files.readString(restartedLog));
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
        assertEquals(4, db.admin.queryForObject("SELECT count(*) FROM strategy_experiment_trials t JOIN strategy_experiment_attempts a USING(trial_id) WHERE t.experiment_id=? AND a.status='SUCCEEDED'", Integer.class, experiment));
        assertEquals(4320, db.admin.queryForObject("SELECT count(*) FROM sim_pnl_snapshots WHERE backtest_run_id=?", Integer.class, run));
        return Map.of("interruptedPid", interruptedPid, "restartedPid", restartedPid,
                "originalRun", run, "originalAttemptRecovered", true, "heapLimitBytes", 512L * 1024 * 1024,
                "rollbackEquityRows", 0, "rollbackTradeRows", 0);
    }

    /** 只在任务自有数据库用管理员注入物理损坏；运行角色不能执行这些更新，原块在finally完整恢复。 */
    private static void verifyCorruptedAndMissingChunk(TenantIsolationTestDatabase db, TenantIdentity owner,
            MarketdataDatasetService datasets, JsonNode snapshot) {
        UUID version = UUID.fromString(snapshot.path("frozenInput").path("datasetVersionId").asText());
        String original = db.admin.queryForObject("SELECT row_to_json(c)::text FROM research_dataset_chunks c WHERE dataset_version_id=? AND ordinal=0", String.class, version);
        db.admin.execute("ALTER TABLE research_dataset_chunks DISABLE TRIGGER USER");
        try {
            db.admin.update("UPDATE research_dataset_chunks SET content_sha256=? WHERE dataset_version_id=? AND ordinal=0", "0".repeat(64), version);
            assertThrows(RuntimeException.class, () -> db.as(owner, () -> datasets.readFrozenInput(snapshot)));
            db.admin.update("DELETE FROM research_dataset_chunks WHERE dataset_version_id=? AND ordinal=0", version);
            assertThrows(RuntimeException.class, () -> db.as(owner, () -> datasets.readFrozenInput(snapshot)));
        } finally {
            db.admin.update("DELETE FROM research_dataset_chunks WHERE dataset_version_id=? AND ordinal=0", version);
            db.admin.update("INSERT INTO research_dataset_chunks SELECT * FROM jsonb_populate_record(NULL::research_dataset_chunks,?::jsonb)", original);
            db.admin.execute("ALTER TABLE research_dataset_chunks ENABLE TRIGGER USER");
        }
        assertEquals(4320, db.as(owner, () -> datasets.readFrozenInput(snapshot)).size());
    }

    private static JsonNode json(String raw) {
        try { return LongHistoricalResearchBrowserFixtureMain.JSON.readTree(raw); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("synthetic test JSON invalid", failure); }
    }

    private static String economicFacts(TenantIsolationTestDatabase db, String run) {
        return db.admin.queryForObject("""
                SELECT jsonb_build_object(
                  'trades',(SELECT coalesce(jsonb_agg(to_jsonb(t)-ARRAY['sim_trade_id','sim_order_id','backtest_run_id','created_at','updated_at'] ORDER BY execution_sequence),'[]'::jsonb) FROM sim_trades t WHERE backtest_run_id=?),
                  'equity',(SELECT jsonb_agg(to_jsonb(p)-ARRAY['sim_pnl_snapshot_id','backtest_run_id','created_at'] ORDER BY valuation_sequence) FROM sim_pnl_snapshots p WHERE backtest_run_id=?))::text
                """, String.class, run, run);
    }

    /** 独立逐成交重建现金，成交价已包含滑点，不能再减一次slippage_amount。 */
    private static void assertLedgerOracle(TenantIsolationTestDatabase db, String run, BacktestEvaluationReport report) {
        var rows = db.admin.queryForList("SELECT side,quantity,trade_price,fee_amount FROM sim_trades WHERE backtest_run_id=? ORDER BY execution_sequence", run);
        BigDecimal cash = new BigDecimal("10000"), quantity = BigDecimal.ZERO, fees = BigDecimal.ZERO;
        for (var row : rows) {
            BigDecimal amount = ((BigDecimal) row.get("quantity")).multiply((BigDecimal) row.get("trade_price"));
            BigDecimal fee = (BigDecimal) row.get("fee_amount");
            cash = "BUY".equals(row.get("side")) ? cash.subtract(amount).subtract(fee) : cash.add(amount).subtract(fee);
            quantity = "BUY".equals(row.get("side")) ? quantity.add((BigDecimal) row.get("quantity")) : quantity.subtract((BigDecimal) row.get("quantity"));
            fees = fees.add(fee);
        }
        assertTrue(cash.subtract(report.finalCashBalance()).abs().compareTo(new BigDecimal("0.00000001")) < 0);
        assertEquals(0, fees.compareTo(report.totalFee()));
        assertTrue(quantity.signum() >= 0);
    }

    private static void resetPeakHeap() {
        ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.isValid() && pool.getType() == MemoryType.HEAP)
                .forEach(java.lang.management.MemoryPoolMXBean::resetPeakUsage);
    }

    private static long peakHeap() {
        return ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.isValid() && pool.getType() == MemoryType.HEAP)
                .mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
    }

    private static void evidence(String name, Map<String, ?> value) throws Exception {
        Path directory = evidenceDirectory();
        Files.writeString(directory.resolve(name + ".json"), LongHistoricalResearchBrowserFixtureMain.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    private static Path evidenceDirectory() throws Exception {
        Path directory = Path.of(System.getProperty("nq.long-history.evidence", "../artifacts/nq-v103-long-history")).toAbsolutePath();
        Files.createDirectories(directory); return directory;
    }
}
