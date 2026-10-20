package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.app.marketdata.InstrumentCatalogSyncRequestService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncResult;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationFailedException;
import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

/** 一个独立PG证明库验证升级、事务与并发；受控服务输入不冒充正式浏览器或外部交易所资格。 */
@EnabledIfSystemProperty(named = "nq.original.request.pg.required", matches = "true")
@Timeout(120)
class OriginalRequestPostgresIntegrationTest {
  @Test
  void durableIdentityAtomicityConcurrencyFailureAndOriginalSnapshot() throws Exception {
    String maintenance = System.getenv("TASK_REQUEST_MAINTENANCE_URL"),
        user = System.getenv("TASK_REQUEST_DB_USER"),
        password = System.getenv("TASK_REQUEST_DB_PASSWORD");
    assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
    String database = "nq_request_proof_" + UUID.randomUUID().toString().replace("-", "");
    new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password))
        .execute("CREATE DATABASE " + database);
    String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + database;
    Flyway.configure().dataSource(url, user, password).target("28").load().migrate();
    var ds = new DriverManagerDataSource(url, user, password);
    var jdbc = new JdbcTemplate(ds);
    jdbc.setQueryTimeout(10);
    jdbc.update(
        "INSERT INTO users(id,username,password_hash,enabled,must_change_password)"
            + " VALUES(17,'request_owner','controlled',true,false),(18,'request_other','controlled',true,false)");
    Long account =
        jdbc.queryForObject(
            "INSERT INTO accounts(account_code,venue) VALUES('request_fixture','PAPER') RETURNING"
                + " account_id",
            Long.class);
    jdbc.update(
        "INSERT INTO"
            + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id)"
            + " VALUES('request-strategy','request-strategy','controlled','TEST','OKX',?)",
        account);
    jdbc.update(
        "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)"
            + " VALUES('request-research','request-strategy','controlled','{}')");
    jdbc.update(
        "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name)"
            + " VALUES('request-config','request-research','controlled')");
    jdbc.update(
        "INSERT INTO"
            + " backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at,config_snapshot_json)"
            + " VALUES('request-run','request-config','request-research','request-strategy','SUCCEEDED','{}','{}',now(),'{\"sentinel\":17}')");
    String frozen =
        jdbc.queryForObject(
            "SELECT config_snapshot_json::text FROM backtest_runs WHERE"
                + " backtest_run_id='request-run'",
            String.class);
    var migration = Flyway.configure().dataSource(url, user, password).target("29").load();
    migration.migrate();
    migration.validate();
    assertEquals(
        29,
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
    assertEquals(
        2, jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN(17,18)", Integer.class));
    jdbc.execute("CREATE TABLE request_effect_probe(value integer NOT NULL)");
    var manager = new DataSourceTransactionManager(ds);
    var mapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    var sync = mock(InstrumentCatalogSyncService.class);
    var calls = new AtomicInteger();
    doAnswer(
            call -> {
              calls.incrementAndGet();
              jdbc.update("INSERT INTO request_effect_probe VALUES(1)");
              return new InstrumentCatalogSyncResult(
                  List.of("OKX"), 1, 1, 0, Instant.now(), Instant.now());
            })
        .when(sync)
        .sync(any(), any());
    var service = new InstrumentCatalogSyncRequestService(jdbc, mapper, sync, manager, true);
    UUID id = UUID.randomUUID();
    var original = service.submit(id, "okx", 17);
    assertEquals("SUCCEEDED", original.state());
    assertEquals(original, service.submit(id, "OKX", 17));
    assertEquals(1, calls.get());
    assertEquals(
        original,
        new InstrumentCatalogSyncRequestService(jdbc, mapper, sync, manager, false)
            .find(id, 17)
            .orElseThrow());
    assertTrue(service.find(id, 18).isEmpty());
    assertTrue(service.find(UUID.randomUUID(), 17).isEmpty());
    assertThrows(ResponseStatusException.class, () -> service.submit(id, "BINANCE", 17));
    assertThrows(ResponseStatusException.class, () -> service.submit(id, "OKX", 18));
    assertThrows(
        Exception.class,
        () ->
            jdbc.update(
                "UPDATE instrument_catalog_sync_requests SET result_json='{}' WHERE request_id=?",
                id));
    assertThrows(
        Exception.class,
        () -> jdbc.update("DELETE FROM instrument_catalog_sync_requests WHERE request_id=?", id));
    UUID rollback = UUID.randomUUID();
    doAnswer(
            call -> {
              jdbc.update("INSERT INTO request_effect_probe VALUES(99)");
              throw new IllegalStateException("controlled rollback");
            })
        .when(sync)
        .sync(any(), any());
    assertThrows(IllegalStateException.class, () -> service.submit(rollback, "OKX", 17));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM request_effect_probe WHERE value=99", Integer.class));
    assertEquals("PENDING", service.find(rollback, 17).orElseThrow().state());
    int prior = calls.get();
    assertEquals("PENDING", service.submit(rollback, "OKX", 17).state());
    assertEquals(prior, calls.get());
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    UUID concurrent = UUID.randomUUID();
    doAnswer(
            call -> {
              calls.incrementAndGet();
              entered.countDown();
              assertTrue(release.await(5, TimeUnit.SECONDS));
              return new InstrumentCatalogSyncResult(
                  List.of("OKX"), 0, 0, 0, Instant.now(), Instant.now());
            })
        .when(sync)
        .sync(any(), any());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> service.submit(concurrent, "OKX", 17));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      var duplicate = pool.submit(() -> service.submit(concurrent, "OKX", 17));
      assertEquals("PENDING", duplicate.get(5, TimeUnit.SECONDS).state());
      release.countDown();
      assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).state());
      assertEquals(prior + 1, calls.get());
    }
    var runs = mock(BacktestRunApiService.class);
    var report = report("eval-original", EvaluationStatus.SUCCEEDED);
    when(runs.evaluate("request-run")).thenReturn(report);
    var evaluations = new BacktestEvaluationRequestService(jdbc, mapper, runs, manager);
    UUID evaluation = UUID.randomUUID();
    var saved = evaluations.submit(evaluation, "request-run", 17);
    assertEquals("eval-original", saved.result().evalReportId());
    assertEquals(new BigDecimal("123456789.123456789123456789"), saved.result().netPnl());
    assertEquals(saved, evaluations.submit(evaluation, "request-run", 17));
    verify(runs, times(1)).evaluate("request-run");
    var newerReport = report("eval-new", EvaluationStatus.SUCCEEDED);
    when(runs.evaluate("request-run")).thenReturn(newerReport);
    assertEquals(
        "eval-new",
        evaluations.submit(UUID.randomUUID(), "request-run", 17).result().evalReportId());
    assertEquals(
        saved,
        new BacktestEvaluationRequestService(jdbc, mapper, runs, manager)
            .find(evaluation, 17)
            .orElseThrow());
    assertTrue(evaluations.find(evaluation, 18).isEmpty());
    var failed = report("eval-failed", EvaluationStatus.FAILED);
    var failureException =
        new BacktestEvaluationFailedException(
            failed, new IllegalStateException("controlled failure"));
    doThrow(failureException).when(runs).evaluate("request-run");
    UUID failure = UUID.randomUUID();
    var failureReceipt = evaluations.submit(failure, "request-run", 17);
    assertEquals("FAILED", failureReceipt.state());
    assertEquals("eval-failed", failureReceipt.result().evalReportId());
    assertEquals(failureReceipt, evaluations.submit(failure, "request-run", 17));
    assertEquals(
        frozen,
        jdbc.queryForObject(
            "SELECT config_snapshot_json::text FROM backtest_runs WHERE"
                + " backtest_run_id='request-run'",
            String.class));
    assertThrows(
        Exception.class,
        () ->
            jdbc.update(
                "UPDATE backtest_evaluation_requests SET backtest_run_id='other' WHERE"
                    + " request_id=?",
                evaluation));
    Path evidence = Path.of(System.getProperty("nq.original.request.pg.evidence"));
    Files.createDirectories(evidence);
    Files.writeString(
        evidence.resolve("postgres-proof.json"),
        mapper.writeValueAsString(
            java.util.Map.of(
                "database",
                database,
                "schema",
                29,
                "checks",
                List.of(
                    "fresh-V28-upgrade-V29-validate",
                    "original owner and target",
                    "immutable terminal receipt",
                    "exact decimal round trip",
                    "rollback leaves PENDING and no effects",
                    "concurrent same identity executes once",
                    "new context reads same receipt",
                    "later evaluation does not replace original receipt",
                    "formal failure carrier retained",
                    "frozen backtest unchanged"),
                "limits",
                "Controlled delegates; actual Servlet and business chain verified separately")));
  }

  private static BacktestEvaluationReport report(String id, EvaluationStatus state) {
    var value = mock(BacktestEvaluationReport.class);
    when(value.evalReportId()).thenReturn(id);
    when(value.backtestRunId()).thenReturn("request-run");
    when(value.evaluationStatus()).thenReturn(state);
    when(value.netPnl()).thenReturn(new BigDecimal("123456789.123456789123456789"));
    return value;
  }
}
