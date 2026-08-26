package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionAuthorityType;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionCommand;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionStateMachine;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotPrePlaceRecoveryRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

/**
 * LIVE control-plane 的短事务编排 owner。该 service 不调用 exchange，也不持有 credential material。
 */
@Service
public class LiveSessionControlService {

    private static final String SESSION_CREATOR_ROLE = "OPERATOR";

    private final LiveControlRepository repository;
    private final LiveControlAuthorizationPort authorization;
    private final LiveSessionStateMachine stateMachine;
    private final PilotPrePlaceRecoveryRepository recoveries;

    @Autowired
    public LiveSessionControlService(
            LiveControlRepository repository,
            LiveControlAuthorizationPort authorization,
            PilotPrePlaceRecoveryRepository recoveries
    ) {
        this(repository, authorization, new LiveSessionStateMachine(), recoveries);
    }

    public LiveSessionControlService(
            LiveControlRepository repository,
            LiveControlAuthorizationPort authorization
    ) {
        this(repository, authorization, new LiveSessionStateMachine(), null);
    }

    LiveSessionControlService(
            LiveControlRepository repository,
            LiveControlAuthorizationPort authorization,
            LiveSessionStateMachine stateMachine
    ) {
        this(repository, authorization, stateMachine, null);
    }

    LiveSessionControlService(
            LiveControlRepository repository,
            LiveControlAuthorizationPort authorization,
            LiveSessionStateMachine stateMachine,
            PilotPrePlaceRecoveryRepository recoveries
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        this.recoveries = recoveries;
    }

    @Transactional
    public RiskLimitSet createRiskLimitSet(
            AuthenticatedLiveControlActor actor,
            RiskLimitSet riskLimitSet
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(riskLimitSet, "riskLimitSet must not be null");
        if (riskLimitSet.createdBy() != actor.userId()) {
            throw new LiveControlException(
                    "RISK_LIMIT_SET_CREATOR_IDENTITY_MISMATCH",
                    "risk limit set creator must equal the authenticated actor"
            );
        }
        if (!authorization.lockAndCheckRole(actor.userId(), SESSION_CREATOR_ROLE)) {
            throw new LiveControlException(
                    "RISK_LIMIT_SET_OPERATOR_ROLE_REQUIRED",
                    "authenticated actor does not currently hold the required role"
            );
        }
        repository.createRiskLimitSet(riskLimitSet);
        return riskLimitSet;
    }

    @Transactional
    public LiveSession createSession(
            AuthenticatedLiveControlActor actor,
            LiveSession session,
            RiskLimitSet riskLimitSet,
            LiveSessionEvent createdEvent
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(riskLimitSet, "riskLimitSet must not be null");
        Objects.requireNonNull(createdEvent, "createdEvent must not be null");
        if (session.state() != LiveSessionState.APPROVAL_PENDING || session.version() != 1) {
            throw new LiveControlException("LIVE_SESSION_INVALID_INITIAL_STATE", "new session must be approval pending");
        }
        if (session.createdBy() != actor.userId() || session.ownerId() != actor.userId()) {
            throw new LiveControlException(
                    "LIVE_SESSION_CREATOR_IDENTITY_MISMATCH",
                    "session creator and owner must equal the authenticated actor"
            );
        }
        if (!authorization.lockAndCheckRole(actor.userId(), SESSION_CREATOR_ROLE)) {
            throw new LiveControlException(
                    "LIVE_SESSION_OPERATOR_ROLE_REQUIRED",
                    "authenticated actor does not currently hold the required role"
            );
        }
        if (!createdEvent.sessionId().equals(session.id())
                || createdEvent.fromState() != null
                || createdEvent.toState() != LiveSessionState.APPROVAL_PENDING
                || createdEvent.actorId() == null
                || createdEvent.actorId() != actor.userId()
                || !"CREATE".equals(createdEvent.command())
                || !"SESSION_CREATED".equals(createdEvent.reasonCode())) {
            throw new LiveControlException(
                    "LIVE_SESSION_CREATED_EVENT_MISMATCH",
                    "created event must bind the authenticated creator and initial session state"
            );
        }
        session.requireWithinRiskLimit(riskLimitSet);
        if (!session.hasCanonicalApprovalScopeHash()) {
            throw new LiveControlException(
                    "LIVE_SESSION_SCOPE_HASH_MISMATCH",
                    "session approval scope hash is not canonical"
            );
        }
        if (!repository.lockAndValidateSessionReferences(session)) {
            throw new LiveControlException(
                    "LIVE_SESSION_REFERENCE_MISMATCH",
                    "account, credential, release admission, or risk reference is inconsistent"
            );
        }
        repository.createSession(session);
        repository.appendSessionEvent(createdEvent);
        return session;
    }

