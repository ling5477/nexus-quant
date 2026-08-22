package com.guidinglight.nexusquant.livecontrol.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingDraft;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingValidation;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeAuthorizationCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlResult;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeApprovalCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationResult;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotScopeAuthorization;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExactPilotScopeControlSurfaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00.000000Z");
    private static final long CREATOR = 11L;
    private static final long APPROVER = 22L;
    private static final String DIGEST = "a".repeat(64);

    private PilotScopeControlPlane pilotScopeControlPlane;
    private ExactPilotScopeAuthorizationService authorizationService;
    private ExactPilotBindingControlPlane bindingControlPlane;
    private ExactPilotScopeControlSurfaceService service;
    private ExactPilotScopeControlCommand command;
    private PilotScopeMaterializationResult materialized;

    @BeforeEach
    void setUp() {
        pilotScopeControlPlane = mock(PilotScopeControlPlane.class);
        authorizationService = mock(ExactPilotScopeAuthorizationService.class);
        bindingControlPlane = mock(ExactPilotBindingControlPlane.class);
        service = new ExactPilotScopeControlSurfaceService(
                pilotScopeControlPlane, authorizationService, bindingControlPlane);
        command = command();
        materialized = new PilotScopeMaterializationResult(
                command.materialization().sessionId(), command.materialization().pilotScopeId(),
                UUID.randomUUID(), command.materialization().expectedPilotScopeHash());
        ExactPilotBindingCommand bindingCommand = command.binding().toCommand(
                materialized.sessionId(), materialized.pilotScopeId(), materialized.observationSetId());
        ExactPilotBinding.AuthoritativeFacts facts = facts(bindingCommand);
        ExactPilotScopeAuthorization authorization = ExactPilotScopeAuthorization.approved(
                facts, bindingCommand, CREATOR, APPROVER);
        ExactPilotBinding binding = ExactPilotBinding.verified(
                bindingCommand.bindingId(), bindingCommand.sessionId(), bindingCommand.pilotScopeId(),
                bindingCommand.observationSetId(), facts.deployment(), facts.account(), facts.order(),
                facts.observations(), facts.riskPolicy(), facts.pilotWindowStart(), facts.pilotWindowEnd(),
                bindingCommand.correlation(), NOW, bindingCommand.bindingExpiresAt());
        when(pilotScopeControlPlane.materialize(any(), any())).thenReturn(materialized);
        when(authorizationService.authorizeAndApprove(any(), any(), any(), any())).thenReturn(authorization);
        when(bindingControlPlane.create(any(), any())).thenReturn(binding);
        when(bindingControlPlane.validate(any(), any(), any())).thenReturn(
                new ExactPilotBindingValidation(
                        binding.id(), ExactPilotBinding.Lifecycle.VERIFIED, NOW, List.of(), false));
    }

    @Test
    void materializesApprovesAuthorizesAndCreatesBindingWithoutConsume() {
        ExactPilotScopeControlResult result = service.materializeAndBind(
                new AuthenticatedLiveControlActor(CREATOR), new AuthenticatedLiveControlActor(APPROVER), command);

        assertEquals(ExactPilotBinding.Lifecycle.VERIFIED, result.lifecycle());
        assertFalse(result.bindingConsumed());
        assertFalse(result.tradingAuthorized());
        assertFalse(result.exchangeMutation());
        verify(pilotScopeControlPlane).materialize(any(), any());
        verify(authorizationService).preflightPrincipals(any(), any());
        verify(pilotScopeControlPlane).approve(any(), any());
        verify(authorizationService).authorizeAndApprove(any(), any(), any(), any());
        verify(bindingControlPlane).create(any(), any());
        verify(bindingControlPlane).validate(any(), any(), any());
        verify(bindingControlPlane, never()).consume(any(), any());
    }

    @Test
    void rejectsSelfApprovalBeforeAnyMaterialization() {
        LiveControlException exception = assertThrows(LiveControlException.class,
                () -> service.materializeAndBind(
                        new AuthenticatedLiveControlActor(CREATOR),
                        new AuthenticatedLiveControlActor(CREATOR), command));

        assertEquals("EXACT_PILOT_SCOPE_SELF_APPROVAL_FORBIDDEN", exception.code());
        verify(pilotScopeControlPlane, never()).materialize(any(), any());
        verify(authorizationService, never()).preflightPrincipals(any(), any());
    }

    @Test
    void rejectsMaterializationWhoseServerHashDiffersFromOperatorInput() {
        when(pilotScopeControlPlane.materialize(any(), any())).thenReturn(
                new PilotScopeMaterializationResult(
                        command.materialization().sessionId(), command.materialization().pilotScopeId(),
                        UUID.randomUUID(), "b".repeat(64)));

        LiveControlException exception = assertThrows(LiveControlException.class,
                () -> service.materializeAndBind(
                        new AuthenticatedLiveControlActor(CREATOR),
                        new AuthenticatedLiveControlActor(APPROVER), command));

        assertEquals("EXACT_PILOT_SCOPE_MATERIALIZATION_MISMATCH", exception.code());
        verify(pilotScopeControlPlane, never()).approve(any(), any());
        verify(bindingControlPlane, never()).create(any(), any());
    }

    private static ExactPilotScopeControlCommand command() {
        UUID sessionId = UUID.randomUUID();
        UUID pilotScopeId = UUID.randomUUID();
        UUID riskId = UUID.randomUUID();
        Instant start = NOW.minusSeconds(60);
        Instant end = NOW.plusSeconds(600);
        PilotScopeMaterializationCommand.RiskSelection risk =
                new PilotScopeMaterializationCommand.RiskSelection(
                        riskId, DIGEST, 1, decimal("25"), decimal("20"), decimal("25"),
                        decimal("5"), decimal("10"), 1, 2, List.of("BTC-USDT"),
                        900, decimal("10"), decimal("10"), 1_000, 9_000);
        PilotScopeMaterializationCommand materialization = new PilotScopeMaterializationCommand(
                sessionId, pilotScopeId, 21L, 31L, "release-record", DIGEST, 1, risk,
                List.of("BTC-USDT"), decimal("25"), start, end, DIGEST,
                "pilot-idempotency", "pilot-request", "pilot-trace");
        PilotScopeApprovalCommand approval = new PilotScopeApprovalCommand(
                UUID.randomUUID(), sessionId, pilotScopeId, DIGEST, "exact scope reviewed",
                NOW.minusSeconds(1), NOW.plusSeconds(300));
        ExactPilotBindingDraft binding = new ExactPilotBindingDraft(
                UUID.randomUUID(), new ExactPilotBinding.OrderEnvelope(
                101L, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("10")), start, end,
                correlation("binding"), NOW.plusSeconds(300));
        ExactPilotScopeAuthorizationCommand exactApproval = new ExactPilotScopeAuthorizationCommand(
                correlation("creator"), correlation("approver"),
                ExactPilotScopeAuthorizationCommand.REQUIRED_REASON,
                NOW.minusSeconds(1), NOW.plusSeconds(300));
        return new ExactPilotScopeControlCommand(materialization, approval, binding, exactApproval);
    }

    private static ExactPilotBinding.AuthoritativeFacts facts(ExactPilotBindingCommand command) {
        return new ExactPilotBinding.AuthoritativeFacts(
                command.sessionId(), command.pilotScopeId(), command.observationSetId(),
                new ExactPilotBinding.DeploymentIdentity(
                        "1".repeat(40), "1".repeat(40), "c".repeat(64), "server-a",
                        ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE),
                new ExactPilotBinding.AccountIdentity("OKX", "LIVE", CREATOR, 21L, 31L),
                command.order(), new ExactPilotBinding.ObservationIdentities(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                new ExactPilotBinding.RiskPolicyIdentity(UUID.randomUUID(), 1, DIGEST, "ENGAGED"),
                command.pilotWindowStart(), command.pilotWindowEnd());
    }

    private static ExactPilotBinding.Correlation correlation(String suffix) {
        return new ExactPilotBinding.Correlation(
                "request-" + suffix, "trace-" + suffix, "idempotency-" + suffix);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }
}
