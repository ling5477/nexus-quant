package com.guidinglight.nexusquant.livecontrol.execution.infra;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptOutcome;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.port.TradingCancelGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayFailure;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayResultCategory;
import com.guidinglight.nexusquant.trading.application.port.TradingOrderStatusSnapshot;
import com.guidinglight.nexusquant.trading.application.port.TradingPlaceGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * One-shot GateY pilot gateway。PLACE/CANCEL前均先持久化ExecutionIntent与lease动作绑定；
 * provider未知结果只query，不会第二次调用PLACE。
 */
public final class MinimalPilotTradingVenueGateway implements TradingVenueGateway {

    public static final String SOURCE = "GATEY_MINIMAL_LIVE_PILOT";
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(1);

    private final ExecutionIntentRepository intents;
    private final ExactPilotBindingRepository bindings;
    private final PilotExecutionLeaseRepository leases;
    private final PilotExecutionLeaseControlPlane leaseControl;
    private final SpotExecutionProviderPort provider;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MinimalPilotTradingVenueGateway(
            ExecutionIntentRepository intents,
            ExactPilotBindingRepository bindings,
            PilotExecutionLeaseRepository leases,
            PilotExecutionLeaseControlPlane leaseControl,
            SpotExecutionProviderPort provider,
            JdbcTemplate jdbc,
            Clock clock
    ) {
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.leaseControl = Objects.requireNonNull(leaseControl, "leaseControl must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public TradingPlaceGatewayResult placeOrder(OrderRecord order, PlaceOrderRequest request) {
        PilotInvocation invocation = requirePlaceInvocation(request);
        PilotExecutionLease lease = lease(invocation.leaseId());
        ExactPilotBinding binding = binding(lease);
        if (!orderMatches(order, request, binding)) throw rejected("PILOT_ORDER_SCOPE_MISMATCH");
        ExecutionIntent intent = intents.createOrGet(ExecutionIntentCanonicalEncoder.place(
                invocation.intentId(), binding.sessionId(), order.symbol(), order.side(),
                order.qty(), order.price(), order.orderId()));
        var correlation = binding.correlation();
        leaseControl.bindPlace(
                new AuthenticatedLiveControlActor(binding.account().ownerId()), lease.id(), intent.intentId(),
                binding, correlation);
        ExecutionIntent sendStarted = claimAndMarkSend(intent);
        SpotProviderRequests.RequestContext context = context(binding, correlation);
        SpotProviderResults.MutationResult result = provider.placeLimit(
                SpotProviderRequests.PlaceLimit.fromIntent(sendStarted, context));
        ExecutionIntent completed = appendPlaceReceipt(sendStarted, result);
        SpotProviderResults.OrderObservation observation = result.observation();
        if (completed.state() == ExecutionIntentState.UNKNOWN) {
            observation = provider.queryOrderByClientOrderId(new SpotProviderRequests.OrderQuery(
                    com.guidinglight.nexusquant.livecontrol.execution.application.provider.ProviderClientOrderId
                            .fromIntent(completed),
                    SpotProviderRequests.Venue.OKX_SPOT, binding.order().exchangeInstrumentId(), context));
            completed = appendQueryReceipt(completed, observation);
        }
        return placeResult(result, observation, completed);
    }

    @Override
    public TradingCancelGatewayResult cancelOrder(OrderRecord order, CancelOrderRequest request) {
        LeasePlace place = findLeasePlace(order.clientOrderId());
        PilotExecutionLease lease = lease(place.leaseId());
        ExactPilotBinding binding = binding(lease);
        SpotProviderRequests.RequestContext context = context(binding, binding.correlation());
        var clientId = com.guidinglight.nexusquant.livecontrol.execution.application.provider.ProviderClientOrderId
                .from(place.intentId(), order.clientOrderId());
        var query = new SpotProviderRequests.OrderQuery(
                clientId, SpotProviderRequests.Venue.OKX_SPOT, binding.order().exchangeInstrumentId(), context);
        SpotProviderResults.OrderObservation confirmed = provider.queryOrderByClientOrderId(query);
        UUID cancelIntentId = UUID.randomUUID();
        ExecutionIntent cancelIntent = intents.createOrGet(ExecutionIntentCanonicalEncoder.cancel(
                cancelIntentId, binding.sessionId(), binding.order().exchangeInstrumentId(),
                order.orderId(), order.clientOrderId()));
        leaseControl.bindCancel(lease.id(), cancelIntentId);
        ExecutionIntent sendStarted = claimAndMarkSend(cancelIntent);
        SpotProviderResults.CancelResult result = provider.cancel(new SpotProviderRequests.Cancel(
                query, confirmed, SpotProviderRequests.PartialCancelPolicy.ALLOW_CONFIRMED_REMAINDER));
        appendCancelReceipt(sendStarted, result);
        boolean accepted = result.disposition() == SpotProviderResults.CancelDisposition.MUTATION_ACCEPTED
                || result.disposition() == SpotProviderResults.CancelDisposition.NO_MUTATION_TERMINAL;
        return new TradingCancelGatewayResult(
                accepted,
                accepted ? TradingGatewayResultCategory.ACCEPTED : category(result.error()),
                accepted ? null : failure(result.error()),
                result.observation() == null ? clock.instant() : result.observation().observedAt(), "LIVE");
    }

    @Override
    public TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String traceId) {
        LeasePlace place = findLeasePlace(order.clientOrderId());
        ExactPilotBinding binding = binding(lease(place.leaseId()));
        var observation = provider.readOrderStatus(new SpotProviderRequests.OrderQuery(
                com.guidinglight.nexusquant.livecontrol.execution.application.provider.ProviderClientOrderId
                        .from(place.intentId(), order.clientOrderId()),
                SpotProviderRequests.Venue.OKX_SPOT, binding.order().exchangeInstrumentId(),
                context(binding, binding.correlation())));
        return new TradingOrderStatusSnapshot(
                observation.exchangeOrderId(), observation.state().name(), category(observation.error()),
                observation.error() == null ? null : failure(observation.error()), observation.observedAt(), "LIVE");
    }