    /**
     * OPERATOR_PILOT 会话只绑定显式人工 authority，不创建或借用 strategy/risk facts。
     */
    @Transactional
    public LiveSession createOperatorPilotSession(
            AuthenticatedLiveControlActor actor,
            LiveSession session,
            OperatorPilotAuthority authority,
            LiveSessionEvent createdEvent
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(authority, "authority must not be null");
        Objects.requireNonNull(createdEvent, "createdEvent must not be null");
        if (session.authorityType() != LiveSessionAuthorityType.OPERATOR_PILOT
                || session.state() != LiveSessionState.APPROVAL_PENDING || session.version() != 1
                || session.createdBy() != actor.userId() || session.ownerId() != actor.userId()
                || !session.operatorPilotAuthorityId().equals(authority.id())
                || !session.operatorPilotAuthorityDigest().equals(authority.canonicalDigest())
                || !authority.activeAt(repository.currentTime())
                || !authority.hasCanonicalDigest()
                || !authorization.lockAndCheckRole(actor.userId(), SESSION_CREATOR_ROLE)) {
            throw new LiveControlException(
                    "OPERATOR_PILOT_SESSION_AUTHORITY_REJECTED",
                    "operator pilot session authority is invalid");
        }
        authority.requireScope(
                session.ownerId(), session.exchangeAccountId(), session.credentialReference(),
                session.symbolAllowlist().getFirst(), OperatorPilotAuthority.Side.BUY,
                OperatorPilotAuthority.OrderType.LIMIT, session.capitalCap(), repository.currentTime());
        if (!createdEvent.sessionId().equals(session.id()) || createdEvent.fromState() != null
                || createdEvent.toState() != LiveSessionState.APPROVAL_PENDING
                || createdEvent.actorId() == null || createdEvent.actorId() != actor.userId()
                || !"CREATE".equals(createdEvent.command())
                || !"SESSION_CREATED".equals(createdEvent.reasonCode())
                || !session.hasCanonicalApprovalScopeHash()
                || !repository.lockAndValidateSessionReferences(session)) {
            throw new LiveControlException(
                    "OPERATOR_PILOT_SESSION_REFERENCE_MISMATCH",
                    "operator pilot session references are inconsistent");
        }
        repository.createSession(session);
        repository.appendSessionEvent(createdEvent);
        return session;
    }

    @Transactional
    public OperatorApproval decide(AuthenticatedLiveControlActor actor, OperatorApprovalCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        LiveSession current = repository.lockSession(command.sessionId())
                .orElseThrow(() -> new LiveControlException("LIVE_SESSION_NOT_FOUND", "live session was not found"));
        if (!authorization.lockAndCheckRole(actor.userId(), OperatorApproval.REQUIRED_ROLE)) {
            throw new LiveControlException(
                    "LIVE_APPROVER_ROLE_REQUIRED",
                    "authenticated actor does not currently hold the required role"
            );
        }
        if (!repository.lockAndValidateSessionReferences(current)) {
            throw new LiveControlException(
                    "APPROVAL_REFERENCE_STALE",
                    "account, credential, release admission, or risk reference changed before approval"
            );
        }
        repository.findApproval(command.approvalId()).ifPresent(existing -> {
            if (sameApproval(existing, actor, command)) {
                throw new ApprovalReplay(existing);
            }
            throw new LiveControlException("APPROVAL_ID_REUSED", "approval id was reused with different facts");
        });
        if (current.version() != command.expectedSessionVersion()
                || !current.approvalScopeHash().equals(command.expectedScopeHash())) {
            throw new LiveControlException("APPROVAL_STALE_SCOPE", "session version or scope changed before approval");
        }
        if (current.state() != LiveSessionState.APPROVAL_PENDING) {
            throw new LiveControlException("APPROVAL_STATE_CONFLICT", "session is no longer approval pending");
        }
        if (current.createdBy() == actor.userId()) {
            throw new LiveControlException("SELF_APPROVAL_FORBIDDEN", "session creator cannot approve the session");
        }
        Instant transactionTime = repository.currentTime();
        if (command.occurredAt().isAfter(transactionTime)
                || command.expiresAt().isAfter(current.executionWindowEnd())) {
            throw new LiveControlException(
                    "APPROVAL_TIME_INVALID",
                    "approval decision time is in the future or expiry exceeds the execution window"
            );
        }
        OperatorApproval approval = new OperatorApproval(
                command.approvalId(), current.id(), current.approvalScopeHash(), current.releaseDigest(),
                current.riskLimitSetDigest(), actor.userId(), OperatorApproval.REQUIRED_ROLE, command.decision(),
                command.reason(), command.occurredAt(), command.expiresAt()
        );
        LiveSessionState target = command.decision() == OperatorApproval.Decision.APPROVED
                ? stateMachine.transition(current.state(), com.guidinglight.nexusquant.livecontrol.domain.LiveSessionCommand.APPROVE)
                : stateMachine.transition(current.state(), com.guidinglight.nexusquant.livecontrol.domain.LiveSessionCommand.REJECT);
        LiveSession updated = current.recordApprovalDecision(target, command.occurredAt());
        repository.appendApproval(approval);
        if (!repository.compareAndSetSession(current, updated)) {
            throw new LiveControlException("LIVE_SESSION_VERSION_CONFLICT", "session changed concurrently");
        }
        repository.appendSessionEvent(event(
                current, target, command.decision().name(), actor.userId(), command.requestId(),
                command.traceId(), "OPERATOR_" + command.decision().name(), command.idempotencyKey(),
                command.commandPayloadHash(), command.occurredAt()
        ));
        return approval;
    }

