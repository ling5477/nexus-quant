package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.EndpointGuardReason;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.CancelCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.CancelResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.FillCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.FillResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.OrderCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.OrderResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.PlaceCommand;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.PlaceResponse;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.RawFill;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.RawOrder;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseMetadata;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseOutcome;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.ResponseReadLimit;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.TransportFailure;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.ProviderClientOrderId;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Cancel;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.ClockContract;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.FillQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.OrderQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.PartialCancelPolicy;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.PlaceLimit;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.RequestContext;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.ResponseBounds;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.TimestampSource;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.CancelDisposition;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillPage;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.MutationOutcome;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderObservation;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxSpotProviderAdapterContractTest {
    private static final UUID INTENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final BigDecimal ONE = new BigDecimal("1.00000000");
    private static final BigDecimal ZERO = new BigDecimal("0.00000000");

    @Test
    void shouldAcceptLimitPlaceAndKeepTypedTransportSingleShot() {
        FakeTransport fake = new FakeTransport();
        fake.placeResponse = new PlaceResponse(
                metadata(OkxSpotProviderOperation.PLACE_LIMIT, 256),
                ResponseOutcome.ACCEPTED,
                order("live", ZERO, ONE),
                null
        );
        SpotExecutionProviderPort provider = provider(fake);

        var result = provider.placeLimit(placeRequest(healthyContext()));

        assertEquals(MutationOutcome.ACCEPTED, result.outcome());
        assertEquals(OrderState.OPEN, result.observation().state());
        assertFalse(result.queryByClientOrderIdRequired());
        assertEquals(1, fake.placeCalls.get());
        assertEquals(0, fake.queryCalls.get());
    }

    @Test
    void shouldMapDefinitiveRejectionAndTimeoutUnknownWithoutBlindPlaceRetry() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        fake.placeResponse = new PlaceResponse(
                metadata(OkxSpotProviderOperation.PLACE_LIMIT, 128),
                ResponseOutcome.REJECTED,
                null,
                null
        );

        var rejected = provider.placeLimit(placeRequest(healthyContext()));
        assertEquals(MutationOutcome.DEFINITIVELY_REJECTED, rejected.outcome());
        assertEquals(1, fake.placeCalls.get());

        fake.placeResponse = new PlaceResponse(
                metadata(OkxSpotProviderOperation.PLACE_LIMIT, 0),
                ResponseOutcome.ERROR,
                null,
                new TransportFailure(SpotProviderError.Category.TRANSPORT_TIMEOUT, true)
        );
        var unknown = provider.placeLimit(placeRequest(healthyContext()));

        assertEquals(MutationOutcome.UNKNOWN, unknown.outcome());
        assertTrue(unknown.queryByClientOrderIdRequired());
        assertFalse(unknown.error().mutationRetryable());
        assertEquals(2, fake.placeCalls.get());
        assertEquals(0, fake.queryCalls.get());

        fake.queryResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 128),
                order("live", ZERO, ONE),
                null
        );
        assertEquals(OrderState.OPEN, provider.queryOrderByClientOrderId(orderQuery(healthyContext())).state());
        assertEquals(2, fake.placeCalls.get(), "query-first recovery must not resend PLACE");
        assertEquals(1, fake.queryCalls.get());
    }

    @Test
    void shouldIgnoreUnsafeTransportCertaintyHintsAfterMutationInvocation() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);

        for (SpotProviderError.Category category : List.of(
                SpotProviderError.Category.TRANSPORT_TIMEOUT,
                SpotProviderError.Category.HTTP_ERROR,
                SpotProviderError.Category.RATE_LIMITED)) {
            fake.placeResponse = errorPlace(category, false);
            var place = provider.placeLimit(placeRequest(healthyContext()));
            assertEquals(MutationOutcome.UNKNOWN, place.outcome(), category.name());
            assertTrue(place.queryByClientOrderIdRequired(), category.name());

            fake.cancelResponse = new CancelResponse(
                    metadata(OkxSpotProviderOperation.CANCEL_ORDER, 0),
                    ResponseOutcome.ERROR,
                    null,
                    new TransportFailure(category, false)
            );
            var cancel = provider.cancel(new Cancel(
                    orderQuery(healthyContext()),
                    observation(OrderState.OPEN, ZERO, ONE, null),
                    PartialCancelPolicy.QUERY_FIRST
            ));
            assertEquals(CancelDisposition.UNKNOWN, cancel.disposition(), category.name());
            assertTrue(cancel.queryByClientOrderIdRequired(), category.name());
        }

        assertEquals(3, fake.placeCalls.get());
        assertEquals(3, fake.cancelCalls.get());
    }

    @Test
    void shouldKeepUnknownThenNotFoundQueryOnlyWithoutResendingPlace() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        fake.placeResponse = errorPlace(SpotProviderError.Category.TRANSPORT_TIMEOUT, false);
        fake.queryResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 64), null, null);

        var place = provider.placeLimit(placeRequest(healthyContext()));
        var query = provider.queryOrderByClientOrderId(orderQuery(healthyContext()));

        assertEquals(MutationOutcome.UNKNOWN, place.outcome());
        assertEquals(OrderState.NOT_FOUND, query.state());
        assertEquals(1, fake.placeCalls.get(), "NOT_FOUND observation must not resend PLACE");
        assertEquals(1, fake.queryCalls.get());
    }

    @Test
    void shouldTranslateEveryQueryStateAndFailClosedOnUnknownRawState() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        Map<String, OrderState> cases = Map.of(
                "live", OrderState.OPEN,
                "partially_filled", OrderState.PARTIALLY_FILLED,
                "filled", OrderState.FILLED,
                "canceled", OrderState.CANCELED,
                "rejected", OrderState.REJECTED
        );

        for (Map.Entry<String, OrderState> testCase : cases.entrySet()) {
            BigDecimal executed = testCase.getValue() == OrderState.PARTIALLY_FILLED
                    ? new BigDecimal("0.40000000")
                    : testCase.getValue() == OrderState.FILLED ? ONE : ZERO;
            BigDecimal remaining = ONE.subtract(executed);
            fake.queryResponse = new OrderResponse(
                    metadata(OkxSpotProviderOperation.QUERY_ORDER, 128),
                    order(testCase.getKey(), executed, remaining),
                    null
            );
            assertEquals(
                    testCase.getValue(),
                    provider.queryOrderByClientOrderId(orderQuery(healthyContext())).state(),
                    testCase.getKey()
            );
        }

        fake.queryResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 128), null, null);
        assertEquals(OrderState.NOT_FOUND, provider.queryOrderByClientOrderId(orderQuery(healthyContext())).state());

        fake.queryResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 128),
                order("future_unknown_state", ZERO, ONE),
                null
        );
        OrderObservation unknown = provider.queryOrderByClientOrderId(orderQuery(healthyContext()));
        assertEquals(OrderState.UNKNOWN, unknown.state());
        assertEquals(SpotProviderError.Category.UNKNOWN_RESULT, unknown.error().category());

        fake.readResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.READ_ORDER, 128),
                order("filled", ONE, ZERO),
                null
        );
        assertEquals(OrderState.FILLED, provider.readOrderStatus(orderQuery(healthyContext())).state());
        assertEquals(1, fake.readCalls.get());
    }

    @Test
    void shouldApplyStateAwareCancelAndQueryFirstForPartialFilledAndUnknown() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        fake.cancelResponse = new CancelResponse(
                metadata(OkxSpotProviderOperation.CANCEL_ORDER, 128),
                ResponseOutcome.ACCEPTED,
                order("canceled", ZERO, ONE),
                null
        );

        var openResult = provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.OPEN, ZERO, ONE, null),
                PartialCancelPolicy.QUERY_FIRST
        ));
        assertEquals(CancelDisposition.MUTATION_ACCEPTED, openResult.disposition());
        assertEquals(1, fake.cancelCalls.get());

        var filledResult = provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.FILLED, ONE, ZERO, null),
                PartialCancelPolicy.QUERY_FIRST
        ));
        assertEquals(CancelDisposition.NO_MUTATION_TERMINAL, filledResult.disposition());

        var partialResult = provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.PARTIALLY_FILLED, new BigDecimal("0.4"), new BigDecimal("0.6"), null),
                PartialCancelPolicy.QUERY_FIRST
        ));
        assertEquals(CancelDisposition.QUERY_REQUIRED, partialResult.disposition());

        SpotProviderError unknownError = SpotProviderError.classify(
                SpotProviderError.Category.UNKNOWN_RESULT, true);
        var unknownResult = provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.UNKNOWN, ZERO, ZERO, unknownError),
                PartialCancelPolicy.QUERY_FIRST
        ));
        assertEquals(CancelDisposition.QUERY_REQUIRED, unknownResult.disposition());
        assertEquals(1, fake.cancelCalls.get(), "terminal/partial/unknown states must not send CANCEL");
    }

    @Test
    void shouldMapCancelTimeoutAndRaceToQueryFirstWithoutBlindRetry() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        fake.cancelResponse = new CancelResponse(
                metadata(OkxSpotProviderOperation.CANCEL_ORDER, 0),
                ResponseOutcome.ERROR,
                null,
                new TransportFailure(SpotProviderError.Category.TRANSPORT_TIMEOUT, true)
        );

        var timeout = provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.OPEN, ZERO, ONE, null),
                PartialCancelPolicy.QUERY_FIRST
        ));
        assertEquals(CancelDisposition.UNKNOWN, timeout.disposition());
        assertTrue(timeout.queryByClientOrderIdRequired());

        fake.cancelResponse = new CancelResponse(
                metadata(OkxSpotProviderOperation.CANCEL_ORDER, 128),
                ResponseOutcome.REJECTED,
                null,
                null
        );
        var race = provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.OPEN, ZERO, ONE, null),
                PartialCancelPolicy.QUERY_FIRST
        ));
        assertEquals(SpotProviderError.Category.CANCEL_RACE, race.error().category());
        assertTrue(race.queryByClientOrderIdRequired());
        assertEquals(2, fake.cancelCalls.get());

        fake.cancelResponse = new CancelResponse(
                metadata(OkxSpotProviderOperation.CANCEL_ORDER, 128),
                ResponseOutcome.ACCEPTED,
                order("filled", ONE, ZERO),
                null
        );
        var filledRace = provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.OPEN, ZERO, ONE, null),
                PartialCancelPolicy.QUERY_FIRST
        ));
        assertEquals(CancelDisposition.QUERY_REQUIRED, filledRace.disposition());
        assertEquals(SpotProviderError.Category.CANCEL_RACE, filledRace.error().category());
        assertEquals(3, fake.cancelCalls.get());
    }

    @Test
    void shouldFailClosedForRateLimitClockSkewOversizeMalformedPermissionAndIpErrors() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);

        fake.placeResponse = errorPlace(SpotProviderError.Category.RATE_LIMITED, true);
        assertEquals(MutationOutcome.UNKNOWN, provider.placeLimit(placeRequest(healthyContext())).outcome());

        var clockRejected = provider.placeLimit(placeRequest(unresolvedClockContext()));
        assertEquals(MutationOutcome.DEFINITIVELY_REJECTED, clockRejected.outcome());
        assertEquals(SpotProviderError.Category.CLOCK_SKEW, clockRejected.error().category());
        assertEquals(1, fake.placeCalls.get(), "clock failure must stop before transport");

        fake.placeResponse = new PlaceResponse(
                metadata(OkxSpotProviderOperation.PLACE_LIMIT, 2048),
                ResponseOutcome.ACCEPTED,
                order("live", ZERO, ONE),
                null
        );
        var oversized = provider.placeLimit(placeRequest(healthyContext()));
        assertEquals(MutationOutcome.UNKNOWN, oversized.outcome());
        assertEquals(SpotProviderError.Category.RESPONSE_TOO_LARGE, oversized.error().category());

        fake.placeResponse = new PlaceResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 128),
                ResponseOutcome.ACCEPTED,
                order("live", ZERO, ONE),
                null
        );
        var malformed = provider.placeLimit(placeRequest(healthyContext()));
        assertEquals(MutationOutcome.UNKNOWN, malformed.outcome());
        assertEquals(SpotProviderError.Category.MALFORMED_RESPONSE, malformed.error().category());

        fake.placeResponse = errorPlace(SpotProviderError.Category.PERMISSION_DENIED, false);
        var permission = provider.placeLimit(placeRequest(healthyContext()));
        assertEquals(MutationOutcome.DEFINITIVELY_REJECTED, permission.outcome());
        assertEquals(SpotProviderError.Recommendation.ENGAGE_KILL_AND_REVIEW,
                permission.error().recommendation());

        fake.placeResponse = errorPlace(SpotProviderError.Category.IP_RESTRICTION, false);
        var ip = provider.placeLimit(placeRequest(healthyContext()));
        assertEquals(MutationOutcome.DEFINITIVELY_REJECTED, ip.outcome());
        assertEquals(SpotProviderError.Recommendation.ENGAGE_KILL_AND_REVIEW, ip.error().recommendation());
    }

    @Test
    void shouldReturnBoundedFillReferencesWithoutCreatingSecondLedgerTruth() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        fake.fillResponse = new FillResponse(
                metadata(OkxSpotProviderOperation.READ_FILLS, 256),
                List.of(new RawFill("trade-1", new BigDecimal("100.0"), new BigDecimal("0.4"), NOW)),
                true,
                null
        );

        FillPage fills = provider.readFills(new FillQuery(
                orderQuery(healthyContext()), NOW.minusSeconds(60), NOW, 10));

        assertEquals(1, fills.fills().size());
        assertEquals("trade-1", fills.fills().getFirst().exchangeTradeId());
        assertTrue(fills.complete());
        assertNull(fills.error());
        assertEquals(1, fake.fillCalls.get());
    }

    @Test
    void shouldPassPreReadResponseLimitsToEveryTypedTransportOperation() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        fake.placeResponse = new PlaceResponse(
                metadata(OkxSpotProviderOperation.PLACE_LIMIT, 64),
                ResponseOutcome.ACCEPTED, order("live", ZERO, ONE), null);
        fake.queryResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 64), order("live", ZERO, ONE), null);
        fake.readResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.READ_ORDER, 64), order("live", ZERO, ONE), null);
        fake.cancelResponse = new CancelResponse(
                metadata(OkxSpotProviderOperation.CANCEL_ORDER, 64),
                ResponseOutcome.ACCEPTED, order("canceled", ZERO, ONE), null);
        fake.fillResponse = new FillResponse(
                metadata(OkxSpotProviderOperation.READ_FILLS, 64), List.of(), true, null);

        provider.placeLimit(placeRequest(healthyContext()));
        provider.queryOrderByClientOrderId(orderQuery(healthyContext()));
        provider.readOrderStatus(orderQuery(healthyContext()));
        provider.cancel(new Cancel(
                orderQuery(healthyContext()),
                observation(OrderState.OPEN, ZERO, ONE, null),
                PartialCancelPolicy.QUERY_FIRST));
        provider.readFills(new FillQuery(orderQuery(healthyContext()), NOW.minusSeconds(60), NOW, 10));

        ResponseReadLimit expected = new ResponseReadLimit(1024, 100);
        assertEquals(expected, fake.lastPlaceCommand.responseLimit());
        assertEquals(expected, fake.lastQueryCommand.responseLimit());
        assertEquals(expected, fake.lastReadCommand.responseLimit());
        assertEquals(expected, fake.lastCancelCommand.responseLimit());
        assertEquals(expected, fake.lastFillCommand.responseLimit());
        assertEquals(10, fake.lastFillCommand.maxRecords());
    }

    @Test
    void shouldFailClosedOnOversizeDuplicateAndInconsistentOrderFacts() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        List<RawFill> oversizedFills = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> rawFill("trade-" + index))
                .toList();
        fake.placeResponse = new PlaceResponse(
                metadata(OkxSpotProviderOperation.PLACE_LIMIT, 512),
                ResponseOutcome.ACCEPTED,
                rawOrder("live", ONE, ZERO, ONE, oversizedFills),
                null
        );
        var oversized = provider.placeLimit(placeRequest(healthyContext()));
        assertEquals(MutationOutcome.UNKNOWN, oversized.outcome());
        assertEquals(SpotProviderError.Category.RESPONSE_TOO_LARGE, oversized.error().category());

        fake.queryResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 256),
                rawOrder("partially_filled", ONE, new BigDecimal("0.4"), new BigDecimal("0.6"),
                        List.of(rawFill("duplicate"), rawFill("duplicate"))),
                null
        );
        var duplicate = provider.queryOrderByClientOrderId(orderQuery(healthyContext()));
        assertEquals(OrderState.UNKNOWN, duplicate.state());
        assertEquals(SpotProviderError.Category.MALFORMED_RESPONSE, duplicate.error().category());

        fake.queryResponse = new OrderResponse(
                metadata(OkxSpotProviderOperation.QUERY_ORDER, 256),
                rawOrder("partially_filled", ONE, new BigDecimal("0.4"), new BigDecimal("0.5"), List.of()),
                null
        );
        var inconsistent = provider.queryOrderByClientOrderId(orderQuery(healthyContext()));
        assertEquals(OrderState.UNKNOWN, inconsistent.state());
        assertEquals(SpotProviderError.Category.MALFORMED_RESPONSE, inconsistent.error().category());
        assertEquals(1, fake.placeCalls.get());
        assertEquals(2, fake.queryCalls.get());
    }

    @Test
    void shouldFailClosedOnMalformedDuplicateAndOversizeFillReads() {
        FakeTransport fake = new FakeTransport();
        SpotExecutionProviderPort provider = provider(fake);
        FillQuery query = new FillQuery(orderQuery(healthyContext()), NOW.minusSeconds(60), NOW, 100);

        fake.fillResponse = new FillResponse(
                metadata(OkxSpotProviderOperation.READ_FILLS, 256),
                List.of(rawFill("duplicate"), rawFill("duplicate")), true, null);
        FillPage duplicate = provider.readFills(query);
        assertEquals(SpotProviderError.Category.MALFORMED_RESPONSE, duplicate.error().category());
        assertTrue(duplicate.fills().isEmpty());

        fake.fillResponse = new FillResponse(
                metadata(OkxSpotProviderOperation.READ_FILLS, 256),
                List.of(new RawFill("zero-price", ZERO, ONE, NOW)), true, null);
        FillPage malformed = provider.readFills(query);
        assertEquals(SpotProviderError.Category.MALFORMED_RESPONSE, malformed.error().category());

        fake.fillResponse = new FillResponse(
                metadata(OkxSpotProviderOperation.READ_FILLS, 512),
                java.util.stream.IntStream.range(0, 101)
                        .mapToObj(index -> rawFill("trade-" + index))
                        .toList(),
                true,
                null
        );
        FillPage oversized = provider.readFills(query);
        assertEquals(SpotProviderError.Category.RESPONSE_TOO_LARGE, oversized.error().category());
        assertEquals(3, fake.fillCalls.get());
    }

    @Test
    void shouldKeepEndpointAllowlistExactAndMakeRawUrlFundsAndUnsupportedFamiliesUnreachable() {
        OkxSpotEndpointGuard guard = new OkxSpotEndpointGuard();
        assertEquals(
                List.of("CANCEL_ORDER", "PLACE_LIMIT", "QUERY_ORDER", "READ_FILLS", "READ_ORDER"),
                OkxSpotProviderOperation.exactAllowlist().stream().map(Enum::name).sorted().toList());
        for (OkxSpotProviderOperation operation : OkxSpotProviderOperation.values()) {
            OkxSpotProviderContractDecision decision = guard.evaluateProviderContract(operation);
            assertTrue(decision.contractAllowed());
            assertFalse(decision.runtimeAuthorized());
            assertFalse(decision.tradingAuthorized());
            assertFalse(operation.path().contains("http"));
        }
        assertFalse(guard.evaluateProviderContract(null).contractAllowed());
        assertEquals(EndpointGuardReason.DENY_FUNDS_MOVEMENT,
                guard.evaluate(ExchangeCapability.WITHDRAW, "POST", "/api/v5/asset/withdrawal").reason());
        assertEquals(EndpointGuardReason.DENY_FUNDS_MOVEMENT,
                guard.evaluate(ExchangeCapability.TRANSFER, "POST", "/api/v5/asset/transfer").reason());
        for (String forbiddenPath : List.of(
                "/api/v5/account/borrow-repay",
                "/api/v5/account/position-margin-balance",
                "/api/v5/account/set-leverage",
                "/api/v5/trade/order?instType=FUTURES",
                "/api/v5/trade/order?instType=OPTION",
                "/api/v5/account/funding-balance",
                "/api/v5/asset/subaccount/transfer",
                "https://invalid.example/api/v5/trade/order",
                "/arbitrary/private/path")) {
            assertFalse(guard.evaluate(ExchangeCapability.ORDER_SUBMISSION, "POST", forbiddenPath).allowed(),
                    forbiddenPath);
        }
    }

    @Test
    void shouldExposeNoCredentialSignatureRawResponseUrlOrFallbackTransportSurface() {
        List<String> transportMethods = Arrays.stream(OkxSpotProviderTransport.class.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .map(method -> method.getName())
                .sorted()
                .toList();
        assertEquals(List.of("cancelOrder", "placeLimit", "queryOrder", "readFills", "readOrder"),
                transportMethods);

        String componentNames = Arrays.stream(OkxSpotProviderTransport.class.getDeclaredClasses())
                .filter(Class::isRecord)
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));
        for (String forbidden : List.of(
                "credential", "apikey", "secret", "passphrase", "signature", "header",
                "rawresponse", "responsebody", "host", "url", "redirect")) {
            assertFalse(componentNames.contains(forbidden), forbidden + " leaked into transport contract");
        }
        assertTrue(Arrays.stream(OkxSpotProviderAdapter.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == OkxHttpClient.class));
        assertSame(OkxSpotProviderTransport.class,
                Arrays.stream(OkxSpotProviderAdapter.class.getDeclaredFields())
                        .filter(field -> field.getName().equals("transport"))
                        .findFirst().orElseThrow().getType());
    }

    private static OkxSpotProviderAdapter provider(FakeTransport fake) {
        return new OkxSpotProviderAdapter(
                fake,
                new OkxSpotEndpointGuard(),
                new ResponseBounds(1024, 100),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static PlaceResponse errorPlace(SpotProviderError.Category category, boolean mayHaveReached) {
        return new PlaceResponse(
                metadata(OkxSpotProviderOperation.PLACE_LIMIT, 0),
                ResponseOutcome.ERROR,
                null,
                new TransportFailure(category, mayHaveReached)
        );
    }

    private static ResponseMetadata metadata(OkxSpotProviderOperation operation, int bytes) {
        return new ResponseMetadata(operation, bytes, "request-1", NOW);
    }

    private static RawOrder order(String state, BigDecimal executed, BigDecimal remaining) {
        return rawOrder(
                state,
                executed.add(remaining),
                executed,
                remaining,
                List.of()
        );
    }

    private static RawOrder rawOrder(
            String state,
            BigDecimal original,
            BigDecimal executed,
            BigDecimal remaining,
            List<RawFill> fills
    ) {
        return new RawOrder(
                providerClientOrderId().value(),
                "exchange-order-1",
                state,
                original,
                executed,
                remaining,
                fills
        );
    }

    private static RawFill rawFill(String exchangeTradeId) {
        return new RawFill(exchangeTradeId, new BigDecimal("100.0"), new BigDecimal("0.1"), NOW);
    }

    private static PlaceLimit placeRequest(RequestContext context) {
        return PlaceLimit.fromIntent(placeIntent(), context);
    }

    private static OrderQuery orderQuery(RequestContext context) {
        return new OrderQuery(
                providerClientOrderId(),
                com.guidinglight.nexusquant.livecontrol.execution.application.provider
                        .SpotProviderRequests.Venue.OKX_SPOT,
                "BTC-USDT",
                context
        );
    }

    private static OrderObservation observation(
            OrderState state,
            BigDecimal executed,
            BigDecimal remaining,
            SpotProviderError error
    ) {
        return new OrderObservation(
                state,
                providerClientOrderId().value(),
                "exchange-order-1",
                executed.add(remaining),
                executed,
                remaining,
                List.of(),
                error,
                NOW
        );
    }

    private static RequestContext healthyContext() {
        return new RequestContext(
                SESSION_ID,
                "reference-1",
                "trace-1",
                "correlation-1",
                new ClockContract(
                        TimestampSource.TRUSTED_UTC_CLOCK,
                        NOW,
                        NOW,
                        Duration.ZERO,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(10)
                )
        );
    }

    private static RequestContext unresolvedClockContext() {
        return new RequestContext(
                SESSION_ID,
                "reference-1",
                "trace-1",
                "correlation-1",
                new ClockContract(
                        TimestampSource.TRUSTED_UTC_CLOCK,
                        NOW,
                        NOW,
                        null,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(10)
                )
        );
    }

    private static ProviderClientOrderId providerClientOrderId() {
        return ProviderClientOrderId.from(
                INTENT_ID, ExecutionIntentCanonicalEncoder.stableClientOrderId(INTENT_ID));
    }

    private static ExecutionIntent placeIntent() {
        ExecutionIntentDraft draft = ExecutionIntentCanonicalEncoder.place(
                INTENT_ID,
                SESSION_ID,
                "BTC-USDT",
                "BUY",
                new BigDecimal("1.00000000"),
                new BigDecimal("100.00000000"),
                "local-order-1"
        );
        return new ExecutionIntent(
                draft.intentId(), draft.sessionId(), 1L, draft.action(), draft.symbol(), draft.side(),
                draft.orderType(), draft.quantity(), draft.limitPrice(), ExecutionIntentDraft.PAYLOAD_SCHEMA,
                draft.payloadHash(), draft.clientOrderId(), draft.localOrderId(), ExecutionIntentState.CREATED,
                1L, null, null, null, null, null, NOW
        );
    }

    private static final class FakeTransport implements OkxSpotProviderTransport {
        private final AtomicInteger placeCalls = new AtomicInteger();
        private final AtomicInteger queryCalls = new AtomicInteger();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private final AtomicInteger readCalls = new AtomicInteger();
        private final AtomicInteger fillCalls = new AtomicInteger();
        private PlaceResponse placeResponse;
        private OrderResponse queryResponse;
        private CancelResponse cancelResponse;
        private OrderResponse readResponse;
        private FillResponse fillResponse;
        private PlaceCommand lastPlaceCommand;
        private OrderCommand lastQueryCommand;
        private CancelCommand lastCancelCommand;
        private OrderCommand lastReadCommand;
        private FillCommand lastFillCommand;

        @Override
        public PlaceResponse placeLimit(PlaceCommand command) {
            placeCalls.incrementAndGet();
            lastPlaceCommand = command;
            return placeResponse;
        }

        @Override
        public OrderResponse queryOrder(OrderCommand command) {
            queryCalls.incrementAndGet();
            lastQueryCommand = command;
            return queryResponse;
        }

        @Override
        public CancelResponse cancelOrder(CancelCommand command) {
            cancelCalls.incrementAndGet();
            lastCancelCommand = command;
            return cancelResponse;
        }

        @Override
        public OrderResponse readOrder(OrderCommand command) {
            readCalls.incrementAndGet();
            lastReadCommand = command;
            return readResponse;
        }

        @Override
        public FillResponse readFills(FillCommand command) {
            fillCalls.incrementAndGet();
            lastFillCommand = command;
            return fillResponse;
        }
    }
}
