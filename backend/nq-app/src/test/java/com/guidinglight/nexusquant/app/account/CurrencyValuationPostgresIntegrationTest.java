package com.guidinglight.nexusquant.app.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.AccountPerformanceService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.smoke.TenantIsolationTestDatabase;
import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.common.currency.ValuationMetadata;
import com.guidinglight.nexusquant.ledger.application.service.SimCashFundingService;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.infra.paper.jdbc.JdbcEquityCurveSnapshotRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 真实隔离PG升级与正式Servlet/JWT证明；资产及行情SQL夹具只证明估值，不冒充成交生产链。 */
@EnabledIfSystemProperty(named = "nq.currency.pg.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(
        classes = NexusQuantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=false", "spring.sql.init.mode=never",
            "nq.auth.bootstrap-admin.enabled=false", "nq.runtime.trading-components.enabled=true",
            "nq.account.credentials.verification-mode=STRUCTURAL",
            "nq.account.credentials.master-key=currency-synthetic-master-key-123456789",
            "nq.security.issuer=nexus-quant-currency-synthetic",
            "nq.instrument.catalog-sync.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.env-safety.no-outbound=true",
            "nq.account.performance.sampling-enabled=false",
            "nq.okx.adapter.stub-on-bootstrap-failure=false"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = CurrencyValuationPostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class CurrencyValuationPostgresIntegrationTest {
    private static JdbcTemplate control;
    private static String database;
    private static String legacyFingerprint;
    private static Path evidence;
    private static boolean successful;
    private static TenantIsolationTestDatabase tenantDatabase;
    private static TenantIdentity localIdentity;
    private TenantContext.Scope localScope;
    private static final String PASSWORD = "synthetic-currency-fixture-password";
    private static final String TOKEN_SIGNING_SECRET = UUID.randomUUID().toString();
    private static final String ORIGINAL =
            """
SELECT md5(row_to_json(s)::text) FROM (SELECT equity_snapshot_id,paper_run_id,snapshot_time,
total_equity,cash_balance,position_value,unrealized_pnl,realized_pnl,drawdown,source,created_at
FROM equity_curve_snapshots WHERE equity_snapshot_id='currency-legacy') s
""";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired ExchangeAccountCommandService accounts;
    @Autowired SimCashFundingService funding;
    @Autowired AccountPerformanceService performance;
    @Autowired JdbcEquityCurveSnapshotRepository snapshots;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        registry.add("nq.security.secret", () -> TOKEN_SIGNING_SECRET);
        String url = System.getenv("TASK_CURRENCY_MAINTENANCE_URL");
        String user = System.getenv("TASK_CURRENCY_DB_USER");
        String password = System.getenv("TASK_CURRENCY_DB_PASSWORD");
        assertNotNull(url);
        assertNotNull(user);
        assertNotNull(password);
        URI uri = URI.create(url.substring("jdbc:".length()));
        assertTrue(
                Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                        && uri.getPort() > 0
                        && "/postgres".equals(uri.getPath())
                        && uri.getQuery() == null
                        && uri.getFragment() == null);
        evidence =
                Path.of(System.getProperty("nq.currency.pg.evidence"))
                        .toAbsolutePath()
                        .resolve(UUID.randomUUID().toString());
        Files.createDirectories(evidence);
        control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url + "?connectTimeout=5&socketTimeout=30", user, password));
        tenantDatabase=TenantIsolationTestDatabase.create("26",url,user,password);
        String owned=tenantDatabase.url();
        database=URI.create(owned.substring("jdbc:".length())).getPath().substring(1);
        Files.writeString(
                evidence.resolve("owned-database.json"),
                "{\"database\":\"" + database + "\",\"scope\":\"DISPOSABLE_LOCAL_ONLY\"}");
        var flyway26 =
                tenantDatabase.migration("26");
        flyway26.validate();
        var fixture = new JdbcTemplate(new DriverManagerDataSource(owned, user, password));
        // 历史元数据链仅为满足正式外键，不通过disable trigger或第二账本伪造生产成功。
        fixture.execute(
                """
INSERT INTO accounts(account_code,venue,status) VALUES('currency-fixture','BINANCE','ACTIVE');
INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)
VALUES('currency-source','currency-source','Explicit valuation fixture','SPOT_SMA_TARGET_V1','BINANCE',
    (SELECT account_id FROM accounts WHERE account_code='currency-fixture'),'SIM',false,'{}',1);
INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)
VALUES('currency-research','currency-source','Explicit valuation fixture','{}');
INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES('currency-config','currency-research','Explicit valuation fixture');
INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at,config_snapshot_json)
VALUES('currency-backtest','currency-config','currency-research','currency-source','SUCCEEDED','{}','{}','2026-10-01','{}');
INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name)
VALUES('currency-publish','currency-backtest','currency-research','currency-config','currency-source','SUCCEEDED','Explicit valuation fixture');
INSERT INTO paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at)
VALUES('currency-legacy-run','currency-publish','STOPPED','SIM','BINANCE','SPOT','BTC-USDT','1h','currency-owner','2026-10-01','2026-10-02');
INSERT INTO equity_curve_snapshots VALUES('currency-legacy','currency-legacy-run','2026-10-01',123.123456789012345678,100,23.123456789012345678,0.1,0.2,0.01,'MANUAL','2026-10-02');
""");
        legacyFingerprint = fixture.queryForObject(ORIGINAL, String.class);
        var flyway27 =
                tenantDatabase.migration("27");
        flyway27.migrate();
        flyway27.validate();
        try (var stream =
                CurrencyValuationPostgresIntegrationTest.class.getResourceAsStream(
                        "/db/migration/V27__currency_registry_and_equity_valuation.sql")) {
            Files.writeString(
                    evidence.resolve("migration-v27-applied.sha256"),
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(stream.readAllBytes())));
        }
        assertEquals(legacyFingerprint, fixture.queryForObject(ORIGINAL, String.class));
        assertEquals("27",flyway27.info().current().getVersion().getVersion());
        assertEquals("UNKNOWN",fixture.queryForObject("SELECT valuation_status FROM equity_curve_snapshots WHERE equity_snapshot_id='currency-legacy'",String.class));
        var rejected=assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->
                fixture.update("UPDATE equity_curve_snapshots SET valuation_currency='USDT' WHERE equity_snapshot_id='currency-legacy'"));
        assertEquals("23514",((java.sql.SQLException)rejected.getMostSpecificCause()).getSQLState());
        Files.writeString(evidence.resolve("historical-upgrade.json"),"{\"from\":26,\"to\":27,\"historyPreserved\":true,\"invalidWriteSqlstate\":\"23514\"}");
        // 历史升级断言先在 V27 完成；当前应用只接管继续正向升级后的独立运行库。
        tenantDatabase.migrate(null);tenantDatabase.runtime();
        tenantDatabase.user("currency-bootstrap-admin","ADMIN");
        var seedRepository=new com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository(tenantDatabase.admin);
        var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        for(String name:List.of("currency-owner","currency-other"))
            seedRepository.upsertSeedUser(new SeedUserCommand(name,encoder.encode(PASSWORD),List.of("VIEWER"),true));
        long localUser=tenantDatabase.admin.queryForObject("SELECT id FROM users WHERE username='currency-owner'",Long.class);
        localIdentity=new TenantIdentity(TenantId.LOCAL,"NQ_LOCAL",TenantKind.LOCAL,localUser);
        registry.add("spring.datasource.url", () -> owned + "?connectTimeout=5&socketTimeout=30");
        registry.add("spring.datasource.username", () -> tenantDatabase.runtime);
        registry.add("spring.datasource.password", () -> tenantDatabase.runtimePassword);
    }

    @BeforeEach void openLocalIdentity() { localScope=TenantContext.open(localIdentity); }
    @AfterEach void closeLocalIdentity() { if(localScope!=null)localScope.close(); }

    @Test
    void upgradeUnknownHistoryStrictNewWritesAndOwnedFormalValuation() throws Exception {
        assertEquals(legacyFingerprint, jdbc.queryForObject(ORIGINAL, String.class));
        var legacy = snapshots.listByRunId("currency-legacy-run").getFirst();
        assertEquals("UNKNOWN", legacy.valuation().status());
        assertNull(legacy.valuation().currency());
        assertEquals(new BigDecimal("123.123456789012345678"), legacy.totalEquity());
        assertThrows(IllegalStateException.class, () -> snapshots.insert(legacy));
        // 数据库也拒绝伪装带币种的UNKNOWN；旧SQL省略新增列仍明确记录为UNKNOWN。
        assertThrows(
                RuntimeException.class,
                () ->
                        jdbc.update(
                                "UPDATE equity_curve_snapshots SET valuation_currency='USDT' WHERE"
                                        + " equity_snapshot_id='currency-legacy'"));
        assertThrows(
                RuntimeException.class,
                () ->
                        jdbc.update(
                                "UPDATE equity_curve_snapshots SET valuation_status='VERIFIED'"
                                        + " WHERE equity_snapshot_id='currency-legacy'"));
        String details =
                "{\"accountId\":1,\"assets\":[{\"currency\":\"USDT\",\"amount\":\"100\",\"source\":\"EXPLICIT_SQL_FIXTURE\"}],\"precision\":{\"scale\":18,\"rounding\":\"UNNECESSARY\"}}";
        Instant oldTime = Instant.parse("2026-10-01T00:00:00Z");
        var known =
                new EquityCurveSnapshot(
                        "currency-new-fixture",
                        "currency-legacy-run",
                        oldTime,
                        new BigDecimal("100"),
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "EXPLICIT_FIXTURE",
                        oldTime,
                        new ValuationMetadata(
                                "USDT",
                                oldTime,
                                "EXPLICIT_SQL_FIXTURE",
                                "IDENTITY_FIXTURE_V1",
                                "VERIFIED",
                                details));
        // 回归两个独立反例：伪造来源以及舍入后分项不守恒都不能进入旧表。
        assertThrows(IllegalStateException.class, () -> snapshots.insert(known));
        var forged =
                new EquityCurveSnapshot(
                        "forged",
                        "currency-legacy-run",
                        oldTime,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "EXPLICIT_FIXTURE",
                        oldTime,
                        new ValuationMetadata(
                                "USDT",
                                oldTime,
                                "CLAIM_ONLY",
                                "v1",
                                "VERIFIED",
                                "{\"accountId\":9223372036854775807,\"assets\":[{}],\"precision\":{\"anything\":true}}"));
        assertThrows(IllegalStateException.class, () -> snapshots.insert(forged));
        var rounded =
                new EquityCurveSnapshot(
                        "rounded",
                        "currency-legacy-run",
                        oldTime,
                        new BigDecimal("1.2E-18"),
                        new BigDecimal("6E-19"),
                        new BigDecimal("6E-19"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "EXPLICIT_FIXTURE",
                        oldTime,
                        known.valuation());
        assertThrows(IllegalStateException.class, () -> snapshots.insert(rounded));
        assertEquals(
                1,
                jdbc.queryForObject("SELECT count(*) FROM equity_curve_snapshots", Integer.class));
        // 结构化元数据往返仅为SQL夹具；PARTIAL不冒充存在真实生成链，也不能用于可信汇总。
        jdbc.update(
                """
INSERT INTO equity_curve_snapshots SELECT 'currency-partial-fixture',paper_run_id,snapshot_time,
total_equity,cash_balance,position_value,unrealized_pnl,realized_pnl,drawdown,source,created_at,
'USDT',snapshot_time,'EXPLICIT_SQL_FIXTURE','METADATA_ROUNDTRIP_ONLY','PARTIAL',?::jsonb
FROM equity_curve_snapshots WHERE equity_snapshot_id='currency-legacy'
""",
                details);
        var reread =
                snapshots.listByRunId("currency-legacy-run").stream()
                        .filter(s -> s.equitySnapshotId().equals("currency-partial-fixture"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(mapper.readTree(details), mapper.readTree(reread.valuation().detailsJson()));
        assertEquals("PARTIAL", reread.valuation().status());
        assertEquals("USDT", reread.valuation().currency());
        assertEquals(legacyFingerprint, jdbc.queryForObject(ORIGINAL, String.class));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EquityCurveSnapshot(
                                "bad",
                                "currency-legacy-run",
                                oldTime,
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                "fixture",
                                oldTime,
                                new ValuationMetadata(
                                        "USDT",
                                        oldTime.plusNanos(1000),
                                        "fixture",
                                        "v1",
                                        "VERIFIED",
                                        details)));

        long owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='currency-owner'", Long.class);
        var account =
                accounts.create(
                        owner,
                        new ExchangeAccountCreateCommand("OKX", "SIM", "currency-estimate", null));
        // 估值夹具通过原注资服务入账，也必须提供真实同账户的运行父对象。
        jdbc.update("""
                INSERT INTO paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,
                    market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)
                SELECT 'currency-funding',publish_id,'STOPPED','SIM',exchange_code,market_type,symbol,
                    interval_code,'currency-owner',now(),now(),? FROM paper_trading_runs
                WHERE paper_run_id='currency-legacy-run'
                """,account.legacyAccountId());
        funding.fundOnce(
                account.legacyAccountId(),
                "currency-funding",
                new BigDecimal("100"),
                "EXPLICIT_VALUATION_FIXTURE");
        Instant now = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        for (String currency : List.of("BTC", "USDC"))
            jdbc.update(
                    """
INSERT INTO account_snapshots(account_id,currency,balance,available,frozen,ts,trace_id,trade_env,balance_basis,balance_scope,recorded_at)
VALUES(?,?,?,?,0,?,'currency-fixture','SIM','POSITION_PROJECTION','NQ_MANAGED_ACCOUNT',?)
""",
                    account.legacyAccountId(),
                    currency,
                    new BigDecimal(currency.equals("BTC") ? "2" : "10"),
                    new BigDecimal(currency.equals("BTC") ? "2" : "10"),
                    Timestamp.from(now.minusSeconds(1)),
                    Timestamp.from(now.minusSeconds(1)));
        performance.sample(account.exchangeAccountId(), "USDT", now);
        var first =
                performance
                        .read(
                                owner,
                                account.exchangeAccountId(),
                                "USDT",
                                now.minusSeconds(1),
                                now.plusSeconds(1))
                        .summary()
                        .samples()
                        .getFirst();
        assertNull(first.equity());
        assertEquals("VALUATION_INCOMPLETE", first.status());
        price("BTC-USDT", "50000", now.minusSeconds(60), now);
        price("USDC-USDT", "0.997", now.minusSeconds(60), now.plusSeconds(950));
        performance.sample(account.exchangeAccountId(), "USDT", now.plusSeconds(901));
        performance.sample(account.exchangeAccountId(), "USDT", now.plusSeconds(1802));
        var summary =
                performance
                        .read(
                                owner,
                                account.exchangeAccountId(),
                                "USDT",
                                now.minusSeconds(1),
                                now.plusSeconds(1803))
                        .summary();
        assertNull(summary.samples().get(1).equity());
        var last = summary.samples().getLast();
        assertEquals(0, new BigDecimal("100109.97").compareTo(last.equity()));
        assertNull(summary.pnl());
        JsonNode source = mapper.readTree(last.sourceJson());
        assertEquals("USDT", source.path("valuationCurrency").asText());
        assertEquals("VERIFIED", source.path("valuationStatus").asText());
        assertEquals("FINAL_TOTAL", source.path("valuationDetails").path("roundingPoint").asText());
        assertEquals(3, source.path("valuationDetails").path("assets").size());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trades", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));

        String token = login("currency-owner"), other = login("currency-other");
        assertEquals(401, request("/api/reference/currencies", null).statusCode());
        var registry = request("/api/reference/currencies", token);
        assertEquals(200, registry.statusCode());
        JsonNode directory = mapper.readTree(registry.body());
        assertEquals(6, directory.path("currencies").size());
        for (JsonNode entry : directory.path("currencies")) {
            assertEquals(
                    "USDT".equals(entry.path("code").asText()),
                    entry.path("valuationSupported").asBoolean());
            assertFalse(entry.path("displayConversionSupported").asBoolean());
            assertEquals(
                    "CNY".equals(entry.path("code").asText()),
                    entry.path("displayOnly").asBoolean());
        }
        String query =
                "/api/exchange-accounts/"
                        + account.exchangeAccountId()
                        + "/performance?from="
                        + now.minusSeconds(1)
                        + "&to="
                        + now.plusSeconds(1803);
        String before =
                jdbc.queryForObject(
                        "SELECT md5(string_agg(sample_id::text||source_json::text,'' ORDER BY"
                                + " sample_id)) FROM account_equity_measurements",
                        String.class);
        var formal = request(query + "&valuationCurrency=USDT", token);
        assertEquals(200, formal.statusCode());
        JsonNode body = mapper.readTree(formal.body());
        assertEquals("USDT", body.path("summary").path("valuationCurrency").asText());
        assertEquals(body, mapper.readTree(request(query + "&quoteCurrency=USDT", token).body()));
        assertEquals(404, request(query, other).statusCode());
        assertEquals(400, request(query + "&valuationCurrency=USD", token).statusCode());
        assertEquals(
                400,
                request(query + "&valuationCurrency=USDT&quoteCurrency=USD", token).statusCode());
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT md5(string_agg(sample_id::text||source_json::text,'' ORDER BY"
                                + " sample_id)) FROM account_equity_measurements",
                        String.class));
        assertEquals(legacyFingerprint, jdbc.queryForObject(ORIGINAL, String.class));
        Files.writeString(
                evidence.resolve("result.json"),
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(
                                Map.of(
                                        "status",
                                        "PASS_SCOPED_CURRENCY_PG_SERVLET",
                                        "legacyFingerprint",
                                        legacyFingerprint,
                                        "schema",
                                        27,
                                        "currencies",
                                        directory,
                                        "newAccountEquity",
                                        last.equity().toPlainString(),
                                        "source",
                                        source,
                                        "orders",
                                        0,
                                        "trades",
                                        0,
                                        "fixture",
                                        "EXPLICIT_ISOLATED_METADATA_PROJECTIONS_AND_PRICES_NOT_TRADING_PROOF")),
                StandardCharsets.UTF_8);
        successful = true;
    }

    private void price(String symbol, String rate, Instant close, Instant visible) {
        var value = new BigDecimal(rate);
        jdbc.update(
                """
INSERT INTO marketdata_bars(exchange_code,market_type,symbol,"interval",open_time,close_time,open_price,high_price,low_price,close_price,volume,source,quality_status,available_at)
VALUES('OKX','SPOT',?,'1m',?,?,?,?,?,?,1,'EXPLICIT_CURRENCY_FIXTURE','OK',?)
""",
                symbol,
                Timestamp.from(close.minusSeconds(59)),
                Timestamp.from(close),
                value,
                value,
                value,
                value,
                Timestamp.from(visible));
    }

    private String login(String name) throws Exception {
        var req =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        mapper.writeValueAsString(
                                                Map.of("username", name, "password", PASSWORD))))
                        .build();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var response = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(),()->response.statusCode()==200?"":response.body());
            return mapper.readTree(response.body()).path("accessToken").asText();
        }
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        var req =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10));
        if (token != null) req.header("Authorization", "Bearer " + token);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    public static class Cleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 999;
        }

        @Override
        public void afterTestClass(TestContext context) throws Exception {
            if (control != null && database != null && successful) {
                tenantDatabase.close();
                Files.writeString(
                        evidence.resolve("cleanup.json"),
                        "{\"ownedDatabase\":\"REMOVED_AFTER_SUCCESS\"}");
            }
        }
    }
}
