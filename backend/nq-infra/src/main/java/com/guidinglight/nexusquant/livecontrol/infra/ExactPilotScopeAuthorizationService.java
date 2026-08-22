package com.guidinglight.nexusquant.livecontrol.infra;

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

import java.time.Instant;
import java.util.Objects;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Exact operator scope creator/approver separation 与 V39 event persistence transaction owner。 */
public final class ExactPilotScopeAuthorizationService {

    private static final String OPERATOR_ROLE = "OPERATOR";

    private final ExactPilotBindingAuthority authority;
    private final ExactPilotBindingRepository bindingRepository;
    private final ExactPilotScopeAuthorizationRepository authorizationRepository;
    private final LiveControlAuthorizationPort roleAuthorization;
    private final TransactionTemplate transactions;

    public ExactPilotScopeAuthorizationService(
            ExactPilotBindingAuthority authority,
            ExactPilotBindingRepository bindingRepository,
            ExactPilotScopeAuthorizationRepository authorizationRepository,
            LiveControlAuthorizationPort roleAuthorization,
            PlatformTransactionManager transactionManager
    ) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository must not be null");
        this.authorizationRepository = Objects.requireNonNull(
                authorizationRepository, "authorizationRepository must not be null");
        this.roleAuthorization = Objects.requireNonNull(
                roleAuthorization, "roleAuthorization must not be null");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public ExactPilotScopeAuthorization authorizeAndApprove(
            AuthenticatedLiveControlActor creator,
            AuthenticatedLiveControlActor approver,
            ExactPilotBindingCommand bindingCommand,
            ExactPilotScopeAuthorizationCommand authorizationCommand
    ) {
        Objects.requireNonNull(creator, "creator must not be null");
        Objects.requireNonNull(approver, "approver must not be null");
        Objects.requireNonNull(bindingCommand, "bindingCommand must not be null");
        Objects.requireNonNull(authorizationCommand, "authorizationCommand must not be null");
        return transactions.execute(status -> {
            requireIndependentRoles(creator, approver);
            LiveSession session = bindingRepository.lockSession(bindingCommand.sessionId());
            if (session.ownerId() != creator.userId()) {
                throw rejected("creator principal does not own the exact pilot session");
            }
            Instant decisionAt = bindingRepository.currentTransactionTime();
            if (authorizationCommand.approvedAt().isAfter(decisionAt)
                    || !authorizationCommand.expiresAt().isAfter(decisionAt)
                    || authorizationCommand.expiresAt().isAfter(bindingCommand.bindingExpiresAt())
                    || authorizationCommand.expiresAt().isAfter(bindingCommand.pilotWindowEnd())) {
                throw rejected("exact scope approval time is invalid");
            }
            if (bindingRepository.find(bindingCommand.sessionId(), bindingCommand.bindingId()).isPresent()
                    || bindingRepository.isConsumed(bindingCommand.sessionId(), bindingCommand.bindingId())) {
                throw rejected("exact binding already exists or was consumed");
            }
            ExactPilotBinding.AuthoritativeFacts facts = authority.resolveForCreation(
                    creator, bindingCommand, decisionAt);
            ExactPilotScopeAuthorization authorization = ExactPilotScopeAuthorization.approved(
                    facts, bindingCommand, creator.userId(), approver.userId());
            return authorizationRepository.recordApproved(
                    authorization, session, authorizationCommand.creatorCorrelation(),
                    authorizationCommand.approverCorrelation(), authorizationCommand.approvedAt(),
                    authorizationCommand.expiresAt());
        });
    }

    /** 在任何 trusted provider collection 前验证独立主体与当前角色。 */
    public void preflightPrincipals(
            AuthenticatedLiveControlActor creator,
            AuthenticatedLiveControlActor approver
    ) {
        Objects.requireNonNull(creator, "creator must not be null");
        Objects.requireNonNull(approver, "approver must not be null");
        transactions.executeWithoutResult(status -> requireIndependentRoles(creator, approver));
    }

    private void requireIndependentRoles(
            AuthenticatedLiveControlActor creator,
            AuthenticatedLiveControlActor approver
    ) {
        if (creator.userId() == approver.userId()
                || !roleAuthorization.lockAndCheckRole(creator.userId(), OPERATOR_ROLE)
                || !roleAuthorization.lockAndCheckRole(approver.userId(), OperatorApproval.REQUIRED_ROLE)) {
            throw rejected("creator and independent approver roles are required");
        }
    }

    private static LiveControlException rejected(String message) {
        return new LiveControlException("EXACT_PILOT_SCOPE_AUTHORIZATION_REJECTED", message);
    }
}
