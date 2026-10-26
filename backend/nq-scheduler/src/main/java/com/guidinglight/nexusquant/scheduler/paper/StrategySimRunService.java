package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingPort;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import com.guidinglight.nexusquant.marketdata.application.service.DiscountAssetPriceService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.command.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.strategy.domain.PublicReplayAssumptionIdentity;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeExecutionSpec;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Charge;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private ResearchFeeSourcePort feeSource = ResearchFeeSourcePort.disabled();
    private SimAssetFundingPort assetFunding;
    private DiscountAssetPriceService discountPrices;

    /** 优惠资金和价格能力缺装配时明确拒绝依赖路径，不能用单币预算或常数价格代替。 */
    @Autowired
    private void bindDiscountAssets(
            ObjectProvider<SimAssetFundingPort> fundingProvider,
            ObjectProvider<DiscountAssetPriceService> priceProvider) {
        assetFunding = fundingProvider.getIfAvailable();
        discountPrices = priceProvider.getIfAvailable();
    }

    /** 正式容器注入来源复核；独立构造只接受原研究假设，不能接受自带私有快照。 */
    @Autowired
    private void bindFeeSource(ObjectProvider<ResearchFeeSourcePort> provider) {
        feeSource = provider.getIfAvailable(ResearchFeeSourcePort::disabled);
    }

    private final StrategyDefinitionRepository definitions;
    private final StrategyExecutionGateway execution;
    private final StrategyRunRepository strategyRuns;
    private final StrategyRunExecutionRepository strategyWork;
    private StrategySimCancellationService cancellations;

    /** 已取消的PAPER/SIM执行必须先验证零成交终态，才能让下一窗口释放预留并重新准入。 */
    @Autowired(required = false)
    private void bindCancellations(StrategySimCancellationService service) {
        this.cancellations = service;
    }

    private final ContinuousSimRepository continuous;

    public StrategySimRunService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PaperTradingRunService paperRuns,
            BacktestPublishRecordRepository publishes,
            BacktestRunRepository backtests,
            SimCashFundingPort funding,
            StrategySimDecisionRepository decisions,
            StrategyManualTriggerService trigger,
            StrategyDefinitionRepository definitions,
            StrategyExecutionGateway execution,
            StrategyRunRepository strategyRuns,
            StrategyRunExecutionRepository strategyWork) {
        this(
                jdbc,
                mapper,
                paperRuns,
                publishes,
                backtests,
                funding,
                decisions,
                trigger,
                definitions,
                execution,
                strategyRuns,
                strategyWork,
                null);
    }

    @Autowired
    public StrategySimRunService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PaperTradingRunService paperRuns,
            BacktestPublishRecordRepository publishes,
            BacktestRunRepository backtests,
            SimCashFundingPort funding,
            StrategySimDecisionRepository decisions,
            StrategyManualTriggerService trigger,
            StrategyDefinitionRepository definitions,
            StrategyExecutionGateway execution,
            StrategyRunRepository strategyRuns,
            StrategyRunExecutionRepository strategyWork,
            ContinuousSimRepository continuous) {
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
        this.continuous = continuous;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public RunView create(String publishId, BigDecimal budget, String createdBy) {
        boolean demo = TenantContext.current().map(identity -> identity.kind() == TenantKind.DEMO).orElse(false);
        if (demo) {
            if (budget != null && budget.compareTo(new BigDecimal("10000.00000000")) != 0) {
                throw new IllegalArgumentException("DEMO_SIM_BUDGET_FIXED");
            }
            budget = new BigDecimal("10000.00000000");
        }
        if (budget == null
                || budget.signum() <= 0
                || budget.scale() > 8
                || budget.compareTo(new BigDecimal("100000")) > 0) {
            throw new IllegalArgumentException("INVALID_SIM_BUDGET");
        }
        BacktestPublishRecord publish =
                publishes
                        .findByPublishRecordId(publishId)
                        .orElseThrow(() -> new IllegalArgumentException("PUBLISH_NOT_FOUND"));
        BacktestRun backtest = boundBacktest(publish);
        JsonNode summary = read(backtest.summaryJson());
        List<HistoricalBar> bars = validatedBars(backtest, summary);
        SpotTargetStrategy strategy =
                SpotTargetStrategy.fromSnapshot(backtest.strategyVersionSnapshotJson(), mapper);
        // 在资金入账前验证整个冻结输入，避免运行中途才发现缺 bar 或迟到数据。
        strategy.validate(bars);
        if (!strategy.strategyVersionId().equals(publish.strategyVersionId())
                || !sameFrozenStrategy(
                        strategy,
                        SpotTargetStrategy.fromSnapshot(publish.versionSnapshotJson(), mapper))
                || !strategy.checksum().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("PUBLISH_VERSION_SNAPSHOT_MISMATCH");
        }
        JsonNode executionSpec = read(backtest.configSnapshotJson()).path("executionSpec");
        SpotFeeModel directionalFee = SpotFeeExecutionSpec.directional(executionSpec, mapper);
        if (demo && ("CURRENT_ACCOUNT_OBSERVATION".equals(executionSpec.path("costSource").asText())
                || directionalFee != null && directionalFee.policy() == Policy.DISCOUNT_ASSET_IF_FUNDED)) {
            throw new IllegalArgumentException("DEMO_SIM_PUBLIC_CASH_ASSUMPTIONS_REQUIRED");
        }
        SpotTargetSizer.Rules rules =
                directionalFee == null ? rules(summary.path("costAndRuleAssumptions")) : null;
        if (directionalFee != null)
            directionalRules(
                    summary.path("costAndRuleAssumptions"),
                    bars.getFirst().symbol(),
                    directionalFee);
        ObjectNode config = mapper.createObjectNode();
        config.put("budget", budget.toPlainString());
        config.set("costAndRuleAssumptions", summary.path("costAndRuleAssumptions"));
        // 完整原观察只继承已有冻结合同；方向模型没有全局feeRate，不强压成零或另一交易所费率。
        if (directionalFee != null) config.set("feeExecutionSpec", executionSpec);
        if ("public-capture".equals(summary.path("datasetProvider").asText())) {
            String costSha =
                    PublicReplayAssumptionIdentity.sha256(summary.path("costAndRuleAssumptions"));
            if (!costSha.equals(summary.path("costAndRuleSha256").asText())) {
                throw new IllegalStateException("SIM_PUBLIC_COST_IDENTITY_INVALID");
            }
            config.put("costAndRuleSha256", costSha);
        }
        config.put("barContentSha256", summary.path("barContentSha256").asText());
        DemoAccount demoAccount = demo ? lockDemoAccount(publish, backtest, bars) : null;
        PaperTradingRunCreateCommand command = new PaperTradingRunCreateCommand(
                                publishId,
                                "SIM",
                                bars.getFirst().exchangeCode(),
                                "SPOT",
                                bars.getFirst().symbol(),
                                bars.getFirst().interval().wireValue(),
                                config.toString(),
                                createdBy);
        PaperTradingRun run = demoAccount == null ? paperRuns.create(command)
                : paperRuns.createForDemo(command, new PaperTradingRunService.ReservedIdentity(demoAccount.runId()));
        Long accountId =
                demoAccount != null ? demoAccount.accountId() : jdbc.queryForObject(
                        """
                INSERT INTO accounts(account_code,venue,status)
                VALUES (?,'PAPER','ACTIVE') RETURNING account_id
                """,
                        Long.class,
                        "STRATEGY-SIM-" + run.paperRunId().substring(4));
        if (accountId == null
                || jdbc.update(
                                """
                UPDATE paper_trading_runs SET canonical_account_id=?
                WHERE paper_run_id=? AND canonical_account_id IS NULL AND status='CREATED'
                """,
                                accountId,
                                run.paperRunId())
                        != 1) {
            throw new IllegalStateException("SIM_ACCOUNT_BINDING_FAILED");
        }
        if (demoAccount != null) {
            if (jdbc.update("""
                    UPDATE tenant_sim_accounts SET bound_paper_run_id=reserved_paper_run_id
                    WHERE tenant_id=nq_current_tenant_id() AND account_id=?
                        AND reserved_paper_run_id=? AND bound_paper_run_id IS NULL
                    """, accountId, run.paperRunId()) != 1) {
                throw new IllegalStateException("DEMO_SIM_ALREADY_BOUND");
            }
        } else if (directionalFee != null && directionalFee.policy() == Policy.DISCOUNT_ASSET_IF_FUNDED) {
            DiscountAssetPriceSnapshot prices = discountSource(executionSpec);
            assetFunding()
                    .fundOnce(
                            accountId,
                            run.paperRunId(),
                            fundingPlan(executionSpec, prices, budget, bars.getFirst().openTime()),
                            "sim-fund-" + run.paperRunId().substring(4, 20));
        } else
            funding.fundOnce(
                    accountId,
                    run.paperRunId(),
                    budget,
                    "sim-fund-" + run.paperRunId().substring(4, 20));
        // durable admission 要求执行定义与隔离账户一致，冻结版本仍由 run 快照固定。
        definitions.insert(simDefinition(run, accountId));
        return new RunView(
                run.paperRunId(),
                publishId,
                strategy.strategyVersionId(),
                summary.path("barContentSha256").asText(),
                accountId,
                budget,
                rules);
    }

    /** 行锁及原注资账户锁共同保护首次绑定；失败回滚只释放绑定，不撤销已完成的注册资金。 */
    private DemoAccount lockDemoAccount(BacktestPublishRecord publish, BacktestRun backtest, List<HistoricalBar> bars) {
        var identity = TenantContext.requireCurrent();
        List<DemoAccount> accounts = jdbc.query("""
                SELECT s.account_id,s.reserved_paper_run_id,s.bound_paper_run_id
                FROM tenant_sim_accounts s
                JOIN exchange_accounts e ON e.exchange_account_id=s.exchange_account_id
                    AND e.legacy_account_id=s.account_id AND e.tenant_id=s.tenant_id
                JOIN accounts a ON a.account_id=s.account_id AND a.tenant_id=s.tenant_id
                JOIN tenant_memberships m ON m.tenant_id=s.tenant_id AND m.user_id=e.owner_user_id
                JOIN tenants t ON t.tenant_id=s.tenant_id
                JOIN users u ON u.id=m.user_id
                WHERE s.tenant_id=nq_current_tenant_id() AND e.owner_user_id=?
                    AND e.exchange_code='PAPER' AND e.trade_env='SIM' AND e.status='ACTIVE'
                    AND a.venue='PAPER' AND a.status='ACTIVE'
                    AND t.tenant_kind='DEMO' AND t.status='ACTIVE'
                    AND m.membership_kind='DEMO' AND m.membership_role='DEMO_USER' AND m.status='ACTIVE'
                    AND u.enabled=true
                    AND NOT EXISTS(SELECT 1 FROM exchange_account_credentials c WHERE c.exchange_account_id=e.exchange_account_id)
                FOR UPDATE OF s
                """, (rs, row) -> new DemoAccount(rs.getLong(1), rs.getString(2), rs.getString(3)), identity.userId());
        if (accounts.size() != 1) throw new IllegalStateException("DEMO_SIM_ACCOUNT_UNAVAILABLE");
        DemoAccount account = accounts.getFirst();
        if (account.boundRunId() != null) throw new IllegalStateException("DEMO_SIM_ALREADY_BOUND");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "nq:account-environment:" + account.accountId());
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)=2 AND count(*) FILTER(WHERE currency='USDT' AND ref_id=?
                    AND ((ref_type='SIM_FUNDING_CASH' AND delta=10000.00000000
                        AND idempotency_key=? AND direction='CREDIT')
                    OR (ref_type='SIM_FUNDING_CONTRA' AND delta=-10000.00000000
                        AND idempotency_key=? AND direction='DEBIT')))=2
                FROM ledger_entries WHERE account_id=?
                """, Boolean.class, account.runId(), account.runId()+":SIM_FUND:CASH",
                account.runId()+":SIM_FUND:CONTRA", account.accountId()))
                || !Boolean.FALSE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM orders WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM trades WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM positions WHERE account_id=?)
                    OR EXISTS(SELECT 1 FROM paper_trading_runs WHERE canonical_account_id=?)
                """, Boolean.class, account.accountId(), account.accountId(), account.accountId(), account.accountId()))
                || funding.cashBalance(account.accountId()).compareTo(new BigDecimal("10000.00000000")) != 0) {
            throw new IllegalStateException("DEMO_SIM_OPENING_FACTS_INVALID");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM backtest_publish_records p
                    JOIN backtest_runs b ON b.backtest_run_id=p.backtest_run_id
                    JOIN strategy_definitions d ON d.strategy_id=p.source_strategy_id
                    JOIN strategy_versions v ON v.strategy_version_id=p.strategy_version_id
                        AND v.strategy_code=d.strategy_code AND v.tenant_id=d.tenant_id
                    WHERE p.publish_record_id=? AND b.backtest_run_id=?
                        AND d.tenant_id=nq_current_tenant_id() AND d.account_id=?
                        AND d.trade_env='SIM' AND b.strategy_version_id=v.strategy_version_id)
                """, Boolean.class, publish.publishRecordId(), backtest.backtestRunId(), account.accountId()))) {
            throw new IllegalArgumentException("DEMO_SIM_PUBLICATION_SCOPE_INVALID");
        }
        requireDemoDataset(backtest, bars);
        return account;
    }

    private void requireDemoDataset(BacktestRun backtest, List<HistoricalBar> bars) {
        UUID dataset;
        try { dataset = UUID.fromString(read(backtest.datasetSnapshotJson()).path("datasetId").asText()); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("DEMO_SIM_APPROVED_DATASET_REQUIRED"); }
        HistoricalBar first = bars.getFirst(), last = bars.getLast();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM marketdata_datasets d
                    JOIN marketdata_dataset_publications p ON p.dataset_id=d.dataset_id
                    WHERE d.dataset_id=? AND d.visibility='SHARED_READONLY'
                        AND p.status='APPROVED' AND p.expires_at>CURRENT_TIMESTAMP
                        AND d.exchange_code=? AND d.market_type='SPOT' AND d.symbol=? AND d."interval"=?
                        AND p.permitted_from<=? AND p.permitted_until>=?)
                """, Boolean.class, dataset, first.exchangeCode(), first.symbol(), first.interval().wireValue(),
                Timestamp.from(first.openTime()), Timestamp.from(last.closeTime())))) {
            throw new IllegalArgumentException("DEMO_SIM_APPROVED_DATASET_REQUIRED");
        }
    }

    private record DemoAccount(long accountId, String runId, String boundRunId) {}

    public StrategySimDecisionRepository.DecisionView advance(String paperRunId) {
        if (continuous != null && continuous.exists(paperRunId)) {
            throw new IllegalStateException("CONTINUOUS_SIM_MANUAL_ADVANCE_REJECTED");
        }
        List<RunLock> locks =
                jdbc.query(
                        """
                SELECT status,trade_env,exchange_code,market_type,symbol,canonical_account_id
                FROM (SELECT tr.* FROM paper_trading_runs tr WHERE tr.tenant_id=nq_current_tenant_id()) paper_trading_runs WHERE paper_run_id=?
                """,
                        (rs, row) ->
                                new RunLock(
                                        rs.getString("status"),
                                        rs.getString("trade_env"),
                                        rs.getString("exchange_code"),
                                        rs.getString("market_type"),
                                        rs.getString("symbol"),
                                        rs.getObject("canonical_account_id", Long.class)),
                        paperRunId);
        if (locks.size() != 1) throw new IllegalArgumentException("SIM_RUN_NOT_FOUND");
        RunLock lock = locks.getFirst();
        if (!"RUNNING".equals(lock.status())
                || !"SIM".equals(lock.tradeEnv())
                || !replayScope(lock.exchangeCode(), lock.marketType(), lock.symbol())
                || lock.accountId() == null) {
            throw new IllegalStateException("SIM_RUN_NOT_ACTIVE_OR_UNBOUND");
        }
        return decisions.withAccountMutex(
                lock.accountId(), () -> advanceLocked(paperRunId, lock, null, null, null, null));
    }

    /** 连续模式只复用既有 canonical 决策与稳定订单身份；游标确认由调用方完成。 */
    public StrategySimDecisionRepository.DecisionView advanceContinuous(
            String paperRunId,
            List<HistoricalBar> observedBars,
            Instant afterOpenTime,
            ClosedBarMarketFeed.Rule rule,
            ClosedBarMarketFeed.Quote quote) {
        if (continuous == null
                || !continuous.exists(paperRunId)
                || "STOPPED".equals(continuous.get(paperRunId).status())) {
            throw new IllegalStateException("CONTINUOUS_SIM_NOT_RUNNING");
        }
        List<RunLock> locks =
                jdbc.query(
                        """
                SELECT status,trade_env,exchange_code,market_type,symbol,canonical_account_id
                FROM (SELECT tr.* FROM paper_trading_runs tr WHERE tr.tenant_id=nq_current_tenant_id()) paper_trading_runs WHERE paper_run_id=?
                """,
                        (rs, row) ->
                                new RunLock(
                                        rs.getString("status"),
                                        rs.getString("trade_env"),
                                        rs.getString("exchange_code"),
                                        rs.getString("market_type"),
                                        rs.getString("symbol"),
                                        rs.getObject("canonical_account_id", Long.class)),
                        paperRunId);
        if (locks.size() != 1
                || locks.getFirst().accountId() == null
                || !"RUNNING".equals(locks.getFirst().status())
                || !"SIM".equals(locks.getFirst().tradeEnv())
                || !"OKX".equals(locks.getFirst().exchangeCode())
                || !"SPOT".equals(locks.getFirst().marketType())
                || !"BTC-USDT".equals(locks.getFirst().symbol())) {
            throw new IllegalStateException("CONTINUOUS_SIM_SCOPE_INVALID");
        }
        RunLock lock = locks.getFirst();
        return decisions.withAccountMutex(
                lock.accountId(),
                () -> advanceLocked(paperRunId, lock, observedBars, afterOpenTime, rule, quote));
    }

    private StrategySimDecisionRepository.DecisionView advanceLocked(
            String paperRunId,
            RunLock lock,
            List<HistoricalBar> observedBars,
            Instant afterOpenTime,
            ClosedBarMarketFeed.Rule liveRule,
            ClosedBarMarketFeed.Quote quote) {
        PaperTradingRun run = paperRuns.getById(paperRunId);
        if (run.status() != PaperTradingRunStatus.RUNNING) {
            throw new IllegalStateException("SIM_RUN_NOT_ACTIVE_OR_UNBOUND");
        }
        BacktestPublishRecord publish =
                publishes.findByPublishRecordId(run.publishId()).orElseThrow();
        BacktestRun backtest = boundBacktest(publish);
        if (!read(run.datasetSnapshotJson()).equals(read(backtest.datasetSnapshotJson()))) {
            throw new IllegalStateException("SIM_DATASET_IDENTITY_DRIFT");
        }
        JsonNode summary = read(backtest.summaryJson());
        List<HistoricalBar> bars =
                observedBars == null ? validatedBars(backtest, summary) : observedBars;
        SpotTargetStrategy strategy =
                SpotTargetStrategy.fromSnapshot(run.strategyVersionSnapshotJson(), mapper);
        strategy.validate(bars);
        if (!strategy.strategyVersionId().equals(run.strategyVersionId())
                || !sameFrozenStrategy(
                        strategy,
                        SpotTargetStrategy.fromSnapshot(
                                backtest.strategyVersionSnapshotJson(), mapper))) {
            throw new IllegalStateException("SIM_STRATEGY_VERSION_DRIFT");
        }
        JsonNode runConfig = read(run.configSnapshotJson());
        if (observedBars == null
                && !summary.path("barContentSha256")
                        .asText()
                        .equals(runConfig.path("barContentSha256").asText())) {
            throw new IllegalStateException("SIM_INPUT_IDENTITY_DRIFT");
        }
        JsonNode frozenSpec = read(backtest.configSnapshotJson()).path("executionSpec");
        SpotFeeModel directionalFee = SpotFeeExecutionSpec.directional(frozenSpec, mapper);
        if (directionalFee != null && !frozenSpec.equals(runConfig.path("feeExecutionSpec"))) {
            throw new IllegalStateException("SIM_FEE_SOURCE_IDENTITY_DRIFT");
        }
        if (!run.exchangeCode().equals(summary.path("exchangeCode").asText())
                || !run.symbol().equals(summary.path("symbol").asText())) {
            throw new IllegalStateException("SIM_SOURCE_MARKET_IDENTITY_DRIFT");
        }
        SpotTargetSizer.Rules frozenRules =
                directionalFee == null ? rules(runConfig.path("costAndRuleAssumptions")) : null;
        SpotTargetSizer.Rules rules = frozenRules;
        SpotTargetSizer.DirectionalRules directional =
                directionalFee == null
                        ? null
                        : directionalRules(
                                runConfig.path("costAndRuleAssumptions"),
                                run.symbol(),
                                directionalFee);
        DiscountAssetPriceSnapshot discountSource =
                directionalFee != null && directionalFee.policy() == Policy.DISCOUNT_ASSET_IF_FUNDED
                        ? discountSource(frozenSpec)
                        : null;
        if (discountSource != null) {
            SimAssetFundingPlan expected =
                    fundingPlan(
                            frozenSpec,
                            discountSource,
                            decimal(runConfig, "budget"),
                            bars.getFirst().openTime());
            if (!assetFunding()
                    .findPlan(lock.accountId(), paperRunId)
                    .filter(expected::equals)
                    .isPresent())
                throw new IllegalStateException("SIM_DISCOUNT_FUNDING_IDENTITY_DRIFT");
        }
        if (observedBars != null) {
            // 当前连续公开feed仅支持原OKX合同；新增BINANCE回放不扩大连续私有/公开来源能力。
            if (directional != null)
                throw new IllegalStateException("CONTINUOUS_SIM_DIRECTIONAL_SOURCE_UNAVAILABLE");
            if (liveRule == null || !"LIVE".equals(liveRule.state())) {
                throw new IllegalStateException("VENUE_RULE_UNAVAILABLE");
            }
            rules =
                    new SpotTargetSizer.Rules(
                            liveRule.lotSize(),
                            liveRule.tickSize(),
                            liveRule.minimumSize(),
                            frozenRules.minimumNotional(),
                            frozenRules.feeRate(),
                            frozenRules.slippageBps());
        }
        if (!summary.path("costAndRuleAssumptions")
                .equals(runConfig.path("costAndRuleAssumptions"))) {
            throw new IllegalStateException("SIM_COST_ASSUMPTION_DRIFT");
        }
        if ("public-capture".equals(summary.path("datasetProvider").asText())
                && (!summary.path("costAndRuleSha256")
                                .asText()
                                .equals(runConfig.path("costAndRuleSha256").asText())
                        || !summary.path("costAndRuleSha256")
                                .asText()
                                .equals(
                                        PublicReplayAssumptionIdentity.sha256(
                                                runConfig.path("costAndRuleAssumptions"))))) {
            throw new IllegalStateException("SIM_PUBLIC_COST_IDENTITY_DRIFT");
        }
        // 连续模式一次读取当前有界 bar 窗口的 durable 决策，避免恢复旧窗口时逐 bar 查库。
        Map<Instant, StrategySimDecisionRepository.DecisionView> durableWindows =
                observedBars == null
                        ? Map.of()
                        : decisions.listByRun(paperRunId).stream()
                                .collect(
                                        Collectors.toMap(
                                                StrategySimDecisionRepository.DecisionView
                                                        ::signalOpenTime,
                                                decision -> decision));
        for (int index = 0; index < bars.size(); index++) {
            HistoricalBar signalBar = bars.get(index);
            boolean cursorConsumed =
                    afterOpenTime != null && !signalBar.openTime().isAfter(afterOpenTime);
            StrategySimDecisionRepository.DecisionView decision =
                    observedBars == null
                            ? decisions.findByWindow(paperRunId, signalBar.openTime()).orElse(null)
                            : durableWindows.get(signalBar.openTime());
            if (decision != null) {
                if ("DECIDING".equals(decision.reason())) {
                    decision = finishAcceptedDecision(decision, run, lock.accountId());
                    if (!cursorConsumed) return decision;
                }
                // 游标只记录窗口已消费；旧窗口的 canonical 执行仍须先收敛。
                settleCanonicalRun(
                        decision,
                        lock.accountId(),
                        "public-capture".equals(summary.path("datasetProvider").asText()));
                if (observedBars != null && !cursorConsumed) return decision;
                continue;
            }
            if (cursorConsumed) continue;
            SpotTargetStrategy.PositionState previousState = previousStrategyState(
                    strategy, run, bars, index, observedBars != null, backtest, summary);
            SpotTargetStrategy.Decision signal =
                    strategy.evaluate(bars.subList(0, index + 1), previousState);
            HistoricalBar executionBar = null;
            if (observedBars == null) {
                for (int later = index + 1; later < bars.size(); later++) {
                    if (bars.get(later).openTime().isAfter(signalBar.closeTime())
                            && signal.availableAt() != null
                            && bars.get(later).openTime().isAfter(signal.availableAt())) {
                        executionBar = bars.get(later);
                        break;
                    }
                }
            }
            boolean freshContinuousQuote =
                    observedBars != null
                            && quote != null
                            && signalBar.equals(bars.getLast())
                            && signal.availableAt() != null
                            && quote.observedAt().isAfter(signal.availableAt())
                            && quote.observedAt().isAfter(signalBar.closeTime())
                            && !liveRule.observedAt()
                                    .isAfter(signalBar.closeTime().plus(Duration.ofMinutes(15)))
                            && !quote.observedAt()
                                    .isAfter(signalBar.closeTime().plus(Duration.ofMinutes(15)))
                            && quote.price() != null
                            && quote.price().signum() > 0;
            boolean staleContinuousSignal =
                    observedBars != null
                            && (signal.availableAt() == null
                                    || signal.availableAt()
                                            .isAfter(
                                                    signalBar
                                                            .closeTime()
                                                            .plus(Duration.ofMinutes(15)))
                                    || liveRule.observedAt()
                                            .isAfter(
                                                    signalBar
                                                            .closeTime()
                                                            .plus(Duration.ofMinutes(15)))
                                    || !signalBar.equals(bars.getLast()));
            // 新信号等待实际可见的公开价格；迟到的补缺只留下不可交易决策。
            if (observedBars != null && !freshContinuousQuote && !staleContinuousSignal)
                return null;
            String decisionId =
                    sha256(
                            paperRunId
                                    + ":"
                                    + strategy.strategyVersionId()
                                    + ":"
                                    + signalBar.openTime());
            SpotBarIdentity.Snapshot input =
                    SpotBarIdentity.capture(bars.subList(0, index + 1), mapper);
            SpotBarIdentity.Snapshot execution =
                    executionBar == null
                            ? null
                            : SpotBarIdentity.capture(List.of(executionBar), mapper);
            ObjectNode snapshot = mapper.createObjectNode();
            snapshot.set("signalBars", read(input.canonicalJson()));
            ObjectNode strategyDecision = snapshot.putObject("strategyDecision");
            strategyDecision.put("executable", strategy.executable());
            strategyDecision.put("previousState", previousState.name());
            strategyDecision.put("desiredState", signal.desiredState().name());
            strategyDecision.put("action", signal.action().name());
            strategyDecision.put("reason", signal.reason());
            if (execution == null) snapshot.putNull("executionBar");
            else snapshot.set("executionBar", read(execution.canonicalJson()).get(0));
            if (freshContinuousQuote) {
                ObjectNode quoteNode = mapper.createObjectNode();
                quoteNode.put("observedAt", quote.observedAt().toString());
                quoteNode.put("price", quote.price().toPlainString());
                snapshot.set("executionQuote", quoteNode);
            }
            String status = "NO_SIGNAL";
            String reason = signal.reason();
            SpotTargetSizer.Result sizing = null;
            FrozenSimFeeCharge frozenPayment = null;
            if (signal.requestsTrade() && !"INSUFFICIENT_HISTORY".equals(signal.reason())) {
                if (staleContinuousSignal) {
                    status = "NOT_TRADABLE";
                    reason = "STALE_DATA";
                } else if (executionBar == null && !freshContinuousQuote) {
                    status = "NOT_TRADABLE";
                    reason = "NO_LATER_TRADABLE_EVENT";
                } else if (executionBar != null
                        && executionBar
                                .openTime()
                                .isAfter(
                                        signal.availableAt()
                                                .plus(
                                                        signalBar
                                                                .interval()
                                                                .duration()
                                                                .multipliedBy(2)))) {
                    status = "NOT_TRADABLE";
                    reason = "STALE_DATA";
                } else {
                    BigDecimal cash = funding.cashBalance(lock.accountId());
                    BigDecimal position =
                            jdbc.queryForObject(
                                    """
                            SELECT COALESCE(MAX(qty),0) FROM positions
                            WHERE account_id=? AND symbol='BTC-USDT'
                            """,
                                    BigDecimal.class,
                                    lock.accountId());
                    List<StrategySimDecisionRepository.PendingOrder> pending =
                            decisions.pendingOrders(lock.accountId());
                    if (pending.size() > 500)
                        throw new IllegalStateException("SIM_PENDING_ORDER_RESERVE_UNKNOWN");
                    BigDecimal pendingBuy = BigDecimal.ZERO;
                    BigDecimal pendingSell = BigDecimal.ZERO;
                    BigDecimal pendingBuyCost = BigDecimal.ZERO;
                    BigDecimal discountReserve = BigDecimal.ZERO;
                    for (var order : pending) {
                        if (order.executionPrice() == null
                                || order.executionAt() == null
                                || !run.symbol().equals(order.symbol())
                                || order.executionPrice().signum() <= 0) {
                            throw new IllegalStateException("SIM_PENDING_ORDER_RESERVE_UNKNOWN");
                        }
                        StrategySimFeeTerms pendingTerms =
                                StrategySimFeeTerms.from(
                                        order.feeRate(), order.feeModelJson(), mapper);
                        if (directionalFee != null
                                && !read(order.feeModelJson()).equals(frozenSpec)) {
                            throw new IllegalStateException(
                                    "SIM_PENDING_FEE_SOURCE_IDENTITY_DRIFT");
                        }
                        Charge pendingCharge = null;
                        if (discountSource != null) {
                            FrozenSimFeeCharge payment =
                                    FrozenSimFeeCharge.read(order.feeChargeJson(), mapper);
                            payment.verifySource(discountSource);
                            pendingCharge =
                                    payment.charge(
                                            pendingTerms,
                                            run.symbol(),
                                            order.side(),
                                            order.quantity(),
                                            order.executionPrice(),
                                            order.executionAt());
                            if (pendingCharge.currency().equals(discountSource.assetCurrency()))
                                discountReserve =
                                        discountReserve.add(pendingCharge.positiveReserve());
                        } else if (order.feeChargeJson() != null)
                            throw new IllegalStateException("SIM_UNEXPECTED_DISCOUNT_FEE_PAYMENT");
                        if ("BUY".equals(order.side())) {
                            pendingBuy =
                                    pendingBuy.add(
                                            pendingCharge == null
                                                    ? pendingTerms.pendingBuyExposure(
                                                            run.symbol(),
                                                            order.quantity(),
                                                            order.executionPrice(),
                                                            order.executionAt())
                                                    : pendingTerms.pendingBuyExposure(
                                                            run.symbol(),
                                                            order.quantity(),
                                                            pendingCharge));
                            // 按原支付币种预留quote，不重复预留base费用估值，也不预支返佣现金。
                            pendingBuyCost =
                                    pendingBuyCost.add(
                                            pendingTerms.quoteReserve(
                                                    order.quantity(), order.executionPrice()));
                        } else if ("SELL".equals(order.side()))
                            pendingSell = pendingSell.add(order.quantity());
                        else throw new IllegalStateException("UNKNOWN_PENDING_SIDE");
                    }
                    var sizingState =
                            new SpotTargetSizer.State(
                                    cash,
                                    position,
                                    pendingBuy,
                                    pendingSell,
                                    pendingBuyCost,
                                    freshContinuousQuote
                                            ? quote.price()
                                            : executionBar.openPrice());
                    if (discountSource == null)
                        sizing =
                                directional == null
                                        ? SpotTargetSizer.size(
                                                signal.targetExposure(), sizingState, rules)
                                        : SpotTargetSizer.size(
                                                signal.targetExposure(), sizingState, directional);
                    else {
                        BigDecimal assetBalance =
                                assetFunding()
                                        .assetBalance(
                                                lock.accountId(), discountSource.assetCurrency());
                        BigDecimal available = assetBalance.subtract(discountReserve);
                        if (available.signum() < 0)
                            throw new IllegalStateException("SIM_DISCOUNT_ASSET_OVER_RESERVED");
                        Instant executionAt = executionBar.openTime();
                        DiscountFunding funds = discountSource.funding(executionAt, available);
                        BigDecimal value =
                                assetBalance
                                        .multiply(funds.quotePerAsset())
                                        .setScale(8, RoundingMode.HALF_UP);
                        sizing =
                                SpotTargetSizer.size(
                                        signal.targetExposure(),
                                        sizingState,
                                        directional,
                                        funds,
                                        executionAt,
                                        value);
                        if (sizing.executable()) {
                            var point = discountSource.priceAt(executionAt);
                            frozenPayment =
                                    new FrozenSimFeeCharge(
                                            run.symbol(),
                                            sizing.side(),
                                            sizing.quantity(),
                                            sizing.fillPrice(),
                                            executionAt,
                                            sizing.feeCharge(),
                                            funds,
                                            point.visibleAt(),
                                            point.sourceJson());
                        }
                    }
                    status = sizing.executable() ? "ACCEPTED" : "NOT_TRADABLE";
                    reason = sizing.reason();
                }
            }
            BigDecimal fillPrice =
                    sizing == null || !sizing.executable()
                            ? freshContinuousQuote
                                    ? quote.price()
                                    : executionBar == null ? null : executionBar.openPrice()
                            : sizing.fillPrice();
            decisions.insert(
                    new StrategySimDecisionRepository.DecisionWrite(
                            decisionId,
                            paperRunId,
                            lock.accountId(),
                            strategy.strategyVersionId(),
                            strategy.checksum(),
                            signalBar.openTime(),
                            signal.availableAt() == null
                                    ? signalBar.availableAt()
                                    : signal.availableAt(),
                            freshContinuousQuote
                                    ? quote.observedAt()
                                    : executionBar == null ? null : executionBar.openTime(),
                            input.sha256(),
                            freshContinuousQuote
                                    ? sha256(
                                            quote.observedAt()
                                                    + ":"
                                                    + quote.price()
                                                            .stripTrailingZeros()
                                                            .toPlainString())
                                    : execution == null ? null : execution.sha256(),
                            snapshot.toString(),
                            signal.targetExposure(),
                            "DECIDING",
                            sizing == null ? null : sizing.side(),
                            sizing == null ? null : sizing.quantity(),
                            fillPrice,
                            directional == null ? rules.feeRate() : null,
                            directional == null ? rules.slippageBps() : directional.slippageBps(),
                            directional == null ? null : frozenSpec.toString(),
                            frozenPayment == null ? null : frozenPayment.json(mapper)),
                    "ACCEPTED".equals(status) ? "NOT_TRADABLE" : status,
                    "ACCEPTED".equals(status) ? "DECIDING" : reason);
            if ("ACCEPTED".equals(status)) {
                return finishAcceptedDecision(
                        decisions.findByWindow(paperRunId, signalBar.openTime()).orElseThrow(),
                        run,
                        lock.accountId());
            }
            return decisions.findByWindow(paperRunId, signalBar.openTime()).orElseThrow();
        }
        // 同一已完成 logical run 的重复推进只返回最后的 durable 决策，不创建第二套经济事实。
        return observedBars == null
                ? decisions.findByWindow(paperRunId, bars.getLast().openTime()).orElseThrow()
                : null;
    }

    private void settleCanonicalRun(
            StrategySimDecisionRepository.DecisionView decision,
            long accountId,
            boolean requireTerminalExecution) {
        if (decision.strategyRunId() == null || decision.orderId() == null) {
            if (requireTerminalExecution
                    && ("ACCEPTED".equals(decision.status())
                            || "RISK_REJECTED".equals(decision.status()))) {
                throw new IllegalStateException("SIM_CANONICAL_RUN_NOT_TERMINAL");
            }
            return;
        }
        String status =
                jdbc.queryForObject(
                        """
                SELECT status FROM orders WHERE order_id=? AND account_id=? AND trade_env='SIM'
                """,
                        String.class,
                        decision.orderId(),
                        accountId);
        if ("CANCELLED".equals(status)) {
            Objects.requireNonNull(cancellations, "SIM cancellation recovery unavailable")
                    .finish(decision.paperRunId(), decision.orderId(), accountId);
        }
        if (!"FILLED".equals(status)
                && !"RISK_REJECTED".equals(status)
                && !"REJECTED".equals(status)
                && !"CANCELLED".equals(status)) {
            // 公开冻结回放先等待上一执行事件终结，避免调度快慢改变后续资金和仓位输入。
            if (requireTerminalExecution)
                throw new IllegalStateException("SIM_PREVIOUS_EXECUTION_PENDING");
            return;
        }
        // 只有 canonical Order/Trade 已达终态才收敛 run；下一 bar 不得绕过活动 run 准入锁。
        strategyWork.project(decision.strategyRunId());
        StrategyRunStatus projected =
                strategyRuns.findByStrategyRunId(decision.strategyRunId()).orElseThrow().status();
        if (("FILLED".equals(status) && projected != StrategyRunStatus.SUCCEEDED)
                || (!"FILLED".equals(status) && projected != StrategyRunStatus.FAILED)) {
            throw new IllegalStateException("SIM_CANONICAL_RUN_NOT_TERMINAL");
        }
    }

    public List<StrategySimDecisionRepository.DecisionView> decisions(String paperRunId) {
        paperRuns.getById(paperRunId);
        return decisions.listByRun(paperRunId);
    }

    private StrategySimDecisionRepository.DecisionView finishAcceptedDecision(
            StrategySimDecisionRepository.DecisionView decision,
            PaperTradingRun run,
            long accountId) {
        if (!"DECIDING".equals(decision.reason())
                || decision.side() == null
                || decision.quantity() == null
                || decision.quantity().signum() <= 0) {
            throw new IllegalStateException("SIM_DECISION_RECOVERY_INVALID");
        }
        StrategyDefinition definition = simDefinition(run, accountId);
        String requestId = "sim-" + decision.decisionId().substring(0, 60);
        var result =
                trigger.trigger(
                        new StrategyManualTriggerRequest(
                                definition.strategyId(),
                                requestId,
                                "BTC-USDT",
                                OrderSide.valueOf(decision.side()),
                                OrderType.MARKET,
                                decision.quantity(),
                                null,
                                "sim-" + decision.decisionId().substring(0, 32),
                                null,
                                definition));
        String strategyRunId = result.strategyRunId();
        String orderId = result.orderId();
        OrderStatus orderStatus = result.orderStatus();
        if (orderId == null && strategyRunId != null) {
            var saved =
                    jdbc.query(
                            """
                    SELECT order_id,status FROM orders WHERE strategy_run_id=? AND account_id=?
                    """,
                            (rs, row) ->
                                    new SavedOrder(
                                            rs.getString(1), OrderStatus.valueOf(rs.getString(2))),
                            strategyRunId,
                            accountId);
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
        String status =
                orderStatus == OrderStatus.RISK_REJECTED || orderStatus == OrderStatus.REJECTED
                        ? "RISK_REJECTED"
                        : "ACCEPTED";
        if ("ACCEPTED".equals(status)
                && orderStatus != OrderStatus.ACCEPTED
                && orderStatus != OrderStatus.ACKED
                && orderStatus != OrderStatus.FILLED) {
            throw new IllegalStateException("SIM_ORDER_NOT_ACCEPTED");
        }
        decisions.complete(
                decision.decisionId(),
                status,
                "RISK_REJECTED".equals(status) ? orderStatus.name() : "EXECUTABLE",
                strategyRunId,
                orderId);
        return decisions
                .findByWindow(decision.paperRunId(), decision.signalOpenTime())
                .orElseThrow();
    }

    private StrategyDefinition simDefinition(PaperTradingRun run, long accountId) {
        String identity = "sim-sim-" + run.paperRunId();
        return new StrategyDefinition(
                identity,
                identity,
                identity,
                SpotTargetStrategy.fromSnapshot(run.strategyVersionSnapshotJson(), mapper).executable(),
                "PAPER",
                accountId,
                "SIM",
                true,
                run.strategyVersionSnapshotJson(),
                1,
                run.createdAt(),
                run.createdAt());
    }

    /** 新连续 run 以发布时冻结窗口预热；突破初始方向为空仓，不重放种子窗口的交易。 */
    public ContinuousSeed continuousSeed(String paperRunId) {
        PaperTradingRun run = paperRuns.getById(paperRunId);
        BacktestPublishRecord publish =
                publishes.findByPublishRecordId(run.publishId()).orElseThrow();
        BacktestRun backtest = boundBacktest(publish);
        JsonNode summary = read(backtest.summaryJson());
        List<HistoricalBar> bars = validatedBars(backtest, summary);
        boolean registeredDemo = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tenant_sim_accounts
                    WHERE tenant_id=nq_current_tenant_id() AND bound_paper_run_id=? AND account_id=?)
                """, Boolean.class, paperRunId, run.canonicalAccountId()));
        boolean approvedDatabaseSeed = registeredDemo
                && "db".equals(summary.path("datasetProvider").asText())
                && "db".equals(read(backtest.datasetSnapshotJson()).path("provider").asText());
        if (registeredDemo) requireDemoDataset(backtest, bars);
        SpotTargetStrategy strategy =
                SpotTargetStrategy.fromSnapshot(run.strategyVersionSnapshotJson(), mapper);
        if (!"OKX".equals(run.exchangeCode())
                || !"BTC-USDT".equals(run.symbol())
                || !"SPOT".equals(run.marketType())
                || !"1h".equals(run.intervalCode())
                || !"SIM".equals(run.tradeEnv())
                || !("public-capture".equals(summary.path("datasetProvider").asText()) || approvedDatabaseSeed)
                || !strategy.strategyVersionId().equals(run.strategyVersionId())
                || !sameFrozenStrategy(
                        strategy,
                        SpotTargetStrategy.fromSnapshot(
                                backtest.strategyVersionSnapshotJson(), mapper))) {
            throw new IllegalStateException("CONTINUOUS_SIM_SEED_INVALID");
        }
        JsonNode costs = read(run.configSnapshotJson()).path("costAndRuleAssumptions");
        rules(costs);
        return new ContinuousSeed(
                bars,
                strategy.strategyVersionId(),
                strategy.checksum(),
                PublicReplayAssumptionIdentity.sha256(costs));
    }

    /** 同次经济投影使用一致的数据库读快照；事件估值时点单独返回。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public FactsView facts(String paperRunId) {
        PaperTradingRun run = paperRuns.getById(paperRunId);
        Long accountId =
                jdbc.queryForObject(
                        """
                SELECT canonical_account_id FROM (SELECT tr.* FROM paper_trading_runs tr WHERE tr.tenant_id=nq_current_tenant_id()) paper_trading_runs WHERE paper_run_id=?
                """,
                        Long.class,
                        paperRunId);
        if (accountId == null)
            throw new IllegalStateException("LEGACY_PAPER_RUN_HAS_NO_CANONICAL_FACTS");
        BacktestPublishRecord publish =
                publishes.findByPublishRecordId(run.publishId()).orElseThrow();
        BacktestRun backtest = boundBacktest(publish);
        if (!read(run.datasetSnapshotJson()).equals(read(backtest.datasetSnapshotJson()))) {
            throw new IllegalStateException("SIM_DATASET_IDENTITY_DRIFT");
        }
        JsonNode summary = read(backtest.summaryJson());
        if (!summary.path("barContentSha256")
                .asText()
                .equals(read(run.configSnapshotJson()).path("barContentSha256").asText())) {
            throw new IllegalStateException("SIM_INPUT_IDENTITY_DRIFT");
        }
        List<HistoricalBar> bars =
                continuous != null && continuous.exists(paperRunId)
                        ? continuous.recentBars(paperRunId)
                        : validatedBars(backtest, summary);
        BigDecimal cash = funding.cashBalance(accountId);
        BigDecimal quantity =
                jdbc.queryForObject(
                        """
                SELECT COALESCE(MAX(qty),0) FROM positions
                WHERE account_id=? AND symbol='BTC-USDT'
                """,
                        BigDecimal.class,
                        accountId);
        // 增量回放只按已处理决策可见的事件估值，不泄露冻结数据集后续 bar 的价格。
        Instant latestEvent = null;
        boolean latestIsExecution = false;
        StrategySimDecisionRepository.DecisionView latestDecision = null;
        BigDecimal mark = BigDecimal.ZERO;
        List<String> durableOrderIds =
                jdbc.queryForList(
                        "SELECT DISTINCT order_id FROM trades WHERE account_id=? ORDER BY order_id LIMIT 10001",
                        String.class,
                        accountId);
        if (durableOrderIds.size() > 10000)
            throw new IllegalStateException("SIM_TRADE_WINDOW_LIMITED");
        Set<String> tradedOrderIds = new HashSet<>(durableOrderIds);
        for (var decision : decisions.listByRun(paperRunId)) {
            // 预先选择的下一开盘还不是已发生的事件，只有durable成交才能成为执行估值时点。
            boolean executionEvent =
                    decision.executionOpenTime() != null
                            && tradedOrderIds.contains(decision.orderId());
            Instant eventTime =
                    executionEvent ? decision.executionOpenTime() : decision.signalAvailableAt();
            if (latestEvent == null
                    || eventTime.isAfter(latestEvent)
                    || (eventTime.equals(latestEvent) && executionEvent && !latestIsExecution)) {
                latestDecision = decision;
                latestEvent = eventTime;
                latestIsExecution = executionEvent;
            }
        }
        if (latestDecision != null) {
            if (continuous != null && continuous.exists(paperRunId) && latestIsExecution) {
                String quotePrice =
                        jdbc.queryForObject(
                                """
                        SELECT input_snapshot_json->'executionQuote'->>'price'
                        FROM strategy_sim_decisions WHERE decision_id=?
                        """,
                                String.class,
                                latestDecision.decisionId());
                if (quotePrice == null)
                    throw new IllegalStateException("SIM_EXECUTION_QUOTE_MISSING");
                mark = new BigDecimal(quotePrice);
            } else {
                Instant barOpenTime =
                        !latestIsExecution
                                ? latestDecision.signalOpenTime()
                                : latestDecision.executionOpenTime();
                HistoricalBar eventBar =
                        bars.stream()
                                .filter(bar -> bar.openTime().equals(barOpenTime))
                                .findFirst()
                                .orElseThrow();
                mark = !latestIsExecution ? eventBar.closePrice() : eventBar.openPrice();
            }
        }
        BigDecimal budget = decimal(read(run.configSnapshotJson()), "budget");
        BigDecimal equity = cash.add(quantity.multiply(mark));
        DiscountAssetView discount = null;
        JsonNode executionSpec = read(backtest.configSnapshotJson()).path("executionSpec");
        if ("DISCOUNT_ASSET_IF_FUNDED".equals(executionSpec.path("feePaymentPolicy").asText())) {
            var prices = discountSource(executionSpec);
            var plan =
                    assetFunding()
                            .findPlan(accountId, paperRunId)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "SIM_DISCOUNT_FUNDING_MISSING"));
            BigDecimal balance = assetFunding().assetBalance(accountId, prices.assetCurrency());
            BigDecimal reserved = discountReserve(accountId, run.symbol(), executionSpec, prices);
            if (balance.signum() < 0 || balance.subtract(reserved).signum() < 0)
                throw new IllegalStateException("SIM_DISCOUNT_ASSET_OVER_RESERVED");
            Instant valuationAt = latestEvent == null ? plan.asOf() : latestEvent;
            try {
                var point = prices.priceAt(valuationAt);
                BigDecimal assetValue =
                        balance.multiply(point.quotePerAsset()).setScale(8, RoundingMode.HALF_UP);
                equity = equity.add(assetValue);
                discount =
                        discountView(
                                "AVAILABLE",
                                plan,
                                balance,
                                reserved,
                                prices,
                                valuationAt,
                                point,
                                assetValue);
            } catch (IllegalArgumentException unavailable) {
                if (!"FEE_DISCOUNT_PRICE_UNAVAILABLE".equals(unavailable.getMessage()))
                    throw unavailable;
                // 没有可见换算时只保留真实币余额，权益和收益保持未知，不能把第三币按零估值。
                equity = null;
                discount =
                        discountView(
                                "PRICE_UNAVAILABLE",
                                plan,
                                balance,
                                reserved,
                                prices,
                                valuationAt,
                                null,
                                null);
            }
        }
        return new FactsView(
                paperRunId,
                accountId,
                run.publishId(),
                run.strategyVersionId(),
                summary.path("barContentSha256").asText(),
                budget,
                cash,
                quantity,
                mark,
                equity,
                equity == null ? null : equity.subtract(budget),
                jdbc.queryForList(
                        """
                        SELECT order_id,strategy_run_id,client_order_id,account_id,symbol,side,type,status,reason,qty,price,qty::text AS exact_qty,price::text AS exact_price,created_at,updated_at
                        FROM orders WHERE account_id=? ORDER BY created_at,order_id LIMIT 500
                        """,
                        accountId),
                jdbc.queryForList(
                        """
                        SELECT t.trade_id,t.order_id,t.account_id,t.symbol,o.side,t.price,t.qty,t.fee,t.fee_currency,t.ts,t.qty::text AS exact_qty,t.price::text AS exact_price,t.fee::text AS exact_fee,
                            d.fee_charge_json->>'paymentStatus' AS fee_payment_status,d.fee_charge_json->>'quoteValue' AS fee_quote_value,
                            d.fee_charge_json->>'quotePerAsset' AS fee_conversion_price,d.fee_charge_json->>'observedAt' AS fee_price_observed_at,
                            d.fee_charge_json->>'visibleAt' AS fee_price_visible_at,d.fee_charge_json->>'sourceSha256' AS fee_price_source_sha256
                        FROM trades t JOIN orders o ON o.order_id=t.order_id AND o.account_id=t.account_id
                        LEFT JOIN strategy_sim_decisions d ON d.order_id=t.order_id AND d.canonical_account_id=t.account_id
                        WHERE t.account_id=? ORDER BY t.ts,t.trade_id LIMIT 500
                        """,
                        accountId),
                jdbc.queryForList(
                        """
                        SELECT entry_id,currency,delta,delta::text AS exact_delta,ref_type,ref_id,idempotency_key,ts
                        FROM ledger_entries WHERE account_id=? ORDER BY ts,entry_id LIMIT 2000
                        """,
                        accountId),
                latestEvent,
                jdbc.queryForObject(
                        "SELECT max(ts) FROM ledger_entries WHERE account_id=?",
                        Timestamp.class,
                        accountId),
                jdbc.queryForObject(
                        "SELECT max(updated_at) FROM positions WHERE account_id=?",
                        Timestamp.class,
                        accountId),
                jdbc.queryForList(
                        """
                        SELECT e.risk_event_id,e.scope_id AS order_id,e.rule_id,e.decision,e.reason,e.created_at
                        FROM risk_events e JOIN orders o ON e.scope='ORDER' AND e.scope_id=o.order_id
                        WHERE o.account_id=? ORDER BY e.created_at,e.risk_event_id LIMIT 500
                        """,
                        accountId),
                jdbc.queryForList(
                        """
                        SELECT id,account_id,symbol,qty,avg_price,updated_at,
                               qty::text AS exact_qty,avg_price::text AS exact_avg_price
                        FROM positions WHERE account_id=? ORDER BY symbol LIMIT 500
                        """,
                        accountId),
                discount);
    }

    private DiscountAssetPriceSnapshot discountSource(JsonNode spec) {
        if (discountPrices == null)
            throw new IllegalStateException("SIM_DISCOUNT_PRICE_SOURCE_DISABLED");
        var prices =
                discountPrices.read(
                        Long.parseLong(spec.path("feeSourceOwnerUserId").asText()),
                        UUID.fromString(spec.path("discountPriceSnapshotId").asText()),
                        spec.path("discountPriceSnapshotSha256").asText());
        JsonNode snapshot = read(spec.path("feeSourceSnapshotJson").asText());
        if (!prices.datasetId().toString().equals(spec.path("discountPriceDatasetId").asText())
                || !prices.exchangeCode().equals(spec.path("feeExchangeCode").asText())
                || !prices.quoteCurrency().equals("USDT")
                || !prices.assetCurrency()
                        .equals(
                                snapshot.path("commissionTerms")
                                        .path("discount")
                                        .path("asset")
                                        .asText())
                || !prices.frozenAt().equals(Instant.parse(snapshot.path("frozenAt").asText())))
            throw new IllegalStateException("SIM_DISCOUNT_SOURCE_IDENTITY_DRIFT");
        return prices;
    }

    private SimAssetFundingPort assetFunding() {
        if (assetFunding == null) throw new IllegalStateException("SIM_DISCOUNT_FUNDING_DISABLED");
        return assetFunding;
    }

    /** 研究份额只拆分本次独立预算；价格、可见时点与来源从已冻结序列读取，不接受页面提供价格。 */
    private SimAssetFundingPlan fundingPlan(
            JsonNode spec, DiscountAssetPriceSnapshot prices, BigDecimal budget, Instant asOf) {
        var point = prices.priceAt(asOf);
        return new SimAssetFundingPlan(
                budget,
                "USDT",
                prices.assetCurrency(),
                decimal(spec, "discountAssetBudgetFraction"),
                point.quotePerAsset(),
                "USDT_PER_" + prices.assetCurrency(),
                point.observedAt(),
                point.visibleAt(),
                asOf,
                point.sourceSha256(),
                point.sourceJson());
    }

    private BigDecimal discountReserve(
            long accountId, String symbol, JsonNode spec, DiscountAssetPriceSnapshot prices) {
        var pending = decisions.pendingOrders(accountId);
        if (pending.size() > 500)
            throw new IllegalStateException("SIM_PENDING_ORDER_RESERVE_UNKNOWN");
        BigDecimal reserve = BigDecimal.ZERO;
        for (var order : pending) {
            if (!symbol.equals(order.symbol())
                    || order.feeModelJson() == null
                    || !read(order.feeModelJson()).equals(spec))
                throw new IllegalStateException("SIM_PENDING_FEE_SOURCE_IDENTITY_DRIFT");
            var payment = FrozenSimFeeCharge.read(order.feeChargeJson(), mapper);
            payment.verifySource(prices);
            Charge charge =
                    payment.charge(
                            StrategySimFeeTerms.from(order.feeRate(), order.feeModelJson(), mapper),
                            symbol,
                            order.side(),
                            order.quantity(),
                            order.executionPrice(),
                            order.executionAt());
            if (prices.assetCurrency().equals(charge.currency()))
                reserve = reserve.add(charge.positiveReserve());
        }
        return reserve;
    }

    private DiscountAssetView discountView(
            String status,
            SimAssetFundingPlan plan,
            BigDecimal balance,
            BigDecimal reserved,
            DiscountAssetPriceSnapshot prices,
            Instant valuationAt,
            DiscountAssetPriceSnapshot.Point point,
            BigDecimal value) {
        return new DiscountAssetView(
                status,
                plan.assetCurrency(),
                plan.assetBudgetFraction().toPlainString(),
                plan.assetQuantity().toPlainString(),
                plan.assetInitialQuoteValue().toPlainString(),
                plan.cashBudget().toPlainString(),
                balance.toPlainString(),
                reserved.toPlainString(),
                balance.subtract(reserved).toPlainString(),
                value == null ? null : value.toPlainString(),
                point == null ? null : point.quotePerAsset().toPlainString(),
                point == null ? null : point.observedAt(),
                point == null ? null : point.visibleAt(),
                valuationAt,
                point == null ? null : point.sourceSha256(),
                prices.datasetId().toString(),
                prices.snapshotId().toString(),
                prices.sourceSha256());
    }

    private BacktestRun boundBacktest(BacktestPublishRecord publish) {
        BacktestRun backtest = backtests.findByBacktestRunId(publish.backtestRunId()).orElseThrow();
        if (publish.publishStatus() != PublishStatus.SUCCEEDED
                || backtest.status() != BacktestRunStatus.SUCCEEDED
                || publish.strategyVersionId() == null
                || !publish.strategyVersionId().equals(backtest.strategyVersionId())) {
            throw new IllegalStateException("BACKTEST_PUBLISH_VERSION_MISMATCH");
        }
        // SIM继承回测采用的原观察，过期只阻止新研究选择，不替换运行中的成本模型。
        feeSource.validateFrozenExecution(
                read(backtest.configSnapshotJson()).path("executionSpec").toString(),
                backtest.datasetSnapshotJson());
        return backtest;
    }

    private List<HistoricalBar> validatedBars(BacktestRun backtest, JsonNode summary) {
        JsonNode dataset = read(backtest.datasetSnapshotJson());
        if ("public-capture".equals(dataset.path("provider").asText())
                != "public-capture".equals(summary.path("datasetProvider").asText())) {
            throw new IllegalStateException("SIM_DATASET_PROVIDER_MISMATCH");
        }
        JsonNode stored = summary.path("consumedBars");
        if (!stored.isArray() || stored.isEmpty() || stored.size() > 720) {
            throw new IllegalStateException("BACKTEST_INPUT_IDENTITY_INVALID");
        }
        List<HistoricalBar> bars = new ArrayList<>(stored.size());
        for (JsonNode bar : stored) {
            bars.add(
                    new HistoricalBar(
                            bar.path("exchangeCode").asText(),
                            bar.path("marketType").asText(),
                            bar.path("symbol").asText(),
                            BarInterval.fromWireValue(bar.path("interval").asText()),
                            Instant.parse(bar.path("openTime").asText()),
                            Instant.parse(bar.path("closeTime").asText()),
                            decimal(bar, "openPrice"),
                            decimal(bar, "highPrice"),
                            decimal(bar, "lowPrice"),
                            decimal(bar, "closePrice"),
                            decimal(bar, "volume"),
                            bar.path("quoteVolume").isNull() ? null : decimal(bar, "quoteVolume"),
                            bar.path("tradeCount").isNull()
                                    ? null
                                    : bar.path("tradeCount").asLong(),
                            bar.path("qualityStatus").asText(),
                            bar.path("rawPayloadJson").asText(),
                            Instant.parse(bar.path("availableAt").asText())));
        }
        if (!summary.path("barContentSha256")
                        .asText()
                        .equals(SpotBarIdentity.capture(bars, mapper).sha256())
                || summary.path("barCount").asInt(-1) != bars.size()
                || !replayScope(
                        summary.path("exchangeCode").asText(),
                        "SPOT",
                        summary.path("symbol").asText())
                || !summary.path("interval").asText().equals(bars.getFirst().interval().wireValue())
                || !summary.path("exchangeCode").asText().equals(bars.getFirst().exchangeCode())
                || !summary.path("symbol").asText().equals(bars.getFirst().symbol())
                || !summary.path("exchangeCode")
                        .asText()
                        .equals(dataset.path("exchangeCode").asText())
                || !summary.path("symbol").asText().equals(dataset.path("symbol").asText())
                || bars.stream()
                        .anyMatch(
                                bar ->
                                        !summary.path("exchangeCode")
                                                        .asText()
                                                        .equals(bar.exchangeCode())
                                                || !"SPOT".equals(bar.marketType())
                                                || !summary.path("symbol")
                                                        .asText()
                                                        .equals(bar.symbol())
                                                || bar.interval() != bars.getFirst().interval())
                || !backtest.strategyVersionId()
                        .equals(summary.path("strategyVersionId").asText())) {
            throw new IllegalStateException("SIM_SCOPE_OR_VERSION_INVALID");
        }
        if ("public-capture".equals(summary.path("datasetProvider").asText())) {
            JsonNode capture = dataset.path("capture");
            JsonNode assumptions = summary.path("costAndRuleAssumptions");
            if (!"OKX".equals(summary.path("exchangeCode").asText())
                    || !"public-capture".equals(dataset.path("provider").asText())
                    || !"OKX_PUBLIC_CAPTURE".equals(dataset.path("source").asText())
                    || !"1h".equals(dataset.path("interval").asText())
                    || !"1h".equals(summary.path("interval").asText())
                    || dataset.path("barCount").asInt(-1) != bars.size()
                    || !summary.path("barContentSha256")
                            .asText()
                            .equals(capture.path("consumedSha256").asText())
                    || !"CLOSED_HOURLY_BOUNDARY_V1"
                            .equals(capture.path("replayVisibilityVersion").asText())
                    || !"EXPERIMENT_ASSUMPTION"
                            .equals(capture.path("replayVisibilitySource").asText())
                    || !capture.path("ruleSha256")
                            .asText()
                            .equals(assumptions.path("ruleSha256").asText())
                    || !"CURRENTLY_OBSERVED_PUBLIC_RULES"
                            .equals(assumptions.path("rulePolicy").asText())) {
                throw new IllegalStateException("SIM_PUBLIC_CAPTURE_IDENTITY_INVALID");
            }
        }
        return List.copyOf(bars);
    }

    /** 只从相邻 durable 决策恢复策略意图；rolling window 截断和账户持仓都不能重置方向。 */
    private SpotTargetStrategy.PositionState previousStrategyState(
            SpotTargetStrategy strategy, PaperTradingRun run, List<HistoricalBar> bars, int index,
            boolean continuous, BacktestRun backtest, JsonNode summary) {
        if (!strategy.stateful()) return SpotTargetStrategy.PositionState.FLAT;
        HistoricalBar current = bars.get(index);
        var previous = decisions.findPreviousWindow(run.paperRunId(), current.openTime());
        if (previous.isPresent()) {
            var value = previous.get();
            if (!value.signalOpenTime().plus(current.interval().duration()).equals(current.openTime())
                    || !strategy.strategyVersionId().equals(value.strategyVersionId())
                    || !strategy.checksum().equals(value.strategyChecksum())) {
                throw new IllegalStateException("SIM_STRATEGY_STATE_IDENTITY_DRIFT");
            }
            return strategy.stateForTarget(value.targetExposure());
        }
        if (continuous) {
            var seed = validatedBars(backtest, summary);
            if (!seed.getLast().openTime().plus(current.interval().duration()).equals(current.openTime())) {
                throw new IllegalStateException("SIM_STRATEGY_STATE_UNAVAILABLE");
            }
        } else if (index != 0) {
            throw new IllegalStateException("SIM_STRATEGY_STATE_UNAVAILABLE");
        }
        return SpotTargetStrategy.PositionState.FLAT;
    }

    private static boolean sameFrozenStrategy(
            SpotTargetStrategy left, SpotTargetStrategy right) {
        return left.sameFrozenStrategy(right);
    }

    private SpotTargetSizer.Rules rules(JsonNode node) {
        return new SpotTargetSizer.Rules(
                decimal(node, "quantityStep"),
                decimal(node, "priceTick"),
                decimal(node, "minimumQuantity"),
                decimal(node, "minimumNotional"),
                decimal(node, "feeRate"),
                decimal(node, "slippageBps"));
    }

    /** 只扩展已完成冻结回测的现货回放来源，不将BINANCE标签改为OKX，也不扩大真实执行权限。 */
    private static boolean replayScope(String exchange, String market, String symbol) {
        return ("OKX".equals(exchange) || "BINANCE".equals(exchange))
                && "SPOT".equals(market)
                && "BTC-USDT".equals(symbol);
    }

    private SpotTargetSizer.DirectionalRules directionalRules(
            JsonNode costs, String symbol, SpotFeeModel model) {
        return new SpotTargetSizer.DirectionalRules(
                symbol,
                decimal(costs, "quantityStep"),
                decimal(costs, "priceTick"),
                decimal(costs, "minimumQuantity"),
                decimal(costs, "minimumNotional"),
                decimal(costs, "slippageBps"),
                model);
    }

    private static BigDecimal decimal(JsonNode node, String name) {
        if (node.path(name).isMissingNode() || node.path(name).isNull())
            throw new IllegalArgumentException("MISSING_SIM_ASSUMPTION_" + name);
        return new BigDecimal(node.path(name).asText());
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("INVALID_FROZEN_JSON", ex);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record RunLock(
            String status,
            String tradeEnv,
            String exchangeCode,
            String marketType,
            String symbol,
            Long accountId) {}

    private record SavedOrder(String orderId, OrderStatus status) {}

    public record RunView(
            String paperRunId,
            String publishId,
            String strategyVersionId,
            String barContentSha256,
            long canonicalAccountId,
            BigDecimal budget,
            SpotTargetSizer.Rules rules) {}

    public record ContinuousSeed(
            List<HistoricalBar> bars,
            String strategyVersionId,
            String strategyChecksum,
            String costSha256) {}

    public record FactsView(
            String paperRunId,
            long canonicalAccountId,
            String publishId,
            String strategyVersionId,
            String inputSha256,
            BigDecimal initialBudget,
            BigDecimal cash,
            BigDecimal positionQuantity,
            BigDecimal markPrice,
            BigDecimal equity,
            BigDecimal pnl,
            List<Map<String, Object>> orders,
            List<Map<String, Object>> trades,
            List<Map<String, Object>> ledgerEntries,
            Instant markAsOf,
            Timestamp ledgerAsOf,
            Timestamp positionAsOf,
            List<Map<String, Object>> riskEvents,
            List<Map<String, Object>> positions,
            DiscountAssetView discountAsset) {
        /** 同一计算结果的十进制文本，避免浏览器解析 JSON 数值时损失有效位。 */
        public Map<String, String> getExactValues() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("cash", cash.toPlainString());
            values.put("positionQuantity", positionQuantity.toPlainString());
            values.put("markPrice", markPrice.toPlainString());
            values.put("equity", equity == null ? null : equity.toPlainString());
            values.put("pnl", pnl == null ? null : pnl.toPlainString());
            return values;
        }
    }

    /** 金额用十进制文本返回，缺少价格时仍展示余额并明确保留不可用估值。 */
    public record DiscountAssetView(
            String status,
            String assetCurrency,
            String budgetFraction,
            String initialQuantity,
            String initialQuoteValue,
            String initialCash,
            String balance,
            String reserved,
            String available,
            String quoteValue,
            String conversionPrice,
            Instant observedAt,
            Instant visibleAt,
            Instant valuationAt,
            String priceSourceSha256,
            String datasetId,
            String snapshotId,
            String snapshotSha256) {}
}
