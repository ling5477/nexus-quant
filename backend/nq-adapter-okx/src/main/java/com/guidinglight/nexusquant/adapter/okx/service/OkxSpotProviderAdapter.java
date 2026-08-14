package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.CancelCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.CancelResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.FillCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.FillResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.OrderCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.OrderResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.PlaceCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.PlaceResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseMetadata;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseOutcome;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseReadLimit;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.TransportContext;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.TransportFailure;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Cancel;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.FillQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.OrderQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.PartialCancelPolicy;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.PlaceLimit;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.RequestContext;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.ResponseBounds;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.CancelDisposition;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.CancelResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillPage;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.MutationOutcome;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.MutationResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderObservation;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderState;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Fake/stub transport 下的 OKX Spot provider contract implementation。
 *
 * <p>该类没有 Spring annotation、default constructor、credential、signer、HTTP client 或 fallback。
 * 每次 mutation 最多调用 transport 一次；任何不确定结果都返回 query-first，不在本类重试。</p>
 */
public final class OkxSpotProviderAdapter implements SpotExecutionProviderPort {
    private final OkxSpotProviderTransport transport;
    private final OkxSpotEndpointGuard endpointGuard;
    private final ResponseBounds responseBounds;
    private final Clock clock;
    private final OkxVenueStateTranslator stateTranslator;

    public OkxSpotProviderAdapter(
            OkxSpotProviderTransport transport,
            OkxSpotEndpointGuard endpointGuard,
            ResponseBounds responseBounds,
            Clock clock
    ) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.endpointGuard = Objects.requireNonNull(endpointGuard, "endpointGuard must not be null");
        this.responseBounds = Objects.requireNonNull(responseBounds, "responseBounds must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.stateTranslator = new OkxVenueStateTranslator();
    }

    @Override
    public MutationResult placeLimit(PlaceLimit request) {
        Objects.requireNonNull(request, "request must not be null");
        requireContractOperation(OkxSpotProviderOperation.PLACE_LIMIT);
        SpotProviderError clockError = validateClock(request.context());
        if (clockError != null) {
            return new MutationResult(MutationOutcome.DEFINITIVELY_REJECTED, null, clockError, false);
        }

        PlaceResponse response;
        try {
            response = transport.placeLimit(new PlaceCommand(
                    request.clientOrderId().value(),
                    request.instrument(),
                    request.side(),
                    request.price(),
                    request.quantity(),
                    transportContext(request.context()),
                    transportReadLimit()
            ));
        } catch (RuntimeException ex) {
            return unknownMutation(SpotProviderError.Category.UNKNOWN_RESULT);
        }
        SpotProviderError envelopeError = validateMetadata(
                response == null ? null : response.metadata(),
                OkxSpotProviderOperation.PLACE_LIMIT,
                true
        );
        if (envelopeError != null) return mutationFromError(envelopeError);
        if (response.failure() != null) {
            return mutationFromError(error(response.failure(), true));
        }
        if (response.outcome() == ResponseOutcome.REJECTED) {
            SpotProviderError rejection = SpotProviderError.classify(
                    SpotProviderError.Category.EXCHANGE_BUSINESS_REJECTION, false);
            return new MutationResult(MutationOutcome.DEFINITIVELY_REJECTED, null, rejection, false);
        }
        if (response.outcome() != ResponseOutcome.ACCEPTED) {
            return unknownMutation(SpotProviderError.Category.UNKNOWN_RESULT);
        }

        OrderObservation observation = stateTranslator.translate(
                response.order(),
                request.clientOrderId().value(),
                response.metadata().observedAt(),
                true,
                responseBounds.maximumFillRecords()
        );
        if (observation.state() == OrderState.UNKNOWN) {
            return new MutationResult(MutationOutcome.UNKNOWN, observation, observation.error(), true);
        }
        if (observation.state() == OrderState.REJECTED) {
            SpotProviderError rejection = SpotProviderError.classify(
                    SpotProviderError.Category.EXCHANGE_BUSINESS_REJECTION, false);
            return new MutationResult(MutationOutcome.DEFINITIVELY_REJECTED, observation, rejection, false);
        }
        return new MutationResult(MutationOutcome.ACCEPTED, observation, null, false);
    }

    @Override
    public OrderObservation queryOrderByClientOrderId(OrderQuery request) {
        return executeOrderRead(request, OkxSpotProviderOperation.QUERY_ORDER, true);
    }

