package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeFreshnessPolicy;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopePreflightResult;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GateY-6D 四类短事务的 infra owner；只组合 stored facts，不接 credential、provider、worker 或 exchange mutation。
 */
@Service
public class PilotScopeFactTransactionService {

    private static final String OPERATOR_ROLE = "OPERATOR";

    private final LiveSessionControlService liveSessionService;
    private final LiveControlRepository liveControlRepository;
    private final PilotScopeRepository pilotScopeRepository;
    private final LiveControlAuthorizationPort authorization;
    private final TransactionTemplate writeTransactions;
    private final TransactionTemplate preflightTransactions;
    private final PilotScopeFreshnessPolicy freshnessPolicy = new PilotScopeFreshnessPolicy();

    public PilotScopeFactTransactionService(
            LiveSessionControlService liveSessionService,
            LiveControlRepository liveControlRepository,
            PilotScopeRepository pilotScopeRepository,
            LiveControlAuthorizationPort authorization,
            PlatformTransactionManager transactionManager
    ) {
        this.liveSessionService = liveSessionService;
        this.liveControlRepository = liveControlRepository;
        this.pilotScopeRepository = pilotScopeRepository;
        this.authorization = authorization;
        this.writeTransactions = new TransactionTemplate(transactionManager);
        this.preflightTransactions = new TransactionTemplate(transactionManager);
        this.preflightTransactions.setReadOnly(true);
        this.preflightTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** 创建 session、scope 和首个完整 observation set；任一步失败由同一事务整体回滚。 */
    public PilotScopeBinding materialize(
            AuthenticatedLiveControlActor actor,
            LiveSession session,
            RiskLimitSet riskLimitSet,
            LiveSessionEvent createdEvent,
            PilotScopeBinding scope,
            PilotObservationSet observations
    ) {
        return writeTransactions.execute(status -> {
            if (!authorization.lockAndCheckRole(actor.userId(), OPERATOR_ROLE)) {
                throw new LiveControlException(
                        "LIVE_SESSION_OPERATOR_ROLE_REQUIRED",
                        "authenticated actor does not currently hold the required role"
                );
            }
            var existingSession = liveControlRepository.lockSession(session.id());
            if (existingSession.isPresent()) {
                LiveSession existing = existingSession.get();
                if (!existing.approvalScopeHash().equals(session.approvalScopeHash())
                        || existing.createdBy() != actor.userId()
                        || !liveControlRepository.lockAndValidateSessionReferences(existing)) {
                    throw new LiveControlException(
                            "PILOT_MATERIALIZATION_IDEMPOTENCY_CONFLICT",
                            "session identity is already bound to different or stale facts"
                    );
                }
                PilotScopeBinding stored = pilotScopeRepository.materialize(existing, scope);
                pilotScopeRepository.appendObservationSet(stored, observations);
                return stored;
            }
            liveSessionService.createSession(actor, session, riskLimitSet, createdEvent);
            PilotScopeBinding stored = pilotScopeRepository.materialize(session, scope);
            pilotScopeRepository.appendObservationSet(stored, observations);
            return stored;
        });
    }

    /** 独立 approval 事务；只追加 exact pilot approval，不创建 ExecutionIntent 或状态迁移。 */
    public OperatorApproval approve(AuthenticatedLiveControlActor actor, OperatorApproval approval) {
        return writeTransactions.execute(status -> {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(approval, "approval must not be null");
            if (!OperatorApproval.PILOT_SCOPE_SCHEMA.equals(approval.scopeSchemaVersion())
                    || !authorization.lockAndCheckRole(actor.userId(), OperatorApproval.REQUIRED_ROLE)) {
                throw new LiveControlException("PILOT_APPROVAL_FORBIDDEN", "pilot approval authorization failed");
            }
            var replay = liveControlRepository.findApproval(approval.id());
            if (replay.isPresent()) {
                if (replay.get().equals(approval)) {
                    return replay.get();
                }
                throw new LiveControlException("APPROVAL_ID_REUSED", "approval id was reused with different facts");
            }
            LiveSession session = liveControlRepository.lockSession(approval.sessionId()).orElseThrow(() ->
                    new LiveControlException("LIVE_SESSION_NOT_FOUND", "pilot approval session was not found"));
            PilotScopeBinding scope = pilotScopeRepository.lockBySessionId(approval.sessionId()).orElseThrow(() ->
                    new LiveControlException("PILOT_SCOPE_NOT_FOUND", "pilot approval scope was not found"));
            if (session.createdBy() == actor.userId()
                    || approval.approverId() != actor.userId()
                    || !approval.validFor(scope, session, liveControlRepository.currentTime())) {
                throw new LiveControlException("PILOT_APPROVAL_INVALID", "pilot approval facts are invalid or expired");
            }
            liveControlRepository.appendApproval(approval);
            return approval;
        });
    }

    /** REPEATABLE READ 内使用一个 DB transaction timestamp 选择并评估 exact complete set。 */
    public PilotScopePreflightResult preflight(UUID sessionId, BigDecimal requiredBalance) {
        return preflightTransactions.execute(status -> {
            var scope = pilotScopeRepository.findBySessionId(sessionId);
            var decisionAt = pilotScopeRepository.currentTransactionTime();
            if (scope.isEmpty()) {
                return new PilotScopePreflightResult(
                        false, null, null, decisionAt,
                        List.of(PilotScopePreflightResult.Violation.SCOPE_NOT_MATERIALIZED), List.of());
            }
            if (pilotScopeRepository.findValidPilotApproval(scope.get(), decisionAt).isEmpty()) {
                return new PilotScopePreflightResult(
                        false, scope.get().id(), null, decisionAt,
                        List.of(PilotScopePreflightResult.Violation.APPROVAL_MISSING_OR_EXPIRED), List.of());
            }
            var observations = pilotScopeRepository.findLatestCompleteObservationSet(scope.get().id());
            if (observations.isEmpty()) {
                return new PilotScopePreflightResult(
                        false, scope.get().id(), null, decisionAt,
                        List.of(PilotScopePreflightResult.Violation.OBSERVATION_SET_MISSING), List.of());
            }
            return freshnessPolicy.evaluate(scope.get(), observations.get(), requiredBalance, decisionAt);
        });
    }
}
