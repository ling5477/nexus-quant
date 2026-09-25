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
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxAccountFactsReadTransport;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchScope;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import org.junit.jupiter.api.Test;
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
    private final InstrumentCatalogService catalog = mock(InstrumentCatalogService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CapturingExecutor executor = new CapturingExecutor();
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
    void matchingCanonicalSnapshotAndNoExternalOrderClassifiesMatch() {
        legacyAccountId = 42L;
        executor.includeBtc = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(42L), eq("BTC-USDT")))
                .thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(42L))).thenReturn(List.of(
                Map.of("currency", "BTC", "balance", BigDecimal.ZERO),
                Map.of("currency", "USDT", "balance", new BigDecimal("12.5"))));
        assertEquals("MATCH", service().observe(7, 8, 9).divergence().value());
    }

    @Test
    void unmatchedExternalOpenOrderClassifiesDivergedWithoutImportingIt() {
        legacyAccountId = 42L;
        executor.includeBtc = true;
        executor.externalOpenOrder = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(42L), eq("BTC-USDT")))
                .thenReturn(List.of());
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals("DIVERGED", snapshot.divergence().value());
        assertEquals(1, snapshot.openOrderCount().value());
    }

    @Test
    void missingExternalOrderForLocalActiveOrderIsDiverged() {
        legacyAccountId = 42L;
        executor.includeBtc = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(42L), eq("BTC-USDT")))
                .thenReturn(List.of("local-active-order"));
        assertEquals("DIVERGED", service().observe(7, 8, 9).divergence().value());
    }

    @Test
    void nonSimpleAccountKeepsPositionsAndDivergenceUnknown() {
        executor.accountMode = "2";
        executor.includeBtc = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.positions().status());
        assertEquals("POSITION_COMPARISON_UNAVAILABLE_FOR_ACCOUNT_MODE", snapshot.divergence().reason());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.status());
        verifyNoInteractions(jdbc);
    }

    @Test
    void unknownDivergencePreventsAggregateObservedEvenWhenOtherFactsAreFresh() {
        executor.includeBtc = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        InstrumentCatalogItem rule = mock(InstrumentCatalogItem.class);
        when(rule.ruleChecksum()).thenReturn("a".repeat(64));
        when(rule.observedAt()).thenReturn(NOW);
        when(catalog.findByExchangeAndSymbols(eq("OKX"), any())).thenReturn(List.of(rule));
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.divergence().status());
        assertEquals(AccountFactsSnapshot.Status.UNKNOWN, snapshot.status());
    }

    @Test
    void unexpectedSpotAssetClassifiesDivergedEvenWhenBtcIsUnreported() {
        legacyAccountId = 42L;
        executor.unexpectedAsset = true;
        setupAllowed();
        when(transport.readServerTime()).thenReturn(NOW);
        AccountFactsSnapshot snapshot = service().observe(7, 8, 9);
        assertEquals("DIVERGED", snapshot.divergence().value());
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
        return new OkxAccountFactsObservationService(accounts, credentials, executor, transport,
                kill, new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)), catalog, jdbc,
                Clock.fixed(NOW, ZoneOffset.UTC), "203.0.113.8");
    }

    private void setupAllowed() {
        when(kill.snapshot()).thenReturn(kill(KillSwitchStatus.ENGAGED));
        when(accounts.findByIdForOwner(7L, 8L)).thenReturn(Optional.of(new ExchangeAccountSummary(
                8L, legacyAccountId, 7L, "OKX", "LIVE", "account", null, true, "ACTIVE")));
        when(credentials.findByCredentialIdForOwner(7L, 8L, 9L)).thenReturn(Optional.of(
                new ExchangeAccountCredentialSummary(9L, 8L, "OKX_API_V5", "masked", "ACTIVE",
                        "VERIFIED", true, null, null, null, NOW, null, NOW,
                        "SUCCEEDED", "READ_ONLY", false, "PASSED", 0,
                        NOW.minusSeconds(30), null)));
        when(catalog.findByExchangeAndSymbols(eq("OKX"), any())).thenReturn(List.of());
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
        boolean externalOpenOrder;
        boolean staleFee;
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
                            new BigDecimal("10.5"), new BigDecimal("2"), NOW));
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
                default -> throw new AssertionError("unexpected operation");
            };
        }
    }
}
