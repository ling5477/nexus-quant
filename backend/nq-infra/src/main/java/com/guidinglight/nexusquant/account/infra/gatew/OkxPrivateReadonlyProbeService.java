package com.guidinglight.nexusquant.account.infra.gatew;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * GateW-2 非持久化 probe：config 必须先于 balance，权限未知或包含 Trade/Withdraw 时 fail-closed。
 *
 * <p>本服务无 scheduler/runner/controller，不写 repository、audit、account、ledger、position 或 snapshot。</p>
 */
public final class OkxPrivateReadonlyProbeService {

    private static final Set<String> SAFE_PERMISSION = Set.of("READ_ONLY");

    private final ExchangeAccountRepository accountRepository;
    private final OkxPrivateCredentialExecutor credentialExecutor;
    private final KillSwitchService killSwitchService;
    private final Clock clock;

    public OkxPrivateReadonlyProbeService(
            ExchangeAccountRepository accountRepository,
            OkxPrivateCredentialExecutor credentialExecutor,
            KillSwitchService killSwitchService,
            Clock clock
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 人工显式调用的只读诊断；credentialType 必须精确为 OKX_API_V5。
     */
    public OkxPrivateReadObservation probe(
            Long ownerId,
            Long exchangeAccountId,
            String credentialType,
            OkxPrivateEnvironment environment,
            Collection<String> currencies
    ) {
        return probeWithKillSwitchContract(
                ownerId,
                exchangeAccountId,
                credentialType,
                environment,
                currencies,
                KillSwitchContract.REQUIRE_DISENGAGED
        );
    }

    /**
     * GateW REAL readonly soak 的专用入口。该入口只允许在 GLOBAL_TRADING 持续 ENGAGED 时执行
     * 两个冻结的只读 operation；它不解除、不修改 kill switch，也不产生交易授权。
     */
    public OkxPrivateReadObservation probeWhileKillSwitchEngaged(
            Long ownerId,
            Long exchangeAccountId,
            String credentialType,
            OkxPrivateEnvironment environment,
            Collection<String> currencies
    ) {
        return probeWithKillSwitchContract(
                ownerId,
                exchangeAccountId,
                credentialType,
                environment,
                currencies,
                KillSwitchContract.REQUIRE_ENGAGED
        );
    }

    private OkxPrivateReadObservation probeWithKillSwitchContract(
            Long ownerId,
            Long exchangeAccountId,
            String credentialType,
            OkxPrivateEnvironment environment,
            Collection<String> currencies,
            KillSwitchContract killSwitchContract
    ) {
        Instant observedAt = clock.instant();
        KillSwitchSnapshot killSwitch = killSwitchService.snapshot();
        boolean killSwitchRejected = killSwitchContract == KillSwitchContract.REQUIRE_ENGAGED
                ? killSwitch.status() != KillSwitchStatus.ENGAGED
                : killSwitch.blocksOperations();
        if (killSwitchRejected) {
            String blocker = killSwitch.status() == KillSwitchStatus.UNKNOWN
                    ? "KILL_SWITCH_STATE_UNKNOWN"
                    : killSwitchContract == KillSwitchContract.REQUIRE_ENGAGED
                    ? "KILL_SWITCH_NOT_ENGAGED"
                    : "KILL_SWITCH_ENGAGED";
            return blocked(observedAt, blocker);
        }
        try {
            ExchangeAccountSummary account = requireSafeAccount(ownerId, exchangeAccountId, environment);
            return credentialExecutor.withActiveCredential(
                    ownerId,
                    account.exchangeAccountId(),
                    credentialType,
                    session -> executeProbe(session, environment, currencies, observedAt)
            );
        } catch (OkxPrivateReadException ex) {
            return blocked(observedAt, ex.category());
        } catch (RuntimeException ex) {
            return blocked(observedAt, OkxPrivateReadError.ACCOUNT_SCOPE_MISMATCH);
        }
    }

    private OkxPrivateReadObservation executeProbe(
            OkxPrivateCredentialExecutor.CredentialSession session,
            OkxPrivateEnvironment environment,
            Collection<String> currencies,
            Instant observedAt
    ) {
        OkxPrivateReadResult configuration = session.execute(
                OkxPrivateReadRequest.accountConfiguration(),
                environment
        );
        if (!configuration.complete() || configuration.normalizedPermissions().isEmpty()) {
            return blocked(observedAt, OkxPrivateReadError.PARTIAL_RESPONSE);
        }
        if (!SAFE_PERMISSION.equals(configuration.normalizedPermissions())) {
            return new OkxPrivateReadObservation(
                    OkxPrivateProbeStatus.BLOCKED,
                    observedAt,
                    null,
                    configuration.normalizedPermissions(),
                    configuration.ipAllowlistConfigured(),
                    null,
                    "BLOCKED",
                    List.of(OkxPrivateReadError.PERMISSION_BLOCKED.name()),
                    List.of(),
                    true, true, true, false, true, false
            );
        }
        if (!configuration.ipAllowlistConfigured()) {
            return new OkxPrivateReadObservation(
                    OkxPrivateProbeStatus.BLOCKED,
                    observedAt,
                    null,
                    configuration.normalizedPermissions(),
                    false,
                    null,
                    "BLOCKED",
                    List.of(OkxPrivateReadError.IP_ALLOWLIST_FAILED.name()),
                    List.of(),
                    true, true, true, false, true, false
            );
        }

        OkxPrivateReadResult balance = session.execute(
                OkxPrivateReadRequest.accountBalance(currencies),
                environment
        );
        return new OkxPrivateReadObservation(
                balance.complete() ? OkxPrivateProbeStatus.PASSED_READ_ONLY : OkxPrivateProbeStatus.PARTIAL,
                observedAt,
                null,
                configuration.normalizedPermissions(),
                configuration.ipAllowlistConfigured(),
                balance.complete() ? balance.assetCount() : null,
                balance.complete() ? "COMPLETE" : "PARTIAL",
                List.of(),
                balance.complete() ? List.of() : List.of(OkxPrivateReadError.PARTIAL_RESPONSE.name()),
                true, true, true, false, true, false
        );
    }

    private ExchangeAccountSummary requireSafeAccount(
            Long ownerId,
            Long exchangeAccountId,
            OkxPrivateEnvironment environment
    ) {
        if (ownerId == null || ownerId <= 0 || exchangeAccountId == null || exchangeAccountId <= 0) {
            throw new OkxPrivateReadException(OkxPrivateReadError.ACCOUNT_SCOPE_MISMATCH);
        }
        Objects.requireNonNull(environment, "environment must not be null");
        ExchangeAccountSummary account = accountRepository.findByIdForOwner(ownerId, exchangeAccountId)
                .orElseThrow(() -> new OkxPrivateReadException(OkxPrivateReadError.ACCOUNT_SCOPE_MISMATCH));
        if (!"OKX".equalsIgnoreCase(account.exchangeCode())
                || !"ACTIVE".equalsIgnoreCase(account.status())
                || !environment.accountTradeEnvironment().equalsIgnoreCase(account.tradeEnv())) {
            throw new OkxPrivateReadException(OkxPrivateReadError.ENVIRONMENT_MISMATCH);
        }
        return account;
    }

    private OkxPrivateReadObservation blocked(Instant observedAt, OkxPrivateReadError error) {
        return blocked(observedAt, error.name());
    }

    private OkxPrivateReadObservation blocked(Instant observedAt, String blocker) {
        return new OkxPrivateReadObservation(
                OkxPrivateProbeStatus.BLOCKED,
                observedAt,
                null,
                Set.of(),
                false,
                null,
                "UNKNOWN",
                List.of(blocker),
                List.of(),
                true, true, true, false, true, false
        );
    }

    private enum KillSwitchContract {
        REQUIRE_DISENGAGED,
        REQUIRE_ENGAGED
    }
}