    /** Query/fills typed reconciliation；不写Order/Trade/Ledger。 */
    public PilotReconciliation reconcile(OrderRecord order) {
        LeasePlace place = findLeasePlace(order.clientOrderId());
        ExactPilotBinding binding = binding(lease(place.leaseId()));
        requireQueryOnlyRecoveryState(place.intentId());
        SpotProviderRequests.RequestContext frozenContext = context(binding, binding.correlation());
        SpotProviderRequests.RequestContext context = refreshedReadOnlyContext(
                frozenContext, provider.readClock(frozenContext), clock.instant());
        var orderQuery = new SpotProviderRequests.OrderQuery(
                com.guidinglight.nexusquant.livecontrol.execution.application.provider.ProviderClientOrderId
                        .from(place.intentId(), order.clientOrderId()),
                SpotProviderRequests.Venue.OKX_SPOT, binding.order().exchangeInstrumentId(), context);
        SpotProviderResults.OrderObservation observation = provider.readOrderStatus(orderQuery);
        if (observation.state() == SpotProviderResults.OrderState.UNKNOWN || observation.error() != null) {
            throw rejected("PILOT_RECONCILIATION_ORDER_UNKNOWN");
        }
        reconcileIntentObservation(place.intentId(), observation);
        SpotProviderResults.FillPage fillPage;
        if (observation.state() == SpotProviderResults.OrderState.REJECTED
                || observation.state() == SpotProviderResults.OrderState.NOT_FOUND) {
            fillPage = new SpotProviderResults.FillPage(
                    orderQuery.clientOrderId().value(), List.of(), true, null, observation.observedAt());
        } else {
            SpotProviderRequests.RequestContext frozenFillContext = context(binding, binding.correlation());
            SpotProviderRequests.RequestContext fillContext = refreshedReadOnlyContext(
                    frozenFillContext, provider.readClock(frozenFillContext), clock.instant());
            fillPage = provider.readFills(new SpotProviderRequests.FillQuery(
                    new SpotProviderRequests.OrderQuery(
                            orderQuery.clientOrderId(), orderQuery.venue(), orderQuery.instrument(), fillContext),
                    binding.pilotWindowStart(), binding.pilotWindowEnd(), 100));
        }
        if (fillPage.error() != null || !fillPage.complete()) {
            throw rejected("PILOT_RECONCILIATION_FILLS_INCOMPLETE");
        }
        java.math.BigDecimal total = fillPage.fills().stream()
                .map(SpotProviderResults.FillReference::quantity)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (!order.symbol().equals(binding.order().exchangeInstrumentId())
                || !order.side().equals(binding.order().side().name())
                || order.price() == null || order.price().compareTo(binding.order().price()) != 0
                || order.qty().compareTo(binding.order().quantity()) != 0
                || total.compareTo(observation.executedQuantity()) != 0) {
            throw rejected("REAL_ORDER_RECONCILIATION_DIVERGENCE");
        }
        return new PilotReconciliation(binding, observation, fillPage);
    }

