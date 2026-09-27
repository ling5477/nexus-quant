package com.guidinglight.nexusquant.app.account;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot;
import com.guidinglight.nexusquant.account.infra.okx.readonly.AccountDivergenceReport;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxAccountFactsObservationService;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxCurrentPublicRuleReader;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateBalanceFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateFeeFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivatePositionFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxAccountFactsReadTransport;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchScope;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot.Status.OBSERVED;
import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot.Status.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 完整迁移后的隔离 PostgreSQL 证明；账户、凭证、行情及私有响应全部为合成事实。
 * 仅接受显式本机测试配置或 CI 的本机数据库，随机 schema 是唯一可写范围，不创建业务表替身。
 */
class OkxAccountFactsPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");
    private static final BigDecimal BTC = new BigDecimal("0.25");
    private static final BigDecimal USDT = new BigDecimal("12.5");
    private static final long OWNER_ID = 7L;
    private static final long ACCOUNT_ID = 8L;
    private static final long CREDENTIAL_ID = 9L;
    private static final List<String> CANONICAL_TABLES = List.of(
            "accounts", "orders", "trades", "ledger_entries", "ledger_events", "positions",
            "account_snapshots", "event_store", "sim_orders", "sim_trades",
            "paper_trading_runs", "paper_trading_orders", "paper_trading_trades", "paper_trading_positions",
            "kill_switch_states");
    private static JdbcTemplate admin;
    private static JdbcTemplate jdbc;
    private static String schema;

    private final ExchangeAccountRepository accounts = mock(ExchangeAccountRepository.class);
    private final ExchangeAccountCredentialRepository credentials = mock(ExchangeAccountCredentialRepository.class);
    private final KillSwitchService kill = mock(KillSwitchService.class);
    private final OkxAccountFactsReadTransport transport = mock(OkxAccountFactsReadTransport.class);
    private final OkxCurrentPublicRuleReader publicRules = mock(OkxCurrentPublicRuleReader.class);
    private final SyntheticExecutor executor = new SyntheticExecutor();
    private long legacyAccountId;
    private String clientOrderId;

    @BeforeAll
    static void migrateDisposableSchema() {
        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        String url = configuration("nq.postgres.smoke.url", "NQ_DB_URL", ci).trim();
        String user = configuration("nq.postgres.smoke.user", "NQ_DB_USER", ci).trim();
        String password = configuration("nq.postgres.smoke.password", "NQ_DB_PASSWORD", ci);
        boolean required = Boolean.getBoolean("nq.postgres.smoke.required");
        boolean configured = !url.isBlank() && !user.isBlank() && !password.isBlank();
        if (!required) assumeTrue(configured, "disposable PostgreSQL configuration is absent");
        assertTrue(configured, "required disposable PostgreSQL configuration is absent");
        assertTrue(isLoopbackDatabase(url), "only an explicit loopback PostgreSQL URL without URL options is accepted");

        schema = "account_facts_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                url + "?connectTimeout=5&socketTimeout=30", user, password);
        admin = new JdbcTemplate(dataSource);
        admin.setQueryTimeout(30);
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema).createSchemas(true).target("54").load();
        flyway.migrate();
        flyway.validate();
        assertEquals("54", flyway.info().current().getVersion().getVersion());
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                url + "?connectTimeout=5&socketTimeout=30&currentSchema=" + schema, user, password));
        jdbc.setQueryTimeout(15);
        assertEquals(schema, jdbc.queryForObject("SELECT current_schema()", String.class));
    }

    @AfterAll
    static void dropOnlyOwnedSchema() {
        if (admin == null || schema == null) return;
        assertTrue(schema.matches("account_facts_[0-9a-f]{32}"));
        admin.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
    }

    @BeforeEach
    void seedCanonicalFactsAndSyntheticReads() {
        legacyAccountId = insertAccount();
        clientOrderId = "client-" + UUID.randomUUID();
        insertOrder(legacyAccountId, clientOrderId, "BTC-USDT", "LIVE", "ACCEPTED");
        String settledOrder = insertOrder(legacyAccountId, "settled-" + UUID.randomUUID(),
                "BTC-USDT", "LIVE", "FILLED");
        jdbc.update("""
                INSERT INTO trades (trade_id, order_id, account_id, symbol, exchange, exchange_code,
                    exchange_trade_id, trade_env, price, qty, fee, fee_currency, trace_id, ts)
                VALUES (?, ?, ?, 'BTC-USDT', 'OKX', 'OKX', ?, 'LIVE', 10, 0.25, 0, 'USDT', 'synthetic', ?)
                """, UUID.randomUUID().toString(), settledOrder, legacyAccountId,
                UUID.randomUUID().toString(), Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("""
                INSERT INTO ledger_entries (entry_id, account_id, currency, delta, balance_after,
                    direction, ref_type, ref_id, idempotency_key, trace_id, ts)
                VALUES (?, ?, 'BTC', 0.25, 0.25, 'CREDIT', 'TRADE', ?, ?, 'synthetic', ?)
                """, UUID.randomUUID().toString(), legacyAccountId, settledOrder,
                UUID.randomUUID().toString(), Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("""
                INSERT INTO positions (account_id, symbol, qty, available_qty, frozen_qty, avg_price, trace_id)
                VALUES (?, 'BTC-USDT', 0.25, 0.25, 0, 10, 'synthetic')
                """, legacyAccountId);
        // 同币种同时间戳先插入旧值，以验证真实 DISTINCT ON 的 snapshot_id 次序。
        insertSnapshot(legacyAccountId, "BTC", BigDecimal.ONE, NOW.minusSeconds(10));
        insertSnapshot(legacyAccountId, "USDT", BigDecimal.ONE, NOW.minusSeconds(10));
        insertSnapshot(legacyAccountId, "BTC", BTC, NOW.minusSeconds(10));
        insertSnapshot(legacyAccountId, "USDT", USDT, NOW.minusSeconds(10));
        long otherAccount = insertAccount();
        insertSnapshot(otherAccount, "BTC", BigDecimal.TEN, NOW);
        insertSnapshot(otherAccount, "USDT", BigDecimal.TEN, NOW);
        insertOrder(otherAccount, "other-" + UUID.randomUUID(), "BTC-USDT", "LIVE", "ACCEPTED");

        when(kill.snapshot()).thenReturn(new KillSwitchSnapshot(KillSwitchScope.GLOBAL_TRADING,
                KillSwitchStatus.ENGAGED, 1, "SYNTHETIC", "SYNTHETIC", NOW, NOW, "synthetic"));
        when(accounts.findByIdForOwner(OWNER_ID, ACCOUNT_ID)).thenReturn(Optional.of(new ExchangeAccountSummary(
                ACCOUNT_ID, legacyAccountId, OWNER_ID, "OKX", "LIVE", "synthetic", null, true, "ACTIVE")));
        when(credentials.findByCredentialIdForOwner(OWNER_ID, ACCOUNT_ID, CREDENTIAL_ID)).thenReturn(Optional.of(
                new ExchangeAccountCredentialSummary(CREDENTIAL_ID, ACCOUNT_ID, "OKX_API_V5", "masked",
                        "ACTIVE", "VERIFIED", true, null, null, null, NOW, null, NOW,
                        "SUCCEEDED", "READ_ONLY", false, "PASSED", 0, NOW.minusSeconds(30), null)));
        when(transport.readServerTime()).thenReturn(NOW);
        when(publicRules.observeDetailed()).thenReturn(new AccountFactsSnapshot.Fact<>(OBSERVED,
                new OkxCurrentPublicRuleReader.CurrentRule("synthetic-current-rule", new BigDecimal("0.00001")),
                NOW, NOW.plusSeconds(60), "SYNTHETIC_PUBLIC_RULE", null));
        executor.orders = List.of(externalOrder(clientOrderId));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyOrZeroPositionsMatchCompleteCanonicalFacts(boolean includeZeroPosition) {
        noActiveOrders();
        executor.positions = includeZeroPosition ? List.of(position("0")) : List.of();
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(OBSERVED, result.positions().status());
        assertEquals("NO_ACTIVE_POSITION", result.positions().reason());
        assertEquals(executor.positions, result.positions().value());
        assertEquals("MATCH", result.divergence().value());
        assertEquals(OBSERVED, result.status());
        assertEquals(BTC, result.spotBtcExposure().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "-2"})
    void nonzeroPositionsDivergeWithoutChangingSpotExposure(String quantity) {
        noActiveOrders();
        executor.positions = List.of(position(quantity));
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals("DIVERGED", result.divergence().value());
        assertEquals("EXTERNAL_NON_SPOT_POSITION_PRESENT", result.divergence().reason());
        assertHasClassification(result, AccountDivergenceReport.Classification.EXTERNAL_NON_SPOT_POSITION_PRESENT);
        assertEquals(OBSERVED, result.status());
        assertEquals(BTC, result.spotBtcExposure().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BTC", "USDT"})
    void differentBalanceDiverges(String currency) {
        noActiveOrders();
        if ("BTC".equals(currency)) executor.btc = BTC.add(BigDecimal.ONE);
        else executor.usdt = USDT.add(BigDecimal.ONE);
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals("DIVERGED", result.divergence().value());
        assertHasClassification(result, AccountDivergenceReport.Classification.BALANCE_MISMATCH);
        assertEquals(OBSERVED, result.status());
    }

    @Test
    void missingExternalOrderDiverges() {
        executor.orders = List.of();
        assertHasClassification(observeWithoutCanonicalMutation(),
                AccountDivergenceReport.Classification.LOCAL_ACTIVE_ORDER_ONLY);
    }

    @Test
    void emptyOrderSetsMatchOnlyWithCompleteCanonicalBalances() {
        noActiveOrders();
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals("MATCH", result.divergence().value());
        assertEquals(OBSERVED, result.status());
    }

    @Test
    void unmatchedExternalOrderDiverges() {
        executor.orders = List.of(externalOrder("unmatched-synthetic-client"));
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertHasClassification(result, AccountDivergenceReport.Classification.EXTERNAL_OPEN_ORDER_ONLY);
        assertHasClassification(result, AccountDivergenceReport.Classification.LOCAL_ACTIVE_ORDER_ONLY);
        assertEquals(AccountDivergenceReport.Classification.UNKNOWN,
                result.divergenceReport().aggregate());
    }

    @Test
    void sameOrderIdentityWithDifferentQuantityDiverges() {
        jdbc.update("UPDATE orders SET qty=2 WHERE account_id=? AND client_order_id=?",
                legacyAccountId, clientOrderId);
        assertHasClassification(observeWithoutCanonicalMutation(),
                AccountDivergenceReport.Classification.ORDER_QUANTITY_MISMATCH);
    }

    @Test
    void sameTotalWithDifferentAvailableAndFrozenBalanceIsSemanticallyUnknown() {
        noActiveOrders();
        jdbc.update("""
                UPDATE account_snapshots SET available=balance-0.1, frozen=0.1
                WHERE account_id=? AND currency='USDT'
                """, legacyAccountId);
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(UNKNOWN, result.divergence().status());
        assertHasClassification(result, AccountDivergenceReport.Classification.BALANCE_SEMANTIC_MISMATCH);
    }

    @Test
    void currentMinimumSizeClassifiesPositiveBtcResidualWithoutRoundingToZero() {
        jdbc.update("UPDATE account_snapshots SET balance=0, available=0 WHERE account_id=? AND currency='BTC'",
                legacyAccountId);
        executor.btc = new BigDecimal("0.00000001");
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertHasClassification(result, AccountDivergenceReport.Classification.EXTERNAL_DUST_BALANCE);
        assertEquals(new BigDecimal("0.00000001"), result.balances().get("BTC").value().total());
    }

    @Test
    void positiveUnexpectedAssetIsNotSilentlyDiscarded() {
        noActiveOrders();
        executor.usdc = BigDecimal.ONE;
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertHasClassification(result, AccountDivergenceReport.Classification.UNEXPECTED_EXTERNAL_ASSET);
        assertEquals("DIVERGED", result.divergence().value());
    }

    @Test
    void equalButOldCanonicalSnapshotsCannotMatch() {
        noActiveOrders();
        jdbc.update("UPDATE account_snapshots SET ts=? WHERE account_id=?",
                Timestamp.from(NOW.minusSeconds(120)), legacyAccountId);
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(UNKNOWN, result.divergence().status());
        assertHasClassification(result, AccountDivergenceReport.Classification.CANONICAL_FACT_STALE);
    }

    @Test
    void localSentAndExternalLiveCannotMatchStateClass() {
        jdbc.update("UPDATE orders SET status='SENT' WHERE account_id=? AND client_order_id=?",
                legacyAccountId, clientOrderId);
        assertHasClassification(observeWithoutCanonicalMutation(),
                AccountDivergenceReport.Classification.ORDER_STATE_MISMATCH);
    }

    @Test
    void localPositionAndSnapshotMismatchIsVisible() {
        noActiveOrders();
        jdbc.update("UPDATE positions SET qty=0.5, updated_at=? WHERE account_id=? AND symbol='BTC-USDT'",
                Timestamp.from(NOW), legacyAccountId);
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertHasClassification(result, AccountDivergenceReport.Classification.POSITION_MISMATCH);
        assertEquals("DIVERGED", result.divergence().value());
    }

    @Test
    void exchangeOrderIdChangesCanonicalFactIdentity() {
        AccountFactsSnapshot before = observeWithoutCanonicalMutation();
        jdbc.update("UPDATE orders SET exchange_order_id=? WHERE account_id=? AND client_order_id=?",
                "changed-" + clientOrderId, legacyAccountId, clientOrderId);
        executor.operations.clear();
        AccountFactsSnapshot after = observeWithoutCanonicalMutation();
        assertHasClassification(after, AccountDivergenceReport.Classification.ORDER_IDENTITY_MISMATCH);
        assertTrue(!before.divergenceReport().canonicalFactIdentity()
                .equals(after.divergenceReport().canonicalFactIdentity()));
    }

    @Test
    void staleLocalOnlyAssetCannotBeCalledCurrentDivergence() {
        noActiveOrders();
        insertSnapshot(legacyAccountId, "USDC", BigDecimal.ONE, NOW.minusSeconds(120));
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(UNKNOWN, result.divergence().status());
        assertHasClassification(result, AccountDivergenceReport.Classification.CANONICAL_FACT_STALE);
        assertTrue(result.divergenceReport().items().stream().noneMatch(item ->
                item.classification() == AccountDivergenceReport.Classification.UNEXPECTED_LOCAL_ASSET));
    }

    @Test
    void venueChangeChangesCanonicalFactIdentity() {
        AccountFactsSnapshot before = observeWithoutCanonicalMutation();
        jdbc.update("UPDATE orders SET venue='BINANCE' WHERE account_id=? AND client_order_id=?",
                legacyAccountId, clientOrderId);
        executor.operations.clear();
        AccountFactsSnapshot after = observeWithoutCanonicalMutation();
        assertHasClassification(after, AccountDivergenceReport.Classification.ORDER_IDENTITY_MISMATCH);
        assertTrue(!before.divergenceReport().canonicalFactIdentity()
                .equals(after.divergenceReport().canonicalFactIdentity()));
    }

    @Test
    void twoTotalDifferencesRetainTwoItems() {
        noActiveOrders();
        executor.btc = BTC.add(BigDecimal.ONE);
        executor.usdt = USDT.add(BigDecimal.ONE);
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(AccountDivergenceReport.Classification.MULTIPLE_DIVERGENCES,
                result.divergenceReport().aggregate());
        assertEquals(2, result.divergenceReport().items().stream()
                .filter(item -> item.classification() == AccountDivergenceReport.Classification.BALANCE_MISMATCH)
                .count());
    }

    @Test
    void changedLimitPriceHasSpecificClassification() {
        jdbc.update("UPDATE orders SET price=11 WHERE account_id=? AND client_order_id=?",
                legacyAccountId, clientOrderId);
        assertHasClassification(observeWithoutCanonicalMutation(),
                AccountDivergenceReport.Classification.ORDER_PRICE_MISMATCH);
    }

    @Test
    void changedSideHasIdentityClassification() {
        jdbc.update("UPDATE orders SET side='SELL' WHERE account_id=? AND client_order_id=?",
                legacyAccountId, clientOrderId);
        assertHasClassification(observeWithoutCanonicalMutation(),
                AccountDivergenceReport.Classification.ORDER_IDENTITY_MISMATCH);
    }

    @Test
    void duplicateProviderOrderIsUnknown() {
        executor.orders = List.of(externalOrder(clientOrderId), externalOrder(clientOrderId));
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(UNKNOWN, result.divergence().status());
        assertHasClassification(result, AccountDivergenceReport.Classification.UNKNOWN);
    }

    @Test
    void simTradeOnMappedAccountBlocksBalanceSemanticClaim() {
        String simOrder = insertOrder(legacyAccountId, "sim-" + UUID.randomUUID(), "BTC-USDT", "SIM", "FILLED");
        jdbc.update("""
                INSERT INTO trades (trade_id, order_id, account_id, symbol, exchange, exchange_code,
                    exchange_trade_id, trade_env, price, qty, fee, fee_currency, trace_id, ts)
                VALUES (?, ?, ?, 'BTC-USDT', 'OKX', 'OKX', ?, 'SIM', 10, 0.1, 0, 'USDT', 'synthetic', ?)
                """, UUID.randomUUID().toString(), simOrder, legacyAccountId,
                UUID.randomUUID().toString(), Timestamp.from(NOW));
        assertUnknownDivergence(observeWithoutCanonicalMutation(), "BALANCE_SEMANTIC_MISMATCH");
    }

    @Test
    void latestPublishedSnapshotWinsEvenWhenItsBusinessTimestampIsOlder() {
        noActiveOrders();
        insertSnapshot(legacyAccountId, "BTC", new BigDecimal("0.75"), NOW.minusSeconds(60));
        jdbc.update("UPDATE positions SET updated_at=? WHERE account_id=? AND symbol='BTC-USDT'",
                Timestamp.from(NOW), legacyAccountId);
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals("DIVERGED", result.divergence().value());
        assertEquals(OBSERVED, result.status());
    }

    @Test
    void localLiveOrderForAnotherSymbolDiverges() {
        insertOrder(legacyAccountId, "eth-" + UUID.randomUUID(), "ETH-USDT", "LIVE", "ACCEPTED");
        assertHasClassification(observeWithoutCanonicalMutation(),
                AccountDivergenceReport.Classification.LOCAL_ACTIVE_ORDER_ONLY);
    }

    @Test
    void localSimOrderDoesNotEnterLiveAccountComparison() {
        noActiveOrders();
        insertOrder(legacyAccountId, "sim-" + UUID.randomUUID(), "BTC-USDT", "SIM", "ACCEPTED");
        assertEquals("MATCH", observeWithoutCanonicalMutation().divergence().value());
    }

    @ParameterizedTest
    @EnumSource(value = OkxPrivateReadError.class, names = {"NETWORK_TIMEOUT", "RESPONSE_PARSE_FAILED"})
    void failedPositionReadStaysUnknown(OkxPrivateReadError failure) {
        executor.positionFailure = failure;
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(UNKNOWN, result.positions().status());
        assertNull(result.positions().value());
        assertUnknownDivergence(result, "POSITION_OBSERVATION_INCOMPLETE");
    }

    @Test
    void partialPositionReadStaysUnknown() {
        executor.completePositions = false;
        AccountFactsSnapshot result = observeWithoutCanonicalMutation();
        assertEquals(UNKNOWN, result.positions().status());
        assertEquals("POSITION_RESPONSE_PARTIAL", result.positions().reason());
        assertNull(result.positions().value());
        assertUnknownDivergence(result, "POSITION_OBSERVATION_INCOMPLETE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BTC", "USDT"})
    void missingCanonicalCurrencyStaysUnknown(String currency) {
        noActiveOrders();
        jdbc.update("DELETE FROM account_snapshots WHERE account_id=? AND currency=?", legacyAccountId, currency);
        assertUnknownDivergence(observeWithoutCanonicalMutation(), "CANONICAL_BALANCE_MISSING");
    }

    @Test
    void nonzeroPositionCannotHideMissingCanonicalBalance() {
        noActiveOrders();
        executor.positions = List.of(position("2"));
        jdbc.update("DELETE FROM account_snapshots WHERE account_id=?", legacyAccountId);
        assertUnknownDivergence(observeWithoutCanonicalMutation(), "CANONICAL_BALANCE_MISSING");
    }

    private AccountFactsSnapshot observeWithoutCanonicalMutation() {
        Map<String, List<String>> before = canonicalContent();
        try {
            AccountFactsSnapshot result = new OkxAccountFactsObservationService(accounts, credentials, executor,
                    transport, kill, new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)),
                    ScopedCredentialCapabilityPolicy.PermissionScope.READ_ONLY, publicRules, jdbc,
                    Clock.fixed(NOW, ZoneOffset.UTC), "203.0.113.8")
                    .observe(OWNER_ID, ACCOUNT_ID, CREDENTIAL_ID);
            assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                    OkxPrivateReadOperation.OKX_ALL_ACCOUNT_BALANCES_READ,
                    OkxPrivateReadOperation.OKX_SPOT_ACCOUNT_FEE_READ,
                    OkxPrivateReadOperation.OKX_ALL_SPOT_OPEN_ORDERS_READ,
                    OkxPrivateReadOperation.OKX_ACCOUNT_POSITIONS_READ), executor.operations);
            return result;
        } finally {
            assertEquals(before, canonicalContent(), "read-only observation must preserve canonical contents");
        }
    }

    private void noActiveOrders() {
        executor.orders = List.of();
        jdbc.update("UPDATE orders SET status='REJECTED' WHERE account_id=? AND client_order_id=?",
                legacyAccountId, clientOrderId);
    }

    private static Map<String, List<String>> canonicalContent() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : CANONICAL_TABLES) {
            result.put(table, jdbc.queryForList(
                    "SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text", String.class));
        }
        return result;
    }

    private static void assertUnknownDivergence(AccountFactsSnapshot result, String reason) {
        assertEquals(UNKNOWN, result.divergence().status());
        assertNull(result.divergence().value());
        assertEquals(reason, result.divergence().reason());
        assertEquals(UNKNOWN, result.status());
    }

    private static void assertHasClassification(AccountFactsSnapshot result,
            AccountDivergenceReport.Classification classification) {
        assertTrue(result.divergenceReport() != null && result.divergenceReport().items().stream()
                .anyMatch(item -> item.classification() == classification), classification.name());
    }

    private static long insertAccount() {
        return jdbc.queryForObject("""
                INSERT INTO accounts (account_code, venue, status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id
                """, Long.class, "synthetic-" + UUID.randomUUID());
    }

    private static String insertOrder(long accountId, String clientId, String symbol, String environment, String status) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO orders (order_id, account_id, symbol, client_order_id, side, type, price, qty,
                    status, trace_id, venue, exchange_code, trade_env, exchange_order_id)
                VALUES (?, ?, ?, ?, 'BUY', 'LIMIT', 10, 1, ?, 'synthetic', 'OKX', 'OKX', ?, ?)
                """, id, accountId, symbol, clientId, status, environment, "exchange-" + clientId);
        return id;
    }

    private static void insertSnapshot(long accountId, String currency, BigDecimal value, Instant at) {
        jdbc.update("""
                INSERT INTO account_snapshots (account_id, currency, balance, available, frozen, ts, trace_id)
                VALUES (?, ?, ?, ?, 0, ?, 'synthetic')
                """, accountId, currency, value, value, Timestamp.from(at));
    }

    private static OkxPrivatePositionFact position(String quantity) {
        return new OkxPrivatePositionFact("SWAP", "BTC-USDT-SWAP", "cross", "net",
                new BigDecimal(quantity), null, "USDT", NOW);
    }

    private static OkxPrivateOrderSnapshot externalOrder(String clientId) {
        return new OkxPrivateOrderSnapshot("exchange-" + clientId, clientId, "BTC-USDT", "buy", "limit",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, "live", NOW,
                OkxPrivateReadOperation.OKX_ALL_SPOT_OPEN_ORDERS_READ);
    }

    private static boolean isLoopbackDatabase(String url) {
        try {
            if (!url.startsWith("jdbc:postgresql://")) return false;
            URI uri = URI.create(url.substring("jdbc:".length()));
            return Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(uri.getHost())
                    && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getFragment() == null
                    && uri.getPath() != null && uri.getPath().matches("/[A-Za-z0-9_]+")
                    && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String configuration(String property, String environment, boolean ci) {
        String explicit = System.getProperty(property);
        if (explicit != null) return explicit;
        String value = ci ? System.getenv(environment) : null;
        return value == null ? "" : value;
    }

    private static final class SyntheticExecutor implements OkxPrivateCredentialExecutor {
        private BigDecimal btc = BTC;
        private BigDecimal usdt = USDT;
        private BigDecimal usdc;
        private List<OkxPrivatePositionFact> positions = List.of();
        private List<OkxPrivateOrderSnapshot> orders = List.of();
        private boolean completePositions = true;
        private OkxPrivateReadError positionFailure;
        private final List<OkxPrivateReadOperation> operations = new ArrayList<>();

        @Override
        public <T> T withActiveCredential(Long ownerId, Long accountId, String type, CredentialCallback<T> callback) {
            throw new AssertionError("exact credential reference is required");
        }

        @Override
        public <T> T withActiveCredential(Long ownerId, Long accountId, Long credentialId,
                                         String type, CredentialCallback<T> callback) {
            assertEquals(OWNER_ID, ownerId);
            assertEquals(ACCOUNT_ID, accountId);
            assertEquals(CREDENTIAL_ID, credentialId);
            assertEquals("OKX_API_V5", type);
            return callback.execute((request, environment) -> {
                assertEquals(OkxPrivateEnvironment.PRODUCTION, environment);
                operations.add(request.operation());
                return response(request);
            });
        }

        private OkxPrivateReadResult response(OkxPrivateReadRequest request) {
            return switch (request.operation()) {
                case OKX_ACCOUNT_CONFIGURATION_READ -> new OkxPrivateReadResult(request.operation(),
                        Set.of("READ_ONLY"), 0, true, List.of(), List.of(), true,
                        OkxIpAllowlistStatus.MATCHED, NOW, "2", List.of(), null);
                case OKX_ALL_ACCOUNT_BALANCES_READ -> {
                    List<OkxPrivateBalanceFact> balances = new ArrayList<>(List.of(
                            new OkxPrivateBalanceFact("BTC", btc, btc, BigDecimal.ZERO, NOW),
                            new OkxPrivateBalanceFact("USDT", usdt, usdt, BigDecimal.ZERO, NOW)));
                    if (usdc != null) balances.add(new OkxPrivateBalanceFact("USDC", usdc, usdc,
                            BigDecimal.ZERO, NOW));
                    yield new OkxPrivateReadResult(request.operation(), Set.of(), balances.size(), true,
                            List.of(), List.of(), false, OkxIpAllowlistStatus.NOT_CHECKED, NOW, null,
                            balances, null);
                }
                case OKX_SPOT_ACCOUNT_FEE_READ -> new OkxPrivateReadResult(request.operation(), Set.of(),
                        0, true, List.of(), List.of(), false, OkxIpAllowlistStatus.NOT_CHECKED, NOW, null, List.of(),
                        new OkxPrivateFeeFact("BTC-USDT", new BigDecimal("-0.0008"),
                                new BigDecimal("-0.001"), "Lv1", NOW));
                case OKX_ALL_SPOT_OPEN_ORDERS_READ -> new OkxPrivateReadResult(request.operation(), Set.of(),
                        orders.size(), true, orders, List.of(), NOW);
                case OKX_ACCOUNT_POSITIONS_READ -> {
                    if (positionFailure != null) throw new OkxPrivateReadException(positionFailure);
                    yield new OkxPrivateReadResult(request.operation(), Set.of(), positions.size(), completePositions,
                            List.of(), List.of(), false, OkxIpAllowlistStatus.NOT_CHECKED,
                            NOW, null, List.of(), null, positions);
                }
                default -> throw new AssertionError("unexpected private operation: " + request.operation());
            };
        }
    }
}
