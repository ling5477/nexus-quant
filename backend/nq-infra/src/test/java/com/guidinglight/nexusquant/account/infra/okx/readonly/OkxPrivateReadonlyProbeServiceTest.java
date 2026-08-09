package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateCredentialContext;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
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
import org.junit.jupiter.api.Test;

import java.time.Clock;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxPrivateReadonlyProbeServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);

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

    private static final class TrackingExecutor implements OkxPrivateCredentialExecutor {
        private final AtomicInteger callbacks = new AtomicInteger();
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
    }

    private static final class RecordingTransport implements OkxPrivateReadTransport {
        private final Set<String> permissions;
        private final boolean configComplete;
        private final boolean balanceComplete;
        private final boolean ipAllowlistConfigured;
        private final List<OkxPrivateReadOperation> operations = new ArrayList<>();
        private OkxPrivateReadError failure;

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
            return request.operation() == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ
                    ? new OkxPrivateReadResult(
                    request.operation(), permissions, 0, configComplete,
                    List.of(), List.of(), ipAllowlistConfigured, CLOCK.instant())
                    : new OkxPrivateReadResult(request.operation(), Set.of(), 2, balanceComplete);
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
