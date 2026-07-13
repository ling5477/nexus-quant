package com.guidinglight.nexusquant.adapter.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EndpointPolicyDecisionTest {

    @Test
    void shouldNeverTurnEndpointPolicyIntoTradingAuthorization() {
        EndpointPolicyDecision decision = new EndpointPolicyDecision(
                true,
                ExchangeCapability.PUBLIC_MARKET_DATA,
                EndpointAccessClass.PUBLIC_READ,
                EndpointGuardReason.ALLOW_PUBLIC_READ,
                true
        );

        assertFalse(decision.tradingAuthorization());
    }
}
