package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.EndpointAccessClass;
import com.guidinglight.nexusquant.adapter.api.model.EndpointGuardReason;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxSpotCapabilityMatrixTest {

    private final OkxSpotCapabilityMatrix matrix = new OkxSpotCapabilityMatrix();

    @Test
    void shouldKeepExistingPublicMarketDataContractSeparateFromTradingAuthorization() {
        OkxSpotCapabilityDefinition definition = matrix.definitionFor(ExchangeCapability.PUBLIC_MARKET_DATA);

        assertEquals(EndpointAccessClass.PUBLIC_READ, definition.endpointAccessClass());
        assertTrue(definition.implemented());
        assertFalse(definition.runtimeEnabled());
        assertFalse(definition.credentialRequired());
        assertTrue(definition.networkRequired());
        assertFalse(definition.tradingAuthorization());
        assertEquals(EndpointGuardReason.ALLOW_PUBLIC_READ, definition.reasonCode());
    }

    @Test
    void shouldKeepEveryPrivateReadCapabilityContractOnlyAndRuntimeDisabled() {
        Set<ExchangeCapability> privateReadCapabilities = Set.of(
                ExchangeCapability.PRIVATE_ACCOUNT_CONFIGURATION_READ,
                ExchangeCapability.PRIVATE_ACCOUNT_BALANCE_READ,
                ExchangeCapability.PRIVATE_PERMISSION_READ
        );

        for (ExchangeCapability capability : privateReadCapabilities) {
            OkxSpotCapabilityDefinition definition = matrix.definitionFor(capability);
            assertEquals(EndpointAccessClass.PRIVATE_READ_ONLY, definition.endpointAccessClass());
            assertFalse(definition.implemented());
            assertFalse(definition.runtimeEnabled());
            assertTrue(definition.credentialRequired());
            assertTrue(definition.networkRequired());
            assertFalse(definition.tradingAuthorization());
            assertEquals(EndpointGuardReason.DENY_PRIVATE_RUNTIME_DISABLED, definition.reasonCode());
        }
    }

    @Test
    void shouldKeepMutatingAndFundsCapabilitiesDisabledWithoutTradingAuthorization() {
        for (ExchangeCapability capability : Set.of(
                ExchangeCapability.ORDER_SUBMISSION,
                ExchangeCapability.ORDER_CANCEL,
                ExchangeCapability.TRANSFER,
                ExchangeCapability.WITHDRAW
        )) {
            OkxSpotCapabilityDefinition definition = matrix.definitionFor(capability);
            assertFalse(definition.implemented());
            assertFalse(definition.runtimeEnabled());
            assertTrue(definition.credentialRequired());
            assertTrue(definition.networkRequired());
            assertFalse(definition.tradingAuthorization());
        }

        assertEquals(
                EndpointAccessClass.PRIVATE_MUTATING,
                matrix.definitionFor(ExchangeCapability.ORDER_SUBMISSION).endpointAccessClass()
        );
        assertEquals(
                EndpointAccessClass.FUNDS_MOVEMENT,
                matrix.definitionFor(ExchangeCapability.TRANSFER).endpointAccessClass()
        );
    }

    @Test
    void shouldFailClosedForNullAndUnknownCapabilitiesWithoutCredentialMaterial() {
        OkxSpotCapabilityDefinition unknown = matrix.definitionFor(null);

        assertEquals(ExchangeCapability.UNKNOWN, unknown.capability());
        assertEquals(EndpointAccessClass.UNKNOWN, unknown.endpointAccessClass());
        assertFalse(unknown.implemented());
        assertFalse(unknown.runtimeEnabled());
        assertFalse(unknown.credentialRequired());
        assertFalse(unknown.networkRequired());
        assertFalse(unknown.tradingAuthorization());
        assertEquals(EndpointGuardReason.DENY_UNKNOWN_ENDPOINT, unknown.reasonCode());

        for (var component : OkxSpotCapabilityDefinition.class.getRecordComponents()) {
            assertFalse(String.class.equals(component.getType()), "capability matrix must not contain raw credential material");
        }
    }

    @Test
    void shouldKeepEveryMatrixRowOutOfTradingAuthorization() {
        assertEquals(ExchangeCapability.values().length, matrix.definitions().size());
        assertTrue(matrix.definitions().stream().noneMatch(OkxSpotCapabilityDefinition::tradingAuthorization));
    }
}
