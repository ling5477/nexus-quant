package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialReference;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialReference.RemoteIpVerificationStatus;

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
    private final ExchangeAccountCredentialRepository credentialRepository;
    private final ScopedCredentialCapabilityPolicy credentialCapabilityPolicy;

    public OkxPrivateReadonlyProbeService(
            ExchangeAccountRepository accountRepository,
            OkxPrivateCredentialExecutor credentialExecutor,
            KillSwitchService killSwitchService,
            Clock clock
    ) {
        this(accountRepository, credentialExecutor, killSwitchService, clock, null, null);
    }

    /**
     * Scoped private-read composition constructor；额外依赖只读取 credential metadata，并在 JIT decrypt 前完成 policy。
     */
    public OkxPrivateReadonlyProbeService(
            ExchangeAccountRepository accountRepository,
            OkxPrivateCredentialExecutor credentialExecutor,
            KillSwitchService killSwitchService,
            Clock clock,
            ExchangeAccountCredentialRepository credentialRepository,
            ScopedCredentialCapabilityPolicy credentialCapabilityPolicy
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.credentialRepository = credentialRepository;
        this.credentialCapabilityPolicy = credentialCapabilityPolicy;
    }

    /**
     * 唯一 callable capability：精确 credential reference + production read-only diagnostic。
     *
     * <p>当前 Gate 要求 durable kill 持续 ENGAGED；metadata policy 通过后才进入既有 JIT executor，
     * 并只调用 account/config 与 balance 两个 typed GET operation。</p>
     */
    public ScopedPrivateReadonlyProbeObservation probeScopedDiagnostic(ScopedPrivateReadonlyProbeRequest request) {
        Instant observedAt = clock.instant();
        if (request == null || credentialRepository == null || credentialCapabilityPolicy == null) {
            return scopedProbeBlocked(observedAt, 0, null, "SCOPED_PROBE_NOT_CONFIGURED");
        }
        KillSwitchSnapshot kill = killSwitchService.snapshot();
        if (kill.status() != KillSwitchStatus.ENGAGED) {
            return scopedProbeBlocked(observedAt, safeReference(request.credentialReference()), request.capability(),
                    kill.status() == KillSwitchStatus.UNKNOWN
                            ? "KILL_SWITCH_STATE_UNKNOWN" : "KILL_SWITCH_NOT_ENGAGED");
        }
        try {
            ExchangeAccountSummary account = requireSafeAccount(
                    request.ownerId(), request.exchangeAccountId(), request.environment());
            ExchangeAccountCredentialSummary summary = credentialRepository.findByCredentialIdForOwner(
                            request.ownerId(), account.exchangeAccountId(), request.credentialReference())
                    .orElseThrow(() -> new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE));
            if (!Objects.equals(summary.exchangeAccountId(), request.exchangeAccountId())
                    || !Objects.equals(summary.credentialId(), request.credentialReference())
                    || !Objects.equals(summary.credentialType(), request.credentialType())) {
                throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
            }
            ScopedCredentialReference reference = ScopedCredentialReference.fromSummary(
                    request.ownerId(), "OKX_SPOT", request.capability(), summary);
            ScopedCredentialCapabilityPolicy.Decision decision =
                    credentialCapabilityPolicy.evaluate(reference, observedAt);
            if (decision.status() != ScopedCredentialCapabilityPolicy.Status.ALLOWED) {
                return scopedProbeBlocked(observedAt, reference.credentialReference(), request.capability(),
                        decision.reason().name());
            }
            return credentialExecutor.withActiveCredential(
                    request.ownerId(),
                    account.exchangeAccountId(),
                    request.credentialReference(),
                    request.credentialType(),
                    session -> executeScopedProbe(session, request, observedAt, kill)
            );
        } catch (OkxPrivateReadException exception) {
            return scopedProbeBlocked(observedAt, safeReference(request.credentialReference()), request.capability(),
                    exception.category().name());
        } catch (RuntimeException exception) {
            return scopedProbeBlocked(observedAt, safeReference(request.credentialReference()), request.capability(),
                    "SCOPED_PRIVATE_READ_FAILED");
        }
    }

    private ScopedPrivateReadonlyProbeObservation executeScopedProbe(
            OkxPrivateCredentialExecutor.CredentialSession session,
            ScopedPrivateReadonlyProbeRequest request,
            Instant observedAt,
            KillSwitchSnapshot expectedKill
    ) {
        if (!sameEngagedKill(expectedKill, killSwitchService.snapshot())) {
            return scopedProbeBlocked(observedAt, request.credentialReference(), request.capability(),
                    "KILL_SWITCH_CHANGED_DURING_PROBE");
        }
        OkxPrivateReadResult configuration = session.execute(
                OkxPrivateReadRequest.accountConfiguration(request.expectedIp()), request.environment());
        RemoteIpVerificationStatus ipStatus = remoteIpStatus(configuration.ipAllowlistStatus());
        if (!configuration.complete() || !SAFE_PERMISSION.equals(configuration.normalizedPermissions())
                || !configuration.ipAllowlistConfigured()
                || ipStatus != RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED) {
            return new ScopedPrivateReadonlyProbeObservation(
                    OkxPrivateProbeStatus.BLOCKED, observedAt, request.credentialReference(), request.capability(),
                    configuration.normalizedPermissions(), configuration.ipAllowlistConfigured(), ipStatus, null,
                    List.of("REMOTE_PERMISSION_OR_IP_NOT_VERIFIED"), true, true, true, false, true, false);
        }
        if (!sameEngagedKill(expectedKill, killSwitchService.snapshot())) {
            return scopedProbeBlocked(observedAt, request.credentialReference(), request.capability(),
                    "KILL_SWITCH_CHANGED_DURING_PROBE");
        }
        OkxPrivateReadResult balance = session.execute(
                OkxPrivateReadRequest.accountBalance(request.currencies()), request.environment());
        return new ScopedPrivateReadonlyProbeObservation(
                balance.complete() ? OkxPrivateProbeStatus.PASSED_READ_ONLY : OkxPrivateProbeStatus.PARTIAL,
                observedAt,
                request.credentialReference(),
                request.capability(),
                configuration.normalizedPermissions(),
                true,
                ipStatus,
                balance.complete() ? balance.assetCount() : null,
                balance.complete() ? List.of() : List.of(OkxPrivateReadError.PARTIAL_RESPONSE.name()),
                true, true, true, false, true, false
        );
    }

    private ScopedPrivateReadonlyProbeObservation scopedProbeBlocked(
            Instant observedAt,
            long credentialReference,
            com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapability capability,
            String blocker
    ) {
        return new ScopedPrivateReadonlyProbeObservation(
                OkxPrivateProbeStatus.BLOCKED,
                observedAt,
                credentialReference,
                capability == null
                        ? com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapability.FORBIDDEN
                        : capability,
                Set.of(),
                false,
                RemoteIpVerificationStatus.UNKNOWN,
                null,
                List.of(blocker),
                true, true, true, false, true, false
        );
    }

    private static long safeReference(Long value) {
        return value == null || value <= 0 ? 0 : value;
    }

    private static boolean sameEngagedKill(KillSwitchSnapshot expected, KillSwitchSnapshot current) {
        return expected != null
                && current != null
                && expected.status() == KillSwitchStatus.ENGAGED
                && current.status() == KillSwitchStatus.ENGAGED
                && expected.scope() == current.scope()
                && expected.version() == current.version()
                && Objects.equals(expected.updatedAt(), current.updatedAt())
                && Objects.equals(expected.source(), current.source());
    }

    private static RemoteIpVerificationStatus remoteIpStatus(OkxIpAllowlistStatus status) {
        return switch (status) {
            case MATCHED -> RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED;
            case MISSING -> RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_MISSING;
            case MISMATCHED -> RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_MISMATCH;
            case NOT_CHECKED -> RemoteIpVerificationStatus.NOT_VERIFIABLE;
            case UNKNOWN -> RemoteIpVerificationStatus.UNKNOWN;
        };
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