    /**
     * 提供无异常幂等语义的审批入口；内部 sentinel 只用于退出当前事务编排，不写数据库。
     */
    @Transactional
    public OperatorApproval decideIdempotently(
            AuthenticatedLiveControlActor actor,
            OperatorApprovalCommand command
    ) {
        try {
            return decide(actor, command);
        } catch (ApprovalReplay replay) {
            return replay.approval;
        }
    }

    /**
     * 单一 authenticated OPERATOR 的最小pilot内部状态事实；仍严格消费既有状态机。
     * 本入口不访问provider、不改变kill，也不创建ExecutionIntent。
     */
    @Transactional
    public LiveSession transitionMinimalPilot(
            AuthenticatedLiveControlActor actor,
            UUID sessionId,
            LiveSessionCommand command,
            String requestId,
            String traceId,
            String idempotencyKey
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (!Set.of(
                LiveSessionCommand.APPROVE,
                LiveSessionCommand.START,
                LiveSessionCommand.ACTIVATE,
                LiveSessionCommand.STOP,
                LiveSessionCommand.BEGIN_RECONCILE,
                LiveSessionCommand.RECONCILE_PASS,
                LiveSessionCommand.RECONCILE_BLOCK,
                LiveSessionCommand.FAIL,
                LiveSessionCommand.KILL
        ).contains(command)) {
            throw new LiveControlException("MINIMAL_PILOT_TRANSITION_FORBIDDEN", "command is outside minimal pilot");
        }
        LiveSession current = repository.lockSession(sessionId)
                .orElseThrow(() -> new LiveControlException("LIVE_SESSION_NOT_FOUND", "live session was not found"));
        if (current.ownerId() != actor.userId()
                || current.authorityType() != LiveSessionAuthorityType.OPERATOR_PILOT
                || !authorization.lockAndCheckRole(actor.userId(), SESSION_CREATOR_ROLE)
                || !repository.lockAndValidateSessionReferences(current)) {
            throw new LiveControlException("MINIMAL_PILOT_OPERATOR_FORBIDDEN", "operator/session authority failed");
        }
        LiveSessionState target = stateMachine.transition(current.state(), command);
        Instant occurredAt = repository.currentTime();
        LiveSession updated = withState(current, target, occurredAt);
        if (!repository.compareAndSetSession(current, updated)) {
            throw new LiveControlException("LIVE_SESSION_VERSION_CONFLICT", "session changed concurrently");
        }
        repository.appendSessionEvent(event(
                current, target, "MINIMAL_PILOT_" + command.name(), actor.userId(),
                requestId, traceId,
                command == LiveSessionCommand.APPROVE
                        ? "MINIMAL_PILOT_INTERNAL_APPROVAL" : "MINIMAL_PILOT_" + command.name(),
                idempotencyKey,
                com.guidinglight.nexusquant.livecontrol.domain.LiveSessionApprovalScopeEncoder.digest(current),
                occurredAt
        ));
        return updated;
    }

