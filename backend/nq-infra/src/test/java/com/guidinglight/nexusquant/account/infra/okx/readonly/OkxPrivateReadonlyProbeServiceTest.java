package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateCredentialContext;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapability;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialReference.RemoteIpVerificationStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OkxPrivateReadonlyProbeServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void legacyExecutorMustFailClosedForExactCredentialReference() {
        OkxPrivateCredentialExecutor legacy = new OkxPrivateCredentialExecutor() {
            @Override
            public <T> T withActiveCredential(
                    Long ownerId,
                    Long accountId,
                    String credentialType,
                    CredentialCallback<T> callback
            ) {
                throw new AssertionError("non-exact credential lookup must not be called");
            }
        };

        OkxPrivateReadException failure = assertThrows(OkxPrivateReadException.class,
                () -> legacy.withActiveCredential(7L, 9L, 11L, "OKX_API_V5", session -> null));

        assertEquals(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE, failure.category());
    }

    @Test
    void callsConfigBeforeBalanceOnlyForExplicitReadOnlyPermission() {
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        TrackingExecutor executor = new TrackingExecutor();
        OkxPrivateReadObservation observation = service(account("OKX", "SIM", "ACTIVE"), executor, transport)
                .probe(7L, 9L, "OKX_API_V5", OkxPrivateEnvironment.DEMO, List.of("BTC"));

        assertEquals(List.of(
                OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ
        ), transport.operations);
        assertEquals(1, executor.callbacks.get());
        assertEquals(OkxPrivateProbeStatus.PASSED_READ_ONLY, observation.probeStatus());
        assertTrue(observation.ipAllowlistConfigured());
        assertEquals(2, observation.assetCount());
        assertTrue(observation.diagnosticOnly());
        assertTrue(observation.noSideEffect());
        assertTrue(observation.notTradingAuthorization());
        assertFalse(observation.tradingAuthorization());
        assertTrue(observation.liveDisabled());
        assertFalse(observation.orderSubmitted());
    }

    @Test
    void blocksTradeWithdrawUnknownAndMissingPermissionsWithoutBalance() {
        for (Set<String> permissions : List.of(
                Set.of("READ_ONLY", "TRADE"),
                Set.of("READ_ONLY", "WITHDRAW"),
                Set.of("READ_ONLY", "UNKNOWN_SCOPE"),
                Set.<String>of()
        )) {
            RecordingTransport transport = new RecordingTransport(permissions, !permissions.isEmpty(), true);
            OkxPrivateReadObservation observation = service(account("OKX", "SIM", "ACTIVE"),
                    new TrackingExecutor(), transport)
                    .probe(7L, 9L, "OKX_API_V5", OkxPrivateEnvironment.DEMO, List.of());

            assertEquals(OkxPrivateProbeStatus.BLOCKED, observation.probeStatus());
            assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), transport.operations);
            assertNull(observation.assetCount());
        }
    }

    @Test
    void returnsPartialForIncompleteBalanceWithoutInventingValues() {
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), true, false);
        OkxPrivateReadObservation observation = service(account("OKX", "LIVE", "ACTIVE"),
                new TrackingExecutor(), transport)
                .probe(7L, 9L, "OKX_API_V5", OkxPrivateEnvironment.PRODUCTION, List.of("BTC"));

        assertEquals(OkxPrivateProbeStatus.PARTIAL, observation.probeStatus());
        assertEquals("PARTIAL", observation.dataCompleteness());
        assertNull(observation.assetCount());
        assertEquals(List.of(OkxPrivateReadError.PARTIAL_RESPONSE.name()), observation.warnings());
    }

    @Test
    void incompleteConfigurationNeverCallsBalanceEvenWhenPermissionLooksReadOnly() {
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), false, true);
        OkxPrivateReadObservation observation = service(account("OKX", "SIM", "ACTIVE"),
                new TrackingExecutor(), transport)
                .probe(7L, 9L, "OKX_API_V5", OkxPrivateEnvironment.DEMO, List.of());

        assertEquals(OkxPrivateProbeStatus.BLOCKED, observation.probeStatus());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), transport.operations);
        assertNull(observation.assetCount());
    }

    @Test
    void failsClosedBeforeDecryptForOwnerExchangeStatusOrEnvironmentMismatch() {
        for (ExchangeAccountSummary account : List.of(
                account("BINANCE", "SIM", "ACTIVE"),
                account("OKX", "SIM", "DISABLED"),
                account("OKX", "LIVE", "ACTIVE")
        )) {
            TrackingExecutor executor = new TrackingExecutor();
            OkxPrivateReadObservation observation = service(account, executor,
                    new RecordingTransport(Set.of("READ_ONLY"), true, true))
                    .probe(7L, 9L, "OKX_API_V5", OkxPrivateEnvironment.DEMO, List.of());

            assertEquals(OkxPrivateProbeStatus.BLOCKED, observation.probeStatus());
            assertEquals(0, executor.callbacks.get());
        }
    }

    @Test
    void configFailureNeverCallsBalanceAndReturnsSanitizedBlocker() {
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        transport.failure = OkxPrivateReadError.CLOCK_SKEW;
        OkxPrivateReadObservation observation = service(account("OKX", "SIM", "ACTIVE"),
                new TrackingExecutor(), transport)
                .probe(7L, 9L, "OKX_API_V5", OkxPrivateEnvironment.DEMO, List.of());

        assertEquals(OkxPrivateProbeStatus.BLOCKED, observation.probeStatus());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), transport.operations);
        assertEquals(List.of("CLOCK_SKEW"), observation.blockers());
    }

    @Test
    void blocksMissingIpAllowlistBeforeBalance() {
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), true, true, false);

        OkxPrivateReadObservation observation = service(
                account("OKX", "LIVE", "ACTIVE"),
                new TrackingExecutor(),
                transport
        ).probe(7L, 9L, "OKX_API_V5", OkxPrivateEnvironment.PRODUCTION, List.of("BTC"));

        assertEquals(OkxPrivateProbeStatus.BLOCKED, observation.probeStatus());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), transport.operations);
        assertEquals(List.of(OkxPrivateReadError.IP_ALLOWLIST_FAILED.name()), observation.blockers());
        assertFalse(observation.ipAllowlistConfigured());
    }

    @Test
    void engagedUnknownAndRepositoryFailureStopBeforeCredentialAndNetwork() {
        assertStoppedBeforeCredentialAndNetwork(
                killSwitchRepository(KillSwitchStatus.ENGAGED),
                "KILL_SWITCH_ENGAGED"
        );
        assertStoppedBeforeCredentialAndNetwork(
                killSwitchRepository(null),
                "KILL_SWITCH_STATE_UNKNOWN"
        );
        assertStoppedBeforeCredentialAndNetwork(
                new FailingKillSwitchRepository(),
                "KILL_SWITCH_STATE_UNKNOWN"
        );
    }

    @Test
    void realReadonlySoakRequiresEngagedWithoutDisengagingOrBypassingUnknown() {
        TrackingExecutor engagedExecutor = new TrackingExecutor();
        RecordingTransport engagedTransport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        engagedExecutor.transport = engagedTransport;
        OkxPrivateReadonlyProbeService engagedService = new OkxPrivateReadonlyProbeService(
                new StubAccountRepository(account("OKX", "LIVE", "ACTIVE")),
                engagedExecutor,
                new KillSwitchService(killSwitchRepository(KillSwitchStatus.ENGAGED), CLOCK),
                CLOCK
        );

        OkxPrivateReadObservation accepted = engagedService.probeWhileKillSwitchEngaged(
                7L,
                9L,
                "OKX_API_V5",
                OkxPrivateEnvironment.PRODUCTION,
                List.of("BTC")
        );

        assertEquals(OkxPrivateProbeStatus.PASSED_READ_ONLY, accepted.probeStatus());
        assertEquals(1, engagedExecutor.callbacks.get());
        assertEquals(List.of(
                OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ
        ), engagedTransport.operations);

        for (KillSwitchStatus unsafeStatus : List.of(KillSwitchStatus.DISENGAGED, KillSwitchStatus.UNKNOWN)) {
            TrackingExecutor blockedExecutor = new TrackingExecutor();
            RecordingTransport blockedTransport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
            blockedExecutor.transport = blockedTransport;
            OkxPrivateReadonlyProbeService blockedService = new OkxPrivateReadonlyProbeService(
                    new StubAccountRepository(account("OKX", "LIVE", "ACTIVE")),
                    blockedExecutor,
                    new KillSwitchService(killSwitchRepository(unsafeStatus), CLOCK),
                    CLOCK
            );

            OkxPrivateReadObservation blocked = blockedService.probeWhileKillSwitchEngaged(
                    7L,
                    9L,
                    "OKX_API_V5",
                    OkxPrivateEnvironment.PRODUCTION,
                    List.of("BTC")
            );

            assertEquals(OkxPrivateProbeStatus.BLOCKED, blocked.probeStatus());
            assertEquals(0, blockedExecutor.callbacks.get());
            assertTrue(blockedTransport.operations.isEmpty());
        }
    }

    @Test
    void scopedDiagnosticBindsExactCredentialAndRemotePermissionIpFacts() {
        TrackingExecutor executor = new TrackingExecutor();
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        transport.ipStatus = OkxIpAllowlistStatus.MATCHED;
        executor.transport = transport;
        ExchangeAccountCredentialRepository credentials = mock(ExchangeAccountCredentialRepository.class);
        ExchangeAccountCredentialSummary summary = credentialSummary("READ_ONLY", false, "SUCCEEDED", "PASSED");
        when(credentials.findByCredentialIdForOwner(7L, 9L, 13L)).thenReturn(Optional.of(summary));
        OkxPrivateReadonlyProbeService service = new OkxPrivateReadonlyProbeService(
                new StubAccountRepository(account("OKX", "LIVE", "ACTIVE")),
                executor,
                new KillSwitchService(killSwitchRepository(KillSwitchStatus.ENGAGED), CLOCK),
                CLOCK,
                credentials,
                new ScopedCredentialCapabilityPolicy(Duration.ofHours(1))
        );

        ScopedPrivateReadonlyProbeObservation observation = service.probeScopedDiagnostic(
                new ScopedPrivateReadonlyProbeRequest(
                        7L, 9L, 13L, "OKX_API_V5",
                        ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC,
                        OkxPrivateEnvironment.PRODUCTION, List.of("BTC"), "203.0.113.10"));

        assertEquals(OkxPrivateProbeStatus.PASSED_READ_ONLY, observation.probeStatus());
        assertEquals(List.of(13L), executor.credentialReferences);
        assertEquals(RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED,
                observation.remoteIpVerificationStatus());
        assertEquals(List.of(
                OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ), transport.operations);
        assertFalse(observation.tradingAuthorization());
    }

    @Test
    void scopedDiagnosticRechecksExactKillFactBeforeBalanceRead() {
        MutableKillSwitchRepository killRepository = new MutableKillSwitchRepository();
        TrackingExecutor executor = new TrackingExecutor();
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        transport.ipStatus = OkxIpAllowlistStatus.MATCHED;
        transport.afterConfiguration = () -> killRepository.status = KillSwitchStatus.DISENGAGED;
        executor.transport = transport;
        ExchangeAccountCredentialRepository credentials = mock(ExchangeAccountCredentialRepository.class);
        when(credentials.findByCredentialIdForOwner(7L, 9L, 13L))
                .thenReturn(Optional.of(credentialSummary("READ_ONLY", false, "SUCCEEDED", "PASSED")));
        OkxPrivateReadonlyProbeService service = new OkxPrivateReadonlyProbeService(
                new StubAccountRepository(account("OKX", "LIVE", "ACTIVE")), executor,
                new KillSwitchService(killRepository, CLOCK), CLOCK,
                credentials, new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)));

        ScopedPrivateReadonlyProbeObservation observation = service.probeScopedDiagnostic(
                new ScopedPrivateReadonlyProbeRequest(
                        7L, 9L, 13L, "OKX_API_V5",
                        ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC,
                        OkxPrivateEnvironment.PRODUCTION, List.of("BTC"), "203.0.113.10"));

        assertEquals(OkxPrivateProbeStatus.BLOCKED, observation.probeStatus());
        assertEquals(List.of("KILL_SWITCH_CHANGED_DURING_PROBE"), observation.blockers());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), transport.operations);
    }

    @Test
    void futureCapabilityAndUnverifiableIpStopBeforeUnsafeContinuation() {
        TrackingExecutor futureExecutor = new TrackingExecutor();
        futureExecutor.transport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        ExchangeAccountCredentialRepository credentials = mock(ExchangeAccountCredentialRepository.class);
        when(credentials.findByCredentialIdForOwner(7L, 9L, 13L))
                .thenReturn(Optional.of(credentialSummary("READ_ONLY", false, "SUCCEEDED", "PASSED")));
        OkxPrivateReadonlyProbeService service = new OkxPrivateReadonlyProbeService(
                new StubAccountRepository(account("OKX", "LIVE", "ACTIVE")), futureExecutor,
                new KillSwitchService(killSwitchRepository(KillSwitchStatus.ENGAGED), CLOCK), CLOCK,
                credentials, new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)));

        ScopedPrivateReadonlyProbeObservation future = service.probeScopedDiagnostic(new ScopedPrivateReadonlyProbeRequest(
                7L, 9L, 13L, "OKX_API_V5", ScopedCredentialCapability.FUTURE_MICRO_LIVE,
                OkxPrivateEnvironment.PRODUCTION, List.of("BTC"), "203.0.113.10"));

        assertEquals(OkxPrivateProbeStatus.BLOCKED, future.probeStatus());
        assertEquals(0, futureExecutor.callbacks.get());
        assertEquals(List.of("FUTURE_CAPABILITY_NOT_CALLABLE"), future.blockers());

        TrackingExecutor ipExecutor = new TrackingExecutor();
        RecordingTransport ipTransport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        ipTransport.ipStatus = OkxIpAllowlistStatus.NOT_CHECKED;
        ipExecutor.transport = ipTransport;
        OkxPrivateReadonlyProbeService ipService = new OkxPrivateReadonlyProbeService(
                new StubAccountRepository(account("OKX", "LIVE", "ACTIVE")), ipExecutor,
                new KillSwitchService(killSwitchRepository(KillSwitchStatus.ENGAGED), CLOCK), CLOCK,
                credentials, new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)));
        ScopedPrivateReadonlyProbeObservation ip = ipService.probeScopedDiagnostic(new ScopedPrivateReadonlyProbeRequest(
                7L, 9L, 13L, "OKX_API_V5", ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC,
                OkxPrivateEnvironment.PRODUCTION, List.of("BTC"), "203.0.113.10"));
        assertEquals(OkxPrivateProbeStatus.BLOCKED, ip.probeStatus());
        assertEquals(RemoteIpVerificationStatus.NOT_VERIFIABLE, ip.remoteIpVerificationStatus());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), ipTransport.operations);
    }

    private static void assertStoppedBeforeCredentialAndNetwork(
            KillSwitchStateRepository killSwitchRepository,
            String expectedBlocker
    ) {
        TrackingExecutor executor = new TrackingExecutor();
        RecordingTransport transport = new RecordingTransport(Set.of("READ_ONLY"), true, true);
        executor.transport = transport;
        OkxPrivateReadonlyProbeService service = new OkxPrivateReadonlyProbeService(
                new FailOnAccessAccountRepository(),
                executor,
                new KillSwitchService(killSwitchRepository, CLOCK),
                CLOCK
        );

        OkxPrivateReadObservation observation = service.probe(
                7L,
                9L,
                "OKX_API_V5",
                OkxPrivateEnvironment.DEMO,
                List.of("BTC")
        );

        assertEquals(OkxPrivateProbeStatus.BLOCKED, observation.probeStatus());
        assertEquals(List.of(expectedBlocker), observation.blockers());
        assertEquals(0, executor.callbacks.get());
        assertTrue(transport.operations.isEmpty());
    }

    private static OkxPrivateReadonlyProbeService service(
            ExchangeAccountSummary account,
            TrackingExecutor executor,
            RecordingTransport transport
    ) {
        ExchangeAccountRepository repository = new StubAccountRepository(account);
        executor.transport = transport;
        return new OkxPrivateReadonlyProbeService(
                repository,
                executor,
                new KillSwitchService(killSwitchRepository(KillSwitchStatus.DISENGAGED), CLOCK),
                CLOCK
        );
    }

    private static KillSwitchStateRepository killSwitchRepository(KillSwitchStatus status) {
        return new KillSwitchStateRepository() {
            @Override
            public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
                if (status == null) {
                    return Optional.empty();
                }
                return Optional.of(new KillSwitchState(
                        scope,
                        status,
                        1,
                        "TEST_STATE",
                        "TEST_FIXTURE",
                        CLOCK.instant().minusSeconds(1),
                        "tester",
                        "trace-kill-switch-fixture"
                ));
            }

            @Override
            public KillSwitchState engage(KillSwitchEngageCommand command) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static ExchangeAccountSummary account(String exchange, String environment, String status) {
        return new ExchangeAccountSummary(9L, null, 7L, exchange, environment, "test", null, false, status);
    }

    private static ExchangeAccountCredentialSummary credentialSummary(
            String scope,
            boolean withdraw,
            String permissionStatus,
            String ipStatus
    ) {
        return new ExchangeAccountCredentialSummary(
                13L, 9L, "OKX_API_V5", "****", "ACTIVE", "VERIFIED", true,
                null, null, null, CLOCK.instant().minusSeconds(60), null, CLOCK.instant().minusSeconds(60),
                permissionStatus, scope, withdraw, ipStatus, 0, CLOCK.instant().minusSeconds(60), null);
    }

    private static final class TrackingExecutor implements OkxPrivateCredentialExecutor {
        private final AtomicInteger callbacks = new AtomicInteger();
        private final List<Long> credentialReferences = new ArrayList<>();
        private RecordingTransport transport;

        @Override
        public <T> T withActiveCredential(
                Long ownerId,
                Long accountId,
                String type,
                CredentialCallback<T> callback
        ) {
            if (!"OKX_API_V5".equals(type)) {
                throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
            }
            callbacks.incrementAndGet();
            return callback.execute((request, environment) -> transport.execute(request, null, environment));
        }

        @Override
        public <T> T withActiveCredential(
                Long ownerId,
                Long accountId,
                Long credentialId,
                String type,
                CredentialCallback<T> callback
        ) {
            credentialReferences.add(credentialId);
            return withActiveCredential(ownerId, accountId, type, callback);
        }
    }

    private static final class RecordingTransport implements OkxPrivateReadTransport {
        private final Set<String> permissions;
        private final boolean configComplete;
        private final boolean balanceComplete;
        private final boolean ipAllowlistConfigured;
        private final List<OkxPrivateReadOperation> operations = new ArrayList<>();
        private OkxPrivateReadError failure;
        private OkxIpAllowlistStatus ipStatus = OkxIpAllowlistStatus.NOT_CHECKED;
        private Runnable afterConfiguration = () -> { };

        private RecordingTransport(Set<String> permissions, boolean configComplete, boolean balanceComplete) {
            this(permissions, configComplete, balanceComplete, true);
        }

        private RecordingTransport(
                Set<String> permissions,
                boolean configComplete,
                boolean balanceComplete,
                boolean ipAllowlistConfigured
        ) {
            this.permissions = permissions;
            this.configComplete = configComplete;
            this.balanceComplete = balanceComplete;
            this.ipAllowlistConfigured = ipAllowlistConfigured;
        }

        @Override
        public OkxPrivateReadResult execute(
                OkxPrivateReadRequest request,
                OkxPrivateCredentialContext credential,
                OkxPrivateEnvironment environment
        ) {
            operations.add(request.operation());
            if (failure != null) {
                throw new OkxPrivateReadException(failure);
            }
            if (request.operation() == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ) {
                OkxPrivateReadResult result = new OkxPrivateReadResult(
                        request.operation(), permissions, 0, configComplete,
                        List.of(), List.of(), ipAllowlistConfigured, ipStatus, CLOCK.instant());
                afterConfiguration.run();
                return result;
            }
            return new OkxPrivateReadResult(request.operation(), Set.of(), 2, balanceComplete);
        }
    }

    private static final class MutableKillSwitchRepository implements KillSwitchStateRepository {
        private KillSwitchStatus status = KillSwitchStatus.ENGAGED;

        @Override
        public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            return Optional.of(new KillSwitchState(
                    scope, status, status == KillSwitchStatus.ENGAGED ? 1 : 2,
                    "TEST_STATE", "TEST_FIXTURE", CLOCK.instant().minusSeconds(1),
                    "tester", "trace-kill-switch-race"));
        }

        @Override
        public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubAccountRepository(ExchangeAccountSummary account) implements ExchangeAccountRepository {
        @Override
        public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
            return List.of(account);
        }

        @Override
        public Optional<ExchangeAccountSummary> findById(Long accountId) {
            return accountId.equals(account.exchangeAccountId()) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long accountId) {
            return ownerUserId.equals(account.ownerUserId()) && accountId.equals(account.exchangeAccountId())
                    ? Optional.of(account) : Optional.empty();
        }

        @Override
        public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
            return Optional.empty();
        }

        @Override
        public ExchangeAccountSummary create(Long ownerUserId, String exchangeCode, String tradeEnv, String accountAlias, String externalAccountRef, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateProfile(Long ownerUserId, Long exchangeAccountId, String accountAlias, String externalAccountRef, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FailOnAccessAccountRepository implements ExchangeAccountRepository {
        private static AssertionError unexpected() {
            return new AssertionError("account repository must not be called before kill-switch pass");
        }

        @Override
        public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
            throw unexpected();
        }

        @Override
        public Optional<ExchangeAccountSummary> findById(Long accountId) {
            throw unexpected();
        }

        @Override
        public Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long accountId) {
            throw unexpected();
        }

        @Override
        public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
            throw unexpected();
        }

        @Override
        public ExchangeAccountSummary create(Long ownerUserId, String exchangeCode, String tradeEnv, String accountAlias, String externalAccountRef, Instant now) {
            throw unexpected();
        }

        @Override
        public boolean updateProfile(Long ownerUserId, Long exchangeAccountId, String accountAlias, String externalAccountRef, Instant now) {
            throw unexpected();
        }

        @Override
        public boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw unexpected();
        }

        @Override
        public boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw unexpected();
        }

        @Override
        public void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now) {
            throw unexpected();
        }

        @Override
        public boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw unexpected();
        }
    }

    private static final class FailingKillSwitchRepository implements KillSwitchStateRepository {
        @Override
        public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            throw new IllegalStateException("simulated kill-switch repository failure");
        }

        @Override
        public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
