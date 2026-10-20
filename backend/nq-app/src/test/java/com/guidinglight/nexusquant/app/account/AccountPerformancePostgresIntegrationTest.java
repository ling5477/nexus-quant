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
import com.guidinglight.nexusquant.account.domain.performance.AccountEquitySample;
import com.guidinglight.nexusquant.account.domain.port.AccountPerformanceRepository;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.search.application.ObjectSearchService;
import com.guidinglight.nexusquant.search.infra.JdbcObjectSearchRepository;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

/** 真实PG16、JWT与正式SIM注资服务证明派生统计；测试投影/行情夹具不冒充成交经济证明。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
        classes = NexusQuantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
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
        listeners = AccountPerformancePostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class AccountPerformancePostgresIntegrationTest {
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
    @Autowired StrategyRunRepository strategyRuns;
    @Autowired AccountPerformanceRepository measurements;
    private long owner;

    @Test
    void statisticsUseOneCoverageSignedFeesAndOwnedReadonlyFacts() throws Exception {
        var account = account();
        funding.fundOnce(
                account.legacyAccountId(),
                "statistics-" + UUID.randomUUID(),
                new BigDecimal("1000"),
                "statistics-fixture");
        Instant at = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        // 受控采样与成交仅证明统计读取；它们不是撮合生成的经济验收，也不进入正式验收库。
        for (int index = 0; index < 3; index++)
            measurements.save(
                    new AccountEquitySample(
                            UUID.randomUUID(),
                            account.exchangeAccountId(),
                            "USDT",
                            at.plusSeconds(index * 900L),
                            new BigDecimal(List.of("1000", "1008", "1006").get(index)),
                            new BigDecimal("1000"),
                            "AVAILABLE",
                            "{\"source\":\"STATISTICS_FIXTURE\"}"));
        statisticsTrade(account.legacyAccountId(), "BUY", "2", "100", "2", at.minusSeconds(1));
        statisticsTrade(account.legacyAccountId(), "SELL", "1", "110", "1", at.plusSeconds(900));
        statisticsTrade(account.legacyAccountId(), "SELL", "1", "100", "1", at.plusSeconds(1800));
        long before =
                jdbc.queryForObject(
                        "SELECT count(*) FROM trades WHERE account_id=?",
                        Long.class,
                        account.legacyAccountId());
        var result =
                performance.read(
                        owner,
                        account.exchangeAccountId(),
                        "USDT",
                        at.minusSeconds(2),
                        at.plusSeconds(1801));
        assertEquals(0, result.summary().pnl().compareTo(new BigDecimal("6")));
        assertEquals("AVAILABLE", result.sharpe().status());
        assertTrue(
                result.sharpe()
                                .value()
                                .subtract(new BigDecimal("0.602543720190779"))
                                .abs()
                                .compareTo(new BigDecimal("1e-15"))
                        < 0);
        assertEquals(0, result.outcomes().winRate().compareTo(new BigDecimal("0.5")));
        assertEquals(0, result.outcomes().profitLossRatio().compareTo(new BigDecimal("4")));
        String path = "/api/exchange-accounts/" + account.exchangeAccountId() + "/performance";
        mvc.perform(
                        get(path)
                                .header("Authorization", "Bearer " + login("performance-owner"))
                                .param("from", at.minusSeconds(2).toString())
                                .param("to", at.plusSeconds(1801).toString()))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.outcomes.winningCount").value(1));
        mvc.perform(
                        get(path)
                                .header("Authorization", "Bearer " + login("performance-other"))
                                .param("from", at.minusSeconds(2).toString())
                                .param("to", at.plusSeconds(1801).toString()))
                .andExpect(status().isNotFound());
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT count(*) FROM trades WHERE account_id=?",
                        Long.class,
                        account.legacyAccountId()));
        assertEquals(
                3,
                performance
                        .read(owner, account.exchangeAccountId(), "USDT", at, at.plusSeconds(1801))
                        .summary()
                        .samples()
                        .size());
        jdbc.update(
                "UPDATE trades SET fee_currency='BTC' WHERE account_id=? AND fee<>0",
                account.legacyAccountId());
        assertEquals(
                "FEE_BASIS_UNAVAILABLE",
                performance
                        .read(owner, account.exchangeAccountId(), "USDT", at, at.plusSeconds(1801))
                        .outcomes()
                        .status());
    }

    private void statisticsTrade(
            long account, String side, String qty, String price, String fee, Instant at) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,qty,status,trace_id,venue,exchange_code,trade_env,created_at) VALUES(?,?,'BTC-USDT',?,?,'MARKET',?,'FILLED','statistics-fixture','OKX','OKX','SIM',?)",
                id,
                account,
                id,
                side,
                new BigDecimal(qty),
                Timestamp.from(at));
        jdbc.update(
                "INSERT INTO trades(trade_id,order_id,account_id,symbol,price,qty,fee,fee_currency,trace_id,exchange_code,trade_env,ts,created_at) VALUES(?,?,?,'BTC-USDT',?,?,?,'USDT','statistics-fixture','OKX','SIM',?,?)",
                id,
                id,
                account,
                new BigDecimal(price),
                new BigDecimal(qty),
                new BigDecimal(fee),
                Timestamp.from(at),
                Timestamp.from(at));
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("nq.postgres.smoke.url", ""),
                user = System.getProperty("nq.postgres.smoke.user", ""),
                password = System.getProperty("nq.postgres.smoke.password", "");
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
                        "SELECT current_setting('server_version_num')::integer/10000",
                        Integer.class));
        database = "account_performance_" + UUID.randomUUID().toString().replace("-", "");
        control.execute("CREATE DATABASE \"" + database + "\"");
        registry.add(
                "spring.datasource.url",
                () -> url.substring(0, url.lastIndexOf('/') + 1) + database);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
    }

    @BeforeEach
    void identities() {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "performance-owner",
                                passwords.encode(PASSWORD),
                                List.of("VIEWER"),
                                true),
                        new SeedUserCommand(
                                "performance-other",
                                passwords.encode(PASSWORD),
                                List.of("VIEWER"),
                                true)));
        owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='performance-owner'", Long.class);
    }

    private ExchangeAccountSummary account() {
        return accounts.create(
                owner,
                new ExchangeAccountCreateCommand(
                        "OKX", "SIM", "measure-" + UUID.randomUUID(), null));
    }

    @Test
    void objectSearchScopesOwnersAndTreatsWildcardsAsLiteralText() throws Exception {
        var own =
                accounts.create(
                        owner,
                        new ExchangeAccountCreateCommand("OKX", "SIM", "search_%_literal", null));
        long other =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='performance-other'", Long.class);
        accounts.create(
                other, new ExchangeAccountCreateCommand("OKX", "SIM", "search_%_private", null));
        var search = new ObjectSearchService(new JdbcObjectSearchRepository(jdbc));
        var result = search.search(owner, "search_%", "ALL", 0);
        assertEquals(1, result.items().size());
        assertEquals(own.exchangeAccountId().toString(), result.items().getFirst().id());
        assertEquals(0, search.search(owner, "private", "ACCOUNT", 0).items().size());
        assertThrows(IllegalArgumentException.class, () -> search.search(owner, "x", "ALL", 0));
        assertThrows(
                IllegalArgumentException.class, () -> search.search(owner, "search", "ALL", 1000));
        assertThrows(
                IllegalArgumentException.class,
                () -> search.search(owner, "search", "UNSUPPORTED", 0));
        mvc.perform(
                        get("/api/search")
                                .header("Authorization", "Bearer " + login("performance-owner"))
                                .param("q", "search_%"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/search").param("q", "search")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/search")
                                .header("Authorization", "Bearer " + login("performance-owner"))
                                .param("q", "search")
                                .param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void formallyCreatedStrategyAndVersionAreSearchableOnlyForTheirAccountOwner() throws Exception {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "search-operator",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true)));
        String token = login("search-operator"), code = "search-proof-" + UUID.randomUUID();
        var account =
                mapper.readTree(
                        mvc.perform(
                                        post("/api/exchange-accounts")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType("application/json")
                                                .content(
                                                        "{\"exchangeCode\":\"OKX\",\"tradeEnv\":\"SIM\",\"accountAlias\":\"search-strategy-account\"}"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        mvc.perform(
                        post("/api/strategies")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "strategyCode",
                                                        code,
                                                        "strategyName",
                                                        "Owned search strategy",
                                                        "strategyType",
                                                        "SPOT_SMA_TARGET_V1",
                                                        "exchangeCode",
                                                        "OKX",
                                                        "accountId",
                                                        account.path("legacyAccountId").asLong(),
                                                        "tradeEnv",
                                                        "SIM",
                                                        "configSnapshot",
                                                        "{}"))))
                .andExpect(status().isOk());
        var version =
                mapper.readTree(
                        mvc.perform(
                                        post("/api/strategies/" + code + "/versions")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType("application/json")
                                                .content(
                                                        "{\"versionName\":\"Frozen search version\",\"status\":\"DRAFT\",\"paramSnapshotJson\":\"{}\",\"configSnapshotJson\":\"{}\",\"sourceSnapshotJson\":\"{}\"}"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var result =
                mapper.readTree(
                        mvc.perform(
                                        get("/api/search")
                                                .header("Authorization", "Bearer " + token)
                                                .param("q", code))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(2, result.path("items").size());
        assertTrue(
                StreamSupport.stream(result.path("items").spliterator(), false)
                        .anyMatch(
                                hit ->
                                        hit.path("kind").asText().equals("VERSION")
                                                && hit.path("id")
                                                        .asText()
                                                        .equals(
                                                                version.path("strategyVersionId")
                                                                        .asText())));
        mvc.perform(
                        get("/api/search")
                                .header("Authorization", "Bearer " + login("performance-other"))
                                .param("q", code))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(0));
    }

    @Test
    void fundingIsNotProfitOrCancelledByContraAndGetHasNoSideEffect() throws Exception {
        var account = account();
        funding.fundOnce(
                account.legacyAccountId(),
                "measurement-" + UUID.randomUUID(),
                new BigDecimal("123456789012345.12345678"),
                "measurement-proof");
        Instant first = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS),
                second = first.plusSeconds(901);
        performance.sample(account.exchangeAccountId(), "USDT", first);
        performance.sample(account.exchangeAccountId(), "USDT", first.plusSeconds(2));
        performance.sample(account.exchangeAccountId(), "USDT", second);
        var view =
                performance.read(
                        owner,
                        account.exchangeAccountId(),
                        "USDT",
                        first.minusSeconds(1),
                        second.plusSeconds(1));
        assertEquals("AVAILABLE", view.summary().status());
        assertEquals(0, view.summary().pnl().compareTo(BigDecimal.ZERO));
        assertEquals(0, view.summary().netFlow().compareTo(BigDecimal.ZERO));
        assertEquals(0, view.summary().maxDrawdown().compareTo(BigDecimal.ZERO));
        assertEquals(
                0,
                view.summary().finalEquity().compareTo(new BigDecimal("123456789012345.12345678")));
        assertEquals(2, view.summary().samples().size());
        assertTrue(
                view.summary()
                        .samples()
                        .getFirst()
                        .sourceJson()
                        .contains("LEDGER_CASH_PROJECTION"));
        var token = login("performance-owner");
        String path = "/api/exchange-accounts/" + account.exchangeAccountId() + "/performance";
        long measurementsBefore = count("account_equity_measurements");
        var body =
                mvc.perform(
                                get(path)
                                        .header("Authorization", "Bearer " + token)
                                        .param("from", first.minusSeconds(1).toString())
                                        .param("to", second.plusSeconds(1).toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertTrue(
                body.contains("\"finalEquity\":" + view.summary().finalEquity().toPlainString()));
        assertEquals(measurementsBefore, count("account_equity_measurements"));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM ledger_entries WHERE account_id=?",
                        Long.class,
                        account.legacyAccountId()));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM account_snapshots WHERE account_id=?",
                        Long.class,
                        account.legacyAccountId()));
        mvc.perform(
                        get(path)
                                .header("Authorization", "Bearer " + login("performance-other"))
                                .param("from", first.toString())
                                .param("to", second.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).param("from", first.toString()).param("to", second.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get(path)
                                .header("Authorization", "Bearer " + token)
                                .param("from", second.toString())
                                .param("to", first.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get(path)
                                .header("Authorization", "Bearer " + token)
                                .param("from", first.toString())
                                .param("to", second.toString())
                                .param("quoteCurrency", "CNY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void canonicalSimSearchAndExactOrderReadUsePublishedOwnershipBeyondOverviewWindow()
            throws Exception {
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
                "INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version) VALUES(?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}',1)",
                strategy,
                strategy,
                strategy,
                own.legacyAccountId());
        jdbc.update(
                "INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,param_snapshot_json,config_snapshot_json,source_snapshot_json,checksum) VALUES(?,?,1,'scope','ACTIVE','{}','{}','{}',?)",
                version,
                strategy,
                "a".repeat(64));
        jdbc.update(
                "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES(?,?,?,'{}')",
                research,
                strategy,
                research);
        jdbc.update(
                "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES(?,?,?)",
                config,
                research,
                config);
        jdbc.update(
                "INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at) VALUES(?,?,?,?,'CREATED','{}','{}',now())",
                backtest,
                config,
                research,
                strategy);
        jdbc.update(
                "INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name,strategy_version_id) VALUES(?,?,?,?,?,'SUCCEEDED','scope',?)",
                published,
                backtest,
                research,
                config,
                strategy,
                version);
        long canonical =
                jdbc.queryForObject(
                        "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE') RETURNING account_id",
                        Long.class,
                        "scope-canonical-" + suffix);
        jdbc.update(
                "INSERT INTO paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id) VALUES(?,?,?,'CREATED','SIM','OKX','SPOT','BTC-USDT','1h','system',now(),now(),?)",
                paper,
                published,
                version,
                canonical);
        String simStrategy = "scope-sim-" + suffix, request = "scope-request-" + suffix;
        jdbc.update(
                "INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version) VALUES(?,?,?,'SPOT_SMA_TARGET_V1','PAPER',?,'SIM',true,'{}',1)",
                simStrategy,
                simStrategy,
                simStrategy,
                canonical);
        var admitted =
                new StrategyRun(
                        run,
                        simStrategy,
                        canonical,
                        "PAPER",
                        "SIM",
                        "MANUAL",
                        StrategyRunStatus.CREATED,
                        "{}",
                        request,
                        Instant.now(),
                        null,
                        null,
                        "scope-fixture");
        var work =
                new StrategyDispatchWork(
                        run,
                        1,
                        1,
                        canonical,
                        "coid-" + request,
                        "BTC-USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        new BigDecimal("0.00000001"),
                        null,
                        "IOC");
        assertTrue(strategyRuns.admit(admitted, work, null).admitted());
        for (int index = 0; index < 501; index++) {
            String order = "scope-order-" + suffix + "-" + index;
            jdbc.update(
                    "INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,qty,status,trace_id,venue,exchange_code,trade_env,created_at) VALUES(?,?,'BTC-USDT',?,'BUY','MARKET',0.00000001,'REJECTED','scope-fixture','PAPER','PAPER','SIM',now()+?*interval '1 second')",
                    order,
                    canonical,
                    order,
                    index);
        }
        String last = "scope-order-" + suffix + "-500",
                token = login("performance-owner"),
                other = login("performance-other");
        for (var identity : Map.of("SIM", paper, "RUN", run, "ORDER", last).entrySet()) {
            var found =
                    mapper.readTree(
                            mvc.perform(
                                            get("/api/search")
                                                    .header("Authorization", "Bearer " + token)
                                                    .param("q", identity.getValue())
                                                    .param("kind", identity.getKey()))
                                    .andExpect(status().isOk())
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString());
            assertEquals(1, found.path("items").size());
            assertEquals(paper, found.path("items").get(0).path("paperRunId").asText());
            mvc.perform(
                            get("/api/search")
                                    .header("Authorization", "Bearer " + other)
                                    .param("q", identity.getValue()))
                    .andExpect(status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(0));
        }
        assertFalse(
                jdbc.queryForList(
                                "SELECT order_id FROM orders WHERE account_id=? ORDER BY created_at,order_id LIMIT 500",
                                String.class,
                                canonical)
                        .contains(last));
        String path = "/api/paper-trading/strategy-sim/runs/" + paper + "/orders/" + last;
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders WHERE account_id=?", Long.class, canonical);
        var details =
                mapper.readTree(
                        mvc.perform(get(path).header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(last, details.path("order").path("order_id").asText());
        assertEquals("0.00000001", details.path("order").path("exact_qty").asText());
        mvc.perform(get(path).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(
                        get(path.replace(paper, "missing-run"))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        assertEquals(
                count,
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders WHERE account_id=?", Long.class, canonical));
        String refreshPath = path + "/refresh-execution";
        mvc.perform(post(refreshPath).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "performance-owner",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "performance-other",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true)));
        mvc.perform(
                        post(refreshPath)
                                .header("Authorization", "Bearer " + login("performance-other")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(refreshPath)
                                .header("Authorization", "Bearer " + login("performance-owner")))
                .andExpect(status().isConflict());
        assertEquals(
                count,
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders WHERE account_id=?", Long.class, canonical));
        assertEquals(
                "CREATED", strategyRuns.findByStrategyRunId(run).orElseThrow().status().name());
    }

    @Test
    void valuationUsesOneProjectionAndVisibleClosedMarketPrice() throws Exception {
        var account = account();
        funding.fundOnce(
                account.legacyAccountId(),
                "valuation-" + UUID.randomUUID(),
                new BigDecimal("100"),
                "measurement-proof");
        Instant now = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        // 这是受控资产投影与行情fixture，只证明估值边界，不声称已发生BTC成交。
        jdbc.update(
                """
                INSERT INTO account_snapshots(account_id,currency,balance,available,frozen,ts,trace_id,trade_env,balance_basis,balance_scope,recorded_at)
                VALUES(?,'BTC',2,2,0,?,'valuation-fixture','SIM','POSITION_PROJECTION','NQ_MANAGED_ACCOUNT',?)
                """,
                account.legacyAccountId(),
                Timestamp.from(now.minusSeconds(1)),
                Timestamp.from(now.minusSeconds(1)));
        performance.sample(account.exchangeAccountId(), "USDT", now);
        var missing =
                performance.read(
                        owner,
                        account.exchangeAccountId(),
                        "USDT",
                        now.minusSeconds(1),
                        now.plusSeconds(1));
        assertNull(missing.summary().samples().getFirst().equity());
        assertEquals("VALUATION_INCOMPLETE", missing.summary().samples().getFirst().status());
        var later = now.plusSeconds(901);
        insertPrice(now.minusSeconds(60), later.plusSeconds(1), "900");
        performance.sample(account.exchangeAccountId(), "USDT", later);
        assertNull(
                performance
                        .read(owner, account.exchangeAccountId(), "USDT", now, later)
                        .summary()
                        .samples()
                        .getLast()
                        .equity());
        insertPrice(now.minusSeconds(120), now, "50");
        var available = later.plusSeconds(901);
        performance.sample(account.exchangeAccountId(), "USDT", available);
        var result = performance.read(owner, account.exchangeAccountId(), "USDT", now, available);
        // 最新可见fixture价格900已在available时可见：100现金+2BTC×900，不能再加一次position。
        assertEquals(
                0, result.summary().samples().getLast().equity().compareTo(new BigDecimal("1900")));
        assertTrue(result.summary().samples().getLast().sourceJson().contains("FIXTURE"));
        assertNull(result.summary().pnl());
        assertEquals("INSUFFICIENT_OR_PARTIAL", result.summary().status());
    }

    @Test
    void unknownEnvironmentProjectionIsExcludedInsteadOfSummed() {
        var account = account();
        funding.fundOnce(
                account.legacyAccountId(),
                "ambiguous-" + UUID.randomUUID(),
                new BigDecimal("100"),
                "measurement-proof");
        jdbc.update(
                "INSERT INTO account_snapshots(account_id,currency,balance,available,frozen,ts,trace_id) VALUES(?,'USDT',999,999,0,now(),'legacy-fixture')",
                account.legacyAccountId());
        Instant at = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        performance.sample(account.exchangeAccountId(), "USDT", at);
        var view =
                performance.read(
                        owner,
                        account.exchangeAccountId(),
                        "USDT",
                        at.minusSeconds(1),
                        at.plusSeconds(1));
        assertNull(view.summary().finalEquity());
        assertNull(view.summary().pnl());
        assertFalse(
                mapperValue(
                        view.summary().samples().getFirst().sourceJson(), "projectionComplete"));
    }

    private boolean mapperValue(String json, String field) {
        try {
            return mapper.readTree(json).path(field).asBoolean();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void insertPrice(Instant close, Instant visibleAt, String price) {
        jdbc.update(
                """
                INSERT INTO marketdata_bars(exchange_code,market_type,symbol,"interval",open_time,close_time,open_price,high_price,low_price,close_price,volume,source,quality_status,available_at)
                VALUES('OKX','SPOT','BTC-USDT','1m',?,?,?,?,?,?,1,'FIXTURE','OK',?)
                """,
                Timestamp.from(close.minusSeconds(59)),
                Timestamp.from(close),
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(price),
                Timestamp.from(visibleAt));
    }

    private long count(String table) {
        assertTrue(Set.of("account_equity_measurements").contains(table));
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String login(String username) throws Exception {
        var body =
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
                assertTrue(database.matches("account_performance_[0-9a-f]{32}"));
                control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
            }
        }
    }
}