    private ExecutionIntent claimAndMarkSend(ExecutionIntent intent) {
        UUID token = UUID.randomUUID();
        ExecutionIntent claimed = intents.claim(intent.intentId(), "gatey-minimal-pilot", token, CLAIM_LEASE)
                .orElseThrow(() -> rejected("PILOT_INTENT_CLAIM_REJECTED"));
        return intents.markSendStarted(claimed.intentId(), claimed.version(), token)
                .orElseThrow(() -> rejected("PILOT_INTENT_SEND_REJECTED"));
    }

    private ExecutionIntent appendPlaceReceipt(
            ExecutionIntent intent,
            SpotProviderResults.MutationResult result
    ) {
        ExecutionIntentState target = switch (result.outcome()) {
            case ACCEPTED -> ExecutionIntentState.SEND_SUCCEEDED;
            case DEFINITIVELY_REJECTED -> ExecutionIntentState.FAILED;
            case UNKNOWN -> ExecutionIntentState.UNKNOWN;
        };
        ExecutionReceiptOutcome outcome = switch (result.outcome()) {
            case ACCEPTED -> ExecutionReceiptOutcome.ACKNOWLEDGED;
            case DEFINITIVELY_REJECTED -> ExecutionReceiptOutcome.REJECTED;
            case UNKNOWN -> ExecutionReceiptOutcome.UNKNOWN;
        };
        var receipt = ExecutionReceiptCanonicalEncoder.draft(
                UUID.randomUUID(), intent.intentId(), outcome, null,
                result.observation() == null ? null : result.observation().exchangeOrderId(),
                result.error() == null ? null : result.error().category().name(),
                result.error() == null ? null : result.error().certainty().name(),
                canonicalReceiptTime(clock.instant()));
        return intents.appendReceiptAndTransition(
                intent.intentId(), intent.version(), intent.claimToken(), receipt, target);
    }

    ExecutionIntent appendQueryReceipt(ExecutionIntent intent, SpotProviderResults.OrderObservation value) {
        if (value.state() == SpotProviderResults.OrderState.UNKNOWN) {
            return intent;
        }
        var receipt = ExecutionReceiptCanonicalEncoder.draft(
                UUID.randomUUID(), intent.intentId(),
                value.state() == SpotProviderResults.OrderState.NOT_FOUND
                        ? ExecutionReceiptOutcome.QUERY_NOT_FOUND : ExecutionReceiptOutcome.QUERY_CONFIRMED,
                null, value.exchangeOrderId(), value.error() == null ? "PILOT_QUERY" : value.error().category().name(),
                value.state().name(), canonicalReceiptTime(value.observedAt()));
        return intents.appendReceiptAndTransition(
                intent.intentId(), intent.version(), intent.claimToken(), receipt, ExecutionIntentState.RECONCILED);
    }

    ExecutionIntent reconcileIntentObservation(
            UUID intentId,
            SpotProviderResults.OrderObservation observation
    ) {
        ExecutionIntent intent = intents.find(intentId)
                .orElseThrow(() -> rejected("PILOT_INTENT_NOT_FOUND"));
        return switch (intent.state()) {
            case SEND_STARTED, UNKNOWN -> appendQueryReceipt(intent, observation);
            case SEND_SUCCEEDED, FAILED, CANCELLED, RECONCILED -> intent;
            case CREATED, CLAIMED -> throw rejected("PILOT_INTENT_RECOVERY_STATE_INVALID");
        };
    }

    private ExecutionIntent requireQueryOnlyRecoveryState(UUID intentId) {
        ExecutionIntent intent = intents.find(intentId)
                .orElseThrow(() -> rejected("PILOT_INTENT_NOT_FOUND"));
        if (intent.state() == ExecutionIntentState.CREATED || intent.state() == ExecutionIntentState.CLAIMED) {
            throw rejected("PILOT_INTENT_RECOVERY_STATE_INVALID");
        }
        return intent;
    }

