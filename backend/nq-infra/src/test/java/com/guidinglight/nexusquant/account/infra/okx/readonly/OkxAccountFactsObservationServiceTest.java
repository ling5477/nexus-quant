package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateBalanceFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateFeeFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivatePositionFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxAccountFactsReadTransport;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchScope;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OkxAccountFactsObservationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private final ExchangeAccountRepository accounts = mock(ExchangeAccountRepository.class);
    private final ExchangeAccountCredentialRepository credentials = mock(ExchangeAccountCredentialRepository.class);
    private final KillSwitchService kill = mock(KillSwitchService.class);
    private final OkxAccountFactsReadTransport transport = mock(OkxAccountFactsReadTransport.class);
    private final OkxCurrentPublicRuleReader publicRuleReader = mock(OkxCurrentPublicRuleReader.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CapturingExecutor executor = new CapturingExecutor();
    private Clock observationClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private Long legacyAccountId;

    @Test
    void killRejectsBeforeCredentialOrPrivateCall() {
        when(kill.snapshot()).thenReturn(kill(KillSwitchStatus.DISENGAGED));
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.REJECTED, snapshot.status());
        assertEquals("KILL_NOT_ENGAGED", snapshot.reason());
        assertEquals(0, executor.calls);
        verifyNoInteractions(transport, jdbc);
    }

    @Test
    void explicitObservationUsesOnlyFourTypedGetsAndPreservesMissingFacts() {
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(1, executor.calls);
        assertEquals(List.of(
                OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                OkxPrivateReadOperation.OKX_ALL_ACCOUNT_BALANCES_READ,
                OkxPrivateReadOperation.OKX_SPOT_ACCOUNT_FEE_READ,
                OkxPrivateReadOperation.OKX_ALL_SPOT_OPEN_ORDERS_READ), executor.operations);
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.balances().get("USDT").status());
        assertEquals(new BigDecimal("12.5"), snapshot.balances().get("USDT").value().total());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.balances().get("BTC").status());
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.fee().status());
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.openOrderCount().status());
        assertEquals(AccountFactsSnapshot.Status.NOT_APPLICABLE, snapshot.positions().status());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.divergence().status());
        assertEquals(AccountFactsSnapshot.Status.STALE,
                snapshot.fee().statusAt(NOW.plus(Duration.ofMinutes(2))));
        assertEquals("AccountFactsSnapshot[REDACTED]", snapshot.toString());
    }

    @Test
    void tradePermissionStopsBeforeFinancialReads() {
        setupAllowed();
        executor.tradePermission = true;
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.REJECTED, snapshot.status());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), executor.operations);
        verifyNoInteractions(transport, jdbc);
    }

    @Test
    void scopedTradeCredentialCanOnlyReachFixedReadsAfterExactPermissionCheck() {
        setupAllowed("TRADE");
        executor.tradePermission = true;
        when(transport.readServerTime()).thenReturn(NOW);
        AccountFactsSnapshot snapshot = service(ScopedCredentialCapabilityPolicy.PermissionScope.TRADE)
                .observe(7, 8, 9);
        assertEquals(List.of("READ", "TRADE"), snapshot.permissions().value());
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.balances().get("USDT").status());
        assertEquals(List.of(
                OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                OkxPrivateReadOperation.OKX_ALL_ACCOUNT_BALANCES_READ,
                OkxPrivateReadOperation.OKX_SPOT_ACCOUNT_FEE_READ,
                OkxPrivateReadOperation.OKX_ALL_SPOT_OPEN_ORDERS_READ), executor.operations);
    }

    @Test
    void scopedTradeCredentialRejectsRemotePermissionMismatchBeforeFinancialReads() {
        setupAllowed("TRADE");
        AccountFactsSnapshot snapshot = service(ScopedCredentialCapabilityPolicy.PermissionScope.TRADE)
                .observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.REJECTED, snapshot.status());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), executor.operations);
        verifyNoInteractions(transport, jdbc);
    }

    @Test
    void matchingNumbersDoNotQualifyManagedProjectionAsVenueBalance() {
        legacyAccountId = 42L;
        executor.includeBtc = true;
        executor.unlockedUsdt = true;
        setupAllowed();
        currentRule();
        when(transport.readServerTime()).thenReturn(NOW);
        stubCanonical(List.of(), canonicalBalances());
        assertEquals("NOT_APPLICABLE", service().observe(7, 8, 9).divergence().value());
    }

    @Test
    void unmatchedExternalOpenOrderRemainsVisibleWithoutImportingIt() {
        legacyAccountId = 42L;
        executor.includeBtc = true;
        executor.externalOpenOrder = true;
        setupAllowed();
        currentRule();
        when(transport.readServerTime()).thenReturn(NOW);
        stubCanonical(List.of(), canonicalBalances());
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals("NOT_APPLICABLE", snapshot.divergence().value());
        assertTrue(snapshot.divergenceReport().items().stream().anyMatch(item ->
                item.classification() == AccountDivergenceReport.Classification.EXTERNAL_ORDER_OWNERSHIP_UNKNOWN
                        && item.role() == AccountDivergenceReport.Role.EXTERNAL_CONTEXT));
        assertEquals(1, snapshot.openOrderCount().value());
    }

    @Test
    void missingExternalOrderForLocalActiveOrderIsDiverged() {
        legacyAccountId = 42L;
        executor.includeBtc = true;
        setupAllowed();
        currentRule();
        when(transport.readServerTime()).thenReturn(NOW);
        stubCanonical(List.of(canonicalOrder("local-active-order")), canonicalBalances());
        var snapshot = service().observe(7, 8, 9);
        assertTrue(snapshot.divergenceReport().items().stream().anyMatch(item ->
                item.classification() == AccountDivergenceReport.Classification.LOCAL_ACTIVE_ORDER_ONLY));
    }

    @Test
    void unqualifiedAccountModeKeepsPositionsAndDivergenceUnknown() {
        executor.accountMode = "3";
        executor.includeBtc = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.positions().status());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.divergence().status());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.status());
        verifyNoInteractions(jdbc);
    }

    @Test
    void unknownManagedComparisonDoesNotDowngradeCompleteObservation() {
        executor.includeBtc = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        currentRule();
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.divergence().status());
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.status());
    }

    @Test
    void unexpectedSpotAssetCannotHideMissingBtc() {
        legacyAccountId = 42L;
        executor.unexpectedAsset = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals("NOT_APPLICABLE", snapshot.divergence().value());
        assertEquals("USDC", snapshot.balances().get("USDC").value().currency());
    }

    @Test
    void staleFeeCannotBePresentedAsCurrentActualAccountFee() {
        setupAllowed();
        executor.staleFee = true;
        when(transport.readServerTime()).thenReturn(NOW);
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.STALE, snapshot.fee().status());
        assertEquals("FEE_PROVIDER_TIMESTAMP_STALE", snapshot.fee().reason());
    }

    private OkxAccountFactsObservationService service() {
        return service(ScopedCredentialCapabilityPolicy.PermissionScope.READ_ONLY);
    }

    @Test
    void modeTwoEmptyPositionSnapshotObservesFactsWithoutBalanceEquivalence() {
        modeTwoMatching();
        var snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.positions().status());
        assertEquals(List.of(), snapshot.positions().value());
        assertEquals("NO_ACTIVE_POSITION", snapshot.positions().reason());
        assertEquals("NOT_APPLICABLE", snapshot.divergence().value());
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.status());
        assertEquals(BigDecimal.ZERO, snapshot.spotBtcExposure().value());
        assertEquals(1, executor.operations.stream()
                .filter(op -> op == OkxPrivateReadOperation.OKX_ACCOUNT_POSITIONS_READ).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SWAP", "FUTURES", "MARGIN", "OPTION", "EVENTS"})
    void modeTwoNonZeroPositionIsExternalContextWithoutConflatingSpotUnits(String type) {
        modeTwoMatching();
        executor.positions = List.of(position(type, "1"));
        var snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.positions().status());
        assertEquals("NOT_APPLICABLE", snapshot.divergence().value());
        assertTrue(snapshot.divergenceReport().items().stream().anyMatch(item ->
                item.classification() == AccountDivergenceReport.Classification.EXTERNAL_NON_SPOT_EXPOSURE
                        && item.role() == AccountDivergenceReport.Role.EXTERNAL_CONTEXT));
        assertEquals(BigDecimal.ZERO, snapshot.spotBtcExposure().value());
        assertEquals(AccountFactsSnapshot.Status.OBSERVED, snapshot.status());
    }

    @Test
    void modeTwoZeroPositionDoesNotCreateFalseDivergence() {
        modeTwoMatching();
        executor.positions = List.of(position("SWAP", "0"));
        var snapshot = service().observe(7, 8, 9);
        assertEquals("NO_ACTIVE_POSITION", snapshot.positions().reason());
        assertEquals("NOT_APPLICABLE", snapshot.divergence().value());
    }

    @Test
    void modeTwoPositionFailureAndPartialSnapshotPreventMatch() {
        modeTwoMatching();
        for (var failure : List.of(OkxPrivateReadError.RESPONSE_CONTRACT_MISMATCH,
                OkxPrivateReadError.POSITION_RESPONSE_OVER_LIMIT, OkxPrivateReadError.HTTP_RATE_LIMITED)) {
            executor.positionFailure = failure;
            var snapshot = service().observe(7, 8, 9);
            assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.positions().status());
            assertEquals(failure.name(), snapshot.positions().reason());
            assertEquals("NOT_APPLICABLE", snapshot.divergence().value());
            assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.status());
        }
        executor.positionFailure = null;
        executor.partialPositions = true;
        var partial = service().observe(7, 8, 9);
        assertEquals("POSITION_RESPONSE_PARTIAL", partial.positions().reason());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, partial.status());
    }

    @Test
    void modeTwoMissingBalancesCannotMatchOrClaimCompleteDivergence() {
        modeTwoMatching();
        executor.includeBtc = false;
        executor.positions = List.of(position("SWAP", "1"));
        assertEquals("NOT_APPLICABLE", service().observe(7, 8, 9).divergence().value());
        executor.includeBtc = true;
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<JdbcAccountSnapshotReader.Snapshot>>any(),
                eq(42L), eq("LIVE"))).thenReturn(List.of());
        assertEquals("NOT_APPLICABLE", service().observe(7, 8, 9).divergence().value());
    }

    @Test
    void staleOrMissingCurrentPublicRulePreventsAggregateObserved() {
        modeTwoMatching();
        when(publicRuleReader.observeDetailed()).thenReturn(new AccountFactsSnapshot.Fact<>(
                AccountFactsSnapshot.Status.STALE,
                new OkxCurrentPublicRuleReader.CurrentRule("OKX:BTC-USDT:" + "a".repeat(64),
                        new BigDecimal("0.00001")),
                NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1)),
                "OKX_PUBLIC_INSTRUMENTS", "PUBLIC_RULE_OBSERVATION_STALE"));
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, service().observe(7, 8, 9).status());
        when(publicRuleReader.observeDetailed()).thenReturn(new AccountFactsSnapshot.Fact<>(
                AccountFactsSnapshot.Status.UNKNOWN, null, NOW, null,
                "OKX_PUBLIC_INSTRUMENTS", "PUBLIC_RULE_READ_FAILED"));
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, service().observe(7, 8, 9).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "4"})
    void unqualifiedModesDoNotReadPositionsOrClaimAggregateObserved(String mode) {
        modeTwoMatching();
        executor.accountMode = mode;
        var snapshot = service().observe(7, 8, 9);
        assertEquals("ACCOUNT_MODE_NOT_YET_QUALIFIED", snapshot.positions().reason());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.status());
        assertTrue(executor.operations.stream().noneMatch(op ->
                op == OkxPrivateReadOperation.OKX_ACCOUNT_POSITIONS_READ));
    }

    private void modeTwoMatching() {
        legacyAccountId = 42L;
        executor.accountMode = "2";
        executor.includeBtc = true;
        executor.unlockedUsdt = true;
        setupAllowed();
        currentRule();
        when(transport.readServerTime()).thenReturn(NOW);
        stubCanonical(List.of(), canonicalBalances());
    }

    private void stubCanonical(List<Map<String, Object>> orders, List<Map<String, Object>> balances) {
        when(jdbc.queryForList(anyString(), eq(42L))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("FROM account_snapshots") ? balances
                    : sql.contains("FROM positions") ? List.of() : orders;
        });
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(42L), eq(42L)))
                .thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(42L))).thenReturn(false);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<JdbcAccountSnapshotReader.Snapshot>>any(),
                eq(42L), eq("LIVE"))).thenReturn(balances.stream().map(row -> new JdbcAccountSnapshotReader.Snapshot(
                (Long) row.get("snapshot_id"), (String) row.get("currency"), (BigDecimal) row.get("balance"),
                (BigDecimal) row.get("available"), (BigDecimal) row.get("frozen"), NOW, NOW,
                "LIVE", "LEDGER_CASH_PROJECTION", "NQ_MANAGED_ACCOUNT")).toList());
    }

    private static List<Map<String, Object>> canonicalBalances() {
        return List.of(
                Map.of("snapshot_id", 1L, "currency", "BTC", "balance", BigDecimal.ZERO,
                        "available", BigDecimal.ZERO, "frozen", BigDecimal.ZERO,
                        "ts", java.sql.Timestamp.from(NOW), "created_at", java.sql.Timestamp.from(NOW)),
                Map.of("snapshot_id", 2L, "currency", "USDT", "balance", new BigDecimal("12.5"),
                        "available", new BigDecimal("12.5"), "frozen", BigDecimal.ZERO,
                        "ts", java.sql.Timestamp.from(NOW), "created_at", java.sql.Timestamp.from(NOW)));
    }

    private static Map<String, Object> canonicalOrder(String id) {
        return Map.ofEntries(Map.entry("order_id", id), Map.entry("client_order_id", id),
                Map.entry("exchange_order_id", id), Map.entry("exchange_code", "OKX"),
                Map.entry("venue", "OKX"), Map.entry("symbol", "BTC-USDT"), Map.entry("side", "BUY"),
                Map.entry("type", "LIMIT"), Map.entry("price", BigDecimal.TEN),
                Map.entry("qty", BigDecimal.ONE), Map.entry("status", "ACCEPTED"),
                Map.entry("filled", BigDecimal.ZERO),
                Map.entry("created_at", java.sql.Timestamp.from(NOW)),
                Map.entry("updated_at", java.sql.Timestamp.from(NOW)));
    }

    private void currentRule() {
        when(publicRuleReader.observeDetailed()).thenReturn(new AccountFactsSnapshot.Fact<>(
                AccountFactsSnapshot.Status.OBSERVED,
                new OkxCurrentPublicRuleReader.CurrentRule("OKX:BTC-USDT:" + "a".repeat(64),
                        new BigDecimal("0.00001")), NOW,
                NOW.plus(Duration.ofHours(24)), "OKX_PUBLIC_INSTRUMENTS", "CURRENTLY_OBSERVED_PUBLIC_RULE"));
    }

    @Test
    void comparisonCrossingPrivateTtlCannotReturnObservedOrFreshMatch() {
        modeTwoMatching();
        observationClock = mock(Clock.class);
        when(observationClock.instant()).thenReturn(NOW);
        when(jdbc.queryForList(anyString(), eq(42L))).thenAnswer(invocation -> {
            when(observationClock.instant()).thenReturn(NOW.plusSeconds(61));
            String sql = invocation.getArgument(0);
            return sql.contains("FROM account_snapshots") ? canonicalBalances() : List.of();
        });
        var snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.status());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.divergence().status());
        assertEquals("ACCOUNT_FACTS_EXPIRED_DURING_COMPARISON", snapshot.divergence().reason());
        assertEquals(AccountFactsSnapshot.Status.STALE, snapshot.positions().statusAt(NOW.plusSeconds(61)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void killStateOrVersionChangeDuringComparisonRejectsResult(boolean remainEngaged) {
        modeTwoMatching();
        when(jdbc.queryForList(anyString(), eq(42L))).thenAnswer(invocation -> {
            when(kill.snapshot()).thenReturn(new KillSwitchSnapshot(KillSwitchScope.GLOBAL_TRADING,
                    remainEngaged ? KillSwitchStatus.ENGAGED : KillSwitchStatus.DISENGAGED, 2,
                    "TEST", "TEST", NOW, NOW, "trace"));
            String sql = invocation.getArgument(0);
            return sql.contains("FROM account_snapshots") ? canonicalBalances() : List.of();
        });
        var snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.REJECTED, snapshot.status());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.divergence().status());
    }

    private static OkxPrivatePositionFact position(String type, String quantity) {
        String instrument = switch (type) {
            case "MARGIN" -> "BTC-USDT";
            case "FUTURES" -> "BTC-USDT-261225";
            case "OPTION" -> "BTC-USD-261225-100000-C";
            case "EVENTS" -> "BTC-USDT-EVENTS";
            default -> "BTC-USDT-SWAP";
        };
        return new OkxPrivatePositionFact(type, instrument,
                "cross", "net", new BigDecimal(quantity), "MARGIN".equals(type) ? "BTC" : null,
                "USDT", NOW);
    }

    private OkxAccountFactsObservationService service(
            ScopedCredentialCapabilityPolicy.PermissionScope permissionScope) {
        return new OkxAccountFactsObservationService(accounts, credentials, executor, transport,
                kill, new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)), permissionScope, publicRuleReader, jdbc,
                observationClock, "203.0.113.8");
    }

    private void setupAllowed() {
        setupAllowed("READ_ONLY");
    }

    private void setupAllowed(String permissionScope) {
        when(kill.snapshot()).thenReturn(kill(KillSwitchStatus.ENGAGED));
        when(accounts.findByIdForOwner(7L, 8L)).thenReturn(Optional.of(new ExchangeAccountSummary(
                8L, legacyAccountId, 7L, "OKX", "LIVE", "account", null, true, "ACTIVE")));
        when(credentials.findByCredentialIdForOwner(7L, 8L, 9L)).thenReturn(Optional.of(
                new ExchangeAccountCredentialSummary(9L, 8L, "OKX_API_V5", "masked", "ACTIVE",
                        "VERIFIED", true, null, null, null, NOW, null, NOW,
                        "SUCCEEDED", permissionScope, false, "PASSED", 0,
                        NOW.minusSeconds(30), null)));
        when(publicRuleReader.observeDetailed()).thenReturn(new AccountFactsSnapshot.Fact<>(
                AccountFactsSnapshot.Status.UNKNOWN, null, NOW, null,
                "OKX_PUBLIC_INSTRUMENTS", "PUBLIC_RULE_IDENTITY_UNAVAILABLE"));
    }

    private static KillSwitchSnapshot kill(KillSwitchStatus status) {
        return new KillSwitchSnapshot(KillSwitchScope.GLOBAL_TRADING, status, 1,
                "TEST", "TEST", NOW, NOW, "trace");
    }

    private final class CapturingExecutor implements OkxPrivateCredentialExecutor {
        int calls;
        boolean tradePermission;
        boolean includeBtc;
        boolean unexpectedAsset;
        boolean unlockedUsdt;
        boolean externalOpenOrder;
        boolean staleFee;
        boolean partialPositions;
        OkxPrivateReadError positionFailure;
        List<OkxPrivatePositionFact> positions = List.of();
        String accountMode = "1";
        final List<OkxPrivateReadOperation> operations = new ArrayList<>();

        @Override
        public <T> T withActiveCredential(Long ownerId, Long accountId, String type, CredentialCallback<T> callback) {
            throw new AssertionError("inexact credential lookup forbidden");
        }

        @Override
        public <T> T withActiveCredential(Long ownerId, Long accountId, Long credentialId,
                                          String type, CredentialCallback<T> callback) {
            assertEquals(9L, credentialId);
            calls++;
            return callback.execute((request, environment) -> {
                assertEquals(OkxPrivateEnvironment.PRODUCTION, environment);
                operations.add(request.operation());
                return response(request);
            });
        }

        private OkxPrivateReadResult response(OkxPrivateReadRequest request) {
            return switch (request.operation()) {
                case OKX_ACCOUNT_CONFIGURATION_READ -> new OkxPrivateReadResult(request.operation(),
                        tradePermission ? Set.of("READ_ONLY", "TRADE") : Set.of("READ_ONLY"),
                        0, true, List.of(), List.of(), true, OkxIpAllowlistStatus.MATCHED,
                        NOW, accountMode, List.of(), null);
                case OKX_ALL_ACCOUNT_BALANCES_READ -> {
                    List<OkxPrivateBalanceFact> items = new ArrayList<>();
                    items.add(new OkxPrivateBalanceFact("USDT", new BigDecimal("12.5"),
                            unlockedUsdt ? new BigDecimal("12.5") : new BigDecimal("10.5"),
                            unlockedUsdt ? BigDecimal.ZERO : new BigDecimal("2"), NOW));
                    if (includeBtc) items.add(new OkxPrivateBalanceFact("BTC",
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOW));
                    if (unexpectedAsset) items.add(new OkxPrivateBalanceFact("USDC",
                            new BigDecimal("1"), new BigDecimal("1"), BigDecimal.ZERO, NOW));
                    yield new OkxPrivateReadResult(request.operation(), Set.of(), items.size(),
                            true, List.of(), List.of(), false, OkxIpAllowlistStatus.NOT_CHECKED,
                            NOW, null, items, null);
                }
                case OKX_SPOT_ACCOUNT_FEE_READ -> new OkxPrivateReadResult(request.operation(), Set.of(),
                        0, true, List.of(), List.of(), false, OkxIpAllowlistStatus.NOT_CHECKED,
                        NOW, null, List.of(), new OkxPrivateFeeFact("BTC-USDT",
                        new BigDecimal("-0.0008"), new BigDecimal("-0.001"), "Lv1",
                        staleFee ? NOW.minus(Duration.ofHours(2)) : NOW));
                case OKX_ALL_SPOT_OPEN_ORDERS_READ -> new OkxPrivateReadResult(request.operation(),
                        Set.of(), 0, true,
                        externalOpenOrder ? List.of(new OkxPrivateOrderSnapshot("external-order", "external-client",
                                "BTC-USDT", "buy", "limit", new BigDecimal("10"), BigDecimal.ONE,
                                BigDecimal.ZERO, "live", NOW, request.operation())) : List.of(),
                        List.of(), NOW);
                case OKX_ACCOUNT_POSITIONS_READ -> {
                    if (positionFailure != null) throw new OkxPrivateReadException(positionFailure);
                    yield new OkxPrivateReadResult(request.operation(), Set.of(), 0, !partialPositions,
                            List.of(), List.of(), false, OkxIpAllowlistStatus.NOT_CHECKED,
                            NOW, null, List.of(), null, positions);
                }
                default -> throw new AssertionError("unexpected operation");
            };
        }
    }
}
