package com.guidinglight.nexusquant.livecontrol.execution.provider;

import com.guidinglight.nexusquant.livecontrol.execution.application.provider.ProviderClientOrderId;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.ClockContract;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.FillQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.OrderType;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.OrderQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.PlaceLimit;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.RequestContext;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.ResponseBounds;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Side;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.TimestampSource;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Venue;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.CancelDisposition;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.CancelResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillPage;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.MutationOutcome;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.MutationResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderObservation;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotProviderContractTest {
    private static final UUID INTENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Test
    void shouldBuildLimitOnlyOkxSpotRequestFromAcceptedIntentIdentity() {
        ExecutionIntent intent = placeIntent(INTENT_ID);

        PlaceLimit request = PlaceLimit.fromIntent(intent, context());

        assertEquals(Venue.OKX_SPOT, request.venue());
        assertEquals(OrderType.LIMIT, request.orderType());
        assertEquals("BTC-USDT", request.instrument());
        assertEquals("11111111222233334444555555555555", request.clientOrderId().value());
        assertEquals(32, request.clientOrderId().value().length());
        assertTrue(request.context().clock().healthyAt(NOW));
    }

    @Test
    void shouldRejectMarketUnsupportedVenueAndInvalidPriceOrQuantity() {
        assertThrows(IllegalArgumentException.class, () -> OrderType.require("MARKET"));
        assertThrows(IllegalArgumentException.class, () -> Venue.require("BINANCE_SPOT"));
        assertThrows(IllegalArgumentException.class, () -> new PlaceLimit(
                INTENT_ID,
                ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID)),
                Venue.OKX_SPOT,
                "BTC-USDT",
                SpotProviderRequests.Side.BUY,
                OrderType.LIMIT,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                context()
        ));
        assertThrows(IllegalArgumentException.class, () -> new PlaceLimit(
                INTENT_ID,
                ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID)),
                Venue.OKX_SPOT,
                "BTC-USDT",
                SpotProviderRequests.Side.BUY,
                OrderType.LIMIT,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                context()
        ));
    }

    @Test
    void shouldKeepClientOrderIdentityStableAcrossReplayAndRestartAndDistinctAcrossIntents() {
        ProviderClientOrderId first = ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID));
        ProviderClientOrderId replay = ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID));
        UUID otherIntent = UUID.fromString("99999999-2222-3333-4444-555555555555");
        ProviderClientOrderId other = ProviderClientOrderId.from(otherIntent, stableExecutionId(otherIntent));

        assertEquals(first, replay);
        assertEquals(first, ProviderClientOrderId.fromIntent(placeIntent(INTENT_ID)));
        assertNotEquals(first.value(), other.value());
    }

    @Test
    void shouldFailClosedOnClientOrderIdCollisionLengthFormatOrExecutionIdentityTampering() {
        String firstValue = ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID)).value();
        UUID otherIntent = UUID.fromString("99999999-2222-3333-4444-555555555555");

        assertThrows(IllegalArgumentException.class, () -> new ProviderClientOrderId(
                otherIntent, stableExecutionId(otherIntent), firstValue));
        assertThrows(IllegalArgumentException.class, () -> new ProviderClientOrderId(
                INTENT_ID, stableExecutionId(INTENT_ID), "x".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> new ProviderClientOrderId(
                INTENT_ID, stableExecutionId(INTENT_ID), "not-okx-client-order-id"));
        assertThrows(IllegalArgumentException.class, () -> new ProviderClientOrderId(
                INTENT_ID, stableExecutionId(otherIntent), firstValue));
    }

    @Test
    void shouldFreezeErrorTaxonomyWithMutationRetryAlwaysDisabled() {
        for (SpotProviderError.Category category : SpotProviderError.Category.values()) {
            SpotProviderError beforeSend = SpotProviderError.classify(category, false);
            SpotProviderError afterSend = SpotProviderError.classify(category, true);

            assertFalse(beforeSend.mutationRetryable(), category.name());
            assertFalse(afterSend.mutationRetryable(), category.name());
            assertTrue(beforeSend.auditCode().startsWith("REAL_"));
            assertTrue(afterSend.auditCode().startsWith("REAL_"));
        }
        assertEquals(
                SpotProviderError.Certainty.INDETERMINATE,
                SpotProviderError.classify(SpotProviderError.Category.TRANSPORT_TIMEOUT, true).certainty());
        assertEquals(
                SpotProviderError.Recommendation.ENGAGE_KILL_AND_REVIEW,
                SpotProviderError.classify(SpotProviderError.Category.PERMISSION_DENIED, false).recommendation());
    }

    @Test
    void shouldRequireCallerSuppliedFreshClockObservationWithoutInventingPilotThreshold() {
        ClockContract unresolved = new ClockContract(
                TimestampSource.TRUSTED_UTC_CLOCK,
                NOW,
                NOW,
                null,
                Duration.ofSeconds(2),
                Duration.ofSeconds(10)
        );
        ClockContract stale = new ClockContract(
                TimestampSource.TRUSTED_UTC_CLOCK,
                NOW,
                NOW.minusSeconds(20),
                Duration.ZERO,
                Duration.ofSeconds(2),
                Duration.ofSeconds(10)
        );

        assertFalse(unresolved.healthyAt(NOW));
        assertFalse(stale.healthyAt(NOW));
    }

    @Test
    void shouldRejectMissingOrInvalidImmutableRequestFieldsBeforeTransport() {
        ProviderClientOrderId clientOrderId = ProviderClientOrderId.from(
                INTENT_ID, stableExecutionId(INTENT_ID));

        assertThrows(NullPointerException.class, () -> new PlaceLimit(
                null, clientOrderId, Venue.OKX_SPOT, "BTC-USDT", Side.BUY,
                OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE, context()));
        assertThrows(NullPointerException.class, () -> new PlaceLimit(
                INTENT_ID, null, Venue.OKX_SPOT, "BTC-USDT", Side.BUY,
                OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE, context()));
        assertThrows(IllegalArgumentException.class, () -> new PlaceLimit(
                INTENT_ID, clientOrderId, Venue.OKX_SPOT, " ", Side.BUY,
                OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE, context()));
        assertThrows(NullPointerException.class, () -> new PlaceLimit(
                INTENT_ID, clientOrderId, Venue.OKX_SPOT, "BTC-USDT", null,
                OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE, context()));
        assertThrows(IllegalArgumentException.class, () -> new PlaceLimit(
                INTENT_ID, clientOrderId, Venue.OKX_SPOT, "BTC-USDT", Side.BUY,
                OrderType.LIMIT, null, BigDecimal.ONE, context()));
        assertThrows(IllegalArgumentException.class, () -> new PlaceLimit(
                INTENT_ID, clientOrderId, Venue.OKX_SPOT, "BTC-USDT", Side.BUY,
                OrderType.LIMIT, BigDecimal.ONE.negate(), BigDecimal.ONE, context()));
        assertThrows(IllegalArgumentException.class, () -> new PlaceLimit(
                INTENT_ID, clientOrderId, Venue.OKX_SPOT, "BTC-USDT", Side.BUY,
                OrderType.LIMIT, BigDecimal.ONE, null, context()));
        assertThrows(IllegalArgumentException.class, () -> new PlaceLimit(
                INTENT_ID, clientOrderId, Venue.OKX_SPOT, "BTC-USDT", Side.BUY,
                OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE.negate(), context()));
        assertThrows(NullPointerException.class, () -> new PlaceLimit(
                INTENT_ID, clientOrderId, Venue.OKX_SPOT, "BTC-USDT", Side.BUY,
                OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE, null));

        assertThrows(NullPointerException.class, () -> new RequestContext(
                null, "reference-1", "trace-1", "correlation-1", context().clock()));
        assertThrows(IllegalArgumentException.class, () -> new RequestContext(
                SESSION_ID, null, "trace-1", "correlation-1", context().clock()));
        assertThrows(IllegalArgumentException.class, () -> new RequestContext(
                SESSION_ID, "reference-1", null, "correlation-1", context().clock()));
        assertThrows(IllegalArgumentException.class, () -> new RequestContext(
                SESSION_ID, "reference-1", "trace-1", null, context().clock()));
    }

    @Test
    void shouldFailClosedOnMalformedQuantitiesDuplicateOrUnboundedFillReferences() {
        FillReference valid = new FillReference(
                "trade-1", BigDecimal.ONE, BigDecimal.ONE, NOW);
        assertThrows(IllegalArgumentException.class, () -> new FillReference(
                "trade-zero", BigDecimal.ZERO, BigDecimal.ONE, NOW));
        assertThrows(IllegalArgumentException.class, () -> new FillReference(
                "trade-zero", BigDecimal.ONE, BigDecimal.ZERO, NOW));

        assertThrows(IllegalArgumentException.class, () -> new FillPage(
                ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID)).value(),
                List.of(valid, valid), true, null, NOW));
        List<FillReference> tooMany = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> new FillReference(
                        "trade-" + index, BigDecimal.ONE, BigDecimal.ONE, NOW))
                .toList();
        assertThrows(IllegalArgumentException.class, () -> new FillPage(
                ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID)).value(),
                tooMany, true, null, NOW));

        assertThrows(IllegalArgumentException.class, () -> observation(
                OrderState.PARTIALLY_FILLED, BigDecimal.ONE, BigDecimal.ZERO, List.of()));
        assertThrows(IllegalArgumentException.class, () -> observation(
                OrderState.OPEN, BigDecimal.ONE, BigDecimal.ZERO, List.of()));

        OrderQuery query = new OrderQuery(
                ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID)),
                Venue.OKX_SPOT, "BTC-USDT", context());
        assertThrows(IllegalArgumentException.class, () -> new FillQuery(
                query, NOW.minusSeconds(1), NOW, 0));
        assertThrows(IllegalArgumentException.class, () -> new FillQuery(
                query, NOW.minusSeconds(1), NOW, 101));
        assertThrows(IllegalArgumentException.class, () -> new ResponseBounds(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new ResponseBounds(1024, 101));
    }

    @Test
    void shouldEnforceUnknownAndDefinitiveResultCertaintyInvariants() {
        SpotProviderError definitive = SpotProviderError.classify(
                SpotProviderError.Category.TRANSPORT_TIMEOUT, false);
        SpotProviderError indeterminate = SpotProviderError.classify(
                SpotProviderError.Category.TRANSPORT_TIMEOUT, true);

        assertThrows(IllegalArgumentException.class, () -> new MutationResult(
                MutationOutcome.UNKNOWN, null, definitive, true));
        assertThrows(IllegalArgumentException.class, () -> new MutationResult(
                MutationOutcome.DEFINITIVELY_REJECTED, null, indeterminate, false));
        assertThrows(IllegalArgumentException.class, () -> new CancelResult(
                CancelDisposition.UNKNOWN, null, definitive, true));
    }

    private static OrderObservation observation(
            OrderState state,
            BigDecimal executed,
            BigDecimal remaining,
            List<FillReference> fills
    ) {
        return new OrderObservation(
                state,
                ProviderClientOrderId.from(INTENT_ID, stableExecutionId(INTENT_ID)).value(),
                "exchange-order-1",
                executed.add(remaining),
                executed,
                remaining,
                fills,
                null,
                NOW
        );
    }

    private static RequestContext context() {
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

    private static ExecutionIntent placeIntent(UUID intentId) {
        ExecutionIntentDraft draft = ExecutionIntentCanonicalEncoder.place(
                intentId,
                SESSION_ID,
                "BTC-USDT",
                "BUY",
                new BigDecimal("0.01000000"),
                new BigDecimal("100.00000000"),
                "local-order-1"
        );
        return new ExecutionIntent(
                draft.intentId(),
                draft.sessionId(),
                1L,
                draft.action(),
                draft.symbol(),
                draft.side(),
                draft.orderType(),
                draft.quantity(),
                draft.limitPrice(),
                ExecutionIntentDraft.PAYLOAD_SCHEMA,
                draft.payloadHash(),
                draft.clientOrderId(),
                draft.localOrderId(),
                ExecutionIntentState.CREATED,
                1L,
                null,
                null,
                null,
                null,
                null,
                NOW
        );
    }

    private static String stableExecutionId(UUID intentId) {
        return ExecutionIntentCanonicalEncoder.stableClientOrderId(intentId);
    }
}