    /**
     * 只消费V45 append-only zero-intent判定，以既有状态机关闭旧session；不复活authority/lease。
     */
    @Transactional
    public LiveSession terminalizeMinimalPilotPrePlaceRecovery(
            AuthenticatedLiveControlActor actor,
            UUID sessionId,
            UUID recoveryDecisionId,
            String requestId,
            String traceId,
            String idempotencyKey
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(recoveryDecisionId, "recoveryDecisionId must not be null");
        if (recoveries == null || !authorization.lockAndCheckRole(actor.userId(), SESSION_CREATOR_ROLE)) {
            throw new LiveControlException(
                    "PRE_PLACE_RECOVERY_OPERATOR_FORBIDDEN", "pre-place recovery authorization failed");
        }
        LiveSession current = repository.lockSession(sessionId)
                .orElseThrow(() -> new LiveControlException(
                        "LIVE_SESSION_NOT_FOUND", "live session was not found"));
        if (current.ownerId() != actor.userId()
                || current.authorityType() != LiveSessionAuthorityType.OPERATOR_PILOT
                || !recoveries.lockAndValidateSessionRecovery(current, recoveryDecisionId)) {
            throw new LiveControlException(
                    "PRE_PLACE_RECOVERY_REFERENCE_MISMATCH", "pre-place recovery references are invalid");
        }
        if (current.state() == LiveSessionState.LIVE_RECONCILED) {
            return current;
        }
        for (LiveSessionCommand command : java.util.List.of(
                LiveSessionCommand.STOP,
                LiveSessionCommand.BEGIN_RECONCILE,
                LiveSessionCommand.RECONCILE_BLOCK,
                LiveSessionCommand.RESOLVE_AND_CLOSE)) {
            LiveSessionState target = stateMachine.transition(current.state(), command);
            Instant occurredAt = repository.currentTime();
            LiveSession updated = withState(current, target, occurredAt);
            if (!repository.compareAndSetSession(current, updated)) {
                throw new LiveControlException(
                        "LIVE_SESSION_VERSION_CONFLICT", "session changed during pre-place recovery");
            }
            repository.appendSessionEvent(event(
                    current, target, "MINIMAL_PILOT_" + command.name(), actor.userId(),
                    requestId, traceId, "PRE_PLACE_ZERO_INTENT_" + command.name(),
                    idempotencyKey,
                    com.guidinglight.nexusquant.livecontrol.domain.LiveSessionApprovalScopeEncoder.digest(current),
                    occurredAt));
            current = updated;
        }
        return current;
    }

    private static LiveSession withState(LiveSession current, LiveSessionState target, Instant occurredAt) {
        return new LiveSession(
                current.id(), current.ownerId(), current.exchangeAccountId(), current.venue(),
                current.authorityType(), current.operatorPilotAuthorityId(),
                current.operatorPilotAuthorityDigest(),
                current.strategyReleaseId(), current.releaseDigest(), current.releaseAdmissionRevision(),
                current.riskLimitSetId(), current.riskLimitSetDigest(), current.credentialReference(),
                current.symbolAllowlist(), current.capitalCap(), current.executionWindowStart(),
                current.executionWindowEnd(), target, Math.addExact(current.version(), 1),
                current.approvalScopeHash(), current.nextEventSequence(), current.createdBy(),
                current.createdAt(), occurredAt);
    }

    private static LiveSessionEvent event(
            LiveSession current,
            LiveSessionState target,
            String command,
            Long actorId,
            String requestId,
            String traceId,
            String reasonCode,
            String idempotencyKey,
            String payloadHash,
            Instant occurredAt
    ) {
        return new LiveSessionEvent(
                UUID.randomUUID(), current.id(), 1, current.state(), target, command, actorId,
                requestId, traceId, reasonCode, idempotencyKey, payloadHash, "{}", occurredAt
        );
    }

    private static boolean sameApproval(
            OperatorApproval existing,
            AuthenticatedLiveControlActor actor,
            OperatorApprovalCommand command
    ) {
        return existing.id().equals(command.approvalId())
                && existing.sessionId().equals(command.sessionId())
                && existing.scopeHash().equals(command.expectedScopeHash())
                && existing.approverId() == actor.userId()
                && existing.approverRole().equals(OperatorApproval.REQUIRED_ROLE)
                && existing.decision() == command.decision()
                && existing.reason().equals(command.reason())
                && existing.approvedAt().equals(command.occurredAt())
                && existing.expiresAt().equals(command.expiresAt());
    }

    private static final class ApprovalReplay extends RuntimeException {
        private final OperatorApproval approval;

        private ApprovalReplay(OperatorApproval approval) {
            super(null, null, false, false);
            this.approval = approval;
        }
    }
}
