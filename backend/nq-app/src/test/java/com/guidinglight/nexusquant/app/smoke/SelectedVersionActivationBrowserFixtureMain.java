package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 浏览器只连接专属PG16/真实Servlet；SMA和通道实验通过原worker完成，激活与发布留给人工UI。 */
public final class SelectedVersionActivationBrowserFixtureMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("ready, stop and evidence paths required");
        Path ready = Path.of(args[0]).toAbsolutePath(), stop = Path.of(args[1]).toAbsolutePath(), evidence = Path.of(args[2]).toAbsolutePath();
        if (Files.exists(ready) || Files.exists(stop) || Files.exists(evidence)
                || !ready.getParent().equals(stop.getParent()) || !ready.getParent().equals(evidence.getParent()))
            throw new IllegalArgumentException("fresh task-owned fixture files required");
        try (var db = TenantIsolationTestDatabase.create(null, System.getenv("TASK_EXPERIMENT_PG_URL"), "postgres", System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var templates = db.as(owner, () -> WalkForwardPostgresIntegrationTest.seed(app));
                var prepared = new LinkedHashMap<String, SelectedVersionActivationPostgresIntegrationTest.Prepared>();
                var immutable = new LinkedHashMap<String, String>();
                var snapshots = new LinkedHashMap<String, String>();
                for (String type : List.of("SPOT_SMA_TARGET_V1", "SPOT_CHANNEL_BREAKOUT_V1")) {
                    var selected = SelectedVersionActivationPostgresIntegrationTest.prepare(app, db, owner, templates.get(type), type);
                    prepared.put(type, selected);
                    immutable.put(type, db.admin.queryForObject("SELECT (to_jsonb(v)-ARRAY['status','updated_at'])::text FROM strategy_versions v WHERE strategy_version_id=?", String.class, selected.strategyVersionId()));
                    snapshots.put(type, db.admin.queryForObject("SELECT strategy_version_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=?", String.class, selected.runId()));
                }
                var json = WalkForwardPostgresIntegrationTest.JSON;
                Files.writeString(ready, json.writeValueAsString(Map.of("port", app.getWebServer().getPort(), "selected", prepared,
                        "pid", ProcessHandle.current().pid())), StandardOpenOption.CREATE_NEW);
                long deadline = System.nanoTime() + Duration.ofMinutes(30).toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(200);
                var results = new LinkedHashMap<String, Object>();
                for (var item : prepared.entrySet()) {
                    var selected = item.getValue();
                    assertEquals("ACTIVE", db.admin.queryForObject("SELECT status FROM strategy_versions WHERE strategy_version_id=?", String.class, selected.strategyVersionId()));
                    assertEquals(immutable.get(item.getKey()), db.admin.queryForObject("SELECT (to_jsonb(v)-ARRAY['status','updated_at'])::text FROM strategy_versions v WHERE strategy_version_id=?", String.class, selected.strategyVersionId()));
                    assertEquals(snapshots.get(item.getKey()), db.admin.queryForObject("SELECT strategy_version_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=?", String.class, selected.runId()));
                    assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM audit_logs WHERE action='ACTIVATE_SELECTED_VERSION' AND detail_json->>'strategyVersionId'=?", Integer.class, selected.strategyVersionId()));
                    assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM backtest_publish_records WHERE backtest_run_id=? AND strategy_version_id=? AND publish_status='SUCCEEDED'", Integer.class, selected.runId(), selected.strategyVersionId()));
                    results.put(item.getKey(), Map.of("identity", selected, "activationAuditCount", 1, "publishSucceeded", true,
                            "economicVersionIdentity", "MATCH", "historicalRunSnapshot", "MATCH"));
                }
                for (String table : List.of("orders", "trades", "ledger_entries", "paper_trading_runs", "continuous_sim_runs"))
                    assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM " + table, Integer.class));
                Files.writeString(evidence, json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "result", "PASS", "strategies", results, "canonicalTradingMutation", "NONE", "automaticSimCreation", "NONE")), StandardOpenOption.CREATE_NEW);
            }
        }
    }
}
