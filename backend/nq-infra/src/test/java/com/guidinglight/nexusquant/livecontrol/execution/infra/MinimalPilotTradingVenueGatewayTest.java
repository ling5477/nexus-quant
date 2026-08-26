package com.guidinglight.nexusquant.livecontrol.execution.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MinimalPilotTradingVenueGatewayTest {

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
        MinimalPilotTradingVenueGateway gateway = new MinimalPilotTradingVenueGateway(
                intents,
                mock(ExactPilotBindingRepository.class),
                mock(PilotExecutionLeaseRepository.class),
                mock(PilotExecutionLeaseControlPlane.class),
                mock(SpotExecutionProviderPort.class),
                mock(JdbcTemplate.class),
                Clock.systemUTC());
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

    private static PlaceOrderRequest request(String strategyRunId, String executionScopeId) {
        return new PlaceOrderRequest(
                "request-pilot", 17L, strategyRunId, "OKX", "BTC-USDT",
                "client-pilot", "client-pilot", MinimalPilotTradingVenueGateway.SOURCE,
                OrderSide.BUY, OrderType.LIMIT, new BigDecimal("100"),
                new BigDecimal("0.01"), "GTC", "trace-pilot", executionScopeId);
    }
}
