package com.guidinglight.nexusquant.livecontrol.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopePreflightResult;
import com.guidinglight.nexusquant.security.token.TokenClaims;

import java.math.BigDecimal;
import java.util.Arrays;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** GateY-6D API 只从认证 profile 解析 actor，且 preflight 保持 eligibility-only。 */
class PilotScopeControlPlaneControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-16T08:00:00Z");
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);

    @Test
    void materializationRequestShouldExposeIntentOnly() {
        Set<String> components = Arrays.stream(PilotScopeMaterializationRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        for (String forbidden : List.of(
                "observationSetId", "scopeBindings", "observations", "availableBalance", "observedSkewMs",
                "makerFeeRate", "takerFeeRate", "observationIdentity", "observationPayloadHash", "observedAt")) {
            assertFalse(components.contains(forbidden), forbidden);
        }
    }

    @Test
    void shouldRejectLegacyObservationAuthorityFieldsEvenWhenUnknownFieldsAreGloballyIgnored() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ObjectNode canonical = (ObjectNode) mapper.valueToTree(validRequest());

        for (String forbidden : List.of(
                "availableBalance", "observedSkewMs", "makerFeeRate", "observationIdentity",
                "observationPayloadHash", "observations", "scopeBindings")) {
            ObjectNode payload = canonical.deepCopy();
            payload.put(forbidden, "operator-forged");

            JsonMappingException failure = assertThrows(
                    JsonMappingException.class,
                    () -> mapper.readValue(mapper.writeValueAsBytes(payload), PilotScopeMaterializationRequest.class),
                    forbidden);

            assertTrue(rootMessage(failure).contains("unsupported pilot materialization field: " + forbidden),
                    () -> forbidden + ": " + rootMessage(failure));
        }
    }

    @Test
    void shouldRequireAuthenticationBeforeCallingControlPlane() {
        GatewayAuthFacade gateway = mock(GatewayAuthFacade.class);
        CurrentUserProfileService profiles = mock(CurrentUserProfileService.class);
        PilotScopeControlPlane controlPlane = mock(PilotScopeControlPlane.class);
        when(gateway.currentUser()).thenReturn(Optional.empty());
        var controller = new PilotScopeControlPlaneController(gateway, profiles, controlPlane);

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.preflight(UUID.randomUUID()));

        verifyNoInteractions(profiles, controlPlane);
    }

    @Test
    void shouldUseAuthenticatedProfileIdentityAndReturnOnlyStoredFactEligibility() {
        GatewayAuthFacade gateway = mock(GatewayAuthFacade.class);
        CurrentUserProfileService profiles = mock(CurrentUserProfileService.class);
        PilotScopeControlPlane controlPlane = mock(PilotScopeControlPlane.class);
        UUID sessionId = UUID.randomUUID();
        when(gateway.currentUser()).thenReturn(Optional.of(new TokenClaims(
                "sub", "operator", List.of("OPERATOR"), NOW, NOW.plusSeconds(60), "issuer", "jti")));
        when(profiles.findByUsername("operator")).thenReturn(Optional.of(new AuthUserProfile(
                11L, "operator", "hash", List.of("OPERATOR"), true)));
        when(controlPlane.preflight(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(sessionId)))
                .thenReturn(new PilotScopePreflightResult(
                        false, null, null, NOW,
                        List.of(PilotScopePreflightResult.Violation.SCOPE_NOT_MATERIALIZED), List.of()));
        var controller = new PilotScopeControlPlaneController(gateway, profiles, controlPlane);

        PilotScopePreflightResult result = controller.preflight(sessionId);

        ArgumentCaptor<AuthenticatedLiveControlActor> actor =
                ArgumentCaptor.forClass(AuthenticatedLiveControlActor.class);
        verify(controlPlane).preflight(actor.capture(), org.mockito.ArgumentMatchers.eq(sessionId));
        assertEquals(11L, actor.getValue().userId());
        assertEquals(List.of(PilotScopePreflightResult.Violation.SCOPE_NOT_MATERIALIZED), result.violations());
    }

    private static PilotScopeMaterializationRequest validRequest() {
        return new PilotScopeMaterializationRequest(
                UUID.randomUUID(), UUID.randomUUID(), 101, 202, "release-immutable-1", A, 7,
                new PilotScopeMaterializationRequest.RiskSelection(
                        UUID.randomUUID(), B, 1, new BigDecimal("1000"), new BigDecimal("100"),
                        new BigDecimal("500"), new BigDecimal("50"), new BigDecimal("100"),
                        3, 20, List.of("BTC-USDT"), 3600, new BigDecimal("20"), new BigDecimal("30"),
                        1000, 9000),
                List.of("BTC-USDT"), new BigDecimal("500"), NOW.plusSeconds(60), NOW.plusSeconds(3600), B);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
