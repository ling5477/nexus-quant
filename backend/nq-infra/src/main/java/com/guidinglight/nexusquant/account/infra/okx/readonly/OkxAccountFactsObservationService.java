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
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivatePositionFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxAccountFactsReadTransport;
import com.guidinglight.nexusquant.livecontrol.deployment.model.ScopedCredentialCapability;
import com.guidinglight.nexusquant.livecontrol.deployment.model.ScopedCredentialReference;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
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

    private final ExchangeAccountRepository accounts;
    private final ExchangeAccountCredentialRepository credentials;
    private final OkxPrivateCredentialExecutor executor;
    private final OkxAccountFactsReadTransport transport;
    private final KillSwitchService killSwitch;
    private final ScopedCredentialCapabilityPolicy policy;
    private final ScopedCredentialCapabilityPolicy.PermissionScope permissionScope;
    private final OkxCurrentPublicRuleReader publicRuleReader;
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
            ScopedCredentialCapabilityPolicy.PermissionScope permissionScope,
            OkxCurrentPublicRuleReader publicRuleReader,
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
        this.permissionScope = Objects.requireNonNull(permissionScope);
        this.publicRuleReader = Objects.requireNonNull(publicRuleReader);
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
            var decision = policy.evaluate(reference, started, permissionScope);
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
        Set<String> requiredPermissions = permissionScope == ScopedCredentialCapabilityPolicy.PermissionScope.TRADE
                ? Set.of("READ_ONLY", "TRADE") : Set.of("READ_ONLY");
        if (!configuration.complete()
                || !requiredPermissions.equals(configuration.normalizedPermissions())
                || configuration.ipAllowlistStatus() != OkxIpAllowlistStatus.MATCHED) {
            return rejected(observationId, account.exchangeAccountId(), credentialReference, now,
                    "REMOTE_PERMISSION_OR_IP_NOT_VERIFIED");
        }
        AccountFactsSnapshot.Fact<String> mode = configuration.accountMode() == null
                ? unknown(now, "ACCOUNT_MODE_NOT_RETURNED")
                : observed(configuration.accountMode(), configuration.observedAt(), SOURCE, PRIVATE_TTL);
        var permissions = observed(permissionScope == ScopedCredentialCapabilityPolicy.PermissionScope.TRADE
                ? List.of("READ", "TRADE") : List.of("READ"), configuration.observedAt(), SOURCE, PRIVATE_TTL);

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
        AccountFactsSnapshot.Fact<List<OkxPrivateOrderSnapshot>> externalOrders = unknown(now, "OPEN_ORDERS_NOT_OBSERVED");
        try {
            requireKill(initialKill);
            OkxPrivateReadResult result = session.execute(OkxPrivateReadRequest.allSpotOpenOrders(100),
                    OkxPrivateEnvironment.PRODUCTION);
            if (result.complete()) {
                externalOrders = observed(List.copyOf(result.orders()), result.observedAt(), SOURCE, PRIVATE_TTL);
                openOrders = observed(result.orders().size(), result.observedAt(), SOURCE, PRIVATE_TTL);
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
        AccountFactsSnapshot.Fact<List<OkxPrivatePositionFact>> positions = simpleSpotAccount
                ? new AccountFactsSnapshot.Fact<>(Status.NOT_APPLICABLE, null, now, null,
                        "OKX_SPOT", "DERIVATIVE_POSITIONS_NOT_APPLICABLE")
                : unknown(now, "ACCOUNT_MODE_NOT_YET_QUALIFIED");
        if (mode.status() == Status.OBSERVED && "2".equals(mode.value())) {
            try {
                requireKill(initialKill);
                var result = session.execute(OkxPrivateReadRequest.accountPositions(),
                        OkxPrivateEnvironment.PRODUCTION);
                if (result.complete()) {
                    var facts = List.copyOf(result.positions());
                    positions = new AccountFactsSnapshot.Fact<>(Status.OBSERVED, facts,
                            result.observedAt(), result.observedAt().plus(PRIVATE_TTL), SOURCE,
                            facts.stream().anyMatch(p -> p.positionQuantity().signum() != 0)
                                    ? "ACTIVE_POSITION_PRESENT" : "NO_ACTIVE_POSITION");
                } else {
                    positions = unknown(now, "POSITION_RESPONSE_PARTIAL");
                }
            } catch (OkxPrivateReadException ex) {
                positions = unknown(now, ex.category().name());
            }
        }
        requireKill(initialKill);
        var detailedRule = publicRuleReader.observeDetailed();
        var rule = new AccountFactsSnapshot.Fact<>(detailedRule.status(),
                detailedRule.value() == null ? null : detailedRule.value().identity(),
                detailedRule.observedAt(), detailedRule.expiresAt(), detailedRule.source(), detailedRule.reason());
        requireKill(initialKill);
        Instant comparisonAt = clock.instant();
        var report = new AccountDivergenceComparator(jdbc).compare(observationId, account.exchangeAccountId(),
                account.legacyAccountId(), now, comparisonAt, mode, positions, balances, externalOrders,
                rule, detailedRule.value() == null ? null : detailedRule.value().minimumSize());
        var divergence = report.aggregate() == AccountDivergenceReport.Classification.UNKNOWN
                ? OkxAccountFactsObservationService.<String>unknown(comparisonAt,
                    report.items().isEmpty() ? "CANONICAL_OR_EXTERNAL_FACT_INCOMPLETE" : report.items().getFirst().reason())
                : new AccountFactsSnapshot.Fact<>(Status.OBSERVED,
                    report.aggregate() == AccountDivergenceReport.Classification.MATCH ? "MATCH" : "DIVERGED",
                    comparisonAt, comparisonAt.plus(PRIVATE_TTL), "NQ_CANONICAL_READ_COMPARISON",
                    report.aggregate() == AccountDivergenceReport.Classification.MATCH ? null
                        : report.aggregate().name());
        // 本地查询也会消耗时间；返回前重验 kill 与外部输入，不能给过期比较续期。
        requireKill(initialKill);
        Instant completed = clock.instant();
        if (mode.statusAt(completed) == Status.STALE
                || openOrders.statusAt(completed) == Status.STALE
                || balances.values().stream().anyMatch(f -> f.statusAt(completed) == Status.STALE)
                || ("2".equals(mode.value()) && positions.statusAt(completed) == Status.STALE)
                || divergence.statusAt(completed) == Status.STALE) {
            divergence = unknown(completed, "ACCOUNT_FACTS_EXPIRED_DURING_COMPARISON");
            report = null;
        }
        Status status = balances.values().stream().allMatch(f -> f.statusAt(completed) == Status.OBSERVED)
                && openOrders.statusAt(completed) == Status.OBSERVED
                && mode.statusAt(completed) == Status.OBSERVED
                && permissions.statusAt(completed) == Status.OBSERVED
                && exposure.statusAt(completed) == Status.OBSERVED
                && fee.statusAt(completed) == Status.OBSERVED
                && exchangeTime.statusAt(completed) == Status.OBSERVED
                && rule.statusAt(completed) == Status.OBSERVED
                && divergence.status() == Status.OBSERVED
                && (positions.status() == Status.NOT_APPLICABLE
                    || positions.statusAt(completed) == Status.OBSERVED) ? Status.OBSERVED : Status.UNKNOWN;
        return new AccountFactsSnapshot(observationId, "OKX", account.exchangeAccountId(),
                credentialReference, now,
                observed("ACTIVE", now, "NQ_CREDENTIAL_METADATA", PRIVATE_TTL),
                mode, permissions, balances, positions, exposure,
                openOrders, fee, exchangeTime, rule, divergence, report, status,
                status == Status.OBSERVED ? "PRIVATE_READ_ONLY" : "PARTIAL_ACCOUNT_FACTS");
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
                OkxAccountFactsObservationService.<List<OkxPrivatePositionFact>>unknown(at, "ACCOUNT_MODE_NOT_OBSERVED"),
                OkxAccountFactsObservationService.<BigDecimal>unknown(at, reason),
                OkxAccountFactsObservationService.<Integer>unknown(at, reason),
                OkxAccountFactsObservationService.<com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateFeeFact>unknown(at, reason),
                OkxAccountFactsObservationService.<Instant>unknown(at, reason), unknown, unknown, null,
                Status.REJECTED, reason);
    }

    private static <T> AccountFactsSnapshot.Fact<T> observed(T value, Instant at, String source, Duration ttl) {
        return new AccountFactsSnapshot.Fact<>(Status.OBSERVED, value, at, at.plus(ttl), source, null);
    }

    private static <T> AccountFactsSnapshot.Fact<T> unknown(Instant at, String reason) {
        return new AccountFactsSnapshot.Fact<>(Status.UNKNOWN, null, at, null, SOURCE, reason);
    }
}
