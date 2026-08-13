package com.guidinglight.nexusquant.livecontrol.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.execution.application.ExecutionIntentService;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionAttemptLifecycle;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeMutationPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeQueryResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeResult;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentAction;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentStateMachine;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptDraft;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExecutionIntentRuntimeTest {

    private static final UUID INTENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    @Test
    void shouldFreezeStateMatrixAndRejectBlindRetryTransitions() {
        ExecutionIntentStateMachine machine = new ExecutionIntentStateMachine();
        assertEquals(ExecutionIntentState.CLAIMED,
                machine.transition(ExecutionIntentState.CREATED, ExecutionIntentState.CLAIMED));
        assertEquals(ExecutionIntentState.SEND_STARTED,
                machine.transition(ExecutionIntentState.CLAIMED, ExecutionIntentState.SEND_STARTED));
        assertEquals(ExecutionIntentState.UNKNOWN,
                machine.transition(ExecutionIntentState.SEND_STARTED, ExecutionIntentState.UNKNOWN));
        assertEquals(ExecutionIntentState.RECONCILED,
                machine.transition(ExecutionIntentState.UNKNOWN, ExecutionIntentState.RECONCILED));
        assertThrows(LiveControlException.class,
                () -> machine.transition(ExecutionIntentState.SEND_STARTED, ExecutionIntentState.CREATED));
        assertThrows(LiveControlException.class,
                () -> machine.transition(ExecutionIntentState.UNKNOWN, ExecutionIntentState.CLAIMED));
        assertThrows(LiveControlException.class,
                () -> machine.transition(ExecutionIntentState.FAILED, ExecutionIntentState.CLAIMED));
        assertThrows(LiveControlException.class,
                () -> machine.transition(ExecutionIntentState.RECONCILED, ExecutionIntentState.SEND_STARTED));
    }

    @Test
    void shouldProduceStableGoldenPayloadAndClientOrderIdIndependentOfLocale() {
        ExecutionIntentDraft first = place(INTENT_ID);
        ExecutionIntentDraft same = place(INTENT_ID);
        ExecutionIntentDraft other = place(UUID.fromString("10000000-0000-0000-0000-000000000002"));

        assertEquals("nq1-6d3a6706f72a51b2cd08d0672372d3720cf2c30a", first.clientOrderId());
        assertEquals("d37fc1573db221c8212350c6f3e3c98b5a69423043f631ab7fc5351509081b78",
                first.payloadHash());
        assertEquals(first, same);
        assertNotEquals(first.clientOrderId(), other.clientOrderId());
        assertEquals(44, first.clientOrderId().length());
    }

    @Test
    void shouldEnforceIdempotencyAndCancelFieldMatrix() {
        InMemoryRepository repository = new InMemoryRepository();
        DeterministicFakeExchange exchange = new DeterministicFakeExchange();
        ExecutionIntentService service = new ExecutionIntentService(repository, exchange);

        ExecutionIntent first = service.createOrGetPlace(INTENT_ID, SESSION_ID, "btc-usdt", "buy",
                new BigDecimal("1"), new BigDecimal("10"), "order-1");
        ExecutionIntent same = service.createOrGetPlace(INTENT_ID, SESSION_ID, "BTC-USDT", "BUY",
                new BigDecimal("1.00000000"), new BigDecimal("10.00000000"), "order-1");
        assertSame(first, same);
        assertThrows(LiveControlException.class,
                () -> service.createOrGetPlace(INTENT_ID, SESSION_ID, "BTC-USDT", "BUY",
                        new BigDecimal("2"), new BigDecimal("10"), "order-1"));

        ExecutionIntentDraft cancel = ExecutionIntentCanonicalEncoder.cancel(
                UUID.randomUUID(), SESSION_ID, "btc-usdt", "order-1", first.clientOrderId());
        assertEquals(ExecutionIntentAction.CANCEL, cancel.action());
        assertEquals(first.clientOrderId(), cancel.clientOrderId());
        assertEquals(null, cancel.side());
        assertThrows(IllegalArgumentException.class, () -> new ExecutionIntentDraft(
                UUID.randomUUID(), SESSION_ID, ExecutionIntentAction.CANCEL, "BTC-USDT", "BUY",
                null, null, null, "order-1", first.clientOrderId(), "a".repeat(64)));
    }

    @Test
    void shouldNeverMutateTwiceAcrossTimeoutCrashAndRepeatedRecovery() {
        InMemoryRepository repository = new InMemoryRepository();
        DeterministicFakeExchange exchange = new DeterministicFakeExchange();
        exchange.mutationResult = new FakeExchangeResult(
                FakeExchangeResult.Outcome.TIMEOUT, "req-1", null, "TIMEOUT", "FAKE_TIMEOUT");
        exchange.queryResult = new FakeExchangeQueryResult(
                FakeExchangeQueryResult.Status.PARTIAL_FILL_SIMULATION,
                "query-1", "exchange-1", null, null);
        ExecutionIntentService service = new ExecutionIntentService(repository, exchange);
        service.createOrGetPlace(INTENT_ID, SESSION_ID, "BTC-USDT", "BUY",
                new BigDecimal("1"), new BigDecimal("10"), "order-1");

        ExecutionIntent unknown = service.claimAndExecute(
                INTENT_ID, "worker-1", UUID.randomUUID(), Duration.ofMinutes(1), NOW);
        assertEquals(ExecutionIntentState.UNKNOWN, unknown.state());
        assertEquals(1, exchange.mutationCalls);

        ExecutionIntent duplicateWorker = service.claimAndExecute(
                INTENT_ID, "worker-2", UUID.randomUUID(), Duration.ofMinutes(1), NOW.plusSeconds(1));
        assertEquals(ExecutionIntentState.UNKNOWN, duplicateWorker.state());
        assertEquals(1, exchange.mutationCalls);

        ExecutionIntent reconciled = service.reconcileUnknown(INTENT_ID, NOW.plusSeconds(2));
        assertEquals(ExecutionIntentState.RECONCILED, reconciled.state());
        assertEquals(1, exchange.mutationCalls);
        assertEquals(1, exchange.queryCalls);
        assertEquals("PARTIAL_FILL_SIMULATION", repository.receipts.getLast().errorCode());

        assertEquals(ExecutionIntentState.RECONCILED,
                service.reconcileUnknown(INTENT_ID, NOW.plusSeconds(3)).state());
        assertEquals(1, exchange.mutationCalls);
        assertEquals(1, exchange.queryCalls);
    }

    @Test
    void shouldRecoverCrashAfterSendByQueryWithoutMutation() {
        InMemoryRepository repository = new InMemoryRepository();
        DeterministicFakeExchange exchange = new DeterministicFakeExchange();
        ExecutionIntentDraft draft = place(INTENT_ID);
        repository.createOrGet(draft);
        UUID token = UUID.randomUUID();
        ExecutionIntent claimed = repository.claim(INTENT_ID, "worker-1", token, Duration.ofMinutes(1)).orElseThrow();
        repository.markSendStarted(INTENT_ID, claimed.version(), token).orElseThrow();
        exchange.queryResult = new FakeExchangeQueryResult(
                FakeExchangeQueryResult.Status.CANCEL_RACE_SIMULATION,
                "query-2", "exchange-2", null, null);

        ExecutionIntent recovered = new ExecutionIntentService(repository, exchange)
                .reconcileUnknown(INTENT_ID, NOW.plusSeconds(1));
        assertEquals(ExecutionIntentState.RECONCILED, recovered.state());
        assertEquals(0, exchange.mutationCalls);
        assertEquals(1, exchange.queryCalls);
        assertEquals("CANCEL_RACE_SIMULATION", repository.receipts.getLast().errorCode());
    }

    @Test
    void shouldRunLifecycleGuardsAroundDurableSendAndLeaveQueryOnlyRecoveryState() {
        InMemoryRepository repository = new InMemoryRepository();
        DeterministicFakeExchange exchange = new DeterministicFakeExchange();
        repository.createOrGet(place(INTENT_ID));
        List<String> calls = new ArrayList<>();
        ExecutionAttemptLifecycle lifecycle = new ExecutionAttemptLifecycle() {
            @Override
            public void beforeClaim() {
                calls.add("BEFORE_CLAIM");
            }

            @Override
            public void afterClaim(ExecutionIntent intent) {
                calls.add("AFTER_CLAIM:" + intent.state());
            }

            @Override
            public void afterSendStarted(ExecutionIntent intent) {
                calls.add("AFTER_SEND_STARTED:" + intent.state());
            }

            @Override
            public void beforeFakeMutation(ExecutionIntent intent) {
                calls.add("BEFORE_MUTATION");
                throw new LiveControlException("CONTROLLED_KILL_CHANGE", "send is denied");
            }
        };

        LiveControlException denied = assertThrows(LiveControlException.class,
                () -> new ExecutionIntentService(repository, exchange).claimAndExecute(
                        INTENT_ID, "worker", UUID.randomUUID(), Duration.ofMinutes(1), NOW, lifecycle));

        assertEquals("CONTROLLED_KILL_CHANGE", denied.code());
        assertEquals(List.of("BEFORE_CLAIM", "AFTER_CLAIM:CLAIMED",
                "AFTER_SEND_STARTED:SEND_STARTED", "BEFORE_MUTATION"), calls);
        assertEquals(ExecutionIntentState.SEND_STARTED, repository.find(INTENT_ID).orElseThrow().state());
        assertEquals(0, exchange.mutationCalls);
    }

    @Test
    void shouldMapEveryDeterministicMutationOutcomeWithoutBlindRetry() {
        List<MutationCase> cases = List.of(
                new MutationCase(FakeExchangeResult.Outcome.ACKNOWLEDGED,
                        ExecutionIntentState.SEND_SUCCEEDED, "ACKNOWLEDGED"),
                new MutationCase(FakeExchangeResult.Outcome.REJECTED,
                        ExecutionIntentState.FAILED, "REJECTED"),
                new MutationCase(FakeExchangeResult.Outcome.TIMEOUT,
                        ExecutionIntentState.UNKNOWN, "TIMEOUT"),
                new MutationCase(FakeExchangeResult.Outcome.TRANSPORT_ERROR,
                        ExecutionIntentState.UNKNOWN, "TRANSPORT_ERROR"),
                new MutationCase(FakeExchangeResult.Outcome.UNKNOWN,
                        ExecutionIntentState.UNKNOWN, "UNKNOWN")
        );

        for (MutationCase testCase : cases) {
            InMemoryRepository repository = new InMemoryRepository();
            DeterministicFakeExchange exchange = new DeterministicFakeExchange();
            exchange.mutationResult = new FakeExchangeResult(
                    testCase.outcome(), "req", "exchange", "FAKE", testCase.outcome().name());
            ExecutionIntentService service = new ExecutionIntentService(repository, exchange);
            UUID intentId = UUID.randomUUID();
            service.createOrGetPlace(intentId, SESSION_ID, "BTC-USDT", "BUY",
                    BigDecimal.ONE, BigDecimal.TEN, "order-" + intentId);

            assertEquals(testCase.target(), service.claimAndExecute(
                    intentId, "worker", UUID.randomUUID(), Duration.ofMinutes(1), NOW).state());
            assertEquals(1, exchange.mutationCalls);
            assertEquals(testCase.receiptOutcome(), repository.receipts.getFirst().outcome().name());
            assertEquals(testCase.target(), service.claimAndExecute(
                    intentId, "duplicate", UUID.randomUUID(), Duration.ofMinutes(1), NOW).state());
            assertEquals(1, exchange.mutationCalls);
        }
    }

    @Test
    void shouldMapEveryDeterministicQueryScenarioAndRetryOnlyQueries() {
        List<QueryCase> cases = List.of(
                new QueryCase(FakeExchangeQueryResult.Status.CONFIRMED,
                        "CONFIRMED", "QUERY_CONFIRMED", true),
                new QueryCase(FakeExchangeQueryResult.Status.NOT_FOUND,
                        "NOT_FOUND", "QUERY_NOT_FOUND", true),
                new QueryCase(FakeExchangeQueryResult.Status.PARTIAL_FILL_SIMULATION,
                        "PARTIAL_FILL_SIMULATION", "QUERY_CONFIRMED", true),
                new QueryCase(FakeExchangeQueryResult.Status.CANCEL_RACE_SIMULATION,
                        "CANCEL_RACE_SIMULATION", "QUERY_CONFIRMED", true),
                new QueryCase(FakeExchangeQueryResult.Status.UNKNOWN, null, null, false)
        );

        for (QueryCase testCase : cases) {
            InMemoryRepository repository = sendStartedRepository();
            DeterministicFakeExchange exchange = new DeterministicFakeExchange();
            exchange.queryResult = new FakeExchangeQueryResult(
                    testCase.status(), "query", "exchange", null, null);
            ExecutionIntentService service = new ExecutionIntentService(repository, exchange);

            ExecutionIntent result = service.reconcileUnknown(INTENT_ID, NOW);
            assertEquals(testCase.reconciled() ? ExecutionIntentState.RECONCILED : ExecutionIntentState.UNKNOWN,
                    result.state());
            assertEquals(0, exchange.mutationCalls);
            assertEquals(1, exchange.queryCalls);
            if (testCase.reconciled()) {
                assertEquals(testCase.code(), repository.receipts.getLast().errorCode());
                assertEquals(testCase.receiptOutcome(), repository.receipts.getLast().outcome().name());
            } else {
                assertTrue(repository.receipts.isEmpty());
                exchange.queryResult = new FakeExchangeQueryResult(
                        FakeExchangeQueryResult.Status.CONFIRMED, "query-2", "exchange", null, null);
                assertEquals(ExecutionIntentState.RECONCILED,
                        service.reconcileUnknown(INTENT_ID, NOW.plusSeconds(1)).state());
                assertEquals(0, exchange.mutationCalls);
                assertEquals(2, exchange.queryCalls);
            }
        }
    }

    @Test
    void shouldProveCrashMatrixKeepsMutationAtMostOnce() {
        DeterministicFakeExchange beforeClaimExchange = new DeterministicFakeExchange();
        InMemoryRepository beforeClaimRepository = new InMemoryRepository();
        beforeClaimRepository.createOrGet(place(INTENT_ID));
        ExecutionIntentService beforeClaimService = new ExecutionIntentService(
                beforeClaimRepository, beforeClaimExchange);
        assertEquals(ExecutionIntentState.CREATED,
                beforeClaimService.reconcileUnknown(INTENT_ID, NOW).state());
        assertEquals(0, beforeClaimExchange.mutationCalls);

        InMemoryRepository afterClaimRepository = new InMemoryRepository();
        afterClaimRepository.createOrGet(place(INTENT_ID));
        UUID staleToken = UUID.randomUUID();
        afterClaimRepository.claim(INTENT_ID, "crashed", staleToken, Duration.ofSeconds(1)).orElseThrow();
        afterClaimRepository.expireClaim();
        DeterministicFakeExchange afterClaimExchange = new DeterministicFakeExchange();
        ExecutionIntent afterClaim = new ExecutionIntentService(afterClaimRepository, afterClaimExchange)
                .claimAndExecute(INTENT_ID, "reclaimed", UUID.randomUUID(), Duration.ofMinutes(1), NOW);
        assertEquals(ExecutionIntentState.SEND_SUCCEEDED, afterClaim.state());
        assertEquals(1, afterClaimExchange.mutationCalls);

        InMemoryRepository afterSendRepository = sendStartedRepository();
        DeterministicFakeExchange afterSendExchange = new DeterministicFakeExchange();
        ExecutionIntentService afterSendService = new ExecutionIntentService(afterSendRepository, afterSendExchange);
        assertEquals(ExecutionIntentState.RECONCILED,
                afterSendService.reconcileUnknown(INTENT_ID, NOW).state());
        assertEquals(0, afterSendExchange.mutationCalls);

        InMemoryRepository afterMutationRepository = new InMemoryRepository();
        afterMutationRepository.createOrGet(place(INTENT_ID));
        afterMutationRepository.failNextAppend = true;
        DeterministicFakeExchange afterMutationExchange = new DeterministicFakeExchange();
        ExecutionIntentService afterMutationService = new ExecutionIntentService(
                afterMutationRepository, afterMutationExchange);
        assertThrows(IllegalStateException.class, () -> afterMutationService.claimAndExecute(
                INTENT_ID, "worker", UUID.randomUUID(), Duration.ofMinutes(1), NOW));
        assertEquals(ExecutionIntentState.SEND_STARTED, afterMutationRepository.value.state());
        assertEquals(1, afterMutationExchange.mutationCalls);
        assertTrue(afterMutationRepository.receipts.isEmpty());
        assertEquals(ExecutionIntentState.RECONCILED,
                afterMutationService.reconcileUnknown(INTENT_ID, NOW.plusSeconds(1)).state());
        assertEquals(1, afterMutationExchange.mutationCalls);
        assertEquals(1, afterMutationExchange.queryCalls);
    }

    @Test
    void shouldKeepSendStartedAndNeverRetryWhenMutationThrowsOrThreadIsInterrupted() {
        for (boolean interrupted : List.of(false, true)) {
            InMemoryRepository repository = new InMemoryRepository();
            repository.createOrGet(place(UUID.randomUUID()));
            ThrowingFakeExchange exchange = new ThrowingFakeExchange(interrupted);
            ExecutionIntentService service = new ExecutionIntentService(repository, exchange);

            assertThrows(IllegalStateException.class, () -> service.claimAndExecute(
                    repository.value.intentId(), "worker", UUID.randomUUID(), Duration.ofMinutes(1), NOW));
            assertEquals(ExecutionIntentState.SEND_STARTED, repository.value.state());
            assertEquals(1, exchange.mutationCalls);
            assertEquals(ExecutionIntentState.SEND_STARTED, service.claimAndExecute(
                    repository.value.intentId(), "duplicate", UUID.randomUUID(), Duration.ofMinutes(1), NOW).state());
            assertEquals(1, exchange.mutationCalls);
            assertEquals(ExecutionIntentState.RECONCILED,
                    service.reconcileUnknown(repository.value.intentId(), NOW.plusSeconds(1)).state());
            assertEquals(1, exchange.mutationCalls);
            assertEquals(1, exchange.queryCalls);
            if (interrupted) {
                assertTrue(Thread.interrupted());
            } else {
                assertFalse(Thread.currentThread().isInterrupted());
            }
        }
    }

    @Test
    void shouldRejectCanonicalDelimiterAndReceiptNullAmbiguity() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionIntentCanonicalEncoder.place(
                UUID.randomUUID(), SESSION_ID, "BTC-USDT\nside=SELL", "BUY",
                BigDecimal.ONE, BigDecimal.TEN, "order-1"));
        var nullValue = com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptCanonicalEncoder.draft(
                UUID.randomUUID(), INTENT_ID,
                com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptOutcome.UNKNOWN,
                null, null, null, null, NOW);
        var literalNull = com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptCanonicalEncoder.draft(
                nullValue.receiptId(), INTENT_ID,
                com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptOutcome.UNKNOWN,
                "null", null, null, null, NOW);
        assertNotEquals(nullValue.payloadDigest(), literalNull.payloadDigest());
        assertThrows(IllegalArgumentException.class,
                () -> com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptCanonicalEncoder.draft(
                        UUID.randomUUID(), INTENT_ID,
                        com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptOutcome.UNKNOWN,
                        "request\nsecret", null, null, null, NOW));
    }

    private static InMemoryRepository sendStartedRepository() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.createOrGet(place(INTENT_ID));
        UUID token = UUID.randomUUID();
        ExecutionIntent claimed = repository.claim(
                INTENT_ID, "worker", token, Duration.ofMinutes(1)).orElseThrow();
        repository.markSendStarted(INTENT_ID, claimed.version(), token).orElseThrow();
        return repository;
    }

    private static ExecutionIntentDraft place(UUID intentId) {
        return ExecutionIntentCanonicalEncoder.place(intentId, SESSION_ID, "btc-usdt", "buy",
                new BigDecimal("1"), new BigDecimal("10"), "order-1");
    }

    private static final class DeterministicFakeExchange implements FakeExchangeMutationPort {
        private FakeExchangeResult mutationResult = new FakeExchangeResult(
                FakeExchangeResult.Outcome.ACKNOWLEDGED, "req", "exchange", null, null);
        private FakeExchangeQueryResult queryResult = new FakeExchangeQueryResult(
                FakeExchangeQueryResult.Status.CONFIRMED, "query", "exchange", null, null);
        private int mutationCalls;
        private int queryCalls;

        @Override
        public FakeExchangeResult place(ExecutionIntent intent) {
            mutationCalls++;
            return mutationResult;
        }

        @Override
        public FakeExchangeResult cancel(ExecutionIntent intent) {
            mutationCalls++;
            return mutationResult;
        }

        @Override
        public FakeExchangeQueryResult queryByClientOrderId(String clientOrderId) {
            queryCalls++;
            return queryResult;
        }
    }

    private static final class ThrowingFakeExchange implements FakeExchangeMutationPort {
        private final boolean interrupt;
        private int mutationCalls;
        private int queryCalls;

        private ThrowingFakeExchange(boolean interrupt) {
            this.interrupt = interrupt;
        }

        @Override
        public FakeExchangeResult place(ExecutionIntent intent) {
            mutationCalls++;
            if (interrupt) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("simulated mutation exception");
        }

        @Override
        public FakeExchangeResult cancel(ExecutionIntent intent) {
            return place(intent);
        }

        @Override
        public FakeExchangeQueryResult queryByClientOrderId(String clientOrderId) {
            queryCalls++;
            return new FakeExchangeQueryResult(
                    FakeExchangeQueryResult.Status.CONFIRMED, "query", "exchange", null, null);
        }
    }

    private static final class InMemoryRepository implements ExecutionIntentRepository {
        private final ExecutionIntentStateMachine machine = new ExecutionIntentStateMachine();
        private final List<ExecutionReceiptDraft> receipts = new ArrayList<>();
        private ExecutionIntent value;
        private boolean failNextAppend;

        @Override
        public ExecutionIntent createOrGet(ExecutionIntentDraft draft) {
            if (value != null) {
                if (!value.samePayload(draft.payloadHash())) {
                    throw new LiveControlException("IDEMPOTENCY_CONFLICT", "different payload");
                }
                return value;
            }
            value = new ExecutionIntent(draft.intentId(), draft.sessionId(), 1, draft.action(), draft.symbol(),
                    draft.side(), draft.orderType(), draft.quantity(), draft.limitPrice(),
                    ExecutionIntentDraft.PAYLOAD_SCHEMA, draft.payloadHash(), draft.clientOrderId(),
                    draft.localOrderId(), ExecutionIntentState.CREATED, 1, null, null,
                    null, null, null, NOW);
            return value;
        }

        @Override
        public Optional<ExecutionIntent> find(UUID intentId) {
            return value != null && value.intentId().equals(intentId) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public Optional<ExecutionIntent> claim(UUID intentId, String workerId, UUID claimToken, Duration lease) {
            boolean reclaimable = value != null && value.state() == ExecutionIntentState.CLAIMED
                    && value.sendStartedAt() == null && !value.leaseExpiresAt().isAfter(NOW);
            if (value == null || (value.state() != ExecutionIntentState.CREATED && !reclaimable)) {
                return Optional.empty();
            }
            value = copy(ExecutionIntentState.CLAIMED, value.version() + 1, workerId, claimToken,
                    NOW, NOW.plus(lease), null);
            return Optional.of(value);
        }

        @Override
        public Optional<ExecutionIntent> markSendStarted(UUID intentId, long expectedVersion, UUID claimToken) {
            if (value.version() != expectedVersion || value.state() != ExecutionIntentState.CLAIMED
                    || !value.claimToken().equals(claimToken)) {
                return Optional.empty();
            }
            value = copy(ExecutionIntentState.SEND_STARTED, value.version() + 1, value.claimedBy(),
                    claimToken, value.claimedAt(), value.leaseExpiresAt(), NOW);
            return Optional.of(value);
        }

        @Override
        public Optional<ExecutionIntent> markAmbiguousForRecovery(
                UUID intentId,
                long expectedVersion,
                UUID claimToken
        ) {
            if (value.version() != expectedVersion || value.state() != ExecutionIntentState.SEND_STARTED) {
                return Optional.empty();
            }
            value = copy(ExecutionIntentState.UNKNOWN, value.version() + 1, value.claimedBy(),
                    claimToken, value.claimedAt(), value.leaseExpiresAt(), value.sendStartedAt());
            return Optional.of(value);
        }

        @Override
        public ExecutionIntent appendReceiptAndTransition(
                UUID intentId,
                long expectedVersion,
                UUID claimToken,
                ExecutionReceiptDraft receipt,
                ExecutionIntentState target
        ) {
            if (failNextAppend) {
                failNextAppend = false;
                throw new IllegalStateException("simulated receipt write failure");
            }
            if (value.version() != expectedVersion || !value.claimToken().equals(claimToken)) {
                throw new LiveControlException("EXECUTION_INTENT_CAS_CONFLICT", "conflict");
            }
            machine.transition(value.state(), target);
            receipts.add(receipt);
            value = copy(target, value.version() + 1, value.claimedBy(), claimToken,
                    value.claimedAt(), value.leaseExpiresAt(), value.sendStartedAt());
            return value;
        }

        private void expireClaim() {
            value = copy(value.state(), value.version(), value.claimedBy(), value.claimToken(),
                    value.claimedAt(), NOW.minusMillis(1), value.sendStartedAt());
        }

        private ExecutionIntent copy(
                ExecutionIntentState state,
                long version,
                String worker,
                UUID token,
                Instant claimedAt,
                Instant leaseExpiresAt,
                Instant sendStartedAt
        ) {
            return new ExecutionIntent(value.intentId(), value.sessionId(), value.sequence(), value.action(),
                    value.symbol(), value.side(), value.orderType(), value.quantity(), value.limitPrice(),
                    value.payloadHashSchemaVersion(), value.payloadHash(), value.clientOrderId(),
                    value.localOrderId(), state, version, worker, token, claimedAt, leaseExpiresAt,
                    sendStartedAt, value.createdAt());
        }
    }

    private record MutationCase(
            FakeExchangeResult.Outcome outcome,
            ExecutionIntentState target,
            String receiptOutcome
    ) {
    }

    private record QueryCase(
            FakeExchangeQueryResult.Status status,
            String code,
            String receiptOutcome,
            boolean reconciled
    ) {
    }
}
