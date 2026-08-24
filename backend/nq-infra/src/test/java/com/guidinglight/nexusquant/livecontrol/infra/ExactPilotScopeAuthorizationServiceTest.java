package com.guidinglight.nexusquant.livecontrol.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingAuthority;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeAuthorizationCommand;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotScopeAuthorization;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotScopeAuthorizationRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ExactPilotScopeAuthorizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00.000000Z");
    private static final long CREATOR = 11L;
    private static final long APPROVER = 22L;

    private ExactPilotBindingAuthority authority;
    private ExactPilotBindingRepository bindingRepository;
    private ExactPilotScopeAuthorizationRepository authorizationRepository;
    private LiveControlAuthorizationPort roles;
    private ExactPilotScopeAuthorizationService service;
    private ExactPilotBindingCommand bindingCommand;
    private ExactPilotBinding.AuthoritativeFacts facts;

    @BeforeEach
    void setUp() {
        authority = mock(ExactPilotBindingAuthority.class);
        bindingRepository = mock(ExactPilotBindingRepository.class);
        authorizationRepository = mock(ExactPilotScopeAuthorizationRepository.class);
        roles = mock(LiveControlAuthorizationPort.class);
        facts = facts();
        bindingCommand = command(facts);
        LiveSession session = LiveSession.create(
                facts.sessionId(), CREATOR, facts.account().exchangeAccountId(), "release-record",
                "c".repeat(64), 1, facts.riskPolicy().riskLimitSetId(), facts.riskPolicy().riskPolicyDigest(),
                facts.account().credentialReferenceId(), List.of("BTC-USDT"), decimal("25"),
                facts.pilotWindowStart(), facts.pilotWindowEnd(), CREATOR, NOW.minusSeconds(60));
        when(roles.lockAndCheckRole(CREATOR, "OPERATOR")).thenReturn(true);
        when(roles.lockAndCheckRole(APPROVER, OperatorApproval.REQUIRED_ROLE)).thenReturn(true);
        when(bindingRepository.lockSession(facts.sessionId())).thenReturn(session);
        when(bindingRepository.currentTransactionTime()).thenReturn(NOW);
        when(bindingRepository.find(facts.sessionId(), bindingCommand.bindingId())).thenReturn(Optional.empty());
        when(bindingRepository.isConsumed(facts.sessionId(), bindingCommand.bindingId())).thenReturn(false);
        when(authority.resolveForCreation(any(), any(), any())).thenReturn(facts);
        when(authorizationRepository.recordApproved(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new ExactPilotScopeAuthorizationService(
                authority, bindingRepository, authorizationRepository, roles, new NoopTransactions());
    }

    @Test
    void recordsExactScopeWithIndependentCurrentRoles() {
        ExactPilotScopeAuthorization authorization = service.authorizeAndApprove(
                new AuthenticatedLiveControlActor(CREATOR), new AuthenticatedLiveControlActor(APPROVER),
                bindingCommand, approval(NOW.minusSeconds(1), NOW.plusSeconds(120)));

        assertTrue(authorization.hasCanonicalDigest());
        assertEquals(CREATOR, authorization.creatorPrincipal());
        assertEquals(APPROVER, authorization.approverPrincipal());
        verify(roles).lockAndCheckRole(CREATOR, "OPERATOR");
        verify(roles).lockAndCheckRole(APPROVER, OperatorApproval.REQUIRED_ROLE);
        verify(authorizationRepository).recordApproved(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsSelfApprovalBeforeAuthorityOrPersistence() {
        LiveControlException exception = assertThrows(LiveControlException.class,
                () -> service.authorizeAndApprove(
                        new AuthenticatedLiveControlActor(CREATOR),
                        new AuthenticatedLiveControlActor(CREATOR), bindingCommand,
                        approval(NOW.minusSeconds(1), NOW.plusSeconds(120))));

        assertEquals("EXACT_PILOT_SCOPE_AUTHORIZATION_REJECTED", exception.code());
        verify(authority, never()).resolveForCreation(any(), any(), any());
        verify(authorizationRepository, never()).recordApproved(any(), any(), any(), any(), any(), any());
    }

    @Test
    void preflightRejectsMissingApproverRoleBeforeTrustedCollection() {
        when(roles.lockAndCheckRole(APPROVER, OperatorApproval.REQUIRED_ROLE)).thenReturn(false);

        LiveControlException exception = assertThrows(LiveControlException.class,
                () -> service.preflightPrincipals(
                        new AuthenticatedLiveControlActor(CREATOR),
                        new AuthenticatedLiveControlActor(APPROVER)));

        assertEquals("EXACT_PILOT_SCOPE_AUTHORIZATION_REJECTED", exception.code());
        verify(authority, never()).resolveForCreation(any(), any(), any());
    }

    @Test
    void rejectsFutureExpiredOrOverlongApproval() {
        assertRejected(approval(NOW.plusSeconds(1), NOW.plusSeconds(120)));
        assertRejected(approval(NOW.minusSeconds(10), NOW.minusSeconds(1)));
        assertRejected(approval(NOW.minusSeconds(1), bindingCommand.bindingExpiresAt().plusSeconds(1)));
    }

    @Test
    void rejectsAuthorizationAfterBindingAlreadyExists() {
        when(bindingRepository.find(facts.sessionId(), bindingCommand.bindingId()))
                .thenReturn(Optional.of(mock(ExactPilotBinding.class)));

        assertRejected(approval(NOW.minusSeconds(1), NOW.plusSeconds(120)));
        verify(authority, never()).resolveForCreation(any(), any(), any());
    }

    private void assertRejected(ExactPilotScopeAuthorizationCommand command) {
        LiveControlException exception = assertThrows(LiveControlException.class,
                () -> service.authorizeAndApprove(
                        new AuthenticatedLiveControlActor(CREATOR),
                        new AuthenticatedLiveControlActor(APPROVER), bindingCommand, command));
        assertEquals("EXACT_PILOT_SCOPE_AUTHORIZATION_REJECTED", exception.code());
    }

    private static ExactPilotScopeAuthorizationCommand approval(Instant approvedAt, Instant expiresAt) {
        return new ExactPilotScopeAuthorizationCommand(
                correlation("creator"), correlation("approver"),
                ExactPilotScopeAuthorizationCommand.REQUIRED_REASON, approvedAt, expiresAt);
    }

    private static ExactPilotBinding.AuthoritativeFacts facts() {
        ExactPilotBinding.OrderEnvelope order = new ExactPilotBinding.OrderEnvelope(
                101L, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("10"));
        return new ExactPilotBinding.AuthoritativeFacts(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ExactPilotBinding.DeploymentIdentity(
                        "1".repeat(40), "1".repeat(40), "a".repeat(64), "server-a",
                        ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE),
                new ExactPilotBinding.AccountIdentity("OKX", "LIVE", CREATOR, 21L, 31L), order,
                new ExactPilotBinding.ObservationIdentities(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                new ExactPilotBinding.RiskPolicyIdentity(
                        UUID.randomUUID(), 1, "b".repeat(64), "ENGAGED"),
                NOW.minusSeconds(60), NOW.plusSeconds(600));
    }

    private static ExactPilotBindingCommand command(ExactPilotBinding.AuthoritativeFacts facts) {
        return new ExactPilotBindingCommand(
                UUID.randomUUID(), facts.sessionId(), facts.pilotScopeId(), facts.observationSetId(),
                facts.order(), facts.pilotWindowStart(), facts.pilotWindowEnd(), correlation("binding"),
                NOW.plusSeconds(300));
    }

    private static ExactPilotBinding.Correlation correlation(String suffix) {
        return new ExactPilotBinding.Correlation(
                "request-" + suffix, "trace-" + suffix, "idempotency-" + suffix);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }

    private static final class NoopTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // No resource is used by this deterministic unit test.
        }

        @Override
        public void rollback(TransactionStatus status) {
            // No resource is used by this deterministic unit test.
        }
    }
}
