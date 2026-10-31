package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;
import com.guidinglight.nexusquant.account.domain.performance.PerformanceStatistics;
import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingPort;
import com.guidinglight.nexusquant.marketdata.application.service.DiscountAssetPriceService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Charge;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 从已处理的信号价格与canonical成交/账务重建单实例历史；不读取冻结数据集的未来bar，不写测量或交易。 */
@Service
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class StrategySimPerformanceQuery {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();
  private DiscountAssetPriceService discountPrices;
  private SimAssetFundingPort assetFunding;

  /** 历史价格与初始化身份只在第三资产实例使用；缺装配保持明确不可用。 */
  @Autowired
  private void bindDiscountAssets(
      ObjectProvider<DiscountAssetPriceService> prices,
      ObjectProvider<SimAssetFundingPort> funding) {
    discountPrices = prices.getIfAvailable();
    assetFunding = funding.getIfAvailable();
  }

  public StrategySimPerformanceQuery(JdbcTemplate jdbc) {
    this.jdbc = new JdbcTemplate(jdbc.getDataSource());
    this.jdbc.setQueryTimeout(5);
  }

  /** 调用方先复用实例归属与费用来源授权，并在一致事务快照内调用；范围只过滤结果，不裁掉开仓成本。 */
  View read(StrategySimAnalysisQuery.Run run, Instant from, Instant to) {
    return readFacts(run, from, to).forRun(run.paperRunId());
  }

  private View readFacts(StrategySimAnalysisQuery.Run run, Instant from, Instant to) {
    return readFacts(run, from, to, null);
  }

  /** 新观察沿用同一资金、费用校验与计算器；作用域只改变事实截止及有界读取预算。 */
  ObservationData readObservation(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant asOf) {
    // 回放没有前向事实的asOf合同；新入口只识别类型，禁止回退到不带截止的历史绩效查询。
    if (!isContinuous(run.paperRunId()))
      return new ObservationData(unavailable("NOT_COMPARABLE", "REPLAY_EVENT_TIME", 3600, null)
          .forRun(run.paperRunId()), List.of());
    if (!from.isBefore(to)) return new ObservationData(unavailable("INSUFFICIENT_SAMPLES",
        "ACTUAL_EVENT_TIME", 3600, null).forRun(run.paperRunId()), List.of());
    var scope = new ObservationScope(from, to, asOf);
    View value = readFacts(run, from, to, scope).forRun(run.paperRunId());
    return new ObservationData(value, List.copyOf(scope.fills));
  }

  private View readFacts(StrategySimAnalysisQuery.Run run, Instant from, Instant to, ObservationScope scope) {
    if ((from == null) != (to == null)
        || (from != null
            && (!from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0)))
      throw new IllegalArgumentException("INVALID_SIM_PERFORMANCE_RANGE");
    boolean continuous =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM continuous_sim_runs WHERE" + " paper_run_id=?)",
                Boolean.class,
                run.paperRunId()));
    String clock = continuous ? "ACTUAL_EVENT_TIME" : "REPLAY_EVENT_TIME";
    if (scope != null && !continuous)
      return unavailable("NOT_COMPARABLE", clock, 3600, null);
    long cadence = BarInterval.fromWireValue(run.intervalCode()).duration().getSeconds();
    Timestamp latestLedger =
        jdbc.queryForObject(
            "SELECT max(ts) FROM ledger_entries WHERE account_id=?"
                + (scope == null ? "" : " AND ts<=? AND ts<? AND created_at<=?"),
            Timestamp.class,
            scope == null ? new Object[]{run.canonicalAccountId()}
                : new Object[]{run.canonicalAccountId(), Timestamp.from(scope.asOf), Timestamp.from(scope.to), Timestamp.from(scope.asOf)});
    Instant ledgerAsOf = latestLedger == null ? null : latestLedger.toInstant();
    DiscountContext discount = discountContext(run);
    var marks = loadMarks(run, from, to, scope);
    if (marks.size() > (scope == null ? 1000 : 10000))
      return unavailable("HISTORY_WINDOW_LIMITED", clock, cadence, ledgerAsOf);
    if (marks.isEmpty()) return unavailable("NO_DATA", clock, cadence, ledgerAsOf);
    if (marks.stream().anyMatch(mark -> !run.symbol().equals(mark.symbol())))
      return unavailable("MARK_SOURCE_UNAVAILABLE", clock, cadence, ledgerAsOf);
    // 只用确有canonical成交的执行行情；NO_SIGNAL预先保存的下一bar不能提前成为已发生的估值事件。
    // 回放初始注资没有模型时点；只接受正式的一次性预算，额外资金流不能偷换为回放收益。
    var funds =
        jdbc.query(
            "SELECT delta,ts,currency,ref_id FROM ledger_entries WHERE account_id=? AND"
                + " ref_type='SIM_FUNDING_CASH'"
                + (scope == null ? "" : " AND ts<=? AND ts<? AND created_at<=?")
                + " ORDER BY ts,currency LIMIT 3",
            (rs, n) ->
                new Funding(
                    rs.getBigDecimal(1),
                    rs.getTimestamp(2).toInstant(),
                    rs.getString(3),
                    rs.getString(4)),
            scope == null ? new Object[]{run.canonicalAccountId()}
                : new Object[]{run.canonicalAccountId(), Timestamp.from(scope.asOf), Timestamp.from(scope.to), Timestamp.from(scope.asOf)});
    boolean unknownLedger =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
