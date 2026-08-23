package com.guidinglight.nexusquant.livecontrol.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingAuthority;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingConsumptionCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingValidation;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ExactPilotBindingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T01:00:00.000000Z");
    private static final Instant WINDOW_START = NOW.minusSeconds(60);
    private static final Instant WINDOW_END = NOW.plusSeconds(600);
    private static final long OWNER_ID = 11L;
    private static final String RELEASE = "1".repeat(40);
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    private AuthenticatedLiveControlActor actor;
    private ExactPilotBindingCommand command;
    private ExactPilotBinding.AuthoritativeFacts facts;
    private MutableAuthority authority;
    private FakeRepository repository;
    private ExactPilotBindingService service;

    @BeforeEach
    void setUp() {
        actor = new AuthenticatedLiveControlActor(OWNER_ID);
        var order = order(100L, "BTC-USDT", ExactPilotBinding.Side.BUY, "100.00000000", "0.10000000");
        command = new ExactPilotBindingCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), order,
                WINDOW_START, WINDOW_END, correlation("create"), NOW.plusSeconds(300)
        );
        facts = facts(command, deployment(RELEASE, DIGEST_A, "server-a"), account(OWNER_ID, 21L, 31L),
                order, observations(), risk(UUID.randomUUID(), 1, DIGEST_A));
        authority = new MutableAuthority(facts);
        repository = new FakeRepository(session(command.sessionId(), facts));
        LiveControlAuthorizationPort authorization = mock(LiveControlAuthorizationPort.class);
        when(authorization.lockAndCheckRole(OWNER_ID, "OPERATOR")).thenReturn(true);
        service = new ExactPilotBindingService(authority, repository, authorization, new NoopTransactions());
    }

    @Test
    void createsValidatesAndConsumesExactlyOnceWithoutTradingAuthorization() {
        ExactPilotBinding binding = service.create(actor, command);

        assertTrue(binding.hasCanonicalDigest());
        assertEquals(ExactPilotBinding.Lifecycle.VERIFIED,
                service.validate(actor, binding.sessionId(), binding.id()).lifecycle());

        var consumption = service.consume(actor, new ExactPilotBindingConsumptionCommand(
                binding.sessionId(), binding.id(), binding.order(), correlation("consume")));

        assertFalse(consumption.tradingAuthorized());
        assertFalse(consumption.exchangeMutation());
        assertEquals(ExactPilotBinding.Lifecycle.CONSUMED,
                service.validate(actor, binding.sessionId(), binding.id()).lifecycle());
        LiveControlException secondUse = assertThrows(LiveControlException.class, () -> service.consume(
                actor, new ExactPilotBindingConsumptionCommand(
                        binding.sessionId(), binding.id(), binding.order(), correlation("consume-2"))));
        assertEquals("EXACT_PILOT_BINDING_ALREADY_CONSUMED", secondUse.code());
        assertEquals(0, authority.credentialMaterialReads);
        assertEquals(0, authority.providerCalls);
        assertEquals(0, authority.orders);
        assertEquals(0, authority.ledgerDelta);
    }

    @Test
    void everyAuthoritativeFactDriftInvalidatesBinding() {
        ExactPilotBinding binding = service.create(actor, command);
        Map<String, Supplier<ExactPilotBinding.AuthoritativeFacts>> driftCases = new LinkedHashMap<>();
        driftCases.put("wrong release", () -> withDeployment(facts, deployment("2".repeat(40), DIGEST_A, "server-a")));
        driftCases.put("manifest changed", () -> withDeployment(facts, deployment(RELEASE, DIGEST_B, "server-a")));
        driftCases.put("wrong server", () -> withDeployment(facts, deployment(RELEASE, DIGEST_A, "server-b")));
        driftCases.put("wrong profile", authority::rejectingFacts);
        driftCases.put("wrong owner", () -> withAccount(facts, account(12L, 21L, 31L)));
        driftCases.put("wrong account", () -> withAccount(facts, account(OWNER_ID, 22L, 31L)));
        driftCases.put("wrong credential", () -> withAccount(facts, account(OWNER_ID, 21L, 32L)));
        driftCases.put("wrong instrument", () -> withOrder(facts,
                order(101L, "ETH-USDT", ExactPilotBinding.Side.BUY, "100.00000000", "0.10000000")));
        driftCases.put("wrong side", () -> withOrder(facts,
                order(100L, "BTC-USDT", ExactPilotBinding.Side.SELL, "100.00000000", "0.10000000")));
        driftCases.put("price changed", () -> withOrder(facts,
                order(100L, "BTC-USDT", ExactPilotBinding.Side.BUY, "101.00000000", "0.10000000")));
        driftCases.put("quantity changed", () -> withOrder(facts,
                order(100L, "BTC-USDT", ExactPilotBinding.Side.BUY, "100.00000000", "0.20000000")));
        driftCases.put("instrument fact changed", () -> withObservations(facts, observations()));
        driftCases.put("fee fact changed", () -> withObservations(facts, observations()));
        driftCases.put("balance fact changed", () -> withObservations(facts, observations()));
        driftCases.put("time fact changed", () -> withObservations(facts, observations()));
        driftCases.put("risk id changed", () -> withRisk(facts, risk(UUID.randomUUID(), 1, DIGEST_A)));
        driftCases.put("risk version changed", () -> withRisk(facts,
                risk(facts.riskPolicy().riskLimitSetId(), 2, DIGEST_A)));
        driftCases.put("risk digest changed", () -> withRisk(facts,
                risk(facts.riskPolicy().riskLimitSetId(), 1, DIGEST_B)));
        driftCases.put("kill disengaged", authority::rejectingFacts);

        for (Map.Entry<String, Supplier<ExactPilotBinding.AuthoritativeFacts>> entry : driftCases.entrySet()) {
            authority.current = entry.getValue();
            ExactPilotBindingValidation validation = service.validate(actor, binding.sessionId(), binding.id());
            assertEquals(ExactPilotBinding.Lifecycle.INVALID, validation.lifecycle(), entry.getKey());
            assertEquals(List.of(ExactPilotBindingValidation.Violation.AUTHORITATIVE_FACT_DRIFT),
                    validation.violations(), entry.getKey());
        }
    }

    @Test
    void rejectsScopeExpansionOrderChangesAndInconsistentNotional() {
        authority.creation = () -> withOrder(facts,
                order(100L, "BTC-USDT", ExactPilotBinding.Side.SELL, "100.00000000", "0.10000000"));
        LiveControlException expansion = assertThrows(LiveControlException.class, () -> service.create(actor, command));
        assertEquals("EXACT_PILOT_BINDING_SCOPE_EXPANSION_REJECTED", expansion.code());

        authority.creation = () -> facts;
        ExactPilotBinding binding = service.create(actor, command);
        LiveControlException changedAttempt = assertThrows(LiveControlException.class, () -> service.consume(
                actor, new ExactPilotBindingConsumptionCommand(
                        binding.sessionId(), binding.id(),
                        order(100L, "BTC-USDT", ExactPilotBinding.Side.BUY,
                                "100.00000000", "0.20000000"), correlation("changed"))));
        assertEquals("EXACT_PILOT_ATTEMPT_ORDER_MISMATCH", changedAttempt.code());

        assertThrows(IllegalArgumentException.class, () -> new ExactPilotBinding.OrderEnvelope(
                100L, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("11")));
        assertEquals(List.of(ExactPilotBinding.OrderType.LIMIT),
                Arrays.asList(ExactPilotBinding.OrderType.values()));
    }

    @Test
    void rejectsExpiredTamperedAndIdempotencyConflictingBindings() {
        ExactPilotBinding binding = service.create(actor, command);
        repository.now = binding.bindingExpiresAt();
        assertEquals(ExactPilotBinding.Lifecycle.EXPIRED,
                service.validate(actor, binding.sessionId(), binding.id()).lifecycle());

        repository.now = NOW;
        repository.binding = copyWithDigest(binding, DIGEST_B);
        ExactPilotBindingValidation tampered = service.validate(actor, binding.sessionId(), binding.id());
        assertEquals(ExactPilotBinding.Lifecycle.INVALID, tampered.lifecycle());
        assertEquals(List.of(ExactPilotBindingValidation.Violation.BINDING_DIGEST_MISMATCH),
                tampered.violations());

        repository.binding = binding;
        ExactPilotBinding replay = service.create(actor, command);
        assertEquals(binding, replay);
        ExactPilotBindingCommand conflict = new ExactPilotBindingCommand(
                command.bindingId(), command.sessionId(), command.pilotScopeId(), command.observationSetId(),
                order(100L, "BTC-USDT", ExactPilotBinding.Side.BUY, "100.00000000", "0.20000000"),
                command.pilotWindowStart(), command.pilotWindowEnd(), command.correlation(),
                command.bindingExpiresAt());
        LiveControlException rejected = assertThrows(LiveControlException.class,
                () -> service.create(actor, conflict));
        assertEquals("EXACT_PILOT_BINDING_IDEMPOTENCY_CONFLICT", rejected.code());
    }

    @Test
    void controlPlaneHasNoExecutionProviderOrOrderMutationDependency() {
        assertTrue(Arrays.stream(ExactPilotBindingService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .noneMatch(name -> name.contains("execution")
                        || name.contains("provider")
                        || name.contains("OrderRepository")
                        || name.contains("Ledger")));
    }

    private static ExactPilotBinding.AuthoritativeFacts facts(
            ExactPilotBindingCommand command,
            ExactPilotBinding.DeploymentIdentity deployment,
            ExactPilotBinding.AccountIdentity account,
            ExactPilotBinding.OrderEnvelope order,
            ExactPilotBinding.ObservationIdentities observations,
            ExactPilotBinding.RiskPolicyIdentity risk
    ) {
        return new ExactPilotBinding.AuthoritativeFacts(
                command.sessionId(), command.pilotScopeId(), command.observationSetId(), deployment,
                account, order, observations, risk, command.pilotWindowStart(), command.pilotWindowEnd());
    }

    private static ExactPilotBinding.AuthoritativeFacts withDeployment(
            ExactPilotBinding.AuthoritativeFacts value,
            ExactPilotBinding.DeploymentIdentity deployment
    ) {
        return new ExactPilotBinding.AuthoritativeFacts(
                value.sessionId(), value.pilotScopeId(), value.observationSetId(), deployment,
                value.account(), value.order(), value.observations(), value.riskPolicy(),
                value.pilotWindowStart(), value.pilotWindowEnd());
    }

    private static ExactPilotBinding.AuthoritativeFacts withAccount(
            ExactPilotBinding.AuthoritativeFacts value,
            ExactPilotBinding.AccountIdentity account
    ) {
        return new ExactPilotBinding.AuthoritativeFacts(
                value.sessionId(), value.pilotScopeId(), value.observationSetId(), value.deployment(),
                account, value.order(), value.observations(), value.riskPolicy(),
                value.pilotWindowStart(), value.pilotWindowEnd());
    }

    private static ExactPilotBinding.AuthoritativeFacts withOrder(
            ExactPilotBinding.AuthoritativeFacts value,
            ExactPilotBinding.OrderEnvelope order
    ) {
        return new ExactPilotBinding.AuthoritativeFacts(
                value.sessionId(), value.pilotScopeId(), value.observationSetId(), value.deployment(),
                value.account(), order, value.observations(), value.riskPolicy(),
                value.pilotWindowStart(), value.pilotWindowEnd());
    }

    private static ExactPilotBinding.AuthoritativeFacts withObservations(
            ExactPilotBinding.AuthoritativeFacts value,
            ExactPilotBinding.ObservationIdentities observations
    ) {
        return new ExactPilotBinding.AuthoritativeFacts(
                value.sessionId(), value.pilotScopeId(), value.observationSetId(), value.deployment(),
                value.account(), value.order(), observations, value.riskPolicy(),
                value.pilotWindowStart(), value.pilotWindowEnd());
    }

    private static ExactPilotBinding.AuthoritativeFacts withRisk(
            ExactPilotBinding.AuthoritativeFacts value,
            ExactPilotBinding.RiskPolicyIdentity risk
    ) {
        return new ExactPilotBinding.AuthoritativeFacts(
                value.sessionId(), value.pilotScopeId(), value.observationSetId(), value.deployment(),
                value.account(), value.order(), value.observations(), risk,
                value.pilotWindowStart(), value.pilotWindowEnd());
    }

    private static ExactPilotBinding.DeploymentIdentity deployment(
            String release,
            String manifest,
            String server
    ) {
        return new ExactPilotBinding.DeploymentIdentity(
                release, release, manifest, server, ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE);
    }

    private static ExactPilotBinding.AccountIdentity account(long owner, long account, long credential) {
        return new ExactPilotBinding.AccountIdentity(
                ExactPilotBinding.AccountIdentity.EXCHANGE, ExactPilotBinding.AccountIdentity.ENVIRONMENT,
                owner, account, credential);
    }

    private static ExactPilotBinding.OrderEnvelope order(
            long instrumentId,
            String exchangeInstrumentId,
            ExactPilotBinding.Side side,
            String price,
            String quantity
    ) {
        BigDecimal exactPrice = decimal(price);
        BigDecimal exactQuantity = decimal(quantity);
        return new ExactPilotBinding.OrderEnvelope(
                instrumentId, exchangeInstrumentId, side, ExactPilotBinding.OrderType.LIMIT,
                exactPrice, exactQuantity, exactPrice.multiply(exactQuantity));
    }

    private static ExactPilotBinding.ObservationIdentities observations() {
        return new ExactPilotBinding.ObservationIdentities(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static ExactPilotBinding.RiskPolicyIdentity risk(UUID id, int version, String digest) {
        return new ExactPilotBinding.RiskPolicyIdentity(
                id, version, digest, ExactPilotBinding.RiskPolicyIdentity.REQUIRED_KILL_SWITCH_STATE);
    }

    private static ExactPilotBinding.Correlation correlation(String suffix) {
        return new ExactPilotBinding.Correlation(
                "request-" + suffix, "trace-" + suffix, "idempotency-" + suffix);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }

    private static LiveSession session(
            UUID sessionId,
            ExactPilotBinding.AuthoritativeFacts facts
    ) {
        RiskLimitSet risk = riskLimit(facts.riskPolicy());
        return LiveSession.create(
                sessionId, facts.account().ownerId(), facts.account().exchangeAccountId(), "release-record",
                DIGEST_A, 1, risk.id(), risk.canonicalDigest(), facts.account().credentialReferenceId(),
                List.of(facts.order().exchangeInstrumentId()), decimal("25"),
                facts.pilotWindowStart(), facts.pilotWindowEnd(), facts.account().ownerId(), NOW.minusSeconds(120));
    }

    private static RiskLimitSet riskLimit(ExactPilotBinding.RiskPolicyIdentity risk) {
        return new RiskLimitSet(
                risk.riskLimitSetId(), risk.riskPolicyVersion(), decimal("25"), decimal("20"),
                decimal("25"), decimal("5"), decimal("10"), 1, 2, List.of("BTC-USDT"),
                900, decimal("10"), decimal("10"), 1_000, 9_000, OWNER_ID, NOW.minusSeconds(600));
    }

    private static ExactPilotBinding copyWithDigest(ExactPilotBinding value, String digest) {
        return new ExactPilotBinding(
                value.id(), value.sessionId(), value.pilotScopeId(), value.observationSetId(),
                value.deployment(), value.account(), value.order(), value.observations(), value.riskPolicy(),
                value.pilotWindowStart(), value.pilotWindowEnd(), value.correlation(), value.bindingCreatedAt(),
                value.bindingExpiresAt(), digest);
    }

    private final class MutableAuthority implements ExactPilotBindingAuthority {
        private Supplier<ExactPilotBinding.AuthoritativeFacts> creation;
        private Supplier<ExactPilotBinding.AuthoritativeFacts> current;
        private int credentialMaterialReads;
        private int providerCalls;
        private int orders;
        private int ledgerDelta;

        private MutableAuthority(ExactPilotBinding.AuthoritativeFacts initial) {
            creation = () -> initial;
            current = () -> initial;
        }

        @Override
        public ExactPilotBinding.AuthoritativeFacts resolveForCreation(
                AuthenticatedLiveControlActor ignoredActor,
                ExactPilotBindingCommand ignoredCommand,
                Instant ignoredDecisionAt
        ) {
            return creation.get();
        }

        @Override
        public ExactPilotBinding.AuthoritativeFacts resolveCurrent(
                AuthenticatedLiveControlActor ignoredActor,
                ExactPilotBinding ignoredBinding,
                Instant ignoredDecisionAt
        ) {
            return current.get();
        }

        private ExactPilotBinding.AuthoritativeFacts rejectingFacts() {
            throw new LiveControlException("AUTHORITY_REJECTED", "authoritative fact is not accepted");
        }
    }

    private static final class FakeRepository implements ExactPilotBindingRepository {
        private final LiveSession session;
        private Instant now = NOW;
        private ExactPilotBinding binding;
        private boolean consumed;

        private FakeRepository(LiveSession session) {
            this.session = session;
        }

        @Override
        public LiveSession lockSession(UUID sessionId) {
            if (!session.id().equals(sessionId)) {
                throw new LiveControlException("LIVE_SESSION_NOT_FOUND", "session not found");
            }
            return session;
        }

        @Override
        public Instant currentTransactionTime() {
            return now;
        }

        @Override
        public ExactPilotBinding createOrGet(ExactPilotBinding value, LiveSession ignoredSession) {
            if (binding == null) {
                binding = value;
                return value;
            }
            if (!binding.equals(value)) {
                throw new LiveControlException("EXACT_PILOT_BINDING_IDEMPOTENCY_CONFLICT", "binding conflict");
            }
            return binding;
        }

        @Override
        public Optional<ExactPilotBinding> find(UUID sessionId, UUID bindingId) {
            return binding != null && binding.sessionId().equals(sessionId) && binding.id().equals(bindingId)
                    ? Optional.of(binding) : Optional.empty();
        }

        @Override
        public boolean isConsumed(UUID sessionId, UUID bindingId) {
            return consumed && binding.sessionId().equals(sessionId) && binding.id().equals(bindingId);
        }

        @Override
        public com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingConsumption consume(
                ExactPilotBinding value,
                LiveSession ignoredSession,
                ExactPilotBinding.Correlation ignoredCorrelation,
                Instant consumedAt
        ) {
            if (consumed) {
                throw new LiveControlException("EXACT_PILOT_BINDING_ALREADY_CONSUMED", "already consumed");
            }
            consumed = true;
            return new com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingConsumption(
                    value.id(), value.bindingDigest(), consumedAt, false, false);
        }
    }

    private static final class NoopTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // No resource is used by this deterministic unit test.
        }

        @Override
        public void rollback(TransactionStatus status) {
            // No resource is used by this deterministic unit test.
        }
    }
}