    @Override
    public CancelResult cancel(Cancel request) {
        Objects.requireNonNull(request, "request must not be null");
        OrderObservation confirmed = request.confirmedObservation();
        switch (confirmed.state()) {
            case FILLED, CANCELED, REJECTED, NOT_FOUND -> {
                return new CancelResult(CancelDisposition.NO_MUTATION_TERMINAL, confirmed, null, false);
            }
            case UNKNOWN -> {
                return new CancelResult(CancelDisposition.QUERY_REQUIRED, confirmed, confirmed.error(), true);
            }
            case PARTIALLY_FILLED -> {
                if (request.partialFillPolicy() != PartialCancelPolicy.ALLOW_CONFIRMED_REMAINDER
                        || confirmed.remainingQuantity().signum() <= 0) {
                    return new CancelResult(CancelDisposition.QUERY_REQUIRED, confirmed, null, true);
                }
            }
            case OPEN -> {
                // known-open order is the only ordinary controlled cancel candidate.
            }
        }

        requireContractOperation(OkxSpotProviderOperation.CANCEL_ORDER);
        SpotProviderError clockError = validateClock(request.order().context());
        if (clockError != null) {
            return new CancelResult(CancelDisposition.DEFINITIVELY_REJECTED, confirmed, clockError, false);
        }

        CancelResponse response;
        try {
            response = transport.cancelOrder(new CancelCommand(
                    request.order().clientOrderId().value(),
                    request.order().instrument(),
                    confirmed.remainingQuantity(),
                    transportContext(request.order().context()),
                    transportReadLimit()
            ));
        } catch (RuntimeException ex) {
            return unknownCancel(confirmed, SpotProviderError.Category.UNKNOWN_RESULT);
        }
        SpotProviderError envelopeError = validateMetadata(
                response == null ? null : response.metadata(),
                OkxSpotProviderOperation.CANCEL_ORDER,
                true
        );
        if (envelopeError != null) return cancelFromError(confirmed, envelopeError);
        if (response.failure() != null) return cancelFromError(confirmed, error(response.failure(), true));
        if (response.outcome() == ResponseOutcome.REJECTED) {
            SpotProviderError rejection = SpotProviderError.classify(
                    SpotProviderError.Category.CANCEL_RACE, false);
            return new CancelResult(CancelDisposition.DEFINITIVELY_REJECTED, confirmed, rejection, true);
        }
        if (response.outcome() != ResponseOutcome.ACCEPTED) {
            return unknownCancel(confirmed, SpotProviderError.Category.UNKNOWN_RESULT);
        }

        OrderObservation observation = stateTranslator.translate(
                response.order(),
                request.order().clientOrderId().value(),
                response.metadata().observedAt(),
                true,
                responseBounds.maximumFillRecords()
        );
        if (observation.state() == OrderState.UNKNOWN) {
            return new CancelResult(CancelDisposition.UNKNOWN, observation, observation.error(), true);
        }
        if (observation.state() == OrderState.CANCELED) {
            return new CancelResult(CancelDisposition.MUTATION_ACCEPTED, observation, null, false);
        }
        SpotProviderError race = SpotProviderError.classify(SpotProviderError.Category.CANCEL_RACE, false);
        return new CancelResult(CancelDisposition.QUERY_REQUIRED, observation, race, true);
    }

    @Override
    public OrderObservation readOrderStatus(OrderQuery request) {
        return executeOrderRead(request, OkxSpotProviderOperation.READ_ORDER, false);
    }

    @Override
    public FillPage readFills(FillQuery request) {
        Objects.requireNonNull(request, "request must not be null");
        requireContractOperation(OkxSpotProviderOperation.READ_FILLS);
        String clientOrderId = request.order().clientOrderId().value();
        SpotProviderError clockError = validateClock(request.order().context());
        if (clockError != null) {
            return new FillPage(clientOrderId, List.of(), false, clockError, Instant.now(clock));
        }

        FillResponse response;
        try {
            response = transport.readFills(new FillCommand(
                    clientOrderId,
                    request.order().instrument(),
                    request.begin(),
                    request.end(),
                    Math.min(request.maxRecords(), responseBounds.maximumFillRecords()),
                    transportContext(request.order().context()),
                    transportReadLimit()
            ));
        } catch (RuntimeException ex) {
            SpotProviderError error = SpotProviderError.classify(
                    SpotProviderError.Category.UNKNOWN_RESULT, false);
            return new FillPage(clientOrderId, List.of(), false, error, Instant.now(clock));
        }
        SpotProviderError envelopeError = validateMetadata(
                response == null ? null : response.metadata(),
                OkxSpotProviderOperation.READ_FILLS,
                false
        );
        if (envelopeError != null) {
            return new FillPage(clientOrderId, List.of(), false, envelopeError, Instant.now(clock));
        }
        if (response.failure() != null) {
            return new FillPage(
                    clientOrderId, List.of(), false, error(response.failure(), false),
                    response.metadata().observedAt());
        }
        if (response.fills().size() > request.maxRecords()
                || response.fills().size() > responseBounds.maximumFillRecords()) {
            SpotProviderError error = SpotProviderError.classify(
                    SpotProviderError.Category.RESPONSE_TOO_LARGE, false);
            return new FillPage(clientOrderId, List.of(), false, error, response.metadata().observedAt());
        }
        try {
            List<FillReference> fills = response.fills().stream()
                    .map(fill -> new FillReference(
                            fill.exchangeTradeId(), fill.price(), fill.quantity(), fill.filledAt()))
                    .toList();
            return new FillPage(clientOrderId, fills, response.complete(), null, response.metadata().observedAt());
        } catch (IllegalArgumentException ex) {
            SpotProviderError error = SpotProviderError.classify(
                    SpotProviderError.Category.MALFORMED_RESPONSE, false);
            return new FillPage(clientOrderId, List.of(), false, error, response.metadata().observedAt());
        }
    }