SELECT EXISTS(SELECT 1 FROM ledger_entries e WHERE account_id=? %s AND (
    ref_type NOT IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA','TRADE')
    OR (ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA') AND currency NOT IN ('USDT',?))
    OR (ref_type='TRADE' AND NOT EXISTS(SELECT 1 FROM trades t WHERE t.trade_id=e.ref_id AND t.account_id=e.account_id %s))))
OR EXISTS(SELECT 1 FROM ledger_entries WHERE account_id=? %s AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')
    GROUP BY ref_id,currency HAVING count(*)<>2 OR sum(delta)<>0)
""".formatted(scope == null ? "" : "AND e.ts<=? AND e.ts<? AND e.created_at<=?",
                    scope == null ? "" : "AND t.ts<=? AND t.ts<? AND t.created_at<=?",
                    scope == null ? "" : "AND ts<=? AND ts<? AND created_at<=?"),
                Boolean.class,
                scope == null ? new Object[]{run.canonicalAccountId(), discount == null ? "USDT" : discount.plan().assetCurrency(), run.canonicalAccountId()}
                    : new Object[]{run.canonicalAccountId(), Timestamp.from(scope.asOf), Timestamp.from(scope.to), Timestamp.from(scope.asOf),
                        discount == null ? "USDT" : discount.plan().assetCurrency(),
                        Timestamp.from(scope.asOf), Timestamp.from(scope.to), Timestamp.from(scope.asOf), run.canonicalAccountId(),
                        Timestamp.from(scope.asOf), Timestamp.from(scope.to), Timestamp.from(scope.asOf)}));
    BigDecimal initialBudget;
    if (discount == null) {
      if (funds.size() != 1
          || !"USDT".equals(funds.getFirst().currency())
          || funds.getFirst().amount().signum() <= 0
          || unknownLedger)
        return unavailable("FUNDING_HISTORY_UNAVAILABLE", clock, cadence, ledgerAsOf);
      initialBudget = funds.getFirst().amount();
    } else {
      boolean cashMatches =
          funds.stream()
              .anyMatch(
                  f ->
                      "USDT".equals(f.currency())
                          && f.amount().compareTo(discount.plan().cashBudget()) == 0
                          && run.paperRunId().equals(f.runId()));
      boolean assetMatches =
          funds.stream()
              .anyMatch(
                  f ->
                      discount.plan().assetCurrency().equals(f.currency())
                          && f.amount().compareTo(discount.plan().assetQuantity()) == 0
                          && run.paperRunId().equals(f.runId()));
      if (funds.size() != 2 || !cashMatches || !assetMatches || unknownLedger)
        return unavailable("FUNDING_HISTORY_UNAVAILABLE", clock, cadence, ledgerAsOf);
      initialBudget = discount.plan().totalBudget();
    }
    // 持续SIM的历史暖机信号早于真实注资，不能把未来预算提前放入这些时点。
    if (continuous)
      marks = marks.stream().filter(mark -> !mark.at().isBefore(funds.getFirst().at())).toList();
    var trades = loadFills(run, continuous, scope);
    if (trades.size() > 10000)
      return unavailable("TRADE_WINDOW_LIMITED", clock, cadence, ledgerAsOf);
    if (trades.stream().anyMatch(f -> !f.scopeValid() || !run.symbol().equals(f.trade().symbol())))
      return unavailable("UNSUPPORTED_FACT_SCOPE", clock, cadence, ledgerAsOf);
    if (trades.stream()
        .anyMatch(
            f ->
                f.trade().fee() == null
                    || (f.trade().fee().signum() != 0
                        && !"USDT".equals(f.trade().feeCurrency())
                        && !run.symbol().split("-")[0].equals(f.trade().feeCurrency())
                        && (discount == null
                            || !discount.plan().assetCurrency().equals(f.trade().feeCurrency())))
                    || f.entries() != (f.trade().fee().signum() == 0 ? 2 : 4)
                    || f.balance().signum() != 0
                    || f.feeBalance().signum() != 0
                    || !f.knownEntries()))
      return unavailable("LEDGER_INCOMPLETE", clock, cadence, ledgerAsOf);
    if (discount != null) {
      List<Fill> verified = new ArrayList<>(trades.size());
      for (Fill fill : trades) {
        if (fill.feeModelJson() == null || !read(fill.feeModelJson()).equals(discount.spec()))
          return unavailable("FEE_BASIS_UNAVAILABLE", clock, cadence, ledgerAsOf);
        var payment = FrozenSimFeeCharge.read(fill.feeChargeJson(), mapper);
        payment.verifySource(discount.prices());
        Charge charge =
            payment.charge(
                StrategySimFeeTerms.from(null, fill.feeModelJson(), mapper),
                run.symbol(),
                fill.trade().side(),
                fill.trade().quantity(),
                fill.trade().price(),
                fill.trade().at());
        if (!charge.currency().equals(fill.trade().feeCurrency())
            || charge.amount().compareTo(fill.trade().fee()) != 0)
          return unavailable("FEE_BASIS_UNAVAILABLE", clock, cadence, ledgerAsOf);
        var t = fill.trade();
        verified.add(
            new Fill(
                new ClosedTradeStatistics.Trade(
                    t.tradeId(),
                    t.symbol(),
                    t.side(),
                    t.quantity(),
                    t.price(),
                    t.fee(),
                    t.feeCurrency(),
                    t.at(),
                    charge.quoteValue()),
                fill.cashDelta(),
                fill.entries(),
                fill.balance(),
                fill.knownEntries(),
                fill.scopeValid(),
                fill.feeBalance(),
                fill.feeModelJson(),
                fill.feeChargeJson()));
      }
      trades = verified;
    } else if (trades.stream().anyMatch(fill -> fill.feeChargeJson() != null))
      return unavailable("FEE_BASIS_UNAVAILABLE", clock, cadence, ledgerAsOf);
    if (scope != null) scope.fills = List.copyOf(trades);
    return calculate(
        run.paperRunId(),
        clock,
        cadence,
        ledgerAsOf,
        initialBudget,
        marks,
        trades,
        from,
        to,
        discount,
        scope == null ? 1000 : 10000,
        scope != null);
  }

  private List<Mark> loadMarks(StrategySimAnalysisQuery.Run run, Instant from, Instant to, ObservationScope scope) {
    // economicEnd处的既有价格仅作期末估值锚点；成交、账务仍严格小于to，不补写价格。
    String query = """
        WITH scope AS (
          SELECT decision_id,signal_available_at,execution_open_time,input_snapshot_json,status,order_id,canonical_account_id
          FROM strategy_sim_decisions WHERE paper_run_id=? AND canonical_account_id=? %s
        ), events AS (
          SELECT decision_id,signal_available_at event_at,input_snapshot_json->'signalBars'->-1->>'closePrice' price,
            input_snapshot_json->'signalBars'->-1->>'symbol' symbol,input_snapshot_json->'signalBars'->-1->>'availableAt' available_at,
            input_snapshot_json->'signalBars'->-1->>'closeTime' source_at,'SIGNAL_CLOSE' mark_type,0 priority FROM scope
          UNION ALL
          SELECT decision_id,execution_open_time,
            coalesce(input_snapshot_json->'executionQuote'->>'price',input_snapshot_json->'executionBar'->>'openPrice'),
            coalesce(input_snapshot_json->'executionBar'->>'symbol',input_snapshot_json->'signalBars'->-1->>'symbol'),
            coalesce(input_snapshot_json->'executionQuote'->>'observedAt',input_snapshot_json->'executionBar'->>'openTime'),
            coalesce(input_snapshot_json->'executionQuote'->>'observedAt',input_snapshot_json->'executionBar'->>'openTime'),
            'EXECUTION_QUOTE',1 FROM scope WHERE execution_open_time IS NOT NULL AND status='ACCEPTED'
            AND EXISTS(SELECT 1 FROM trades actual WHERE actual.order_id=scope.order_id
                AND actual.account_id=scope.canonical_account_id %s)
            AND coalesce(input_snapshot_json->'executionQuote'->>'price',input_snapshot_json->'executionBar'->>'openPrice') IS NOT NULL
        )
        SELECT DISTINCT ON(event_at) decision_id,event_at,price,symbol,available_at,source_at,mark_type FROM events
        WHERE (?::timestamptz IS NULL OR event_at>=?) AND (?::timestamptz IS NULL OR event_at%s?)
          AND (NOT ? OR event_at>=(SELECT min(ts) FROM ledger_entries WHERE account_id=? AND ref_type='SIM_FUNDING_CASH' %s))
          %s
        ORDER BY event_at,priority DESC,decision_id LIMIT ?
        """.formatted(scope == null ? "" : "AND created_at<=?",
            scope == null ? "" : "AND actual.created_at<=? AND actual.ts<=? AND actual.ts<?",
            "<=",
            scope == null ? "" : "AND created_at<=? AND ts<=? AND ts<?",
            scope == null ? "" : "AND event_at<=? AND (?::timestamptz IS NULL OR event_at>?)");
    List<Mark> result = new ArrayList<>();
    Instant last = null;
    int maximum = scope == null ? 1001 : 10001;
    for (int page = 0; page < (scope == null ? 1 : 21); page++) {
      List<Object> args = new ArrayList<>(Arrays.asList(run.paperRunId(), run.canonicalAccountId()));
      if (scope != null) args.addAll(Arrays.asList(Timestamp.from(scope.asOf), Timestamp.from(scope.asOf),
          Timestamp.from(scope.asOf), Timestamp.from(scope.to)));
      args.addAll(Arrays.asList(from == null ? null : Timestamp.from(from), from == null ? null : Timestamp.from(from),
          to == null ? null : Timestamp.from(to), to == null ? null : Timestamp.from(to),
          scope != null || isContinuous(run.paperRunId()), run.canonicalAccountId()));
      if (scope != null) args.addAll(Arrays.asList(Timestamp.from(scope.asOf), Timestamp.from(scope.asOf),
          Timestamp.from(scope.to), Timestamp.from(scope.asOf), last == null ? null : Timestamp.from(last),
          last == null ? null : Timestamp.from(last)));
      int limit = scope == null ? maximum : Math.min(500, maximum - result.size());
      args.add(limit);
      var rows = jdbc.query(query, (rs, index) -> new Mark(rs.getString(1), rs.getTimestamp(2).toInstant(),
          rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)), args.toArray());
      result.addAll(rows);
      if (scope == null || rows.size() < limit || result.size() == maximum) break;
      last = rows.getLast().at();
    }
    return List.copyOf(result);
  }

  private boolean isContinuous(String run) {
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM continuous_sim_runs WHERE paper_run_id=?)", Boolean.class, run));
  }

  private List<Fill> loadFills(StrategySimAnalysisQuery.Run run, boolean continuous, ObservationScope scope) {
    String selected = scope == null ? "trades t" : """
        (SELECT * FROM trades WHERE account_id=? AND ts<=? AND ts<? AND created_at<=?
          AND (?::timestamptz IS NULL OR (ts,trade_id)>(?::timestamptz,?::varchar))
          ORDER BY ts,trade_id LIMIT ?) t
        """;
    String query = """
        SELECT t.trade_id,t.symbol,o.side,t.qty,t.price,t.fee,t.fee_currency,t.ts,d.execution_open_time,
          o.trade_env,o.exchange_code,t.trade_env,t.exchange_code,d.paper_run_id,d.fee_model_json::text,d.fee_charge_json::text,
          coalesce(sum(e.delta) FILTER(WHERE e.currency='USDT' AND e.idempotency_key IN(t.trade_id||':LEDGER:1',t.trade_id||':LEDGER:FEE_1')),0) cash_delta,
          count(e.entry_id) entries,coalesce(sum(e.delta) FILTER(WHERE e.currency='USDT'),0) balance,
          coalesce(sum(e.delta) FILTER(WHERE e.currency=t.fee_currency),0) fee_balance,
          coalesce(bool_and((e.currency='USDT' AND e.idempotency_key IN(t.trade_id||':LEDGER:1',t.trade_id||':LEDGER:2'))
            OR (e.currency=t.fee_currency AND e.idempotency_key IN(t.trade_id||':LEDGER:FEE_1',t.trade_id||':LEDGER:FEE_2'))),false) known_entries
        FROM %s LEFT JOIN orders o ON o.order_id=t.order_id AND o.account_id=t.account_id %s
        LEFT JOIN strategy_sim_decisions d ON d.order_id=t.order_id AND d.canonical_account_id=t.account_id %s
        LEFT JOIN ledger_entries e ON e.account_id=t.account_id AND e.ref_type='TRADE' AND e.ref_id=t.trade_id %s
        WHERE t.account_id=?
        GROUP BY t.trade_id,t.symbol,o.side,t.qty,t.price,t.fee,t.fee_currency,t.ts,t.trade_env,t.exchange_code,
          o.trade_env,o.exchange_code,d.execution_open_time,d.paper_run_id,d.fee_model_json,d.fee_charge_json
        ORDER BY t.ts,t.trade_id LIMIT ?
        """.formatted(selected, scope == null ? "" : "AND o.created_at<=?",
            scope == null ? "" : "AND d.created_at<=?",
            scope == null ? "" : "AND e.created_at<=? AND e.ts<=? AND e.ts<?");
    var result = new ArrayList<Fill>();
    Instant lastAt = null; String lastId = null;
    for (int page = 0; page < (scope == null ? 1 : 21); page++) {
      int limit = scope == null ? 10001 : Math.min(500, 10001 - result.size());
      List<Object> args = new ArrayList<>();
      if (scope != null) args.addAll(Arrays.asList(run.canonicalAccountId(), Timestamp.from(scope.asOf),
          Timestamp.from(scope.to), Timestamp.from(scope.asOf), lastAt == null ? null : Timestamp.from(lastAt),
          lastAt == null ? null : Timestamp.from(lastAt), lastId, limit, Timestamp.from(scope.asOf),
          Timestamp.from(scope.asOf), Timestamp.from(scope.asOf), Timestamp.from(scope.asOf), Timestamp.from(scope.to)));
      args.addAll(Arrays.asList(run.canonicalAccountId(), limit));
      var rows = jdbc.query(query, (rs, index) -> new Fill(
          new ClosedTradeStatistics.Trade(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
              rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), continuous ? rs.getTimestamp(8).toInstant()
                  : rs.getTimestamp(9) == null ? rs.getTimestamp(8).toInstant() : rs.getTimestamp(9).toInstant()),
          rs.getBigDecimal("cash_delta"), rs.getLong("entries"), rs.getBigDecimal("balance"), rs.getBoolean("known_entries"),
          "SIM".equals(rs.getString(10)) && "PAPER".equals(rs.getString(11)) && "SIM".equals(rs.getString(12))
              && "PAPER".equals(rs.getString(13)) && run.paperRunId().equals(rs.getString(14)) && rs.getTimestamp(9) != null,
          rs.getBigDecimal("fee_balance"), rs.getString("fee_model_json"), rs.getString("fee_charge_json")), args.toArray());
      result.addAll(rows);
      if (scope == null || rows.size() < limit || result.size() == 10001) break;
      lastAt = rows.getLast().trade().at(); lastId = rows.getLast().trade().tradeId();
    }
    return List.copyOf(result);
  }

  record ObservationData(View performance, List<Fill> fills) {}

  /** 每次调用独有的读取收集器，不缓存账户或经济事实。 */
  private static final class ObservationScope {
    final Instant from, to, asOf;
    List<Fill> fills = List.of();
    ObservationScope(Instant from, Instant to, Instant asOf) { this.from = from; this.to = to; this.asOf = asOf; }
  }

  /** 现金只累加正式账户分录，数量只取正式成交；回放映射模型事件，持续SIM使用实际成交时刻。 */
  static View calculate(
      String id,
      String clock,
      long cadence,
      Instant ledgerAsOf,
      BigDecimal funding,
      List<Mark> marks,
      List<Fill> fills,
      Instant from,
      Instant to) {
    return calculate(id, clock, cadence, ledgerAsOf, funding, marks, fills, from, to, null, 1000, false);
  }

  /** 同一计算器的长观察预算；不改变旧入口或从抽样曲线重新计算指标。 */
  static View calculateObservation(String id, String clock, long cadence, Instant ledgerAsOf,
      BigDecimal funding, List<Mark> marks, List<Fill> fills, Instant from, Instant to) {
    return calculate(id, clock, cadence, ledgerAsOf, funding, marks, fills, from, to, null, 10000, true);
  }

  private static View calculate(
      String id,
      String clock,
      long cadence,
      Instant ledgerAsOf,
      BigDecimal funding,
      List<Mark> marks,
      List<Fill> fills,
      Instant from,
      Instant to,
      DiscountContext discount,
      int maximumSamples,
      boolean observation) {
    var ordered =
        fills.stream()
            .filter(fill -> !observation || to == null || fill.trade().at().isBefore(to))
            .sorted(
                Comparator.comparing((Fill f) -> f.trade().at())
                    .thenComparing(f -> f.trade().tradeId()))
            .toList();
    var samples = new ArrayList<Sample>();
    BigDecimal cash = discount == null ? funding : discount.plan().cashBudget(),
        quantity = BigDecimal.ZERO;
    BigDecimal assetBalance = discount == null ? BigDecimal.ZERO : discount.plan().assetQuantity();
    int cursor = 0;
    for (var mark : marks) {
      BigDecimal price;
      try {
        price = new BigDecimal(mark.price());
        if (price.signum() <= 0
            || Instant.parse(mark.availableAt()).isAfter(mark.at())
            || Instant.parse(mark.closeTime()).isAfter(mark.at()))
          return unavailable("MARK_SOURCE_UNAVAILABLE", clock, cadence, ledgerAsOf);
        // 执行估值必须能对应保存的公开报价/模型开盘时刻，不能把已含滑点的成交价倒当行情。
        if ("EXECUTION_QUOTE".equals(mark.markType())
            && !Instant.parse(mark.closeTime()).equals(mark.at()))
          return unavailable("MARK_SOURCE_UNAVAILABLE", clock, cadence, ledgerAsOf);
      } catch (RuntimeException invalid) {
        return unavailable("MARK_SOURCE_UNAVAILABLE", clock, cadence, ledgerAsOf);
      }
      // 半开观察区间的首个估值必须在from同刻成交之前，才能把该成交费用计入期间损益。
      // 后续估值仍复用原账户累加算法；旧接口保持包含估值同刻成交的历史语义。
      boolean openingBoundary = observation && from != null && mark.at().equals(from);
      while (cursor < ordered.size()
          && (openingBoundary ? ordered.get(cursor).trade().at().isBefore(mark.at())
              : !ordered.get(cursor).trade().at().isAfter(mark.at()))) {
        var fill = ordered.get(cursor++);
        cash = cash.add(fill.cashDelta());
        // 现金只取USDT账户分录；base费用由实际成交币种改变净持仓，不能把BTC金额加进USDT现金。
        BigDecimal baseFee =
            fill.trade().symbol().split("-")[0].equals(fill.trade().feeCurrency())
                ? fill.trade().fee()
                : BigDecimal.ZERO;
        quantity =
            quantity
                .add(
                    "BUY".equals(fill.trade().side())
                        ? fill.trade().quantity()
                        : fill.trade().quantity().negate())
                .subtract(baseFee);
        if (discount != null && discount.plan().assetCurrency().equals(fill.trade().feeCurrency()))
          assetBalance = assetBalance.subtract(fill.trade().fee());
      }
      if (quantity.signum() < 0 || assetBalance.signum() < 0)
        return unavailable("OPENING_BASIS_UNAVAILABLE", clock, cadence, ledgerAsOf);
      BigDecimal equity = cash.add(quantity.multiply(price));
      DiscountAssetPriceSnapshot.Point point = null;
      BigDecimal assetValue = null;
      if (discount != null) {
        try {
          point = discount.prices().priceAt(mark.at());
        } catch (IllegalArgumentException absent) {
          if (!"FEE_DISCOUNT_PRICE_UNAVAILABLE".equals(absent.getMessage())) throw absent;
          return unavailable("DISCOUNT_PRICE_UNAVAILABLE", clock, cadence, ledgerAsOf);
        }
        assetValue = assetBalance.multiply(point.quotePerAsset()).setScale(8, RoundingMode.HALF_UP);
        equity = equity.add(assetValue);
      }
      if (from == null || (!mark.at().isBefore(from) && !mark.at().isAfter(to)))
        samples.add(
            new Sample(
                mark.decisionId(),
                mark.at(),
                price,
                cash,
                quantity,
                equity,
                mark.markType(),
                discount == null ? null : discount.plan().assetCurrency(),
                discount == null ? null : assetBalance,
                assetValue,
                point == null ? null : point.quotePerAsset(),
                point == null ? null : point.observedAt(),
                point == null ? null : point.visibleAt(),
                point == null ? null : point.sourceSha256(),
                Instant.parse(mark.closeTime()),
                Instant.parse(mark.availableAt())));
    }
    if (samples.size() > maximumSamples)
      return unavailable("HISTORY_WINDOW_LIMITED", clock, cadence, ledgerAsOf);
    if (samples.isEmpty()) return unavailable("NO_DATA", clock, cadence, ledgerAsOf);
    var first = samples.getFirst();
    var last = samples.getLast();
    BigDecimal pnl = null, ratio = null, maxDrawdown = null, maxDrawdownRatio = null;
    if (samples.size() >= 2) {
      pnl = last.equity().subtract(first.equity());
      ratio =
          first.equity().signum() > 0 ? pnl.divide(first.equity(), MathContext.DECIMAL128) : null;
      BigDecimal peak = first.equity();
      maxDrawdown = BigDecimal.ZERO;
      maxDrawdownRatio = BigDecimal.ZERO;
      for (var sample : samples) {
        peak = peak.max(sample.equity());
        BigDecimal loss = peak.subtract(sample.equity());
        maxDrawdown = maxDrawdown.max(loss);
        if (peak.signum() > 0)
          maxDrawdownRatio = maxDrawdownRatio.max(loss.divide(peak, MathContext.DECIMAL128));
      }
      if (first.equity().signum() <= 0) maxDrawdownRatio = null;
    }
    var outcomes =
        ClosedTradeStatistics.calculate(
            ordered.stream().map(Fill::trade).toList(),
            "USDT",
            // 原统计采用左开下界；仅新观察将from同刻卖出纳入，成交时钟保持原值。
            observation && from != null && first.eventAt().equals(from) ? from.minusNanos(1) : first.eventAt(),
            last.eventAt(),
            true);
    if (observation && from != null && first.eventAt().equals(from))
      outcomes = new ClosedTradeStatistics.Result(outcomes.status(), outcomes.winRate(), outcomes.winningCount(),
          outcomes.losingCount(), outcomes.flatCount(), outcomes.netProfit(), outcomes.netLoss(), outcomes.profitLossRatio(),
          outcomes.method(), from, last.eventAt());
    var sharpe =
        PerformanceStatistics.sharpe(
            samples.stream()
                .map(s -> new PerformanceStatistics.Point(s.eventAt(), s.equity(), funding))
                .toList(),
            cadence,
            maximumSamples);
    long after = ordered.stream().filter(f -> f.trade().at().isAfter(last.eventAt())).count();
    return new View(
        id,
        samples.size() < 2 ? "INSUFFICIENT_SAMPLES" : "AVAILABLE",
        clock,
        "USDT",
        first.eventAt(),
        last.eventAt(),
        ledgerAsOf,
        first.equity(),
        last.equity(),
        pnl,
        ratio,
        maxDrawdown,
        maxDrawdownRatio,
        sharpe,
        outcomes,
        after,
        samples,
        funding);
  }

  private static View unavailable(String status, String clock, long cadence, Instant ledgerAsOf) {
    return new View(
        null,
        status,
        clock,
        "USDT",
        null,
        null,
        ledgerAsOf,
        null,
        null,
        null,
        null,
        null,
        null,
        PerformanceStatistics.unavailable(status, cadence, 0),
        ClosedTradeStatistics.unavailable(status),
        0,
        List.of());
  }

  record Mark(
      String decisionId,
      Instant at,
      String price,
      String symbol,
      String availableAt,
      String closeTime,
      String markType) {
    Mark(
        String decisionId,
        Instant at,
        String price,
        String symbol,
        String availableAt,
        String closeTime) {
      this(decisionId, at, price, symbol, availableAt, closeTime, "SIGNAL_CLOSE");
    }
  }

  private DiscountContext discountContext(StrategySimAnalysisQuery.Run run) {
    JsonNode spec = read(run.configSnapshotJson()).path("feeExecutionSpec");
    if (!"DISCOUNT_ASSET_IF_FUNDED".equals(spec.path("feePaymentPolicy").asText())) return null;
    if (discountPrices == null || assetFunding == null)
      throw new IllegalStateException("SIM_DISCOUNT_ASSET_SOURCE_DISABLED");
    var prices =
        discountPrices.read(
            Long.parseLong(spec.path("feeSourceOwnerUserId").asText()),
            UUID.fromString(spec.path("discountPriceSnapshotId").asText()),
            spec.path("discountPriceSnapshotSha256").asText());
    var plan =
        assetFunding
            .findPlan(run.canonicalAccountId(), run.paperRunId())
            .orElseThrow(() -> new IllegalStateException("SIM_DISCOUNT_FUNDING_MISSING"));
    var point = prices.priceAt(plan.asOf());
    if (!prices.datasetId().toString().equals(spec.path("discountPriceDatasetId").asText())
        || !prices.assetCurrency().equals(plan.assetCurrency())
        || !point.sourceSha256().equals(plan.sourceSha256())
        || !point.sourceJson().equals(plan.sourceJson())
        || new BigDecimal(spec.path("discountAssetBudgetFraction").asText())
                .compareTo(plan.assetBudgetFraction())
            != 0) throw new IllegalStateException("SIM_DISCOUNT_FUNDING_IDENTITY_DRIFT");
    return new DiscountContext(prices, plan, spec);
  }

  private JsonNode read(String raw) {
    if (raw == null || raw.length() > 65_536)
      throw new IllegalStateException("SIM_FEE_TERMS_INVALID");
    try {
      return mapper.readTree(raw);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("SIM_FEE_TERMS_INVALID", failure);
    }
  }

  private record DiscountContext(
      DiscountAssetPriceSnapshot prices, SimAssetFundingPlan plan, JsonNode spec) {}

  private record Funding(BigDecimal amount, Instant at, String currency, String runId) {}

  record Fill(
      ClosedTradeStatistics.Trade trade,
      BigDecimal cashDelta,
      long entries,
      BigDecimal balance,
      boolean knownEntries,
      boolean scopeValid,
      BigDecimal feeBalance,
      String feeModelJson,
      String feeChargeJson) {
    Fill(
        ClosedTradeStatistics.Trade trade,
        BigDecimal cashDelta,
        long entries,
        BigDecimal balance,
        boolean knownEntries,
        boolean scopeValid,
        BigDecimal feeBalance) {
      this(trade, cashDelta, entries, balance, knownEntries, scopeValid, feeBalance, null, null);
    }

    /** 旧单币种夹具仍适用；正式查询对原币费用的另一币种单独核对双边守恒。 */
    Fill(
        ClosedTradeStatistics.Trade trade,
        BigDecimal cashDelta,
        long entries,
        BigDecimal balance,
        boolean knownEntries,
        boolean scopeValid) {
      this(
          trade,
          cashDelta,
          entries,
          balance,
          knownEntries,
          scopeValid,
          BigDecimal.ZERO,
          null,
          null);
    }
  }

  public record Sample(
      String decisionId,
      Instant eventAt,
      BigDecimal markPrice,
      BigDecimal cash,
      BigDecimal positionQuantity,
      BigDecimal equity,
      String markType,
      String discountAssetCurrency,
      BigDecimal discountAssetBalance,
      BigDecimal discountAssetQuoteValue,
      BigDecimal discountConversionPrice,
      Instant discountPriceObservedAt,
      Instant discountPriceVisibleAt,
      String discountPriceSourceSha256,
      Instant markObservedAt,
      Instant markVisibleAt) {
    /** 原构造器供已有统计测试使用；缺失原价时点不能冒充完整价格依据。 */
    public Sample(
        String decisionId,
        Instant eventAt,
        BigDecimal markPrice,
        BigDecimal cash,
        BigDecimal positionQuantity,
        BigDecimal equity,
        String markType,
        String discountAssetCurrency,
        BigDecimal discountAssetBalance,
        BigDecimal discountAssetQuoteValue,
        BigDecimal discountConversionPrice,
        Instant discountPriceObservedAt,
        Instant discountPriceVisibleAt,
        String discountPriceSourceSha256) {
      this(
          decisionId,
          eventAt,
          markPrice,
          cash,
          positionQuantity,
          equity,
          markType,
          discountAssetCurrency,
          discountAssetBalance,
          discountAssetQuoteValue,
          discountConversionPrice,
          discountPriceObservedAt,
          discountPriceVisibleAt,
          discountPriceSourceSha256,
          null,
          null);
    }

    /** 新Paper沿用持久化decision、Trade和Ledger推导；不在读取时新增另一份账务或历史金额。 */
    @JsonProperty("valuationCurrency")
    public String valuationCurrency() {
      return "USDT";
    }

    @JsonProperty("valuationTime")
    public Instant valuationTime() {
      return eventAt;
    }

    @JsonProperty("valuationMethod")
    public String valuationMethod() {
      return "CANONICAL_SIM_LEDGER_AND_FROZEN_MARK_V1";
    }

    @JsonProperty("valuationSource")
    public String valuationSource() {
      return "strategy_sim_decisions:" + decisionId;
    }

    @JsonProperty("valuationStatus")
    public String valuationStatus() {
      return markObservedAt != null && markVisibleAt != null ? "VERIFIED" : "PARTIAL";
    }

    public Sample(
        String decisionId,
        Instant eventAt,
        BigDecimal markPrice,
        BigDecimal cash,
        BigDecimal positionQuantity,
        BigDecimal equity,
        String markType) {
      this(
          decisionId,
          eventAt,
          markPrice,
          cash,
          positionQuantity,
          equity,
          markType,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }
  }

  public record View(
      String paperRunId,
      String status,
      String timeBasis,
      String quoteCurrency,
      Instant coverageStart,
      Instant coverageEnd,
      Instant ledgerAsOf,
      BigDecimal initialEquity,
      BigDecimal finalEquity,
      BigDecimal pnl,
      BigDecimal returnRatio,
      BigDecimal maxDrawdown,
      BigDecimal maxDrawdownRatio,
      PerformanceStatistics.Sharpe sharpe,
      ClosedTradeStatistics.Result outcomes,
      long tradesAfterCoverage,
      List<Sample> samples,
      BigDecimal fundingBudget) {
    /** 保留原只读DTO构造合同；缺失原预算的旧调用不能授予新快照写入资格。 */
    public View(
        String paperRunId,
        String status,
        String timeBasis,
        String quoteCurrency,
        Instant coverageStart,
        Instant coverageEnd,
        Instant ledgerAsOf,
        BigDecimal initialEquity,
        BigDecimal finalEquity,
        BigDecimal pnl,
        BigDecimal returnRatio,
        BigDecimal maxDrawdown,
        BigDecimal maxDrawdownRatio,
        PerformanceStatistics.Sharpe sharpe,
        ClosedTradeStatistics.Result outcomes,
        long tradesAfterCoverage,
        List<Sample> samples) {
      this(
          paperRunId,
          status,
          timeBasis,
          quoteCurrency,
          coverageStart,
          coverageEnd,
          ledgerAsOf,
          initialEquity,
          finalEquity,
          pnl,
          returnRatio,
          maxDrawdown,
          maxDrawdownRatio,
          sharpe,
          outcomes,
          tradesAfterCoverage,
          samples,
          null);
    }

    /** 原quoteCurrency保持兼容；当前正式SIM计价由已验证USDT资金链决定。 */
    @JsonProperty("valuationCurrency")
    public String valuationCurrency() {
      return quoteCurrency;
    }

    private View forRun(String id) {
      return new View(
          id,
          status,
          timeBasis,
          quoteCurrency,
          coverageStart,
          coverageEnd,
          ledgerAsOf,
          initialEquity,
          finalEquity,
          pnl,
          returnRatio,
          maxDrawdown,
          maxDrawdownRatio,
          sharpe,
          outcomes,
          tradesAfterCoverage,
          samples,
          fundingBudget);
    }
  }
}
