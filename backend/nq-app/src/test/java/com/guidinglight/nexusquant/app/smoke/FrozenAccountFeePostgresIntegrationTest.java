package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.application.service.AccountFeeModelService;
import com.guidinglight.nexusquant.account.application.service.AccountFeeService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeSourceIdentity;
import com.guidinglight.nexusquant.account.domain.fee.FrozenAccountFeeModel;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.application.paper.command.PaperRunAlertCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.command.PaperRunDailyReportGenerateCommand;
import com.guidinglight.nexusquant.research.application.paper.command.PaperRunStabilityCheckGenerateCommand;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunStabilityCheckService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.backtest.jdbc.JdbcBacktestRunRepository;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import com.guidinglight.nexusquant.security.token.port.TokenService;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.AdmissionMutationCoordinator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** 合成供应方观察经正式持久化、冻结、发布及Risk/Execution进入SIM；JWT验证私有来源隔离，不联网。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@AutoConfigureMockMvc
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            FrozenAccountFeePostgresIntegrationTest.FixtureConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = FrozenAccountFeePostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never",
            "spring.task.scheduling.enabled=false",
            "nq.runtime.trading-components.enabled=true",
            "nq.strategy-sim.enabled=true",
            "nq.continuous-sim.enabled=false",
            "nq.public-marketdata.outbound.enabled=false",
            "nq.auth.bootstrap-admin.enabled=false",
            "nq.instrument.catalog-sync.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.account.credentials.verification-mode=STRUCTURAL",
            "nq.account.performance.sampling-enabled=false",
            "nq.account.credentials.master-key=signed-fee-synthetic-master-key-123456789",
            "nq.security.issuer=nexus-quant-signed-fee-synthetic",
            "nq.security.secret=FAKE-PLACEHOLDER-CONTROLLED-JWT-SIGNING-KEY"
        })
class FrozenAccountFeePostgresIntegrationTest {
    private static ReleaseBaselineTestDatabase database;
    @Autowired JdbcTemplate jdbc;
    @Autowired MarketdataBarRepository bars;
    @Autowired MarketdataDatasetService datasets;
    @Autowired StrategyDefinitionRepository definitions;
    @Autowired StrategyVersionService versions;
    @Autowired ResearchConfigService research;
    @Autowired BacktestConfigService configs;
    @Autowired BacktestRunService backtests;
    @Autowired BacktestExecutionService execution;
    @Autowired BacktestEvaluationService evaluations;
    @Autowired BacktestPublishService publishes;
    @Autowired StrategySimRunService sim;
    @Autowired PaperTradingRunService runs;
    @Autowired PaperMatchingService matching;
    @Autowired SimCashFundingPort funding;
    @Autowired ExchangeAccountCommandService accounts;
    @Autowired ExchangeAccountCredentialCommandService credentials;
    @Autowired AccountFeeService fees;
    @Autowired AccountFeeModelService models;
    @Autowired ResearchFeeSourcePort source;
    @Autowired ObjectMapper mapper;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @Autowired FixtureSource fixture;
    @Autowired AdmissionMutationCoordinator coordinator;
    @Autowired PaperRunMonitorService monitor;
    @Autowired PaperRunStabilityCheckService stability;
    private long owner;
    private static final String PASSWORD = "frozen-fee-synthetic-login";