    static SpotProviderRequests.RequestContext refreshedReadOnlyContext(
            SpotProviderRequests.RequestContext base,
            SpotProviderResults.ClockObservation observation,
            Instant requestTimestamp
    ) {
        Objects.requireNonNull(base, "base context must not be null");
        Objects.requireNonNull(observation, "clock observation must not be null");
        Objects.requireNonNull(requestTimestamp, "request timestamp must not be null");
        if (observation.error() != null) {
            throw rejected("PILOT_RECONCILIATION_CLOCK_UNAVAILABLE");
        }
        var refreshed = new SpotProviderRequests.RequestContext(
                base.sessionId(), base.referenceId(), base.traceId(), base.correlationId(),
                new SpotProviderRequests.ClockContract(
                        base.clock().timestampSource(), requestTimestamp, observation.localClockMidpoint(),
                        observation.observedSkew(), base.clock().maximumSkew(),
                        base.clock().maximumObservationAge()));
        if (!refreshed.clock().healthyAt(requestTimestamp)) {
            throw rejected("PILOT_RECONCILIATION_CLOCK_UNAVAILABLE");
        }
        return refreshed;
    }

    static Instant canonicalReceiptTime(Instant value) {
        return Objects.requireNonNull(value, "receipt time must not be null").truncatedTo(ChronoUnit.MICROS);
    }

    private void appendCancelReceipt(ExecutionIntent intent, SpotProviderResults.CancelResult result) {
        ExecutionIntentState target = switch (result.disposition()) {
            case MUTATION_ACCEPTED, NO_MUTATION_TERMINAL -> ExecutionIntentState.SEND_SUCCEEDED;
            case DEFINITIVELY_REJECTED -> ExecutionIntentState.FAILED;
            case UNKNOWN, QUERY_REQUIRED -> ExecutionIntentState.UNKNOWN;
        };
        ExecutionReceiptOutcome outcome = switch (target) {
            case SEND_SUCCEEDED -> ExecutionReceiptOutcome.ACKNOWLEDGED;
            case FAILED -> ExecutionReceiptOutcome.REJECTED;
            case UNKNOWN -> ExecutionReceiptOutcome.UNKNOWN;
            default -> throw new IllegalStateException("unexpected cancel target");
        };
        var receipt = ExecutionReceiptCanonicalEncoder.draft(
                UUID.randomUUID(), intent.intentId(), outcome, null,
                result.observation() == null ? null : result.observation().exchangeOrderId(),
                result.error() == null ? null : result.error().category().name(),
                result.disposition().name(), clock.instant());
        intents.appendReceiptAndTransition(intent.intentId(), intent.version(), intent.claimToken(), receipt, target);
    }

    private SpotProviderRequests.RequestContext context(
            ExactPilotBinding binding,
            ExactPilotBinding.Correlation correlation
    ) {
        ClockFact fact = jdbc.queryForObject("""
                SELECT observation.observed_at,observation.observed_skew_ms,
                       scope.maximum_tolerated_skew_ms,scope.clock_maximum_age_ms
                FROM pilot_prerequisite_observations observation
                JOIN pilot_scope_bindings scope ON scope.pilot_scope_id=observation.pilot_scope_id
                WHERE observation.observation_set_id=? AND observation.observation_type='CLOCK_SYNC'
                """, (row, ignored) -> new ClockFact(
                row.getTimestamp(1).toInstant(), row.getLong(2), row.getLong(3), row.getLong(4)),
                binding.observationSetId());
        Instant now = clock.instant();
        return new SpotProviderRequests.RequestContext(
                binding.sessionId(), String.valueOf(binding.account().credentialReferenceId()),
                correlation.traceId(), correlation.requestId(), new SpotProviderRequests.ClockContract(
                SpotProviderRequests.TimestampSource.TRUSTED_UTC_CLOCK, now, fact.observedAt(),
                Duration.ofMillis(fact.skewMs()), Duration.ofMillis(fact.maximumSkewMs()),
                Duration.ofMillis(fact.maximumAgeMs())));
    }

    private PilotExecutionLease lease(UUID leaseId) {
        return leases.find(leaseId).orElseThrow(() -> rejected("PILOT_LEASE_NOT_FOUND"));
    }

    private ExactPilotBinding binding(PilotExecutionLease lease) {
        return bindings.find(lease.liveSessionId(), lease.bindingId())
                .filter(value -> value.bindingDigest().equals(lease.bindingDigest()))
                .orElseThrow(() -> rejected("PILOT_BINDING_NOT_FOUND"));
    }

