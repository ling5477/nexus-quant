package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.infra.eval.jdbc.JdbcBacktestEvaluationQueryPort;
import com.guidinglight.nexusquant.research.infra.eval.jdbc.JdbcBacktestEvaluationReportRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用真实仓储与PG验证报告追加、历史引用和最新投影；合成前提不代表实际回测完成。 */
@EnabledIfSystemProperty(named = "nq.evaluation.history.pg.required", matches = "true")
class BacktestEvaluationHistoryPostgresIntegrationTest {
  @Test
  void freshAndUpgradePreserveFrozenIdentitiesAndBoundLatestReads() throws Exception {
    String maintenance = System.getenv("TASK_PAPER_CREATION_MAINTENANCE_URL");
    String user = System.getenv("TASK_PAPER_CREATION_DB_USER");
    String password = System.getenv("TASK_PAPER_CREATION_DB_PASSWORD");
    assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
    assertNotNull(user);
    assertNotNull(password);
    Path evidence = Path.of(System.getProperty("nq.evaluation.history.pg.evidence"));
    Files.createDirectories(evidence);
    var control = new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password));
    control.setQueryTimeout(10);
    for (boolean upgrade : new boolean[] {true, false}) {
      String name = "evaluation_history_" + UUID.randomUUID().toString().replace("-", "");
      String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + name;
      control.execute("CREATE DATABASE " + name);
      boolean passed = false;
      try {
        var source = new DriverManagerDataSource(url, user, password);
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        migration(url, user, password, upgrade ? "31" : "32").migrate();
        seed(jdbc);
        String prior = digest(jdbc, "backtest_eval_reports", "eval_report_id", "ev-history-old");
        String publication =
            digest(jdbc, "backtest_publish_records", "publish_record_id", "pub-history");
        if (upgrade) migration(url, user, password, "32").migrate();
        migration(url, user, password, "32").validate();
        assertEquals(
            prior, digest(jdbc, "backtest_eval_reports", "eval_report_id", "ev-history-old"));
        var transactions = new DataSourceTransactionManager(source);
        var coordinator = new JdbcAdmissionMutationCoordinator(jdbc, transactions, 256);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var repository = new JdbcBacktestEvaluationReportRepository(jdbc, mapper, coordinator);
        var projection = new JdbcBacktestEvaluationQueryPort(jdbc);
        var old = repository.findByEvalReportId("ev-history-old").orElseThrow();
        var failed = variant(mapper, old, "ev-history-failed", "FAILED", "2026-10-01T01:00:00Z");
        repository.upsert(failed);
        assertEquals(
            "ev-history-failed",
            repository.findByBacktestRunId("run-history").orElseThrow().evalReportId());
        assertEquals(
            "ev-history-failed",
            projection.findByBacktestRunId("run-history").orElseThrow().evalReportId());
        // 两个明确评估并发追加，不能互相覆盖；并列时间用报告身份确定性排序。
        try (var pool = Executors.newFixedThreadPool(2)) {
          var first =
              pool.submit(
                  () ->
                      repository.upsert(
                          variant(
                              mapper, old, "ev-history-a", "SUCCEEDED", "2026-10-01T02:00:00Z")));
          var second =
              pool.submit(
                  () ->
                      repository.upsert(
                          variant(
                              mapper, old, "ev-history-z", "SUCCEEDED", "2026-10-01T02:00:00Z")));
          first.get(15, TimeUnit.SECONDS);
          second.get(15, TimeUnit.SECONDS);
        }
        assertEquals(
            4, jdbc.queryForObject("SELECT count(*) FROM backtest_eval_reports", Integer.class));
        assertEquals(
            "ev-history-z",
            repository.findByBacktestRunId("run-history").orElseThrow().evalReportId());
        assertEquals(
            "ev-history-z",
            projection
                .findByBacktestRunIds(List.of("run-history", "run-history"))
                .get("run-history")
                .evalReportId());
        assertEquals(1, projection.findByBacktestRunIds(List.of("run-history")).size());
        assertEquals(
            prior, digest(jdbc, "backtest_eval_reports", "eval_report_id", "ev-history-old"));
        assertEquals(
            publication,
            digest(jdbc, "backtest_publish_records", "publish_record_id", "pub-history"));
        assertEquals(
            "ev-history-old",
            jdbc.queryForObject(
                "SELECT e.eval_report_id FROM backtest_publish_records p JOIN backtest_eval_reports"
                    + " e ON e.eval_report_id=p.eval_report_id WHERE"
                    + " p.publish_record_id='pub-history'",
                String.class));
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname='fk_shadow_runs_evaluation' AND"
                    + " contype='f'",
                Integer.class));
        reject(() -> repository.upsert(old), "23505");
        reject(
            () ->
                jdbc.update(
                    "INSERT INTO"
                        + " backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status)"
                        + " VALUES('invalid-status','run-history','UNKNOWN')"),
            "23514");
        reject(
            () ->
                jdbc.update(
                    "INSERT INTO"
                        + " backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status)"
                        + " VALUES('invalid-run','missing-run','FAILED')"),
            "23503");
        assertThrows(
            IllegalStateException.class,
            () ->
                new TransactionTemplate(transactions)
                    .execute(
                        status -> {
                          repository.upsert(
                              variant(
                                  mapper,
                                  old,
                                  "ev-history-rollback",
                                  "SUCCEEDED",
                                  "2026-10-01T03:00:00Z"));
                          throw new IllegalStateException("isolated rollback proof");
                        }));
        assertTrue(repository.findByEvalReportId("ev-history-rollback").isEmpty());
        assertEquals(
            4, jdbc.queryForObject("SELECT count(*) FROM backtest_eval_reports", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class));
        Files.writeString(
            evidence.resolve((upgrade ? "upgrade" : "fresh") + ".json"),
            "{\"status\":\"PASS\",\"version\":32,\"priorReportAndPublicationUnchanged\":true,\"parallelReports\":2,\"retainedReports\":4,\"latestProjectionRows\":1,\"rollbackVerified\":true,\"ledgerEntries\":0}");
        passed = true;
      } finally {
        // 只删除本测试新建且全部通过的独立库；失败库保留，不清理其他候选。
        if (passed) {
          assertTrue(name.matches("evaluation_history_[a-f0-9]{32}"));
          control.execute("DROP DATABASE " + name);
        } else Files.writeString(evidence.resolve(name + "-retained.txt"), name);
      }
    }
  }

  private static BacktestEvaluationReport variant(
      ObjectMapper mapper, BacktestEvaluationReport old, String id, String state, String time) {
    try {
      ObjectNode json = mapper.valueToTree(old);
      json.put("evalReportId", id);
      json.put("evaluationStatus", state);
      json.put("evaluatedAt", time);
      return mapper.treeToValue(json, BacktestEvaluationReport.class);
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void seed(JdbcTemplate jdbc) {
    Long account =
        jdbc.queryForObject(
            "INSERT INTO accounts(account_code,venue) VALUES('history-fixture','OKX') RETURNING"
                + " account_id",
            Long.class);
    jdbc.update(
        "INSERT INTO"
            + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled)"
            + " VALUES('source-history','history-fixture','Synthetic history"
            + " contract','SPOT_SMA_TARGET_V1','OKX',?,'SIM',false)",
        account);
    jdbc.execute(
        "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)"
            + " VALUES('research-history','source-history','Synthetic history contract','{}')");
    jdbc.execute(
        "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name)"
            + " VALUES('config-history','research-history','Synthetic history contract')");
    jdbc.execute(
        "INSERT INTO"
            + " backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at)"
            + " VALUES('run-history','config-history','research-history','source-history','SUCCEEDED','{}','{}','2026-10-01T00:00:00Z')");
    jdbc.execute(
        "INSERT INTO"
            + " backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status,net_pnl,evaluated_at,report_json)"
            + " VALUES('ev-history-old','run-history','SUCCEEDED',0.123456789123456789,'2026-10-01T00:00:00Z','{\"frozen\":true}')");
    jdbc.execute(
        "INSERT INTO"
            + " backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,eval_report_id,publish_status,publish_name)"
            + " VALUES('pub-history','run-history','research-history','config-history','source-history','ev-history-old','SUCCEEDED','Synthetic"
            + " history reference')");
  }

  private static String digest(JdbcTemplate jdbc, String table, String key, String id) {
    return jdbc.queryForObject(
        "SELECT md5(to_jsonb(t)::text) FROM " + table + " t WHERE " + key + "=?", String.class, id);
  }

  private static void reject(Runnable action, String state) {
    var failure = assertThrows(DataAccessException.class, action::run);
    assertTrue(
        failure.getMostSpecificCause() instanceof SQLException sql
            && state.equals(sql.getSQLState()));
  }

  private static Flyway migration(String url, String user, String password, String target) {
    return Flyway.configure()
        .dataSource(url, user, password)
        .locations("classpath:db/migration")
        .baselineOnMigrate(false)
        .cleanDisabled(true)
        .target(target)
        .load();
  }
}
