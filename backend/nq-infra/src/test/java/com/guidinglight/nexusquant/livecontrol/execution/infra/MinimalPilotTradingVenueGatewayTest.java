package com.guidinglight.nexusquant.livecontrol.execution.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MinimalPilotTradingVenueGatewayTest {

    @Test
    void truncatesReceiptTimeToCanonicalMicroseconds() {
        assertEquals(Instant.parse("2026-08-26T09:06:52.123456Z"),
                MinimalPilotTradingVenueGateway.canonicalReceiptTime(
                        Instant.parse("2026-08-26T09:06:52.123456789Z")));
    }

    @Test
    void reconcilesDurableSendStartedIntentFromQueryObservation() {
        ExecutionIntentRepository intents = mock(ExecutionIntentRepository.class);
        MinimalPilotTradingVenueGateway gateway = gateway(intents);
        UUID intentId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        ExecutionIntent sendStarted = mock(ExecutionIntent.class);
        ExecutionIntent reconciled = mock(ExecutionIntent.class);
        when(sendStarted.intentId()).thenReturn(intentId);
        when(sendStarted.state()).thenReturn(ExecutionIntentState.SEND_STARTED);
        when(sendStarted.version()).thenReturn(3L);
        when(sendStarted.claimToken()).thenReturn(claimToken);
        when(intents.find(intentId)).thenReturn(Optional.of(sendStarted));
        when(intents.appendReceiptAndTransition(
                org.mockito.ArgumentMatchers.eq(intentId),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(claimToken),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(ExecutionIntentState.RECONCILED)))
                .thenReturn(reconciled);
        SpotProviderResults.OrderObservation confirmed = new SpotProviderResults.OrderObservation(
                SpotProviderResults.OrderState.FILLED,
                "nq-query-confirmed", "okx-order-1", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, List.of(), null,
                Instant.parse("2026-08-26T09:06:52.123456789Z"));

        assertSame(reconciled, gateway.reconcileIntentObservation(intentId, confirmed));
        verify(intents).appendReceiptAndTransition(
                org.mockito.ArgumentMatchers.eq(intentId),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(claimToken),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(ExecutionIntentState.RECONCILED));
    }

    @Test
    void usesDedicatedExecutionScopeWithoutSyntheticStrategyIdentity() {
        UUID leaseId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        PlaceOrderRequest request = request(null, leaseId + "|" + intentId);

        var invocation = MinimalPilotTradingVenueGateway.requirePlaceInvocation(request);

        assertEquals(leaseId, invocation.leaseId());
        assertEquals(intentId, invocation.intentId());
    }

    @Test
    void rejectsSyntheticStrategyIdentityAsPilotExecutionScope() {
        UUID leaseId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        PlaceOrderRequest request = request(leaseId + "|" + intentId, null);

        LiveControlException failure = assertThrows(LiveControlException.class,
                () -> MinimalPilotTradingVenueGateway.requirePlaceInvocation(request));

        assertEquals("PILOT_PROVIDER_SCOPE_REQUIRED", failure.code());
    }

    @Test
    void unknownQueryDoesNotCreateFalseConfirmedReceipt() {
        ExecutionIntentRepository intents = mock(ExecutionIntentRepository.class);
        MinimalPilotTradingVenueGateway gateway = gateway(intents);
        ExecutionIntent intent = mock(ExecutionIntent.class);
        SpotProviderError error = SpotProviderError.classify(
                SpotProviderError.Category.UNKNOWN_RESULT, false);
        SpotProviderResults.OrderObservation unknown = new SpotProviderResults.OrderObservation(
                SpotProviderResults.OrderState.UNKNOWN,
                "nq-unknown-query",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                error,
                Instant.parse("2026-08-23T00:00:00Z"));

        assertSame(intent, gateway.appendQueryReceipt(intent, unknown));
        verifyNoInteractions(intents);
    }

    @Test
    void refreshesOnlyReadClockContextWhenPlaceTimeObservationIsStale() {
        Instant placeTime = Instant.parse("2026-08-26T09:00:00Z");
        Instant recoveryTime = placeTime.plusSeconds(600);
        var stale = new SpotProviderRequests.RequestContext(
                UUID.randomUUID(), "credential-reference", "trace", "request",
                new SpotProviderRequests.ClockContract(
                        SpotProviderRequests.TimestampSource.TRUSTED_UTC_CLOCK,
                        placeTime, placeTime, Duration.ZERO,
                        Duration.ofMillis(100), Duration.ofSeconds(5)));
        var observed = new SpotProviderResults.ClockObservation(
                recoveryTime.plusMillis(25), recoveryTime, Duration.ofMillis(25), null, recoveryTime);

        var refreshed = MinimalPilotTradingVenueGateway.refreshedReadOnlyContext(
                stale, observed, recoveryTime);

        assertEquals(stale.sessionId(), refreshed.sessionId());
        assertEquals(stale.referenceId(), refreshed.referenceId());
        assertEquals(recoveryTime, refreshed.clock().requestTimestamp());
        assertEquals(recoveryTime, refreshed.clock().observationAt());
        assertEquals(Duration.ofMillis(25), refreshed.clock().observedSkew());
        assertTrue(refreshed.clock().healthyAt(recoveryTime));
        assertFalse(stale.clock().healthyAt(recoveryTime));
    }

    @Test
    void rejectsFailedReadClockObservation() {
        Instant now = Instant.parse("2026-08-26T09:10:00Z");
        var base = new SpotProviderRequests.RequestContext(
                UUID.randomUUID(), "credential-reference", "trace", "request",
                new SpotProviderRequests.ClockContract(
                        SpotProviderRequests.TimestampSource.TRUSTED_UTC_CLOCK,
                        now, now, Duration.ZERO, Duration.ofMillis(100), Duration.ofSeconds(5)));
        var failed = new SpotProviderResults.ClockObservation(
                null, null, null,
                SpotProviderError.classify(SpotProviderError.Category.TRANSPORT_TIMEOUT, false), now);

        LiveControlException error = assertThrows(LiveControlException.class,
                () -> MinimalPilotTradingVenueGateway.refreshedReadOnlyContext(base, failed, now));

        assertEquals("PILOT_RECONCILIATION_CLOCK_UNAVAILABLE", error.code());
    }

    private static MinimalPilotTradingVenueGateway gateway(ExecutionIntentRepository intents) {
        return new MinimalPilotTradingVenueGateway(
                intents,
                mock(ExactPilotBindingRepository.class),
                mock(PilotExecutionLeaseRepository.class),
                mock(PilotExecutionLeaseControlPlane.class),
                mock(SpotExecutionProviderPort.class),
                mock(JdbcTemplate.class),
                Clock.systemUTC());
    }

    private static PlaceOrderRequest request(String strategyRunId, String executionScopeId) {
        return new PlaceOrderRequest(
                "request-pilot", 17L, strategyRunId, "OKX", "BTC-USDT",
                "client-pilot", "client-pilot", MinimalPilotTradingVenueGateway.SOURCE,
                OrderSide.BUY, OrderType.LIMIT, new BigDecimal("100"),
                new BigDecimal("0.01"), "GTC", "trace-pilot", "LIVE", executionScopeId);
    }
}