    private LeasePlace findLeasePlace(String clientOrderId) {
        List<LeasePlace> values = jdbc.query("""
                SELECT link.lease_id,intent.intent_id
                FROM pilot_execution_lease_intents link
                JOIN execution_intents intent ON intent.intent_id=link.intent_id
                WHERE link.action='PLACE' AND intent.client_order_id=?
                """, (row, ignored) -> new LeasePlace(
                row.getObject(1, UUID.class), row.getObject(2, UUID.class)), clientOrderId);
        if (values.size() != 1) throw rejected("PILOT_PLACE_IDENTITY_NOT_FOUND");
        return values.getFirst();
    }

    static PilotInvocation requirePlaceInvocation(PlaceOrderRequest request) {
        if (!SOURCE.equals(request.source()) || request.strategyRunId() != null
                || request.executionScopeId() == null) {
            throw rejected("PILOT_PROVIDER_SCOPE_REQUIRED");
        }
        String[] values = request.executionScopeId().split("\\|", -1);
        if (values.length != 2) throw rejected("PILOT_PROVIDER_SCOPE_REQUIRED");
        try {
            return new PilotInvocation(UUID.fromString(values[0]), UUID.fromString(values[1]));
        } catch (IllegalArgumentException failure) {
            throw rejected("PILOT_PROVIDER_SCOPE_REQUIRED");
        }
    }

    private static boolean orderMatches(
            OrderRecord order,
            PlaceOrderRequest request,
            ExactPilotBinding binding
    ) {
        return "OKX".equals(order.venue()) && "LIMIT".equals(order.type())
                && order.symbol().equals(binding.order().exchangeInstrumentId())
                && order.side().equals(binding.order().side().name())
                && order.qty().compareTo(binding.order().quantity()) == 0
                && order.price() != null && order.price().compareTo(binding.order().price()) == 0
                && "LIVE".equals(order.tradeEnv()) && "LIVE".equals(request.tradeEnv())
                && request.type() == com.guidinglight.nexusquant.contracts.model.OrderType.LIMIT;
    }

    private static TradingPlaceGatewayResult placeResult(
            SpotProviderResults.MutationResult mutation,
            SpotProviderResults.OrderObservation observation,
            ExecutionIntent completed
    ) {
        boolean accepted = observation != null && observation.state() != SpotProviderResults.OrderState.UNKNOWN
                && observation.state() != SpotProviderResults.OrderState.NOT_FOUND
                && observation.state() != SpotProviderResults.OrderState.REJECTED;
        return new TradingPlaceGatewayResult(
                accepted,
                observation == null ? null : observation.exchangeOrderId(),
                observation == null ? completed.state().name() : observation.state().name(),
                accepted ? TradingGatewayResultCategory.ACCEPTED : category(mutation.error()),
                accepted ? null : failure(mutation.error()),
                observation == null ? completed.createdAt() : observation.observedAt(), "LIVE");
    }

    private static TradingGatewayResultCategory category(SpotProviderError error) {
        if (error == null) return TradingGatewayResultCategory.ACCEPTED;
        return switch (error.category()) {
            case PERMISSION_DENIED, IP_RESTRICTION -> TradingGatewayResultCategory.AUTH_FAILURE;
            case RATE_LIMITED -> TradingGatewayResultCategory.THROTTLED;
            case TRANSPORT_TIMEOUT, UNKNOWN_RESULT -> TradingGatewayResultCategory.DEFERRED;
            default -> TradingGatewayResultCategory.FATAL_FAILURE;
        };
    }

    private static TradingGatewayFailure failure(SpotProviderError error) {
        if (error == null) return null;
        return new TradingGatewayFailure(
                error.category().name(), "typed OKX pilot operation failed", false);
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "minimal pilot provider operation rejected");
    }

    record PilotInvocation(UUID leaseId, UUID intentId) {
    }

    private record LeasePlace(UUID leaseId, UUID intentId) {
    }

    private record ClockFact(Instant observedAt, long skewMs, long maximumSkewMs, long maximumAgeMs) {
    }

    public record PilotReconciliation(
            ExactPilotBinding binding,
            SpotProviderResults.OrderObservation observation,
            SpotProviderResults.FillPage fills
    ) {
    }
}
