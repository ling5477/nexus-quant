package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.app.research.ExperimentValidationService;
import com.guidinglight.nexusquant.app.research.SelectedStrategyVersionService;
import com.guidinglight.nexusquant.app.research.StrategyExperimentService;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 真实PG16/受限运行角色/Servlet证明选中版本激活，原经济事实和历史Run不变。 */
@EnabledIfSystemProperty(named="nq.tenant.pg.required",matches="true")
class SelectedVersionActivationPostgresIntegrationTest {
    @Test @Timeout(180)
    void failedUnselectedTrainingCandidateDoesNotInvalidateSuccessfulFrozenSelection() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var templates = db.as(owner, () -> WalkForwardPostgresIntegrationTest.seed(app));
                var prepared = prepare(app, db, owner, templates.get("SPOT_SMA_TARGET_V1"), "SPOT_SMA_TARGET_V1", true);
                var result = db.as(owner, () -> app.getBean(ExperimentValidationService.class).get(prepared.validationId(), owner.userId()));
                assertEquals("SUCCEEDED", result.status());
                assertEquals(List.of("FAILED", "SUCCEEDED"), result.results().stream().filter(r -> r.step() == 12).map(ExperimentValidationService.Result::status).toList());
                var before = ResearchSimComparisonPostgresIntegrationTest.economicFacts(db);
                var active = http.ok("POST", "/api/strategy-experiments/validations/" + prepared.validationId() + "/selected-version/activate", http.login("experiment-owner"), null);
                assertEquals("ACTIVE", active.path("status").asText());
                assertEquals(prepared.strategyVersionId(), active.path("strategyVersionId").asText());
                assertEquals(1, auditCount(db, prepared.strategyVersionId()));
                assertEquals(before, ResearchSimComparisonPostgresIntegrationTest.economicFacts(db));
            }
        }
    }

    @Test @Timeout(240)
    void explicitActivationPreservesLineageAuditsAtomicallyAndPublishesBothStrategyTypes() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            db.local("activation-other");
            db.user("activation-viewer", "VIEWER");
            var foreign = db.demo("ACTIVATION_OTHER", "activation-demo");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var templates = db.as(owner, () -> WalkForwardPostgresIntegrationTest.seed(app));
                String token = http.login("experiment-owner");
                String other = http.login("activation-other"), viewer = http.login("activation-viewer");
                var experiments = app.getBean(StrategyExperimentService.class);
                var validations = app.getBean(ExperimentValidationService.class);
                for (String type : List.of("SPOT_SMA_TARGET_V1", "SPOT_CHANNEL_BREAKOUT_V1")) {
                    var prepared = prepare(app, db, owner, templates.get(type), type);
                    String path = "/api/strategy-experiments/validations/" + prepared.validationId() + "/selected-version";
                    String activate = path + "/activate";
                    var selected = http.ok("GET", path, token, null);
                    String version = selected.path("strategyVersionId").asText();
                    assertEquals(prepared.strategyVersionId(), version);
                    assertEquals("DRAFT", selected.path("status").asText());
                    assertTrue(selected.path("eligible").asBoolean());
                    String immutable = immutableVersion(db, version);
                    var before = ResearchSimComparisonPostgresIntegrationTest.economicFacts(db);
                    assertEquals(409, http.send("POST", "/api/backtest-runs/" + prepared.runId() + "/publish", token,
                            Map.of("strategyVersionId", version)).statusCode());
                    assertEquals(401, http.send("POST", activate, null, null).statusCode());
                    assertEquals(403, http.send("POST", activate, viewer, null).statusCode());
                    assertEquals(404, http.send("POST", activate, other, null).statusCode());
                    assertEquals(404, http.send("GET", path, other, null).statusCode());
                    var missing = "/api/strategy-experiments/validations/" + UUID.randomUUID() + "/selected-version/activate";
                    assertEquals(404, http.send("POST", missing, token, null).statusCode());
                    var inaccessible = assertThrows(ResponseStatusException.class, () -> db.as(foreign,
                            () -> app.getBean(SelectedStrategyVersionService.class).activate(prepared.validationId(), foreign.userId())));
                    assertEquals(404, inaccessible.getStatusCode().value());
                    assertEquals(400, http.send("POST", activate, token, Map.of("strategyVersionId", "non-selected-version")).statusCode());

                    // 隔离库制造坏版本反例；不能用保留的checksum掩盖参数、配置或来源漂移。
                    for (String column : List.of("checksum", "param_snapshot_json", "config_snapshot_json", "source_snapshot_json")) {
                        String original = db.admin.queryForObject("SELECT " + column + "::text FROM strategy_versions WHERE strategy_version_id=?", String.class, version);
                        boolean checksum = column.equals("checksum");
                        try {
                            db.admin.update("UPDATE strategy_versions SET " + column + (checksum ? "=?" : "=CAST(? AS jsonb)") + " WHERE strategy_version_id=?",
                                    checksum ? "0".repeat(64) : "{\"unexpected\":true}", version);
                            assertEquals(409, http.send("POST", activate, token, null).statusCode());
                            assertEquals(0, auditCount(db, version));
                        } finally {
                            db.admin.update("UPDATE strategy_versions SET " + column + (checksum ? "=?" : "=CAST(? AS jsonb)") + " WHERE strategy_version_id=?", original, version);
                        }
                    }
                    assertEquals(immutable, immutableVersion(db, version));

                    // 只在隔离测试库撤销审计插入；状态CAS必须随审计失败一起回滚。
                    db.admin.execute("REVOKE INSERT ON audit_logs FROM " + db.runtime);
                    try {
                        assertEquals(500, http.send("POST", activate, token, null).statusCode());
                        assertEquals("DRAFT", db.admin.queryForObject("SELECT status FROM strategy_versions WHERE strategy_version_id=?", String.class, version));
                        assertEquals(0, auditCount(db, version));
                    } finally { db.admin.execute("GRANT INSERT ON audit_logs TO " + db.runtime); }
                    var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
                    try (var pool = Executors.newFixedThreadPool(2)) {
                        var one = pool.submit(() -> {ready.countDown(); start.await(); return http.send("POST", activate, token, null);});
                        var two = pool.submit(() -> {ready.countDown(); start.await(); return http.send("POST", activate, token, null);});
                        assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
                        assertEquals(200, one.get(40, TimeUnit.SECONDS).statusCode());
                        assertEquals(200, two.get(40, TimeUnit.SECONDS).statusCode());
                    }
                    var active = http.ok("GET", path, token, null);
                    assertEquals("ACTIVE", active.path("status").asText());
                    assertFalse(active.path("eligible").asBoolean());
                    assertEquals(version, active.path("strategyVersionId").asText());
                    assertEquals(selected.path("checksum"), active.path("checksum"));
                    assertEquals(immutable, immutableVersion(db, version));
                    assertEquals(active, http.ok("POST", activate, token, null));
                    assertEquals(1, auditCount(db, version));
                    assertEquals(before, ResearchSimComparisonPostgresIntegrationTest.economicFacts(db));
                    JsonNode audit = WalkForwardPostgresIntegrationTest.JSON.readTree(db.admin.queryForObject(
                            "SELECT detail_json::text FROM audit_logs WHERE action='ACTIVATE_SELECTED_VERSION' AND detail_json->>'strategyVersionId'=?", String.class, version));
                    assertEquals(prepared.validationId().toString(), audit.path("validationId").asText());
                    assertEquals(prepared.trialId().toString(), audit.path("selectedTrialId").asText());
                    assertEquals(owner.userId(), audit.path("actorId").asLong());
                    assertEquals("DRAFT", audit.path("previousStatus").asText());
                    assertEquals("ACTIVE", audit.path("newStatus").asText());
                    assertFalse(audit.path("occurredAt").asText().isBlank());
                    assertTrue(db.admin.queryForObject("SELECT length(trace_id)>0 FROM audit_logs WHERE action='ACTIVATE_SELECTED_VERSION' AND detail_json->>'strategyVersionId'=?", Boolean.class, version));
                    var publication = http.ok("POST", "/api/backtest-runs/" + prepared.runId() + "/publish", token,
                            Map.of("strategyVersionId", version));
                    assertEquals("SUCCEEDED", publication.path("publishStatus").asText());
                    assertEquals("DRAFT", db.admin.queryForObject("SELECT strategy_version_snapshot_json->>'status' FROM backtest_runs WHERE backtest_run_id=?", String.class, prepared.runId()));
                    assertEquals(immutable, immutableVersion(db, version));
                    assertEquals(400, http.send("POST", activate, token, Map.of("status", "DRAFT")).statusCode());
                    assertEquals("ACTIVE", http.ok("GET", path, token, null).path("status").asText());

                    // 原有历史状态仅作拒绝反例，正向路径始终经过真实激活服务。
                    db.admin.update("UPDATE strategy_versions SET status='ARCHIVED' WHERE strategy_version_id=?", version);
                    assertEquals(409, http.send("POST", activate, token, null).statusCode());
                    assertEquals(1, auditCount(db, version));
                    var pending = db.as(owner, () -> validations.create(prepared.sourceId(), UUID.randomUUID(), owner.userId()));
                    String pendingPath = "/api/strategy-experiments/validations/" + pending.validationId() + "/selected-version/activate";
                    for (String status : List.of("RUNNING", "FAILED", "CANCELLED", "OUTCOME_UNKNOWN", "SUCCEEDED")) {
                        db.admin.update("UPDATE experiment_validations SET status=? WHERE validation_id=?", status, pending.validationId());
                        assertEquals(409, http.send("POST", pendingPath, token, null).statusCode());
                    }
                }
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM orders", Integer.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM trades", Integer.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM paper_trading_runs", Integer.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM continuous_sim_runs", Integer.class));
                assertTrue(db.admin.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relrowsecurity AND c.relforcerowsecurity", Integer.class) >= 104);
                assertFalse(db.admin.queryForObject("SELECT rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb FROM pg_roles WHERE rolname=?", Boolean.class, db.runtime));
                assertEquals(0, db.jdbc.queryForObject("SELECT count(*) FROM strategy_versions", Integer.class));
                db.migration(null).validate();
            }
        }
    }

    static Prepared prepare(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db,
            TenantIdentity owner, String template, String type) throws Exception {
        return prepare(app, db, owner, template, type, false);
    }

    private static Prepared prepare(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db,
            TenantIdentity owner, String template, String type, boolean failTrainingCandidate) throws Exception {
        var json = WalkForwardPostgresIntegrationTest.JSON;
        var experiments = app.getBean(StrategyExperimentService.class);
        JsonNode candidates = json.readTree(type.contains("SMA") ?
                "[{\"window\":4,\"investedExposure\":\"1\"},{\"window\":20,\"investedExposure\":\"1\"}]" :
                "[{\"entryLookbackBars\":10,\"exitLookbackBars\":4,\"investedExposure\":\"1\"},{\"entryLookbackBars\":20,\"exitLookbackBars\":4,\"investedExposure\":\"1\"}]");
        var source = db.as(owner, () -> experiments.create(UUID.randomUUID(), new StrategyExperimentService.Request(template, candidates, null), owner.userId()));
        WalkForwardPostgresIntegrationTest.advance(app, source.experimentId());
        WalkForwardPostgresIntegrationTest.advance(app, source.experimentId());
        var validations = app.getBean(ExperimentValidationService.class);
        var validation = db.as(owner, () -> validations.create(source.experimentId(), UUID.randomUUID(), owner.userId()));
        if (failTrainingCandidate) {
            ExperimentValidationService.View atFinal = null;
            for (int attempt = 0; attempt < 100; attempt++) {
                var current = db.as(owner, () -> validations.get(validation.validationId(), owner.userId()));
                if (current.steps().getLast().number() == 12) { atFinal = current; break; }
                for (var step : current.steps()) if (step.experimentId() != null) WalkForwardPostgresIntegrationTest.advance(app, step.experimentId());
                WalkForwardPostgresIntegrationTest.advance(app, source.experimentId());
            }
            assertTrue(atFinal != null, "final training was not reached within bounded work budget");
            String failed = atFinal.results().stream().filter(r -> r.step() == 12).findFirst().orElseThrow().backtestRunId();
            db.as(owner, () -> {
                new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                    var jdbc = app.getBean(JdbcTemplate.class);
                    String attempt = jdbc.queryForObject("SELECT attempt_id::text FROM strategy_experiment_attempts WHERE backtest_run_id=?", String.class, failed);
                    jdbc.queryForObject("SELECT set_config('nq.experiment_attempt',?,true)", String.class, attempt);
                    var persistence = app.getBean(BacktestExecutionPersistenceService.class);
                    persistence.markPreparing(failed, Instant.now());
                    persistence.markFailed(failed, Instant.now(), Instant.now(), "TEST_TRAINING_FAILURE", "隔离库中一个未选中训练候选失败", "{}");
                });
                return true;
            });
        }
        WalkForwardPostgresIntegrationTest.drain(app, db, owner, validation.validationId(), source.experimentId());
        var completed = db.as(owner, () -> validations.get(validation.validationId(), owner.userId()));
        assertEquals("SUCCEEDED", completed.status());
        var selected = completed.steps().stream().filter(step -> step.number() == 12).findFirst().orElseThrow().selection().path("selected");
        var trial = db.as(owner, () -> experiments.get(source.experimentId(), owner.userId())).trials().stream()
                .filter(t -> t.strategyVersionId().equals(selected.path("versionId").asText())).findFirst().orElseThrow();
        return new Prepared(source.experimentId(), validation.validationId(), trial.trialId(), trial.strategyVersionId(), trial.backtestConfigId(), trial.backtestRunId());
    }

    private static String immutableVersion(TenantIsolationTestDatabase db, String version) {
        return db.admin.queryForObject("SELECT (to_jsonb(v)-ARRAY['status','updated_at'])::text FROM strategy_versions v WHERE strategy_version_id=?", String.class, version);
    }

    private static int auditCount(TenantIsolationTestDatabase db, String version) {
        return db.admin.queryForObject("SELECT count(*) FROM audit_logs WHERE action='ACTIVATE_SELECTED_VERSION' AND detail_json->>'strategyVersionId'=?", Integer.class, version);
    }

    record Prepared(UUID sourceId, UUID validationId, UUID trialId, String strategyVersionId, String configId, String runId) {}
}
