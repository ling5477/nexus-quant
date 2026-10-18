package com.guidinglight.nexusquant.app.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.AccountPerformanceService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.scheduler.paper.PaperEquitySnapshotService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.server.ResponseStatusException;

/** 真实PG16、JWT与正式SIM注资服务证明派生统计；测试投影/行情夹具不冒充成交经济证明。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
    classes = NexusQuantApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "nq.strategy-sim.enabled=true",
      "nq.instrument.catalog-sync.enabled=false",
      "nq.okx.recovery.enabled=false",
      "nq.okx.ws.enabled=false",
      "nq.binance.ws.enabled=false",
      "nq.env-safety.no-outbound=true",
      "nq.account.performance.sampling-enabled=false",
      "nq.okx.adapter.stub-on-bootstrap-failure=false"
    })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
    listeners = StrategySimAnalysisPostgresIntegrationTest.Cleanup.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class StrategySimAnalysisPostgresIntegrationTest {
  private static JdbcTemplate control;
  private static String database;
  private static final String PASSWORD = "synthetic-performance-fixture-password";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthSeedService seeds;
  @Autowired PasswordEncoder passwords;
  @Autowired ExchangeAccountCommandService accounts;
  @Autowired AccountPerformanceService performance;
  @Autowired SimCashFundingService funding;
  @Autowired TradeLedgerPort postings;
  @Autowired StrategyRunRepository strategyRuns;
  private long owner;
  private String ownerName;
  private String otherName;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    String url = System.getProperty("nq.postgres.smoke.url", ""),
        user =
            System.getenv()
                .getOrDefault(
                    "TASK_NATIVE_BACKTEST_DB_USER",
                    System.getProperty("nq.postgres.smoke.user", "")),
        password =
            System.getenv()
                .getOrDefault(
                    "TASK_NATIVE_BACKTEST_DB_PASSWORD",
                    System.getProperty("nq.postgres.smoke.password", ""));
    assertTrue(url.startsWith("jdbc:postgresql://") && !user.isBlank() && !password.isBlank());
    URI uri = URI.create(url.substring("jdbc:".length()));
    assertTrue(
        Set.of("127.0.0.1", "localhost").contains(uri.getHost())
            && uri.getRawQuery() == null
            && uri.getFragment() == null
            && uri.getPath().matches("/[A-Za-z0-9_]+"));
    control =
        new JdbcTemplate(
            new DriverManagerDataSource(
                url + "?connectTimeout=5&socketTimeout=30", user, password));
    assertEquals(
        16,
        control.queryForObject(
            "SELECT current_setting('server_version_num')::integer/10000", Integer.class));
    database = "sim_analysis_" + UUID.randomUUID().toString().replace("-", "");
    control.execute("CREATE DATABASE \"" + database + "\"");
    registry.add(
        "spring.datasource.url", () -> url.substring(0, url.lastIndexOf('/') + 1) + database);
    registry.add("spring.datasource.username", () -> user);
    registry.add("spring.datasource.password", () -> password);
  }

  @BeforeEach
  void identities() {
    ownerName = "analysis-owner-" + UUID.randomUUID();
    otherName = "analysis-other-" + UUID.randomUUID();
    seeds.seedUsers(
        List.of(
            new SeedUserCommand(ownerName, passwords.encode(PASSWORD), List.of("VIEWER"), true),
            new SeedUserCommand(otherName, passwords.encode(PASSWORD), List.of("VIEWER"), true)));
    owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, ownerName);
  }

  private ExchangeAccountSummary account() {
    return accounts.create(
        owner,
        new ExchangeAccountCreateCommand("OKX", "SIM", "measure-" + UUID.randomUUID(), null));
  }

  @Autowired StrategySimAnalysisQuery analysis;
  @Autowired PaperEquitySnapshotService equitySnapshots;

  @Test
  void performanceReconstructsCanonicalRebatesWithExactScopeAndNoWriteSideEffects()
      throws Exception {
    var own = account();
    String key = UUID.randomUUID().toString(),
        strategy = "ps-" + key,
        version = "pv-" + key,
        research = "pr-" + key,
        config = "pc-" + key,
        backtest = "pb-" + key,
        published = "pp-" + key,
        paper = "pi-" + key,
        simStrategy = "pt-" + key;
    // 关联和价格是隔离PG夹具；注资与双边记账走正式服务，不宣称此处执行了Risk或正式撮合。
    jdbc.update(
        "INSERT INTO"
            + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)"
            + " VALUES(?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}',1)",
        strategy,
        strategy,
        strategy,
        own.legacyAccountId());
    jdbc.update(
        "INSERT INTO"
            + " strategy_versions(strategy_version_id,strategy_code,version,version_name,status,param_snapshot_json,config_snapshot_json,source_snapshot_json,checksum)"
            + " VALUES(?,?,1,'statistics','ACTIVE','{}','{}','{}',?)",
        version,
        strategy,
        "a".repeat(64));
    jdbc.update(
        "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)"
            + " VALUES(?,?,?,'{}')",
        research,
        strategy,
        research);
    jdbc.update(
        "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES(?,?,?)",
        config,
        research,
        config);
    jdbc.update(
        "INSERT INTO"
            + " backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at)"
            + " VALUES(?,?,?,?,'CREATED','{}','{}',now())",
        backtest,
        config,
        research,
        strategy);
    jdbc.update(
        "INSERT INTO"
            + " backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name,strategy_version_id)"
            + " VALUES(?,?,?,?,?,'SUCCEEDED','statistics',?)",
        published,
        backtest,
        research,
        config,
        strategy,
        version);
    long canonical =
        jdbc.queryForObject(
            "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE') RETURNING"
                + " account_id",
            Long.class,
            "pa-" + key);
    jdbc.update(
        "INSERT INTO"
            + " paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)"
            + " VALUES(?,?,?,'CREATED','SIM','OKX','SPOT','BTC-USDT','15m','system',now(),now(),?)",
        paper,
        published,
        version,
        canonical);
    funding.fundOnce(canonical, "pf-" + key, new BigDecimal("1000"), "statistics-fixture");
    Instant origin = Instant.parse("2026-10-01T00:00:00Z");
    for (int index = 0; index < 3; index++) {
      Instant signal = origin.plusSeconds(index * 900L);
      String order = "po-" + key + index, run = "pu-" + key + index, decision = "pd-" + key + index;
      String price = List.of("100", "110", "132").get(index), side = index == 0 ? "BUY" : "SELL";
      if (index < 2) {
        // 查询夹具采用互不争用的策略身份，保留正式准入的一策略单活动运行约束。
        String definition = simStrategy + index;
        jdbc.update(
            "INSERT INTO"
                + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)"
                + " VALUES(?,?,?,'SPOT_SMA_TARGET_V1','PAPER',?,'SIM',true,'{}',1)",
            definition,
            definition,
            definition,
            canonical);
        var admitted =
            new StrategyRun(
                run,
                definition,
                canonical,
                "PAPER",
                "SIM",
                "MANUAL",
                StrategyRunStatus.CREATED,
                "{}",
                "rq-" + key + index,
                Instant.now(),
                null,
                null,
                "statistics-fixture");
        var work =
            new StrategyDispatchWork(
                run,
                1,
                1,
                canonical,
                "coid-rq-" + key + index,
                "BTC-USDT",
                OrderSide.valueOf(side),
                OrderType.MARKET,
                BigDecimal.ONE,
                null,
                "IOC");
        assertTrue(strategyRuns.admit(admitted, work, null).admitted());
        jdbc.update(
            "INSERT INTO"
                + " orders(order_id,account_id,symbol,client_order_id,side,type,qty,status,trace_id,venue,exchange_code,trade_env,created_at)"
                + " VALUES(?,?,'BTC-USDT',?,?,'MARKET',1,'FILLED','statistics-fixture','PAPER','PAPER','SIM',now())",
            order,
            canonical,
            order,
            side);
        String fill = "pf-" + key + index;
        BigDecimal executed = new BigDecimal(index == 0 ? "100" : "120");
        Instant booked = Instant.now();
        jdbc.update(
            "INSERT INTO"
                + " trades(trade_id,order_id,account_id,symbol,price,qty,fee,fee_currency,trace_id,exchange_code,trade_env,ts)"
                + " VALUES(?,?,?,'BTC-USDT',?,1,-1,'USDT','statistics-fixture','PAPER','SIM',?)",
            fill,
            order,
            canonical,
            executed,
            Timestamp.from(booked));
        assertTrue(
            postings
                .postTrade(
                    new TradeLedgerRequest(
                        fill,
                        order,
                        canonical,
                        "BTC-USDT",
                        OrderSide.valueOf(side),
                        executed,
                        BigDecimal.ONE,
                        new BigDecimal("-1"),
                        "USDT",
                        "statistics-fixture",
                        booked,
                        "SIM"))
                .posted());
      }
      var source = new HashMap<String, Object>();
      source.put(
          "signalBars",
          List.of(
              Map.of(
                  "closePrice",
                  price,
                  "symbol",
                  "BTC-USDT",
                  "availableAt",
                  signal.toString(),
                  "closeTime",
                  signal.minusSeconds(1).toString())));
      if (index < 2)
        source.put(
            "executionBar",
            Map.of(
                "symbol",
                "BTC-USDT",
                "openPrice",
                index == 0 ? "100" : "120",
                "openTime",
                signal.plusSeconds(1).toString()));
      String snapshot = mapper.writeValueAsString(source);
      jdbc.update(
          """
INSERT INTO strategy_sim_decisions(decision_id,paper_run_id,canonical_account_id,strategy_version_id,strategy_checksum,
    signal_open_time,signal_available_at,execution_open_time,input_sha256,execution_bar_sha256,input_snapshot_json,
    target_exposure,status,reason,side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id)
VALUES(?,?,?,?,?,?,?,?,?,?,?::jsonb,0,?,'STATISTICS_FIXTURE',?,?,?,-0.001,0,?,?)
""",
          decision,
          paper,
          canonical,
          version,
          "a".repeat(64),
          Timestamp.from(signal.minusSeconds(60)),
          Timestamp.from(signal),
          index < 2 ? Timestamp.from(signal.plusSeconds(1)) : null,
          "b".repeat(64),
          index < 2 ? "c".repeat(64) : null,
          snapshot,
          index < 2 ? "ACCEPTED" : "NO_SIGNAL",
          index < 2 ? side : null,
          index < 2 ? BigDecimal.ONE : null,
          index < 2 ? new BigDecimal(index == 0 ? "100" : "120") : null,
          index < 2 ? run : null,
          index < 2 ? order : null);
    }
    // NO_SIGNAL中预存的未来执行bar没有对应成交，不能延伸曲线覆盖或把其价格用作已发生的估值。
    Instant last = origin.plusSeconds(1800), future = origin.plusSeconds(3600);
    String unexecuted =
        mapper.writeValueAsString(
            Map.of(
                "signalBars",
                List.of(
                    Map.of(
                        "closePrice",
                        "132",
                        "symbol",
                        "BTC-USDT",
                        "availableAt",
                        last.toString(),
                        "closeTime",
                        last.minusSeconds(1).toString())),
                "executionBar",
                Map.of("symbol", "BTC-USDT", "openPrice", "999", "openTime", future.toString())));
    jdbc.update(
        """
INSERT INTO strategy_sim_decisions(decision_id,paper_run_id,canonical_account_id,strategy_version_id,strategy_checksum,signal_open_time,signal_available_at,
    execution_open_time,input_sha256,execution_bar_sha256,input_snapshot_json,target_exposure,status,reason,execution_price,fee_rate,slippage_bps)
VALUES(?,?,?,?,?,?,?,?,?,?,?::jsonb,0,'NO_SIGNAL','STATISTICS_FIXTURE',999,0,0)
""",
        "unexecuted-" + key,
        paper,
        canonical,
        version,
        "a".repeat(64),
        Timestamp.from(last.minusSeconds(30)),
        Timestamp.from(last),
        Timestamp.from(future),
        "b".repeat(64),
        "c".repeat(64),
        unexecuted);
    long before =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE account_id=?", Long.class, canonical);
    var result = analysis.readPerformance(owner, paper, null, null).orElseThrow();
    assertEquals("AVAILABLE", result.status());
    assertEquals(paper, result.paperRunId());
    assertEquals("REPLAY_EVENT_TIME", result.timeBasis());
    assertEquals(0, result.pnl().compareTo(new BigDecimal("22")));
    assertEquals(0, result.outcomes().netProfit().compareTo(new BigDecimal("22")));
    assertEquals(5, result.samples().size());
    assertEquals("IRREGULAR_INTERVALS", result.sharpe().status());
    assertEquals(0, result.tradesAfterCoverage());
    assertEquals(last, result.coverageEnd());
    // 新生产器消费上述真实canonical注资/分录和持久价格夹具；不把这些夹具冒充Risk撮合证明。
    String historicalId = "historical-" + key;
    jdbc.update(
        "INSERT INTO"
            + " equity_curve_snapshots(equity_snapshot_id,paper_run_id,snapshot_time,total_equity,cash_balance,position_value,source,created_at)"
            + " VALUES(?,?,?,42.123456789012345678,42.123456789012345678,0,'MANUAL',now())",
        historicalId,
        paper,
        Timestamp.from(origin.minusSeconds(10)));
    var mark = result.samples().getLast();
    var projected = equitySnapshots.generate(owner, paper, mark.decisionId(), mark.eventAt());
    assertEquals("VERIFIED", projected.status());
    assertEquals(0, projected.snapshot().totalEquity().compareTo(new BigDecimal("1022")));
    assertEquals(0, projected.snapshot().realizedPnl().compareTo(new BigDecimal("22")));
    assertEquals(0, projected.snapshot().unrealizedPnl().signum());
    assertEquals(
        projected.snapshot(),
        equitySnapshots.generate(owner, paper, mark.decisionId(), mark.eventAt()).snapshot());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM equity_curve_snapshots WHERE source='CANONICAL_SIM_PROJECTION'"
                + " AND paper_run_id=?",
            Integer.class,
            paper));
    var executor = Executors.newFixedThreadPool(3);
    try {
      var calls = new ArrayList<Callable<String>>();
      for (int i = 0; i < 3; i++)
        calls.add(
            () ->
                equitySnapshots
                    .generate(owner, paper, mark.decisionId(), mark.eventAt())
                    .snapshot()
                    .equitySnapshotId());
      for (var value : executor.invokeAll(calls))
        assertEquals(projected.snapshot().equitySnapshotId(), value.get());
    } finally {
      executor.shutdownNow();
    }
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "UPDATE equity_curve_snapshots SET total_equity=0 WHERE equity_snapshot_id=?",
                projected.snapshot().equitySnapshotId()));
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "DELETE FROM equity_curve_snapshots WHERE equity_snapshot_id=?",
                projected.snapshot().equitySnapshotId()));
    assertEquals(
        "UNKNOWN",
        jdbc.queryForObject(
            "SELECT valuation_status FROM equity_curve_snapshots WHERE equity_snapshot_id=?",
            String.class,
            historicalId));
    assertNull(
        jdbc.queryForObject(
            "SELECT valuation_currency FROM equity_curve_snapshots WHERE equity_snapshot_id=?",
            String.class,
            historicalId));
    assertEquals(
        new BigDecimal("42.123456789012345678"),
        jdbc.queryForObject(
            "SELECT total_equity FROM equity_curve_snapshots WHERE equity_snapshot_id=?",
            BigDecimal.class,
            historicalId));
    assertTrue(
        equitySnapshots.find(owner, paper, projected.snapshot().equitySnapshotId()).isPresent());
    long otherOwner =
        jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, otherName);
    assertTrue(
        equitySnapshots.find(otherOwner, paper, projected.snapshot().equitySnapshotId()).isEmpty());
    assertThrows(
        ResponseStatusException.class,
        () -> equitySnapshots.generate(owner, paper, "wrong-decision", mark.eventAt()));
    assertTrue(
        result.samples().stream()
            .noneMatch(sample -> sample.markPrice().compareTo(new BigDecimal("999")) == 0));
    String path = "/api/paper-trading/strategy-sim/analysis/runs/" + paper + "/performance",
        token = login(ownerName);
    mvc.perform(get(path).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.outcomes.winningCount").value(1));
    mvc.perform(
            get(path)
                .header("Authorization", "Bearer " + token)
                .param("from", origin.plusSeconds(900).toString())
                .param("to", origin.plusSeconds(1800).toString()))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.samples.length()").value(3));
    mvc.perform(
            get(path)
                .header("Authorization", "Bearer " + token)
                .param("from", origin.plusSeconds(1800).toString())
                .param("to", origin.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).header("Authorization", "Bearer " + login(otherName)))
        .andExpect(status().isNotFound());
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    assertEquals(
        before,
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE account_id=?", Long.class, canonical));
    assertEquals(
        "CREATED",
        jdbc.queryForObject(
            "SELECT status FROM paper_trading_runs WHERE paper_run_id=?", String.class, paper));
    assertEquals(
        "NO_DATA",
        analysis
            .readPerformance(owner, paper, origin.minusSeconds(900), origin.minusSeconds(1))
            .orElseThrow()
            .status());
    // 扩大已处理信号夹具验证上限；缩小范围后仍从起点成交建立现金与成本，不从截断窗口猜指标。
    jdbc.update(
        """
INSERT INTO strategy_sim_decisions(decision_id,paper_run_id,canonical_account_id,strategy_version_id,strategy_checksum,
    signal_open_time,signal_available_at,input_sha256,input_snapshot_json,target_exposure,status,reason,fee_rate,slippage_bps)
SELECT ?||n,?,?,?, ?,?::timestamptz+n*interval '900 seconds'-interval '60 seconds',?::timestamptz+n*interval '900 seconds',?,
    jsonb_build_object('signalBars',jsonb_build_array(jsonb_build_object('closePrice','100','symbol','BTC-USDT',
        'availableAt',?::timestamptz+n*interval '900 seconds','closeTime',?::timestamptz+n*interval '900 seconds'-interval '1 second'))),
    0,'NO_SIGNAL','STATISTICS_FIXTURE',0,0 FROM generate_series(3,1002) n
""",
        "px-" + key,
        paper,
        canonical,
        version,
        "a".repeat(64),
        Timestamp.from(origin),
        Timestamp.from(origin),
        "b".repeat(64),
        Timestamp.from(origin),
        Timestamp.from(origin));
    assertEquals(
        "HISTORY_WINDOW_LIMITED",
        analysis.readPerformance(owner, paper, null, null).orElseThrow().status());
    assertEquals(
        "AVAILABLE",
        analysis
            .readPerformance(owner, paper, origin, origin.plusSeconds(1800))
            .orElseThrow()
            .status());
    // 只破坏临时库中合成成交的币种；不修改正式验收经济事实，也不重写既有冻结证据。
    jdbc.update("UPDATE trades SET fee_currency='BTC' WHERE account_id=?", canonical);
    var missing =
        analysis.readPerformance(owner, paper, origin, origin.plusSeconds(1800)).orElseThrow();
    assertEquals("LEDGER_INCOMPLETE", missing.status());
    assertNull(missing.pnl());
    assertTrue(missing.samples().isEmpty());
    assertEquals(paper, missing.paperRunId());
    assertThrows(
        IllegalStateException.class,
        () -> funding.fundOnce(canonical, "extra-" + key, BigDecimal.ONE, "statistics-fixture"));
    // 人工不完整来源只作为负例：正式服务拒绝二次注资，统计也必须拒绝未知资金记录。
    jdbc.update(
        "INSERT INTO"
            + " ledger_entries(entry_id,account_id,currency,delta,direction,ref_type,ref_id,trace_id,ts)"
            + " VALUES(?,?,'USDT',1,'CREDIT','UNKNOWN_TRANSFER',?,'statistics-fixture',now())",
        "unknown-" + key,
        canonical,
        key);
    assertEquals(
        "FUNDING_HISTORY_UNAVAILABLE",
        analysis
            .readPerformance(owner, paper, origin, origin.plusSeconds(1800))
            .orElseThrow()
            .status());
  }

  @Test
  void completeCountersAreIndependentOfOverviewWindowAndReadsAreSideEffectFree() throws Exception {
    var own = account();
    String suffix = UUID.randomUUID().toString(),
        strategy = "scope-" + suffix,
        version = "scope-version-" + suffix,
        research = "scope-research-" + suffix,
        config = "scope-config-" + suffix,
        backtest = "scope-backtest-" + suffix,
        published = "scope-publish-" + suffix,
        paper = "scope-paper-" + suffix,
        run = "scope-run-" + suffix;
    // 隔离PG中的关联/权限夹具，没有成交和账务，仅证明查询合同，不冒充经济链验收。
    jdbc.update(
        "INSERT INTO"
            + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)"
            + " VALUES(?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}',1)",
        strategy,
        strategy,
        strategy,
        own.legacyAccountId());
    jdbc.update(
        "INSERT INTO"
            + " strategy_versions(strategy_version_id,strategy_code,version,version_name,status,param_snapshot_json,config_snapshot_json,source_snapshot_json,checksum)"
            + " VALUES(?,?,1,'scope','ACTIVE','{}','{}','{}',?)",
        version,
        strategy,
        "a".repeat(64));
    jdbc.update(
        "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)"
            + " VALUES(?,?,?,'{}')",
        research,
        strategy,
        research);
    jdbc.update(
        "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES(?,?,?)",
        config,
        research,
        config);
    jdbc.update(
        "INSERT INTO"
            + " backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at)"
            + " VALUES(?,?,?,?,'CREATED','{}','{}',now())",
        backtest,
        config,
        research,
        strategy);
    jdbc.update(
        "INSERT INTO"
            + " backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name,strategy_version_id)"
            + " VALUES(?,?,?,?,?,'SUCCEEDED','scope',?)",
        published,
        backtest,
        research,
        config,
        strategy,
        version);
    long canonical =
        jdbc.queryForObject(
            "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE') RETURNING"
                + " account_id",
            Long.class,
            "scope-canonical-" + suffix);
    jdbc.update(
        "INSERT INTO"
            + " paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)"
            + " VALUES(?,?,?,'CREATED','SIM','OKX','SPOT','BTC-USDT','1h','system',now(),now(),?)",
        paper,
        published,
        version,
        canonical);
    for (int index = 0; index < 501; index++) {
      String order = "analysis-order-" + suffix + "-" + index;
      jdbc.update(
          "INSERT INTO"
              + " orders(order_id,account_id,symbol,client_order_id,side,type,qty,status,trace_id,venue,exchange_code,trade_env,created_at)"
              + " VALUES(?,?,'BTC-USDT',?,'BUY','MARKET',0.00000001,'REJECTED','analysis-fixture','PAPER','PAPER','SIM',now())",
          order,
          canonical,
          order);
    }
    long before =
        jdbc.queryForObject(
            "SELECT count(*) FROM orders WHERE account_id=?", Long.class, canonical);
    var metrics = analysis.readMetrics(owner, paper).orElseThrow();
    assertEquals(501, metrics.orderStatusCounts().getFirst().count());
    assertEquals("REJECTED", metrics.orderStatusCounts().getFirst().status());
    assertEquals(0, metrics.tradeCount());
    assertTrue(metrics.fees().isEmpty());
    assertEquals(0, metrics.decisionCount());
    long other =
        jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, otherName);
    assertTrue(analysis.readMetrics(other, paper).isEmpty());
    assertTrue(analysis.read(other, paper).isEmpty());
    assertTrue(analysis.readMetrics(owner, "does-not-exist").isEmpty());
    assertEquals(
        before,
        jdbc.queryForObject(
            "SELECT count(*) FROM orders WHERE account_id=?", Long.class, canonical));
    assertEquals(
        "CREATED",
        jdbc.queryForObject(
            "SELECT status FROM paper_trading_runs WHERE paper_run_id=?", String.class, paper));
    mvc.perform(
            get("/api/paper-trading/strategy-sim/analysis/runs/" + paper)
                .header("Authorization", "Bearer " + login(otherName)))
        .andExpect(status().isNotFound());
  }

  @Test
  void catalogRequiresAuthenticationAndPaginatesOwnedCanonicalInstances() throws Exception {
    var own = account();
    String suffix = UUID.randomUUID().toString(),
        strategy = "scope-" + suffix,
        version = "scope-version-" + suffix,
        research = "scope-research-" + suffix,
        config = "scope-config-" + suffix,
        backtest = "scope-backtest-" + suffix,
        published = "scope-publish-" + suffix,
        paper = "scope-paper-" + suffix,
        run = "scope-run-" + suffix;
    // 隔离PG中的关联/权限夹具，没有成交和账务，仅证明查询合同，不冒充经济链验收。
    jdbc.update(
        "INSERT INTO"
            + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)"
            + " VALUES(?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}',1)",
        strategy,
        strategy,
        strategy,
        own.legacyAccountId());
    jdbc.update(
        "INSERT INTO"
            + " strategy_versions(strategy_version_id,strategy_code,version,version_name,status,param_snapshot_json,config_snapshot_json,source_snapshot_json,checksum)"
            + " VALUES(?,?,1,'scope','ACTIVE','{}','{}','{}',?)",
        version,
        strategy,
        "a".repeat(64));
    jdbc.update(
        "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)"
            + " VALUES(?,?,?,'{}')",
        research,
        strategy,
        research);
    jdbc.update(
        "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES(?,?,?)",
        config,
        research,
        config);
    jdbc.update(
        "INSERT INTO"
            + " backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at)"
            + " VALUES(?,?,?,?,'CREATED','{}','{}',now())",
        backtest,
        config,
        research,
        strategy);
    jdbc.update(
        "INSERT INTO"
            + " backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name,strategy_version_id)"
            + " VALUES(?,?,?,?,?,'SUCCEEDED','scope',?)",
        published,
        backtest,
        research,
        config,
        strategy,
        version);
    long canonical =
        jdbc.queryForObject(
            "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE') RETURNING"
                + " account_id",
            Long.class,
            "scope-canonical-" + suffix);
    jdbc.update(
        "INSERT INTO"
            + " paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)"
            + " VALUES(?,?,?,'CREATED','SIM','OKX','SPOT','BTC-USDT','1h','system',now(),now(),?)",
        paper,
        published,
        version,
        canonical);
    for (int index = 0; index < 51; index++) {
      long next =
          jdbc.queryForObject(
              "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE') RETURNING"
                  + " account_id",
              Long.class,
              "analysis-" + suffix + index);
      jdbc.update(
          "INSERT INTO"
              + " paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)"
              + " VALUES(?,?,?,'CREATED','SIM','OKX','SPOT','BTC-USDT','1h',?,now(),now(),?)",
          "analysis-" + suffix + index,
          published,
          version,
          ownerName,
          next);
    }
    jdbc.update(
        "INSERT INTO"
            + " paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at)"
            + " VALUES(?,?,'CREATED','SIM','OKX','SPOT','BTC-USDT','1h',?,now(),now())",
        "legacy-" + suffix,
        published,
        ownerName);
    var first = analysis.list(owner, 0);
    var second = analysis.list(owner, 50);
    assertEquals(50, first.items().size());
    assertTrue(first.hasMore());
    assertEquals(2, second.items().size());
    assertFalse(second.hasMore());
    assertTrue(first.items().stream().allMatch(row -> row.canonicalAccountId() > 0));
    long other =
        jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, otherName);
    assertTrue(analysis.list(other, 0).items().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> analysis.list(owner, 1000));
    assertThrows(IllegalArgumentException.class, () -> analysis.list(owner, 1));
    String path = "/api/paper-trading/strategy-sim/analysis/runs", token = login(ownerName);
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(50));
    mvc.perform(get(path).header("Authorization", "Bearer " + token).param("offset", "50"))
        .andExpect(status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(2));
    mvc.perform(get(path).header("Authorization", "Bearer " + token).param("offset", "-1"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).header("Authorization", "Bearer " + token).param("offset", "1000"))
        .andExpect(status().isBadRequest());
  }

  private String login(String username) throws Exception {
    var body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content(
                        mapper.writeValueAsString(
                            Map.of("username", username, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).path("accessToken").asText();
  }

  public static class Cleanup extends AbstractTestExecutionListener {
    @Override
    public int getOrder() {
      return 999;
    }

    @Override
    public void afterTestClass(TestContext context) {
      if (control != null && database != null) {
        assertTrue(database.matches("sim_analysis_[0-9a-f]{32}"));
        control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
      }
    }
  }
}
