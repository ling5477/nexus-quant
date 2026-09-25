package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.research.application.paper.command.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.strategy.domain.PublicReplayAssumptionIdentity;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 明确开启后才可运行的单版本、单账户 SIM 回放；经济意图仍交给 canonical Risk/Order。 */
@Service
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class StrategySimRunService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PaperTradingRunService paperRuns;
    private final BacktestPublishRecordRepository publishes;
    private final BacktestRunRepository backtests;
    private final SimCashFundingPort funding;
    private final StrategySimDecisionRepository decisions;
    private final StrategyManualTriggerService trigger;
    private final StrategyDefinitionRepository definitions;
    private final StrategyExecutionGateway execution;
    private final StrategyRunRepository strategyRuns;
    private final StrategyRunExecutionRepository strategyWork;

    public StrategySimRunService(JdbcTemplate jdbc, ObjectMapper mapper, PaperTradingRunService paperRuns,
            BacktestPublishRecordRepository publishes, BacktestRunRepository backtests,
            SimCashFundingPort funding, StrategySimDecisionRepository decisions,
            StrategyManualTriggerService trigger, StrategyDefinitionRepository definitions,
            StrategyExecutionGateway execution, StrategyRunRepository strategyRuns,
            StrategyRunExecutionRepository strategyWork) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.mapper = Objects.requireNonNull(mapper);
        this.paperRuns = Objects.requireNonNull(paperRuns);
        this.publishes = Objects.requireNonNull(publishes);
        this.backtests = Objects.requireNonNull(backtests);
        this.funding = Objects.requireNonNull(funding);
        this.decisions = Objects.requireNonNull(decisions);
        this.trigger = Objects.requireNonNull(trigger);
        this.definitions = Objects.requireNonNull(definitions);
        this.execution = Objects.requireNonNull(execution);
        this.strategyRuns = Objects.requireNonNull(strategyRuns);
        this.strategyWork = Objects.requireNonNull(strategyWork);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public RunView create(String publishId, BigDecimal budget, String createdBy) {
        if (budget == null || budget.signum() <= 0 || budget.scale() > 8
                || budget.compareTo(new BigDecimal("100000")) > 0) {
            throw new IllegalArgumentException("INVALID_SIM_BUDGET");
        }
        BacktestPublishRecord publish = publishes.findByPublishRecordId(publishId)
                .orElseThrow(() -> new IllegalArgumentException("PUBLISH_NOT_FOUND"));
        BacktestRun backtest = boundBacktest(publish);
        JsonNode summary = read(backtest.summaryJson());
        List<HistoricalBar> bars = validatedBars(backtest, summary);
        SpotSmaTargetStrategy strategy = SpotSmaTargetStrategy.fromSnapshot(
                backtest.strategyVersionSnapshotJson(), mapper);
        // 在资金入账前验证整个冻结输入，避免运行中途才发现缺 bar 或迟到数据。
        strategy.evaluate(bars);
        if (!strategy.strategyVersionId().equals(publish.strategyVersionId())
                || !sameFrozenStrategy(strategy, SpotSmaTargetStrategy.fromSnapshot(
                        publish.versionSnapshotJson(), mapper))
                || !strategy.checksum().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("PUBLISH_VERSION_SNAPSHOT_MISMATCH");
        }
        SpotTargetSizer.Rules rules = rules(summary.path("costAndRuleAssumptions"));
        ObjectNode config = mapper.createObjectNode();
        config.put("budget", budget.toPlainString());
        config.set("costAndRuleAssumptions", summary.path("costAndRuleAssumptions"));
        if ("public-capture".equals(summary.path("datasetProvider").asText())) {
            String costSha = PublicReplayAssumptionIdentity.sha256(
                    summary.path("costAndRuleAssumptions"));
            if (!costSha.equals(summary.path("costAndRuleSha256").asText())) {
                throw new IllegalStateException("SIM_PUBLIC_COST_IDENTITY_INVALID");
            }
            config.put("costAndRuleSha256", costSha);
        }
        config.put("barContentSha256", summary.path("barContentSha256").asText());
        PaperTradingRun run = paperRuns.create(new PaperTradingRunCreateCommand(
                publishId, "SIM", "OKX", "SPOT", "BTC-USDT", bars.getFirst().interval().wireValue(),
                config.toString(), createdBy));
        Long accountId = jdbc.queryForObject("""
                INSERT INTO accounts(account_code,venue,status)
                VALUES (?,'PAPER','ACTIVE') RETURNING account_id
                """, Long.class, "STRATEGY-SIM-" + run.paperRunId().substring(4));
        if (accountId == null || jdbc.update("""
                UPDATE paper_trading_runs SET canonical_account_id=?
                WHERE paper_run_id=? AND canonical_account_id IS NULL AND status='CREATED'
                """, accountId, run.paperRunId()) != 1) {
            throw new IllegalStateException("SIM_ACCOUNT_BINDING_FAILED");
        }
        funding.fundOnce(accountId, run.paperRunId(), budget, "sim-fund-" + run.paperRunId().substring(4, 20));
        // durable admission 要求执行定义与隔离账户一致，冻结版本仍由 run 快照固定。
        definitions.insert(simDefinition(run, accountId));
        return new RunView(run.paperRunId(), publishId, strategy.strategyVersionId(),
                summary.path("barContentSha256").asText(), accountId, budget, rules);
    }

    public StrategySimDecisionRepository.DecisionView advance(String paperRunId) {
        List<RunLock> locks = jdbc.query("""
                SELECT status,trade_env,exchange_code,market_type,symbol,canonical_account_id
                FROM paper_trading_runs WHERE paper_run_id=?
                """, (rs, row) -> new RunLock(rs.getString("status"), rs.getString("trade_env"),
                rs.getString("exchange_code"), rs.getString("market_type"), rs.getString("symbol"),
                rs.getObject("canonical_account_id", Long.class)), paperRunId);
        if (locks.size() != 1) throw new IllegalArgumentException("SIM_RUN_NOT_FOUND");
        RunLock lock = locks.getFirst();
        if (!"RUNNING".equals(lock.status()) || !"SIM".equals(lock.tradeEnv())
                || !"OKX".equals(lock.exchangeCode()) || !"SPOT".equals(lock.marketType())
                || !"BTC-USDT".equals(lock.symbol()) || lock.accountId() == null) {
            throw new IllegalStateException("SIM_RUN_NOT_ACTIVE_OR_UNBOUND");
        }
        return decisions.withAccountMutex(lock.accountId(), () -> advanceLocked(paperRunId, lock));
    }

    private StrategySimDecisionRepository.DecisionView advanceLocked(String paperRunId, RunLock lock) {
        PaperTradingRun run = paperRuns.getById(paperRunId);
        if (run.status() != com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus.RUNNING) {
            throw new IllegalStateException("SIM_RUN_NOT_ACTIVE_OR_UNBOUND");
        }
        BacktestPublishRecord publish = publishes.findByPublishRecordId(run.publishId()).orElseThrow();
        BacktestRun backtest = boundBacktest(publish);
        if (!read(run.datasetSnapshotJson()).equals(read(backtest.datasetSnapshotJson()))) {
            throw new IllegalStateException("SIM_DATASET_IDENTITY_DRIFT");
        }
        JsonNode summary = read(backtest.summaryJson());
        List<HistoricalBar> bars = validatedBars(backtest, summary);
        SpotSmaTargetStrategy strategy = SpotSmaTargetStrategy.fromSnapshot(run.strategyVersionSnapshotJson(), mapper);
        strategy.evaluate(bars);
        if (!strategy.strategyVersionId().equals(run.strategyVersionId())
                || !sameFrozenStrategy(strategy, SpotSmaTargetStrategy.fromSnapshot(
                        backtest.strategyVersionSnapshotJson(), mapper))) {
            throw new IllegalStateException("SIM_STRATEGY_VERSION_DRIFT");
        }
        JsonNode runConfig = read(run.configSnapshotJson());
        if (!summary.path("barContentSha256").asText().equals(
                runConfig.path("barContentSha256").asText())) {
            throw new IllegalStateException("SIM_INPUT_IDENTITY_DRIFT");
        }
        SpotTargetSizer.Rules rules = rules(runConfig.path("costAndRuleAssumptions"));
        if (!summary.path("costAndRuleAssumptions").equals(
                runConfig.path("costAndRuleAssumptions"))) {
            throw new IllegalStateException("SIM_COST_ASSUMPTION_DRIFT");
        }
        if ("public-capture".equals(summary.path("datasetProvider").asText())
                && (!summary.path("costAndRuleSha256").asText().equals(
                        runConfig.path("costAndRuleSha256").asText())
                || !summary.path("costAndRuleSha256").asText().equals(
                        PublicReplayAssumptionIdentity.sha256(runConfig.path("costAndRuleAssumptions"))))) {
            throw new IllegalStateException("SIM_PUBLIC_COST_IDENTITY_DRIFT");
        }
        for (int index = 0; index < bars.size(); index++) {
            HistoricalBar signalBar = bars.get(index);
            var existing = decisions.findByWindow(paperRunId, signalBar.openTime());
            if (existing.isPresent()) {
                if ("DECIDING".equals(existing.get().reason())) {
                    return finishAcceptedDecision(existing.get(), run, lock.accountId());
                }
                continue;
            }
            SpotSmaTargetStrategy.Decision signal = strategy.evaluate(bars.subList(0, index + 1));
            HistoricalBar executionBar = null;
            for (int later = index + 1; later < bars.size(); later++) {
                if (bars.get(later).openTime().isAfter(signalBar.closeTime())
                        && signal.availableAt() != null
                        && bars.get(later).openTime().isAfter(signal.availableAt())) {
                    executionBar = bars.get(later);
                    break;
                }
            }
            String decisionId = sha256(paperRunId + ":" + strategy.strategyVersionId()
                    + ":" + signalBar.openTime());
            SpotBarIdentity.Snapshot input = SpotBarIdentity.capture(bars.subList(0, index + 1), mapper);
            SpotBarIdentity.Snapshot execution = executionBar == null ? null
                    : SpotBarIdentity.capture(List.of(executionBar), mapper);
            ObjectNode snapshot = mapper.createObjectNode();
            snapshot.set("signalBars", read(input.canonicalJson()));
            if (execution == null) snapshot.putNull("executionBar");
            else snapshot.set("executionBar", read(execution.canonicalJson()).get(0));
            String status = "NO_SIGNAL";
            String reason = signal.reason();
            SpotTargetSizer.Result sizing = null;
            if (!"INSUFFICIENT_HISTORY".equals(signal.reason())) {
                if (executionBar == null) {
                    status = "NOT_TRADABLE";
                    reason = "NO_LATER_TRADABLE_EVENT";
                } else if (executionBar.openTime().isAfter(signal.availableAt().plus(
                        signalBar.interval().duration().multipliedBy(2)))) {
                    status = "NOT_TRADABLE";
                    reason = "STALE_DATA";
                } else {
                    BigDecimal cash = funding.cashBalance(lock.accountId());
                    BigDecimal position = jdbc.queryForObject("""
                            SELECT COALESCE(MAX(qty),0) FROM positions
                            WHERE account_id=? AND symbol='BTC-USDT'
                            """, BigDecimal.class, lock.accountId());
                    List<Pending> pending = jdbc.query("""
                            SELECT o.side,o.qty,d.execution_price,d.fee_rate FROM orders o
                            LEFT JOIN strategy_sim_decisions d ON d.order_id=o.order_id
                            WHERE o.account_id=?
                              AND o.status NOT IN ('FILLED','REJECTED','RISK_REJECTED','CANCELLED','FAILED')
                            """, (rs, row) -> new Pending(rs.getString("side"), rs.getBigDecimal("qty"),
                            rs.getBigDecimal("execution_price"), rs.getBigDecimal("fee_rate")),
                            lock.accountId());
                    BigDecimal pendingBuy = BigDecimal.ZERO;
                    BigDecimal pendingSell = BigDecimal.ZERO;
                    BigDecimal pendingBuyCost = BigDecimal.ZERO;
                    for (Pending order : pending) {
                        if (order.executionPrice() == null || order.feeRate() == null
                                || order.executionPrice().signum() <= 0 || order.feeRate().signum() < 0) {
                            throw new IllegalStateException("SIM_PENDING_ORDER_RESERVE_UNKNOWN");
                        }
                        if ("BUY".equals(order.side())) {
                            pendingBuy = pendingBuy.add(order.quantity());
                            BigDecimal notional = order.quantity().multiply(order.executionPrice());
                            // 费用向上取整，预留额不得低于正式成交记账的费用。
                            pendingBuyCost = pendingBuyCost.add(notional).add(notional.multiply(order.feeRate())
                                    .setScale(8, RoundingMode.CEILING));
                        } else if ("SELL".equals(order.side())) pendingSell = pendingSell.add(order.quantity());
                        else throw new IllegalStateException("UNKNOWN_PENDING_SIDE");
                    }
                    sizing = SpotTargetSizer.size(signal.targetExposure(),
                            new SpotTargetSizer.State(cash, position, pendingBuy, pendingSell,
                                    pendingBuyCost, executionBar.openPrice()), rules);
                    status = sizing.executable() ? "ACCEPTED" : "NOT_TRADABLE";
                    reason = sizing.reason();
                }
            }
            BigDecimal fillPrice = sizing == null || !sizing.executable()
                    ? executionBar == null ? null : executionBar.openPrice() : sizing.fillPrice();
            decisions.insert(new StrategySimDecisionRepository.DecisionWrite(decisionId, paperRunId,
                    lock.accountId(), strategy.strategyVersionId(), strategy.checksum(),
                    signalBar.openTime(), signal.availableAt() == null ? signalBar.availableAt() : signal.availableAt(),
                    executionBar == null ? null : executionBar.openTime(), input.sha256(),
                    execution == null ? null : execution.sha256(), snapshot.toString(),
                    signal.targetExposure(), "DECIDING", sizing == null ? null : sizing.side(),
                    sizing == null ? null : sizing.quantity(), fillPrice, rules.feeRate(), rules.slippageBps()),
                    "ACCEPTED".equals(status) ? "NOT_TRADABLE" : status,
                    "ACCEPTED".equals(status) ? "DECIDING" : reason);
            if ("ACCEPTED".equals(status)) {
                return finishAcceptedDecision(decisions.findByWindow(paperRunId, signalBar.openTime()).orElseThrow(),
                        run, lock.accountId());
            }
            return decisions.findByWindow(paperRunId, signalBar.openTime()).orElseThrow();
        }
        throw new IllegalStateException("NO_NEW_SIM_DECISION");
    }

    public List<StrategySimDecisionRepository.DecisionView> decisions(String paperRunId) {
        paperRuns.getById(paperRunId);
        return decisions.listByRun(paperRunId);
    }

    private StrategySimDecisionRepository.DecisionView finishAcceptedDecision(
            StrategySimDecisionRepository.DecisionView decision, PaperTradingRun run, long accountId) {
        if (!"DECIDING".equals(decision.reason()) || decision.side() == null
                || decision.quantity() == null || decision.quantity().signum() <= 0) {
            throw new IllegalStateException("SIM_DECISION_RECOVERY_INVALID");
        }
        StrategyDefinition definition = simDefinition(run, accountId);
        String requestId = "sim-" + decision.decisionId().substring(0, 60);
        var result = trigger.trigger(new StrategyManualTriggerRequest(definition.strategyId(),
                requestId, "BTC-USDT", OrderSide.valueOf(decision.side()),
                OrderType.MARKET, decision.quantity(), null,
                "sim-" + decision.decisionId().substring(0, 32), null, definition));
        String strategyRunId = result.strategyRunId();
        String orderId = result.orderId();
        OrderStatus orderStatus = result.orderStatus();
        if (orderId == null && strategyRunId != null) {
            var saved = jdbc.query("""
                    SELECT order_id,status FROM orders WHERE strategy_run_id=? AND account_id=?
                    """, (rs, row) -> new SavedOrder(rs.getString(1),
                    OrderStatus.valueOf(rs.getString(2))), strategyRunId, accountId);
            if (saved.size() > 1) throw new IllegalStateException("SIM_DUPLICATE_CANONICAL_ORDER");
            if (saved.isEmpty()) {
                var admitted = strategyRuns.findByStrategyRunId(strategyRunId).orElseThrow();
                var work = strategyWork.findWork(strategyRunId).orElseThrow();
                var resumed = execution.execute(work.intent(admitted));
                orderId = resumed.orderId();
                orderStatus = resumed.status();
            } else {
                orderId = saved.getFirst().orderId();
                orderStatus = saved.getFirst().status();
            }
        }
        if (orderId == null || orderStatus == null) {
            throw new IllegalStateException("SIM_ORDER_ADMISSION_INCOMPLETE");
        }
        String status = orderStatus == OrderStatus.RISK_REJECTED || orderStatus == OrderStatus.REJECTED
                ? "RISK_REJECTED" : "ACCEPTED";
        if ("ACCEPTED".equals(status) && orderStatus != OrderStatus.ACCEPTED
                && orderStatus != OrderStatus.ACKED && orderStatus != OrderStatus.FILLED) {
            throw new IllegalStateException("SIM_ORDER_NOT_ACCEPTED");
        }
        decisions.complete(decision.decisionId(), status,
                "RISK_REJECTED".equals(status) ? orderStatus.name() : "EXECUTABLE",
                strategyRunId, orderId);
        return decisions.findByWindow(decision.paperRunId(), decision.signalOpenTime()).orElseThrow();
    }

    private static StrategyDefinition simDefinition(PaperTradingRun run, long accountId) {
        String identity = "sim-sim-" + run.paperRunId();
        return new StrategyDefinition(identity, identity, identity,
                SpotSmaTargetStrategy.EXECUTABLE, "PAPER", accountId, "SIM", true,
                run.strategyVersionSnapshotJson(), 1, run.createdAt(), run.createdAt());
    }

    @Transactional(readOnly = true, timeout = 10)
    public FactsView facts(String paperRunId) {
        PaperTradingRun run = paperRuns.getById(paperRunId);
        Long accountId = jdbc.queryForObject("""
                SELECT canonical_account_id FROM paper_trading_runs WHERE paper_run_id=?
                """, Long.class, paperRunId);
        if (accountId == null) throw new IllegalStateException("LEGACY_PAPER_RUN_HAS_NO_CANONICAL_FACTS");
        BacktestPublishRecord publish = publishes.findByPublishRecordId(run.publishId()).orElseThrow();
        BacktestRun backtest = boundBacktest(publish);
        if (!read(run.datasetSnapshotJson()).equals(read(backtest.datasetSnapshotJson()))) {
            throw new IllegalStateException("SIM_DATASET_IDENTITY_DRIFT");
        }
        JsonNode summary = read(backtest.summaryJson());
        if (!summary.path("barContentSha256").asText().equals(
                read(run.configSnapshotJson()).path("barContentSha256").asText())) {
            throw new IllegalStateException("SIM_INPUT_IDENTITY_DRIFT");
        }
        List<HistoricalBar> bars = validatedBars(backtest, summary);
        BigDecimal cash = funding.cashBalance(accountId);
        BigDecimal quantity = jdbc.queryForObject("""
                SELECT COALESCE(MAX(qty),0) FROM positions
                WHERE account_id=? AND symbol='BTC-USDT'
                """, BigDecimal.class, accountId);
        // 增量回放只按已处理决策可见的事件估值，不泄露冻结数据集后续 bar 的价格。
        Instant latestEvent = null;
        boolean latestIsExecution = false;
        BigDecimal mark = BigDecimal.ZERO;
        for (var decision : decisions.listByRun(paperRunId)) {
            Instant eventTime = decision.executionOpenTime() == null
                    ? decision.signalAvailableAt() : decision.executionOpenTime();
            boolean executionEvent = decision.executionOpenTime() != null;
            if (latestEvent == null || eventTime.isAfter(latestEvent)
                    || (eventTime.equals(latestEvent) && executionEvent && !latestIsExecution)) {
                Instant barOpenTime = decision.executionOpenTime() == null
                        ? decision.signalOpenTime() : decision.executionOpenTime();
                HistoricalBar eventBar = bars.stream()
                        .filter(bar -> bar.openTime().equals(barOpenTime)).findFirst().orElseThrow();
                mark = decision.executionOpenTime() == null ? eventBar.closePrice() : eventBar.openPrice();
                latestEvent = eventTime;
                latestIsExecution = executionEvent;
            }
        }
        BigDecimal budget = decimal(read(run.configSnapshotJson()), "budget");
        BigDecimal equity = cash.add(quantity.multiply(mark));
        return new FactsView(paperRunId, accountId, run.publishId(), run.strategyVersionId(),
                summary.path("barContentSha256").asText(), budget, cash, quantity, mark,
                equity, equity.subtract(budget),
                jdbc.queryForList("""
                        SELECT order_id,strategy_run_id,client_order_id,side,type,status,qty,price,created_at
                        FROM orders WHERE account_id=? ORDER BY created_at,order_id LIMIT 500
                        """, accountId),
                jdbc.queryForList("""
                        SELECT trade_id,order_id,price,qty,fee,fee_currency,ts
                        FROM trades WHERE account_id=? ORDER BY ts,trade_id LIMIT 500
                        """, accountId),
                jdbc.queryForList("""
                        SELECT entry_id,currency,delta,ref_type,ref_id,idempotency_key,ts
                        FROM ledger_entries WHERE account_id=? ORDER BY ts,entry_id LIMIT 2000
                        """, accountId));
    }

    private BacktestRun boundBacktest(BacktestPublishRecord publish) {
        BacktestRun backtest = backtests.findByBacktestRunId(publish.backtestRunId()).orElseThrow();
        if (publish.publishStatus() != PublishStatus.SUCCEEDED
                || backtest.status() != BacktestRunStatus.SUCCEEDED
                || publish.strategyVersionId() == null
                || !publish.strategyVersionId().equals(backtest.strategyVersionId())) {
            throw new IllegalStateException("BACKTEST_PUBLISH_VERSION_MISMATCH");
        }
        return backtest;
    }

    private List<HistoricalBar> validatedBars(BacktestRun backtest, JsonNode summary) {
        JsonNode stored = summary.path("consumedBars");
        if (!stored.isArray() || stored.isEmpty() || stored.size() > 500) {
            throw new IllegalStateException("BACKTEST_INPUT_IDENTITY_INVALID");
        }
        List<HistoricalBar> bars = new ArrayList<>(stored.size());
        for (JsonNode bar : stored) {
            bars.add(new HistoricalBar(bar.path("exchangeCode").asText(), bar.path("marketType").asText(),
                    bar.path("symbol").asText(), BarInterval.fromWireValue(bar.path("interval").asText()),
                    Instant.parse(bar.path("openTime").asText()), Instant.parse(bar.path("closeTime").asText()),
                    decimal(bar, "openPrice"), decimal(bar, "highPrice"), decimal(bar, "lowPrice"),
                    decimal(bar, "closePrice"), decimal(bar, "volume"),
                    bar.path("quoteVolume").isNull() ? null : decimal(bar, "quoteVolume"),
                    bar.path("tradeCount").isNull() ? null : bar.path("tradeCount").asLong(),
                    bar.path("qualityStatus").asText(), bar.path("rawPayloadJson").asText(),
                    Instant.parse(bar.path("availableAt").asText())));
        }
        if (!summary.path("barContentSha256").asText().equals(
                SpotBarIdentity.capture(bars, mapper).sha256())
                || !"OKX".equals(summary.path("exchangeCode").asText())
                || !"BTC-USDT".equals(summary.path("symbol").asText())
                || !"OKX".equals(bars.getFirst().exchangeCode())
                || !"BTC-USDT".equals(bars.getFirst().symbol())
                || bars.stream().anyMatch(bar -> !"OKX".equals(bar.exchangeCode())
                        || !"SPOT".equals(bar.marketType()) || !"BTC-USDT".equals(bar.symbol()))
                || !backtest.strategyVersionId().equals(summary.path("strategyVersionId").asText())) {
            throw new IllegalStateException("SIM_SCOPE_OR_VERSION_INVALID");
        }
        return List.copyOf(bars);
    }

    private static boolean sameFrozenStrategy(SpotSmaTargetStrategy left,
                                               SpotSmaTargetStrategy right) {
        return left.strategyVersionId().equals(right.strategyVersionId())
                && left.checksum().equals(right.checksum())
                && left.window() == right.window()
                && left.investedExposure().compareTo(right.investedExposure()) == 0;
    }

    private SpotTargetSizer.Rules rules(JsonNode node) {
        return new SpotTargetSizer.Rules(decimal(node, "quantityStep"), decimal(node, "priceTick"),
                decimal(node, "minimumQuantity"), decimal(node, "minimumNotional"),
                decimal(node, "feeRate"), decimal(node, "slippageBps"));
    }

    private static BigDecimal decimal(JsonNode node, String name) {
        if (node.path(name).isMissingNode() || node.path(name).isNull())
            throw new IllegalArgumentException("MISSING_SIM_ASSUMPTION_" + name);
        return new BigDecimal(node.path(name).asText());
    }

    private JsonNode read(String json) {
        try { return mapper.readTree(json); }
        catch (Exception ex) { throw new IllegalArgumentException("INVALID_FROZEN_JSON", ex); }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record RunLock(String status, String tradeEnv, String exchangeCode,
                           String marketType, String symbol, Long accountId) { }
    private record Pending(String side, BigDecimal quantity, BigDecimal executionPrice,
                           BigDecimal feeRate) { }
    private record SavedOrder(String orderId, OrderStatus status) { }
    public record RunView(String paperRunId, String publishId, String strategyVersionId,
                          String barContentSha256, long canonicalAccountId, BigDecimal budget,
                          SpotTargetSizer.Rules rules) { }
    public record FactsView(String paperRunId, long canonicalAccountId, String publishId,
                            String strategyVersionId, String inputSha256, BigDecimal initialBudget,
                            BigDecimal cash, BigDecimal positionQuantity, BigDecimal markPrice,
                            BigDecimal equity, BigDecimal pnl, List<Map<String, Object>> orders,
                            List<Map<String, Object>> trades, List<Map<String, Object>> ledgerEntries) { }
}
