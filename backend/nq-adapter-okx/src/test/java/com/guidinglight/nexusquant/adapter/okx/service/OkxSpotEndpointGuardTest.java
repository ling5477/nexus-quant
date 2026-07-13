package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.EndpointAccessClass;
import com.guidinglight.nexusquant.adapter.api.model.EndpointGuardReason;
import com.guidinglight.nexusquant.adapter.api.model.EndpointPolicyDecision;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxSpotEndpointGuardTest {

    private final OkxSpotEndpointGuard guard = new OkxSpotEndpointGuard();

    @Test
    void shouldAllowKnownPublicReadWithoutGrantingTradingAuthorization() {
        EndpointPolicyDecision decision = guard.evaluate(
                ExchangeCapability.PUBLIC_MARKET_DATA,
                "GET",
                "/api/v5/public/instruments?instType=SPOT"
        );

        assertTrue(decision.allowed());
        assertEquals(EndpointAccessClass.PUBLIC_READ, decision.endpointAccessClass());
        assertEquals(EndpointGuardReason.ALLOW_PUBLIC_READ, decision.reason());
        assertFalse(decision.tradingAuthorization());
    }

    @Test
    void shouldKeepPrivateReadsRuntimeDisabledEvenForGetRequests() {
        for (ExchangeCapability capability : List.of(
                ExchangeCapability.PRIVATE_ACCOUNT_CONFIGURATION_READ,
                ExchangeCapability.PRIVATE_ACCOUNT_BALANCE_READ,
                ExchangeCapability.PRIVATE_PERMISSION_READ
        )) {
            EndpointPolicyDecision decision = guard.evaluate(capability, "GET", "/api/v5/account/config");
            assertFalse(decision.allowed());
            assertEquals(EndpointAccessClass.PRIVATE_READ_ONLY, decision.endpointAccessClass());
            assertEquals(EndpointGuardReason.DENY_PRIVATE_RUNTIME_DISABLED, decision.reason());
            assertFalse(decision.tradingAuthorization());
        }
    }

    @Test
    void shouldRejectEveryPrivateMutatingMethodBeforeAnyTransport() {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            EndpointPolicyDecision decision = guard.evaluate(
                    ExchangeCapability.ORDER_SUBMISSION,
                    method,
                    "/api/v5/trade/order"
            );
            assertFalse(decision.allowed());
            assertEquals(EndpointGuardReason.DENY_MUTATING_ENDPOINT, decision.reason());
        }

        EndpointPolicyDecision cancel = guard.evaluate(
                ExchangeCapability.ORDER_CANCEL,
                "POST",
                "/api/v5/trade/cancel-order"
        );
        assertFalse(cancel.allowed());
        assertEquals(EndpointGuardReason.DENY_MUTATING_ENDPOINT, cancel.reason());
    }

    @Test
    void shouldPermanentlyRejectFundsMovement() {
        for (ExchangeCapability capability : List.of(ExchangeCapability.TRANSFER, ExchangeCapability.WITHDRAW)) {
            EndpointPolicyDecision decision = guard.evaluate(capability, "POST", "/api/v5/asset/operation");
            assertFalse(decision.allowed());
            assertEquals(EndpointAccessClass.FUNDS_MOVEMENT, decision.endpointAccessClass());
            assertEquals(EndpointGuardReason.DENY_FUNDS_MOVEMENT, decision.reason());
        }
    }

    @Test
    void shouldFailClosedForUnknownBlankAndInvalidReferences() {
        for (String endpoint : Arrays.asList(null, "", "   ", "https://example.invalid/private", "/api/v5/%70ublic/instruments")) {
            EndpointPolicyDecision decision = guard.evaluate(ExchangeCapability.PUBLIC_MARKET_DATA, "GET", endpoint);
            assertFalse(decision.allowed());
            assertEquals(EndpointGuardReason.DENY_UNKNOWN_ENDPOINT, decision.reason());
        }

        EndpointPolicyDecision unknownCapability = guard.evaluate(null, "GET", "/api/v5/public/instruments");
        assertFalse(unknownCapability.allowed());
        assertEquals(ExchangeCapability.UNKNOWN, unknownCapability.capability());
        assertEquals(EndpointGuardReason.DENY_UNKNOWN_ENDPOINT, unknownCapability.reason());
    }

    @Test
    void shouldNotAllowQueryCaseOrPathNormalizationToBypassClassification() {
        EndpointPolicyDecision canonicalPublic = guard.evaluate(
                ExchangeCapability.PUBLIC_MARKET_DATA,
                "GET",
                "/api/v5/public/instruments?instType=SPOT"
        );
        EndpointPolicyDecision normalizedPublic = guard.evaluate(
                ExchangeCapability.PUBLIC_MARKET_DATA,
                "get",
                "/api//V5//PUBLIC//instruments?path=%2Fapi%2Fv5%2Faccount%2Fconfig"
        );
        assertEquals(canonicalPublic, normalizedPublic);

        for (String privateVariant : List.of(
                "/api//v5//account//config",
                "/api/v5/public/instruments/../account/config",
                "/api/v5/PRIVATE/account/config"
        )) {
            EndpointPolicyDecision decision = guard.evaluate(
                    ExchangeCapability.PUBLIC_MARKET_DATA,
                    "GET",
                    privateVariant
            );
            assertFalse(decision.allowed());
            assertEquals(EndpointGuardReason.DENY_UNKNOWN_ENDPOINT, decision.reason());
        }
    }

    @Test
    void shouldRejectUnregisteredSymbolicOperationAndKeepGuardFreeOfHttpDependencies() {
        EndpointPolicyDecision decision = guard.evaluate(
                ExchangeCapability.ORDER_PREVIEW_LOCAL,
                "GET",
                "/api/v5/public/instruments"
        );
        assertFalse(decision.allowed());
        assertEquals(EndpointGuardReason.DENY_UNKNOWN_ENDPOINT, decision.reason());

        assertTrue(Arrays.stream(OkxSpotEndpointGuard.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .noneMatch(type -> type.contains("Http") || type.contains("Credential")));
    }

    @Test
    void gateWProfileShouldNotRegisterHistoricalAdapterThatConstructsAnHttpClient() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("gatew");
            context.scan("com.guidinglight.nexusquant.adapter.okx.service");
            context.refresh();

            assertTrue(context.getBeansOfType(OkxHistoricalKlineAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxWsSmokeRunner.class).isEmpty());
        }
    }
}
