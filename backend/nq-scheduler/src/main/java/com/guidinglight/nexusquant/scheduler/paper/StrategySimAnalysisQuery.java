package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 单个独立SIM的只读分析；完整计数不从总览截断窗口推算，也不合计多个实例的资金。 */
@Service
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class StrategySimAnalysisQuery {
  // system是既有控制器的系统actor哨兵；同名真人用户必须通过来源账户归属，不能据字符串取得他人运行。
  private static final String RUNS =
      """
FROM paper_trading_runs p
LEFT JOIN backtest_publish_records published ON published.publish_record_id=p.publish_id
LEFT JOIN strategy_versions frozen ON frozen.strategy_version_id=coalesce(p.strategy_version_id,published.strategy_version_id)
WHERE p.tenant_id=nq_current_tenant_id() AND p.canonical_account_id IS NOT NULL AND p.trade_env='SIM'
    AND ((p.created_by<>'system' AND p.created_by=(SELECT username FROM users WHERE id=?)) OR EXISTS(
        SELECT 1 FROM strategy_definitions source JOIN exchange_accounts a
            ON a.legacy_account_id=source.account_id AND a.trade_env=source.trade_env AND a.exchange_code=source.exchange_code
        WHERE a.owner_user_id=? AND (source.strategy_code=frozen.strategy_code
            OR source.strategy_id=published.target_strategy_definition_id OR source.strategy_id=published.source_strategy_id)))
""";
  private static final String FIELDS =
      """
SELECT p.paper_run_id,p.canonical_account_id,p.status,p.symbol,p.interval_code,p.publish_id,
    p.strategy_version_id,frozen.strategy_code,frozen.version_name,frozen.version,
    published.backtest_config_id,published.backtest_run_id,p.config_snapshot_json,p.created_at,p.updated_at
""";
  private final JdbcTemplate jdbc;
  private ResearchFeeReadScope feeScope =
      new ResearchFeeReadScope(ResearchFeeSourcePort.disabled());

  /** 原账户归属规则外再叠加费用来源范围，任何一层不能替代另一层授权。 */
  @Autowired
  private void bindFeeReadScope(ResearchFeeReadScope scope) {
    feeScope = scope;
  }

  private final StrategySimRunService facts;
  private final StrategySimDecisionRepository decisions;
  private final StrategySimPerformanceQuery performance;

  public StrategySimAnalysisQuery(
      JdbcTemplate jdbc,
      StrategySimRunService facts,
      StrategySimDecisionRepository decisions,
      StrategySimPerformanceQuery performance) {
    this.jdbc = new JdbcTemplate(jdbc.getDataSource());
    this.jdbc.setQueryTimeout(5);
    this.facts = facts;
    this.decisions = decisions;
    this.performance = performance;
  }

  /** 读取最多1000个用户可见canonical SIM实例；每页50条，旧Paper不混入目录，末页截断明确返回。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
  public Catalog list(long owner, int offset) {
    if (owner <= 0 || offset < 0 || offset > 950 || offset % 50 != 0)
      throw new IllegalArgumentException("INVALID_SIM_ANALYSIS_PAGE");
    var runs =
        jdbc.query(
            FIELDS
                + RUNS
                + " AND "
                + feeScope.backtest("published.backtest_run_id", owner)
                + " ORDER BY p.updated_at DESC,p.paper_run_id LIMIT 51 OFFSET ?",
            (rs, n) -> map(rs),
            owner,
            owner,
            offset);
    return new Catalog(
        runs.stream().limit(50).toList(),
        offset,
        offset < 950 && runs.size() > 50,
        offset == 950 && runs.size() > 50);
  }

  /** 同一数据库快照中读取精确实例、全量统计及有界决策；无权或身份不存在返回空，不自动选另一实例。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
  public Optional<Analysis> read(long owner, String paperRunId) {
    return owned(owner, paperRunId)
        .map(
            run ->
                new Analysis(
                    run, facts.facts(paperRunId), metrics(run), decisions.listByRun(paperRunId)));
  }

  /** 统计查询复用实例授权，不依赖列表或决策明细窗口，不能据截断行数计算完整成交与费用。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
  public Optional<Metrics> readMetrics(long owner, String paperRunId) {
    return owned(owner, paperRunId).map(this::metrics);
  }

  /** 新历史读取沿用精确实例权限；无权访问不会进入成交、账务和信号价格查询。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
  public Optional<StrategySimPerformanceQuery.View> readPerformance(
      long owner, String paperRunId, Instant from, Instant to) {
    return owned(owner, paperRunId).map(run -> performance.read(run, from, to));
  }

  private Optional<Run> owned(long owner, String paperRunId) {
    if (owner <= 0 || paperRunId == null || paperRunId.isBlank() || paperRunId.length() > 128)
      throw new IllegalArgumentException("INVALID_SIM_ANALYSIS_IDENTITY");
    return jdbc
        .query(
            FIELDS
                + RUNS
                + " AND "
                + feeScope.backtest("published.backtest_run_id", owner)
                + " AND p.paper_run_id=? LIMIT 1",
            (rs, n) -> map(rs),
            owner,
            owner,
            paperRunId)
        .stream()
        .findFirst();
  }

  /** 可信投影只需不可变关联身份，不调用当前时点资产查询冒充历史估值依据。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
  public Optional<Run> readIdentity(long owner, String paperRunId) {
    return owned(owner, paperRunId);
  }

  /** 旧目录沿用同一所有者及费用范围；每批最多100个身份，不逐运行读取经济事实。 */
  @Transactional(readOnly = true, timeout = 10)
  public java.util.Set<String> readableRunIds(long owner, List<String> ids) {
    if (owner <= 0 || ids.size() > 100)
      throw new IllegalArgumentException("INVALID_SIM_ANALYSIS_IDENTITY");
    if (ids.isEmpty()) return java.util.Set.of();
    var args = new java.util.ArrayList<Object>(ids);
    args.add(owner);
    args.add(owner);
    return new java.util.HashSet<>(jdbc.queryForList(
        "SELECT paper_run_id FROM paper_trading_runs WHERE paper_run_id IN ("
            + String.join(",", java.util.Collections.nCopies(ids.size(), "?"))
            + ") AND (canonical_account_id IS NULL OR paper_run_id IN (SELECT p.paper_run_id "
            + RUNS + " AND " + feeScope.backtest("published.backtest_run_id", owner) + "))",
        String.class, args.toArray()));
  }

  private Metrics metrics(Run run) {
    long account = run.canonicalAccountId();
    var statuses =
        jdbc.query(
            """
SELECT status,count(*) FROM orders WHERE account_id=? AND exchange_code='PAPER' AND trade_env='SIM'
GROUP BY status ORDER BY status
""",
            (rs, n) -> new OrderStatusCount(rs.getString(1), rs.getLong(2)),
            account);
    // 费用按原币种及有费用的成交覆盖分别统计；不跨币种求和，不把未记录费用视为零。
    var fees =
        jdbc.query(
            """
SELECT t.fee_currency,sum(t.fee)::text,count(*),count(t.fee) FROM trades t JOIN orders o
    ON o.order_id=t.order_id AND o.account_id=t.account_id
WHERE t.account_id=? AND o.exchange_code='PAPER' AND o.trade_env='SIM'
GROUP BY t.fee_currency ORDER BY t.fee_currency NULLS LAST
""",
            (rs, n) -> new FeeTotal(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)),
            account);
    long risk =
        jdbc.queryForObject(
            """
SELECT count(*) FROM risk_events r JOIN orders o ON r.scope='ORDER' AND r.scope_id=o.order_id
WHERE o.account_id=? AND o.exchange_code='PAPER' AND o.trade_env='SIM'
""",
            Long.class,
            account);
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM strategy_sim_decisions WHERE paper_run_id=?",
            Long.class,
            run.paperRunId());
    return new Metrics(
        statuses,
        fees.stream().mapToLong(FeeTotal::tradeCount).sum(),
        fees,
        risk,
        count,
        count > 500);
  }

  private static Run map(ResultSet rs) throws SQLException {
    return new Run(
        rs.getString("paper_run_id"),
        rs.getLong("canonical_account_id"),
        rs.getString("status"),
        rs.getString("symbol"),
        rs.getString("interval_code"),
        rs.getString("publish_id"),
        rs.getString("strategy_version_id"),
        rs.getString("strategy_code"),
        rs.getString("version_name"),
        (Integer) rs.getObject("version"),
        rs.getString("backtest_config_id"),
        rs.getString("backtest_run_id"),
        rs.getString("config_snapshot_json"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  public record Run(
      String paperRunId,
      long canonicalAccountId,
      String status,
      String symbol,
      String intervalCode,
      String publishId,
      String strategyVersionId,
      String strategyCode,
      String versionName,
      Integer version,
      String backtestConfigId,
      String backtestRunId,
      String configSnapshotJson,
      Instant createdAt,
      Instant updatedAt) {}

  public record Catalog(List<Run> items, int offset, boolean hasMore, boolean windowLimited) {}

  public record OrderStatusCount(String status, long count) {}

  public record FeeTotal(
      String currency, String totalFee, long tradeCount, long measuredFeeCount) {}

  public record Metrics(
      List<OrderStatusCount> orderStatusCounts,
      long tradeCount,
      List<FeeTotal> fees,
      long riskEventCount,
      long decisionCount,
      boolean decisionWindowLimited) {}

  public record Analysis(
      Run run,
      StrategySimRunService.FactsView facts,
      Metrics metrics,
      List<StrategySimDecisionRepository.DecisionView> decisions) {}
}
