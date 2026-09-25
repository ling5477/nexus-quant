package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.error.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateBalanceFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxAccountFactsReadTransport;
import com.guidinglight.nexusquant.livecontrol.deployment.model.ScopedCredentialCapability;
import com.guidinglight.nexusquant.livecontrol.deployment.model.ScopedCredentialReference;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot.Status;

/** 人工显式账户事实观察；只读当前事实，不写入 canonical Order、Trade、Ledger 或 SIM 账本。 */
public final class OkxAccountFactsObservationService {
    private static final String SOURCE = "OKX_PRIVATE_GET_V5";
    private static final String INSTRUMENT = "BTC-USDT";
    private static final Duration PRIVATE_TTL = Duration.ofMinutes(1);
    private static final Duration FEE_PROVIDER_TTL = Duration.ofHours(1);
    private static final Duration RULE_TTL = Duration.ofHours(24);

    private final ExchangeAccountRepository accounts;
    private final ExchangeAccountCredentialRepository credentials;
    private final OkxPrivateCredentialExecutor executor;
    private final OkxAccountFactsReadTransport transport;
    private final KillSwitchService killSwitch;
    private final ScopedCredentialCapabilityPolicy policy;
    private final InstrumentCatalogService catalog;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String expectedIp;

    public OkxAccountFactsObservationService(
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials,
            OkxPrivateCredentialExecutor executor,
            OkxAccountFactsReadTransport transport,
            KillSwitchService killSwitch,
            ScopedCredentialCapabilityPolicy policy,
            InstrumentCatalogService catalog,
            JdbcTemplate jdbc,
            Clock clock,
            String expectedIp
    ) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.executor = Objects.requireNonNull(executor);
        this.transport = Objects.requireNonNull(transport);
        this.killSwitch = Objects.requireNonNull(killSwitch);
        this.policy = Objects.requireNonNull(policy);
        this.catalog = Objects.requireNonNull(catalog);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
        this.expectedIp = expectedIp;
    }

    /** owner 来自已认证主体，credential reference 必须精确绑定当前账户。 */
    public AccountFactsSnapshot observe(long ownerId, long exchangeAccountId, long credentialReference) {
        Instant started = clock.instant();
        UUID observationId = UUID.randomUUID();
        if (ownerId <= 0 || exchangeAccountId <= 0 || credentialReference <= 0 || expectedIp == null) {
            return rejected(observationId, exchangeAccountId, credentialReference, started, "INVALID_SCOPE_OR_IP");
        }
        KillSwitchSnapshot initialKill = killSwitch.snapshot();
        if (initialKill.status() != KillSwitchStatus.ENGAGED) {
            return rejected(observationId, exchangeAccountId, credentialReference, started, "KILL_NOT_ENGAGED");
        }
        try {
            ExchangeAccountSummary account = accounts.findByIdForOwner(ownerId, exchangeAccountId)
                    .orElseThrow(() -> new IllegalStateException("ACCOUNT_SCOPE_MISMATCH"));
            if (!"OKX".equalsIgnoreCase(account.exchangeCode())
                    || !"ACTIVE".equalsIgnoreCase(account.status())
                    || !"LIVE".equalsIgnoreCase(account.tradeEnv())) {
                return rejected(observationId, exchangeAccountId, credentialReference, started,
                        "ACCOUNT_SCOPE_MISMATCH");
            }
            ExchangeAccountCredentialSummary credential = credentials.findByCredentialIdForOwner(
                    ownerId, exchangeAccountId, credentialReference)
                    .orElseThrow(() -> new IllegalStateException("CREDENTIAL_REFERENCE_MISSING"));
            var reference = ScopedCredentialReference.fromSummary(ownerId, "OKX_SPOT",
                    ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC, credential);
            var decision = policy.evaluate(reference, started);
            if (decision.status() != ScopedCredentialCapabilityPolicy.Status.ALLOWED) {
                return rejected(observationId, exchangeAccountId, credentialReference, started,
                        decision.reason().name());
            }
            return executor.withActiveCredential(ownerId, exchangeAccountId, credentialReference,
                    JdbcOkxPrivateCredentialExecutor.OKX_API_V5,
                    session -> collect(session, account, credentialReference, observationId, initialKill));
        } catch (OkxPrivateReadException ex) {
            return rejected(observationId, exchangeAccountId, credentialReference, started,
                    ex.category().name());
        } catch (RuntimeException ex) {
            // JDBC、HTTP 或 parser cause 不得进入账户事实响应或日志。
            return rejected(observationId, exchangeAccountId, credentialReference, started,
                    "ACCOUNT_FACTS_READ_FAILED");
        }
    }

    private AccountFactsSnapshot collect(
            OkxPrivateCredentialExecutor.CredentialSession session,
            ExchangeAccountSummary account,
            long credentialReference,
            UUID observationId,
            KillSwitchSnapshot initialKill
    ) {
        Instant now = clock.instant();
        requireKill(initialKill);
        OkxPrivateReadResult configuration = session.execute(
                OkxPrivateReadRequest.accountConfiguration(expectedIp), OkxPrivateEnvironment.PRODUCTION);
        if (!configuration.complete()
                || !Set.of("READ_ONLY").equals(configuration.normalizedPermissions())
                || configuration.ipAllowlistStatus() != OkxIpAllowlistStatus.MATCHED) {
            return rejected(observationId, account.exchangeAccountId(), credentialReference, now,
                    "REMOTE_PERMISSION_OR_IP_NOT_VERIFIED");
        }
        AccountFactsSnapshot.Fact<String> mode = configuration.accountMode() == null
                ? unknown(now, "ACCOUNT_MODE_NOT_RETURNED")
                : observed(configuration.accountMode(), configuration.observedAt(), SOURCE, PRIVATE_TTL);
        var permissions = observed(List.of("READ"), configuration.observedAt(), SOURCE, PRIVATE_TTL);

        Map<String, AccountFactsSnapshot.Fact<OkxPrivateBalanceFact>> balances = new HashMap<>();
        OkxPrivateReadResult balanceResult = null;
        String balanceFailure = "BALANCE_NOT_OBSERVED";
        try {
            requireKill(initialKill);
            balanceResult = session.execute(OkxPrivateReadRequest.allAccountBalances(),
                    OkxPrivateEnvironment.PRODUCTION);
            if (balanceResult.complete()) {
                for (OkxPrivateBalanceFact balance : balanceResult.balances()) {
                    balances.put(balance.currency(), observed(balance, balanceResult.observedAt(), SOURCE, PRIVATE_TTL));
                }
            }
        } catch (OkxPrivateReadException ex) {
            // 单项失败保留 UNKNOWN，继续其他固定 GET；不把失败解释为零余额。
            balanceFailure = ex.category().name();
        }
        balances.putIfAbsent("USDT", unknown(now, balanceFailure));
        balances.putIfAbsent("BTC", unknown(now, balanceFailure));
        if (balanceResult != null && !balanceResult.complete()) {
            balances.replaceAll((currency, fact) -> unknown(now, "BALANCE_RESPONSE_PARTIAL"));
        }

        AccountFactsSnapshot.Fact<com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateFeeFact> fee =
                unknown(now, "FEE_NOT_OBSERVED");
        try {
            requireKill(initialKill);
            OkxPrivateReadResult result = session.execute(OkxPrivateReadRequest.spotAccountFee(INSTRUMENT),
                    OkxPrivateEnvironment.PRODUCTION);
            if (result.complete() && result.fee() != null) {
                fee = observed(result.fee(), result.observedAt(), SOURCE, PRIVATE_TTL);
                if (result.fee().providerTimestamp().isAfter(clock.instant())
                        || result.fee().providerTimestamp().isBefore(clock.instant().minus(FEE_PROVIDER_TTL))) {
                    fee = new AccountFactsSnapshot.Fact<>(Status.STALE, result.fee(), result.observedAt(),
                            result.observedAt(), SOURCE, "FEE_PROVIDER_TIMESTAMP_STALE");
                }
            }
        } catch (OkxPrivateReadException ex) {
            fee = unknown(now, ex.category().name());
        }

        AccountFactsSnapshot.Fact<Integer> openOrders = unknown(now, "OPEN_ORDERS_NOT_OBSERVED");
        List<OkxPrivateOrderSnapshot> externalOrders = null;
        try {
            requireKill(initialKill);
            OkxPrivateReadResult result = session.execute(OkxPrivateReadRequest.allSpotOpenOrders(100),
                    OkxPrivateEnvironment.PRODUCTION);
            if (result.complete()) {
                externalOrders = result.orders();
                openOrders = observed(externalOrders.size(), result.observedAt(), SOURCE, PRIVATE_TTL);
            }
        } catch (OkxPrivateReadException ex) {
            openOrders = unknown(now, ex.category().name());
        }

        AccountFactsSnapshot.Fact<Instant> exchangeTime = unknown(now, "SERVER_TIME_NOT_OBSERVED");
        try {
            requireKill(initialKill);
            Instant serverTime = transport.readServerTime();
            exchangeTime = observed(serverTime, clock.instant(), "OKX_PUBLIC_TIME_V5", PRIVATE_TTL);
        } catch (RuntimeException ex) {
            exchangeTime = unknown(now, "SERVER_TIME_READ_FAILED");
        }
        requireKill(initialKill);

        var btc = balances.get("BTC");
        AccountFactsSnapshot.Fact<BigDecimal> exposure = btc.status() == Status.OBSERVED
                ? observed(btc.value().total(), btc.observedAt(), SOURCE, PRIVATE_TTL)
                : unknown(now, "BTC_EXPOSURE_UNKNOWN");
        boolean simpleSpotAccount = "1".equals(mode.value()) && mode.status() == Status.OBSERVED;
        AccountFactsSnapshot.Fact<String> positions = simpleSpotAccount
                ? new AccountFactsSnapshot.Fact<>(Status.NOT_APPLICABLE, null, now, null,
                        "OKX_SPOT", "DERIVATIVE_POSITIONS_NOT_APPLICABLE")
                : unknown(now, "ACCOUNT_MODE_REQUIRES_POSITION_OBSERVATION");
        var rule = publicRule(now);
        var divergence = classifyDivergence(account.legacyAccountId(), mode, balances, externalOrders, now);
        Status status = balances.values().stream().allMatch(f -> f.status() == Status.OBSERVED)
                && openOrders.status() == Status.OBSERVED
                && mode.status() == Status.OBSERVED
                && fee.status() == Status.OBSERVED
                && exchangeTime.status() == Status.OBSERVED
                && rule.status() == Status.OBSERVED
                && divergence.status() == Status.OBSERVED
                && positions.status() == Status.NOT_APPLICABLE ? Status.OBSERVED : Status.UNKNOWN;
        return new AccountFactsSnapshot(observationId, "OKX", account.exchangeAccountId(),
                credentialReference, now,
                observed("ACTIVE", now, "NQ_CREDENTIAL_METADATA", PRIVATE_TTL),
                mode, permissions, balances, positions, exposure,
                openOrders, fee, exchangeTime, rule, divergence, status,
                status == Status.OBSERVED ? "PRIVATE_READ_ONLY" : "PARTIAL_ACCOUNT_FACTS");
    }

    private AccountFactsSnapshot.Fact<String> publicRule(Instant now) {
        try {
            var items = catalog.findByExchangeAndSymbols("OKX", List.of(INSTRUMENT));
            if (items.size() != 1 || items.getFirst().ruleChecksum() == null
                    || items.getFirst().observedAt() == null) {
                return unknown(now, "PUBLIC_RULE_IDENTITY_UNAVAILABLE");
            }
            var item = items.getFirst();
            if (item.observedAt().isAfter(now)
                    || item.observedAt().plus(RULE_TTL).isBefore(now)) {
                return new AccountFactsSnapshot.Fact<>(Status.STALE,
                        "OKX:" + INSTRUMENT + ":" + item.ruleChecksum(), item.observedAt(),
                        item.observedAt().plus(RULE_TTL), "NQ_PUBLIC_INSTRUMENT_RULE",
                        "PUBLIC_RULE_OBSERVATION_STALE");
            }
            return observed("OKX:" + INSTRUMENT + ":" + item.ruleChecksum(),
                    item.observedAt(), "NQ_PUBLIC_INSTRUMENT_RULE", RULE_TTL);
        } catch (RuntimeException ex) {
            return unknown(now, "PUBLIC_RULE_READ_FAILED");
        }
    }

    private AccountFactsSnapshot.Fact<String> classifyDivergence(
            Long legacyAccountId,
            AccountFactsSnapshot.Fact<String> accountMode,
            Map<String, AccountFactsSnapshot.Fact<OkxPrivateBalanceFact>> balances,
            List<OkxPrivateOrderSnapshot> externalOrders,
            Instant now
    ) {
        if (accountMode.status() != Status.OBSERVED || !"1".equals(accountMode.value())) {
            return unknown(now, "POSITION_COMPARISON_UNAVAILABLE_FOR_ACCOUNT_MODE");
        }
        if (legacyAccountId == null || externalOrders == null) {
            return unknown(now, "CANONICAL_OR_EXTERNAL_FACT_INCOMPLETE");
        }
        if (balances.entrySet().stream().anyMatch(entry -> !Set.of("BTC", "USDT").contains(entry.getKey())
                && entry.getValue().status() == Status.OBSERVED
                && entry.getValue().value().total().signum() > 0)) {
            return observed("DIVERGED", now, "NQ_CANONICAL_READ_COMPARISON", PRIVATE_TTL);
        }
        if (balances.values().stream().anyMatch(f -> f.status() != Status.OBSERVED)) {
            return unknown(now, "CANONICAL_OR_EXTERNAL_FACT_INCOMPLETE");
        }
        try {
            List<String> localOrders = jdbc.queryForList("""
                    SELECT client_order_id FROM orders
                    WHERE account_id=? AND symbol=?
                      AND status IN ('SENT','ACCEPTED','SUBMITTING','ACKED','PARTIALLY_FILLED',
                                     'CANCEL_REQUESTED','CANCEL_REJECTED')
                    LIMIT 1001
                    """, String.class, legacyAccountId, INSTRUMENT);
            if (localOrders.size() > 1000) return unknown(now, "CANONICAL_ORDERS_OVER_LIMIT");
            Set<String> externalIds = new java.util.HashSet<>();
            if (externalOrders.stream().anyMatch(order -> !INSTRUMENT.equals(order.instrumentId())
                    || order.clientOrderId() == null
                    || !externalIds.add(order.clientOrderId()))) {
                return observed("DIVERGED", now, "NQ_CANONICAL_READ_COMPARISON", PRIVATE_TTL);
            }
            if (!externalIds.equals(Set.copyOf(localOrders))) {
                return observed("DIVERGED", now, "NQ_CANONICAL_READ_COMPARISON", PRIVATE_TTL);
            }
            List<Map<String, Object>> localBalances = jdbc.queryForList("""
                    SELECT DISTINCT ON (currency) currency, balance
                    FROM account_snapshots
                    WHERE account_id=? AND currency IN ('USDT','BTC')
                    ORDER BY currency, ts DESC, snapshot_id DESC
                    """, legacyAccountId);
            if (localBalances.size() != 2) return unknown(now, "CANONICAL_BALANCE_INCOMPLETE");
            for (Map<String, Object> local : localBalances) {
                String currency = (String) local.get("currency");
                var external = balances.get(currency);
                if (external == null || external.value() == null) {
                    return unknown(now, "CANONICAL_BALANCE_SCOPE_MISMATCH");
                }
                if (external.value().total().compareTo((BigDecimal) local.get("balance")) != 0) {
                    return observed("DIVERGED", now, "NQ_CANONICAL_READ_COMPARISON", PRIVATE_TTL);
                }
            }
            return observed("MATCH", now, "NQ_CANONICAL_READ_COMPARISON", PRIVATE_TTL);
        } catch (RuntimeException ex) {
            return unknown(now, "CANONICAL_COMPARISON_UNAVAILABLE");
        }
    }

    private void requireKill(KillSwitchSnapshot initial) {
        KillSwitchSnapshot current = killSwitch.snapshot();
        if (current.status() != KillSwitchStatus.ENGAGED || current.version() != initial.version()
                || current.scope() != initial.scope()
                || !Objects.equals(current.updatedAt(), initial.updatedAt())
                || !Objects.equals(current.source(), initial.source())) {
            throw new IllegalStateException("KILL_SWITCH_CHANGED");
        }
    }

    private AccountFactsSnapshot rejected(UUID id, long accountId, long credentialId, Instant at, String reason) {
        var unknown = OkxAccountFactsObservationService.<String>unknown(at, reason);
        return new AccountFactsSnapshot(id, "OKX", accountId, credentialId, at,
                unknown, unknown, OkxAccountFactsObservationService.<List<String>>unknown(at, reason),
                Map.of("USDT", OkxAccountFactsObservationService.<OkxPrivateBalanceFact>unknown(at, reason),
                        "BTC", OkxAccountFactsObservationService.<OkxPrivateBalanceFact>unknown(at, reason)),
                OkxAccountFactsObservationService.<String>unknown(at, "ACCOUNT_MODE_NOT_OBSERVED"),
                OkxAccountFactsObservationService.<BigDecimal>unknown(at, reason),
                OkxAccountFactsObservationService.<Integer>unknown(at, reason),
                OkxAccountFactsObservationService.<com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateFeeFact>unknown(at, reason),
                OkxAccountFactsObservationService.<Instant>unknown(at, reason), unknown, unknown,
                Status.REJECTED, reason);
    }

    private static <T> AccountFactsSnapshot.Fact<T> observed(T value, Instant at, String source, Duration ttl) {
        return new AccountFactsSnapshot.Fact<>(Status.OBSERVED, value, at, at.plus(ttl), source, null);
    }

    private static <T> AccountFactsSnapshot.Fact<T> unknown(Instant at, String reason) {
        return new AccountFactsSnapshot.Fact<>(Status.UNKNOWN, null, at, null, SOURCE, reason);
    }
}