    private OrderObservation executeOrderRead(
            OrderQuery request,
            OkxSpotProviderOperation operation,
            boolean queryOperation
    ) {
        Objects.requireNonNull(request, "request must not be null");
        requireContractOperation(operation);
        String clientOrderId = request.clientOrderId().value();
        SpotProviderError clockError = validateClock(request.context());
        if (clockError != null) return unknownObservation(clientOrderId, clockError, Instant.now(clock));

        OrderCommand command = new OrderCommand(
                clientOrderId, request.instrument(), transportContext(request.context()), transportReadLimit());
        OrderResponse response;
        try {
            response = queryOperation ? transport.queryOrder(command) : transport.readOrder(command);
        } catch (RuntimeException ex) {
            SpotProviderError error = SpotProviderError.classify(
                    SpotProviderError.Category.UNKNOWN_RESULT, false);
            return unknownObservation(clientOrderId, error, Instant.now(clock));
        }
        SpotProviderError envelopeError = validateMetadata(
                response == null ? null : response.metadata(), operation, false);
        if (envelopeError != null) {
            return unknownObservation(clientOrderId, envelopeError, Instant.now(clock));
        }
        if (response.failure() != null) {
            return unknownObservation(
                    clientOrderId, error(response.failure(), false), response.metadata().observedAt());
        }
        if (response.order() == null) {
            return stateTranslator.notFound(clientOrderId, response.metadata().observedAt());
        }
        return stateTranslator.translate(
                response.order(), clientOrderId, response.metadata().observedAt(), false,
                responseBounds.maximumFillRecords());
    }

    private SpotProviderError validateClock(RequestContext context) {
        return context.clock().healthyAt(Instant.now(clock))
                ? null
                : SpotProviderError.classify(SpotProviderError.Category.CLOCK_SKEW, false);
    }

    private SpotProviderError validateMetadata(
            ResponseMetadata metadata,
            OkxSpotProviderOperation expectedOperation,
            boolean mutationMayHaveReachedVenue
    ) {
        if (metadata == null || metadata.operation() != expectedOperation) {
            return SpotProviderError.classify(
                    SpotProviderError.Category.MALFORMED_RESPONSE, mutationMayHaveReachedVenue);
        }
        if (metadata.responseBytes() > responseBounds.maximumResponseBytes()) {
            return SpotProviderError.classify(
                    SpotProviderError.Category.RESPONSE_TOO_LARGE, mutationMayHaveReachedVenue);
        }
        return null;
    }

    private void requireContractOperation(OkxSpotProviderOperation operation) {
        OkxSpotProviderContractDecision decision = endpointGuard.evaluateProviderContract(operation);
        if (!decision.contractAllowed() || decision.runtimeAuthorized() || decision.tradingAuthorized()) {
            throw new IllegalStateException("OKX provider operation is not an allowed contract");
        }
    }

    private static TransportContext transportContext(RequestContext context) {
        return new TransportContext(
                context.sessionId(),
                context.referenceId(),
                context.traceId(),
                context.correlationId(),
                context.clock().requestTimestamp()
        );
    }

    private ResponseReadLimit transportReadLimit() {
        return new ResponseReadLimit(
                responseBounds.maximumResponseBytes(), responseBounds.maximumFillRecords());
    }

    private static SpotProviderError error(TransportFailure failure, boolean mutationInvoked) {
        return SpotProviderError.classify(
                failure.category(), mutationInvoked || failure.mutationMayHaveReachedVenue());
    }

    private static MutationResult mutationFromError(SpotProviderError error) {
        return error.certainty() == SpotProviderError.Certainty.INDETERMINATE
                ? new MutationResult(MutationOutcome.UNKNOWN, null, error, true)
                : new MutationResult(MutationOutcome.DEFINITIVELY_REJECTED, null, error, false);
    }

    private static MutationResult unknownMutation(SpotProviderError.Category category) {
        return mutationFromError(SpotProviderError.classify(category, true));
    }

    private static CancelResult cancelFromError(OrderObservation confirmed, SpotProviderError error) {
        return error.certainty() == SpotProviderError.Certainty.INDETERMINATE
                ? new CancelResult(CancelDisposition.UNKNOWN, confirmed, error, true)
                : new CancelResult(CancelDisposition.DEFINITIVELY_REJECTED, confirmed, error, false);
    }

    private static CancelResult unknownCancel(
            OrderObservation confirmed,
            SpotProviderError.Category category
    ) {
        return cancelFromError(confirmed, SpotProviderError.classify(category, true));
    }

    private static OrderObservation unknownObservation(
            String clientOrderId,
            SpotProviderError error,
            Instant observedAt
    ) {
        return new OrderObservation(
                OrderState.UNKNOWN,
                clientOrderId,
                null,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                List.of(),
                error,
                observedAt
        );
    }
}