    @BeforeEach
    void users() {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "frozen-admin", passwords.encode(PASSWORD), List.of("ADMIN"), true),
                        new SeedUserCommand(
                                "frozen-owner",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "frozen-other",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "frozen-viewer",
                                passwords.encode(PASSWORD),
                                List.of("VIEWER"),
                                true)));
        owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='frozen-owner'", Long.class);
        fixture.calls.set(0);
        fixture.rate = d("-0.001");
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        database = ReleaseBaselineTestDatabase.create();
        Flyway.configure()
                .dataSource(database.source())
                .locations("classpath:db/migration")
                // 正式登录与会话均使用当前合同；旧schema不能替代当前权限和费用端到端验证。
                .cleanDisabled(true)
                .load()
                .migrate();
        registry.add("spring.datasource.url", database::url);
        registry.add(
                "spring.datasource.username", () -> System.getProperty("nq.postgres.smoke.user"));
        registry.add(
                "spring.datasource.password",
                () -> System.getProperty("nq.postgres.smoke.password"));
    }

    @Test
    void persistedObservedPositiveZeroNegativeCostsFreezeThroughFormalBacktestPublishAndSim()
            throws Exception {
        asUser(
                "frozen-owner",
                () -> {
                    assertEquals(
                            "ENGAGED",
                            jdbc.queryForObject(
                                    "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                                    String.class));
                    for (String rate : List.of("0.001", "0", "-0.001")) {
                        String suffix = UUID.randomUUID().toString().substring(0, 8);
                        List<HistoricalBar> input =
                                List.of(
                                        bar(0, "100"),
                                        bar(1, "101"),
                                        bar(2, "102"),
                                        bar(3, "103"),
                                        bar(4, "104"));
                        bars.upsertBars(input, "FIXTURE_SYNC", Instant.now());
                        var dataset =
                                datasets.createDataset(
                                        new CreateMarketdataDatasetCommand(
                                                "Signed fee bars " + suffix,
                                                "OKX",
                                                "SPOT",
                                                "BTC-USDT",
                                                "1m",
                                                input.getFirst().openTime(),
                                                input.getLast().closeTime(),
                                                "synthetic-test"));
                        assertEquals("READY", dataset.status().name());
                        String snapshot = datasets.buildDatasetSnapshot(dataset.datasetId());
                        var account = account();
                        long credential = credential(account);
                        fixture.rate = d(rate).negate();
                        var observation =
                                fees.sync(
                                        owner,
                                        account.exchangeAccountId(),
                                        UUID.randomUUID(),
                                        credential,
                                        "BTC-USDT");
                        assertEquals(
                                AccountFeeSourceIdentity.digest(observation),
                                observation.sourceSha256());
                        long strategyAccount = account.legacyAccountId();
                        String strategyId = "str-signed-fee-" + suffix,
                                code = "signed-fee-sma-" + suffix;
                        Instant now = Instant.now();
                        definitions.insert(
                                new StrategyDefinition(
                                        strategyId,
                                        code,
                                        "Signed fee synthetic SMA",
                                        SpotSmaTargetStrategy.EXECUTABLE,
                                        "OKX",
                                        strategyAccount,
                                        "SIM",
                                        true,
                                        "{}",
                                        1,
                                        now,
                                        now));
                        var version =
                                versions.create(
                                        new StrategyVersionCreateRequest(
                                                code,
                                                "v1",
                                                "ACTIVE",
                                                "{\"window\":3,\"investedExposure\":\"1\"}",
                                                "{}",
                                                "{\"executable\":\"SPOT_SMA_TARGET_V1\"}",
                                                "synthetic-test"));
                        var experiment =
                                research.create(
                                        new ResearchConfigCreateRequest(
                                                strategyId,
                                                "Signed fee research " + suffix,
                                                "synthetic",
                                                "{}",
                                                "{}",
                                                snapshot));
                        ObjectNode selected = reference(account, observation.observationId());
                        selected.put("quantityStep", "0.0001");
                        selected.put("priceTick", "0.01");
                        selected.put("minimumQuantity", "0.0001");
                        selected.put("minimumNotional", "5");
                        selected.put("slippageBps", "10");
                        String costs = selected.toString();
                        var config =
                                configs.create(
                                        new BacktestConfigCreateRequest(
                                                experiment.researchConfigId(),
                                                "Signed fee backtest " + suffix,
                                                "synthetic",
                                                input.getFirst().openTime(),
                                                input.getLast().closeTime(),
                                                d("100"),
                                                costs,
                                                "{}"));
                        configs.bindDataset(
                                config.backtestConfigId(),
                                dataset.datasetId().toString(),
                                snapshot);
                        configs.bindStrategyVersion(
                                config.backtestConfigId(), version.strategyVersionId());
                        var backtest =
                                backtests.create(
                                        new BacktestRunStartRequest(config.backtestConfigId()));
                        JsonNode spec = tree(backtest.configSnapshotJson()).path("executionSpec");
                        JsonNode frozenSnapshot = tree(spec.path("feeSourceSnapshotJson").asText());
                        assertEquals(
                                observation.observationId().toString(),
                                frozenSnapshot.path("observationId").asText());
                        assertEquals(
                                Long.toString(owner), frozenSnapshot.path("ownerUserId").asText());
                        assertEquals(
                                FrozenAccountFeeModel.digest(frozenSnapshot),
                                spec.path("feeSourceSnapshotSha256").asText());
                        assertEquals(
                                backtest.configSnapshotJson(), backtest.backtestConfigSnapshot());
                        String frozenRun =
                                jdbc.queryForObject(
                                        "SELECT config_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=?",
                                        String.class,
                                        backtest.backtestRunId());
                        // 后续撤销不应改写已采用模型；执行复核读取原观察，不能偷偷联网或替换最新率。
                        credentials.revoke(
                                owner,
                                account.exchangeAccountId(),
                                credential,
                                "fixture",
                                "frozen-test");
                        source.validateFrozenExecution(spec.toString(), snapshot().toString());
                        assertEquals(
                                "SUCCEEDED",
                                execution.startRun(backtest.backtestRunId()).resultStatus().name());
                        assertEquals(
                                frozenRun,
                                jdbc.queryForObject(
                                        "SELECT config_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=?",
                                        String.class,
                                        backtest.backtestRunId()));
                        assertEquals(
                                0,
                                d(rate).compareTo(
                                                jdbc.queryForObject(
                                                        "SELECT (summary_json->'costAndRuleAssumptions'->>'feeRate')::numeric FROM backtest_runs WHERE backtest_run_id=?",
                                                        BigDecimal.class,
                                                        backtest.backtestRunId())));
                        BigDecimal researchFee =
                                jdbc.queryForObject(
                                        "SELECT (summary_json->>'totalFee')::numeric FROM backtest_runs WHERE backtest_run_id=?",
                                        BigDecimal.class,
                                        backtest.backtestRunId());
                        assertEquals(d(rate).signum(), researchFee.signum());
                        assertEquals(
                                "SUCCEEDED",
                                evaluations
                                        .evaluate(backtest.backtestRunId())
                                        .evaluationStatus()
                                        .name());
                        var publish =
                                publishes.publish(
                                        new BacktestPublishRequest(
                                                backtest.backtestRunId(),
                                                "Signed fee publish " + suffix,
                                                version.strategyVersionId()));
                        assertEquals("SUCCEEDED", publish.publishStatus().name());
                        var created =
                                sim.create(publish.publishRecordId(), d("100"), "synthetic-test");
                        runs.start(created.paperRunId());
                        sim.advance(created.paperRunId());
                        sim.advance(created.paperRunId());
                        var decision = sim.advance(created.paperRunId());
                        assertEquals("ACCEPTED", decision.status());
                        BigDecimal notional =
                                decision.quantity().multiply(decision.executionPrice());
                        assertTrue(
                                notional.add(
                                                        notional.multiply(
                                                                        d(rate).max(
                                                                                        BigDecimal
                                                                                                .ZERO))
                                                                .setScale(8, RoundingMode.CEILING))
                                                .compareTo(d("100"))
                                        <= 0);
                        // 下一根bar在前单未成交时只能使用扣除本金预留后的现金，负费率不会让资金复用。
                        sim.advance(created.paperRunId());
                        assertEquals(1, sim.facts(created.paperRunId()).orders().size());
                        matching.matchOnce(100);
                        var facts = sim.facts(created.paperRunId());
                        assertEquals(1, facts.trades().size());
                        BigDecimal fee =
                                jdbc.queryForObject(
                                        "SELECT fee FROM trades WHERE account_id=?",
                                        BigDecimal.class,
                                        created.canonicalAccountId());
                        assertEquals(d(rate).signum(), fee.signum());
                        assertEquals(
                                0,
                                d("100").subtract(notional)
                                        .subtract(fee)
                                        .compareTo(
                                                funding.cashBalance(created.canonicalAccountId())));
                        assertEquals(
                                0,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM (SELECT currency FROM ledger_entries WHERE account_id=? GROUP BY currency HAVING sum(delta)<>0) invalid",
                                        Integer.class,
                                        created.canonicalAccountId()));
                        assertEquals(
                                1,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM risk_events WHERE scope='ORDER' AND scope_id=? AND decision='ALLOW'",
                                        Integer.class,
                                        decision.orderId()));
                        assertEquals(
                                spec,
                                tree(runs.getById(created.paperRunId()).configSnapshotJson())
                                        .path("executionSpec"));
                        long first = reopened(backtest.backtestRunId(), created.paperRunId());
                        long second = reopened(backtest.backtestRunId(), created.paperRunId());
                        assertNotEquals(first, second);
                        assertEquals(1, fixture.calls.get());
                        privateHttpReads(
                                config.backtestConfigId(),
                                backtest.backtestRunId(),
                                created.paperRunId());
                        privateChildReadsAndWrites(created.paperRunId());
                        fixture.calls.set(0);
                        String frozen =
                                jdbc.queryForObject(
                                        "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                                        String.class,
                                        created.paperRunId());
                        matching.matchOnce(100);
                        assertEquals(1, sim.facts(created.paperRunId()).trades().size());
                        assertEquals(
                                frozen,
                                jdbc.queryForObject(
                                        "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                                        String.class,
                                        created.paperRunId()));
                    }
                    assertEquals(
                            0,
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM orders WHERE trade_env='LIVE'",
                                    Integer.class));
                    assertEquals(
                            "ENGAGED",
                            jdbc.queryForObject(
                                    "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                                    String.class));
                    return null;
                });
    }

    @Test
    void forgedDeletedExpiredAndCrossScopeSourcesRejectWithoutObservationMutation()
            throws Exception {
        asUser(
                "frozen-owner",
                () -> {
                    var account = account();
                    long credential = credential(account);
                    var observation =
                            fees.sync(
                                    owner,
                                    account.exchangeAccountId(),
                                    UUID.randomUUID(),
                                    credential,
                                    "BTC-USDT");
                    ObjectNode reference = reference(account, observation.observationId());
                    ObjectNode prepared =
                            (ObjectNode) tree(source.prepareConfiguration(reference.toString()));
                    ObjectNode config = mapper.createObjectNode();
                    config.set("executionSpec", prepared);
                    String frozen =
                            source.freezeRunConfiguration(
                                    config.toString(), snapshot().toString(), Instant.now());
                    ObjectNode spec = (ObjectNode) tree(frozen).path("executionSpec");
                    source.validateFrozenExecution(spec.toString(), snapshot().toString());
                    // 删除费率、改原率、换来源并自行重算摘要，都不能替代持久观察。
                    ObjectNode missing = spec.deepCopy();
                    missing.remove("feeRate");
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    source.validateFrozenExecution(
                                            missing.toString(), snapshot().toString()));
                    ObjectNode changed = spec.deepCopy();
                    changed.put("feeRate", "0.9");
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    source.validateFrozenExecution(
                                            changed.toString(), snapshot().toString()));
                    ObjectNode forged = spec.deepCopy();
                    ObjectNode forgedSnapshot =
                            (ObjectNode) tree(spec.path("feeSourceSnapshotJson").asText());
                    forgedSnapshot.put("selectedRate", "0.9");
                    forged.put("feeSourceSnapshotJson", forgedSnapshot.toString());
                    forged.put(
                            "feeSourceSnapshotSha256",
                            FrozenAccountFeeModel.digest(forgedSnapshot));
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    source.validateFrozenExecution(
                                            forged.toString(), snapshot().toString()));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> source.prepareConfiguration(spec.toString()));
                    ObjectNode symbol = reference.deepCopy();
                    symbol.put("feeSymbol", "ETH-USDT");
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> source.prepareConfiguration(symbol.toString()));
                    ObjectNode environment = reference.deepCopy();
                    environment.put("feeTradeEnv", "LIVE");
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> source.prepareConfiguration(environment.toString()));
                    ObjectNode wrongAccount = reference.deepCopy();
                    wrongAccount.put("feeAccountId", Long.toString(account().exchangeAccountId()));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> source.prepareConfiguration(wrongAccount.toString()));
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    source.freezeRunConfiguration(
                                            config.toString(),
                                            snapshot().toString(),
                                            observation.expiresAt()));
                    ObjectNode wrongMarket = snapshot();
                    wrongMarket.put("symbol", "ETH-USDT");
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    source.freezeRunConfiguration(
                                            config.toString(),
                                            wrongMarket.toString(),
                                            Instant.now()));
                    var historical =
                            models.historical(
                                    owner,
                                    observation.observationId(),
                                    account.exchangeAccountId(),
                                    "OKX",
                                    "SIM",
                                    "BTC-USDT",
                                    Instant.parse(
                                            tree(spec.path("feeSourceSnapshotJson").asText())
                                                    .path("frozenAt")
                                                    .asText()));
                    assertEquals(observation, historical.observation());
                    assertEquals(1, fixture.calls.get());
                    try {
                        asUser(
                                "frozen-other",
                                () -> {
                                    assertThrows(
                                            IllegalArgumentException.class,
                                            () ->
                                                    source.prepareConfiguration(
                                                            reference.toString()));
                                    return null;
                                });
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                    return null;
                });
    }

    private ObjectNode reference(ExchangeAccountSummary account, UUID observation) {
        ObjectNode node = mapper.createObjectNode();
        node.put("costSource", FrozenAccountFeeModel.MODE);
        node.put("feeObservationId", observation.toString());
        node.put("feeAccountId", Long.toString(account.exchangeAccountId()));
        node.put("feeTradeEnv", "SIM");
        node.put("feeSymbol", "BTC-USDT");
        return node;
    }

    private ObjectNode snapshot() {
        return mapper.createObjectNode()
                .put("exchangeCode", "OKX")
                .put("marketType", "SPOT")
                .put("symbol", "BTC-USDT");
    }

    private JsonNode tree(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private ExchangeAccountSummary account() {
        return accounts.create(
                owner,
                new ExchangeAccountCreateCommand(
                        "OKX", "SIM", "frozen-fee-" + UUID.randomUUID(), null));
    }

    private long credential(ExchangeAccountSummary account) {
        return credentials
                .upsert(
                        owner,
                        account.exchangeAccountId(),
                        new ExchangeAccountCredentialUpsertCommand(
                                "OKX_API_V5",
                                "synthetic-frozen-key",
                                "synthetic-frozen-secret",
                                "synthetic-frozen-passphrase",
                                null),
                        1)
                .credentialId();
    }

    private String login(String username) throws Exception {
        return mapper.readTree(
                        mvc.perform(
                                        post("/api/auth/login")
                                                .contentType("application/json")
                                                .content(
                                                        mapper.writeValueAsString(
                                                                Map.of(
                                                                        "username",
                                                                        username,
                                                                        "password",
                                                                        PASSWORD))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("accessToken")
                .asText();
    }

    private <T> T asUser(String username, Supplier<T> work) throws Exception {
        var saved = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        tokens.parse(login(username)).orElseThrow(), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(saved);
        }
    }

    private void privateHttpReads(String configId, String runId, String paperId) {
        var saved = SecurityContextHolder.getContext();
        try {
            for (String username : List.of("frozen-other", "frozen-viewer")) {
                String token = login(username);
                for (String path :
                        List.of(
                                "/api/backtest-configs/" + configId,
                                "/api/backtest-runs/" + runId,
                                "/api/paper-trading/runs/" + paperId)) {
                    int status =
                            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                    assertTrue(Set.of(400, 404).contains(status), path + " status " + status);
                }
                for (String path :
                        List.of("/api/backtest-configs", "/api/backtest-runs", "/api/publishes")) {
                    String body =
                            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                                    .andExpect(status().isOk())
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString();
                    assertFalse(body.contains(runId));
                    assertFalse(body.contains(configId));
                    assertFalse(body.contains("feeSourceSnapshotJson"));
                }
            }
            String token = login("frozen-owner");
            String body =
                    mvc.perform(
                                    get("/api/backtest-runs/" + runId)
                                            .header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertTrue(body.contains("feeSourceSnapshotJson"));
            // 默认手工仓储不因缺少装配而公开私有模型。
            assertTrue(
                    new JdbcBacktestRunRepository(jdbc, coordinator)
                            .findByBacktestRunId(runId)
                            .isEmpty());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            SecurityContextHolder.setContext(saved);
        }
    }

    /** 原独立红例的业务回归：子对象权限与路径在写入前检查，拒绝后持久状态保持OPEN。 */
    private void privateChildReadsAndWrites(String paper) {
        var saved = SecurityContextHolder.getContext();
        try {
            var report =
                    monitor.generateDailyReport(
                            new PaperRunDailyReportGenerateCommand(
                                    paper, LocalDate.parse("2026-10-13")));
            var check =
                    stability.generate(
                            new PaperRunStabilityCheckGenerateCommand(
                                    paper,
                                    Instant.parse("2026-10-13T00:00:00Z"),
                                    Instant.parse("2026-10-13T01:00:00Z")));
            var alert =
                    monitor.createAlert(
                            new PaperRunAlertCreateCommand(
                                    paper,
                                    "CHILD_SCOPE_REGRESSION",
                                    "LOW",
                                    "Private alert",
                                    "synthetic",
                                    "TEST",
                                    "{}"));
            String base = "/api/paper-trading/runs/" + paper;
            for (String actor : List.of("frozen-other", "frozen-viewer")) {
                String token = login(actor);
                for (String path :
                        List.of(
                                base + "/daily-reports/" + report.reportId(),
                                base + "/stability-checks/" + check.stabilityCheckId())) {
                    mvc.perform(get(path).header("Authorization", "Bearer " + token))
                            .andExpect(status().isNotFound());
                }
                for (String action : List.of("ack", "resolve")) {
                    int result =
                            mvc.perform(
                                            patch(
                                                            base
                                                                    + "/alerts/"
                                                                    + alert.alertId()
                                                                    + "/"
                                                                    + action)
                                                    .header("Authorization", "Bearer " + token)
                                                    .header(
                                                            "Idempotency-Key",
                                                            UUID.randomUUID().toString()))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                    assertTrue(Set.of(403, 404).contains(result));
                    assertEquals(
                            "OPEN",
                            jdbc.queryForObject(
                                    "SELECT status FROM paper_run_alerts WHERE alert_id=?",
                                    String.class,
                                    alert.alertId()));
                }
                asUser(
                        actor,
                        () -> {
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> monitor.getDailyReportById(report.reportId()));
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> stability.getById(check.stabilityCheckId()));
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> monitor.ackAlert(alert.alertId(), actor));
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> monitor.resolveAlert(alert.alertId(), actor));
                            return null;
                        });
            }
            String token = login("frozen-owner"), wrong = "/api/paper-trading/runs/ptr-nonexistent";
            for (String path :
                    List.of(
                            wrong + "/daily-reports/" + report.reportId(),
                            wrong + "/stability-checks/" + check.stabilityCheckId())) {
                mvc.perform(get(path).header("Authorization", "Bearer " + token))
                        .andExpect(status().isNotFound());
            }
            for (String action : List.of("ack", "resolve")) {
                mvc.perform(
                                patch(wrong + "/alerts/" + alert.alertId() + "/" + action)
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(status().isNotFound());
                assertEquals(
                        "OPEN",
                        jdbc.queryForObject(
                                "SELECT status FROM paper_run_alerts WHERE alert_id=?",
                                String.class,
                                alert.alertId()));
            }
            mvc.perform(
                            get(base + "/daily-reports/" + report.reportId())
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mvc.perform(
                            get(base + "/stability-checks/" + check.stabilityCheckId())
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mvc.perform(
                            patch(base + "/alerts/" + alert.alertId() + "/ack")
                                    .header("Authorization", "Bearer " + token)
                                    .header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isOk());
            assertEquals(
                    "ACKED",
                    jdbc.queryForObject(
                            "SELECT status FROM paper_run_alerts WHERE alert_id=?",
                            String.class,
                            alert.alertId()));
            mvc.perform(
                            patch(base + "/alerts/" + alert.alertId() + "/resolve")
                                    .header("Authorization", "Bearer " + token)
                                    .header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isOk());
            assertEquals(
                    "RESOLVED",
                    jdbc.queryForObject(
                            "SELECT status FROM paper_run_alerts WHERE alert_id=?",
                            String.class,
                            alert.alertId()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            SecurityContextHolder.setContext(saved);
        }
    }

    /** 两个全新JVM只重读原运行，凭据撤销后的原来源及SIM快照必须一致。 */
    private long reopened(String backtest, String paper) {
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        FrozenFeeRestartProbeMain.class.getName(),
                        backtest,
                        paper);
        builder.environment().put("NQ_FROZEN_FEE_DB_URL", database.url());
        builder.environment()
                .put("NQ_FROZEN_FEE_DB_USER", System.getProperty("nq.postgres.smoke.user"));
        builder.environment()
                .put("NQ_FROZEN_FEE_DB_PASSWORD", System.getProperty("nq.postgres.smoke.password"));
        builder.redirectErrorStream(true);
        Process child = null;
        try {
            child = builder.start();
            assertTrue(child.waitFor(30, TimeUnit.SECONDS));
            String output =
                    new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, child.exitValue());
            assertTrue(output.contains("FROZEN_SOURCE_REOPEN=PASS"));
            return child.pid();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            if (child != null && child.isAlive()) child.destroyForcibly();
        }
    }

    /** 受控来源只在本测试装配，保留OKX供应方原始符号，不解密或联网。 */
    @TestConfiguration
    static class FixtureConfiguration {
        @Bean
        FixtureSource frozenFixtureSource() {
            return new FixtureSource();
        }
    }

    static class FixtureSource implements AccountFeeReadPort {
        BigDecimal rate = d("-0.001");
        final AtomicInteger calls = new AtomicInteger();

        public boolean supports(ExchangeAccountSummary account, String symbol) {
            return "OKX".equals(account.exchangeCode())
                    && "SIM".equals(account.tradeEnv())
                    && "BTC-USDT".equals(symbol);
        }

        public AccountFeeQuote read(
                ExchangeAccountSummary account,
                ExchangeAccountCredentialSummary credential,
                String symbol) {
            calls.incrementAndGet();
            Instant now = Instant.now();
            return new AccountFeeQuote(rate, rate, "FixtureLv1", now, now);
        }
    }

    private static HistoricalBar bar(int minute, String price) {
        Instant start = Instant.parse("2026-09-25T00:00:00Z").plusSeconds(minute * 60L);
        BigDecimal value = d(price);
        return new HistoricalBar(
                "OKX",
                "SPOT",
                "BTC-USDT",
                BarInterval.ONE_MINUTE,
                start,
                start.plusSeconds(59),
                value,
                value,
                value,
                value,
                BigDecimal.ONE,
                null,
                null,
                "OK",
                "{}",
                start.plusSeconds(59));
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** Spring完成上下文关闭后只销毁本夹具创建的数据库，失败场景也不遗留临时库。 */
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 999;
        }

        @Override
        public void afterTestClass(TestContext context) throws Exception {
            if (database != null) database.close();
        }
    }
}
