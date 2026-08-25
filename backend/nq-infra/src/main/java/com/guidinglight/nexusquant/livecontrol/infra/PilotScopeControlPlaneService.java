package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeApprovalCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeAuthorityResolver;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationResult;
import com.guidinglight.nexusquant.livecontrol.application.MinimalPilotMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeFreshnessPolicy;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopePreflightResult;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * GateY-6D application facade。仅构造并提交受版本约束的 prerequisite facts；没有 ExecutionIntent/provider/worker 依赖。
 */
@Service
public class PilotScopeControlPlaneService implements PilotScopeControlPlane {

    private static final String OPERATOR_ROLE = "OPERATOR";
    private static final String ZERO_DIGEST = "0".repeat(64);

    private final PilotScopeAuthorityResolver authorityResolver;
    private final PilotPrerequisiteObservationAuthority observationAuthority;
    private final PilotScopeFactTransactionService transactionService;
    private final LiveControlRepository liveControlRepository;
    private final PilotScopeRepository pilotScopeRepository;
    private final LiveControlAuthorizationPort authorization;

    public PilotScopeControlPlaneService(
            PilotScopeAuthorityResolver authorityResolver,
            PilotPrerequisiteObservationAuthority observationAuthority,
            PilotScopeFactTransactionService transactionService,
            LiveControlRepository liveControlRepository,
            PilotScopeRepository pilotScopeRepository,
            LiveControlAuthorizationPort authorization
    ) {
        this.authorityResolver = Objects.requireNonNull(authorityResolver);
        this.observationAuthority = Objects.requireNonNull(observationAuthority);
        this.transactionService = Objects.requireNonNull(transactionService);
        this.liveControlRepository = Objects.requireNonNull(liveControlRepository);
        this.pilotScopeRepository = Objects.requireNonNull(pilotScopeRepository);
        this.authorization = Objects.requireNonNull(authorization);
    }

    @Override
    public PilotScopeMaterializationResult materialize(
            AuthenticatedLiveControlActor actor,
            PilotScopeMaterializationCommand command
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (!authorization.lockAndCheckRole(actor.userId(), OPERATOR_ROLE)) {
            throw new LiveControlException("LIVE_SESSION_OPERATOR_ROLE_REQUIRED", "operator role is required");
        }
        PilotScopeAuthorityResolver.ResolvedAuthority authority = authorityResolver.resolve(actor, command);
        RiskLimitSet risk = authority.riskLimitSet();
        requireExactRisk(command.risk(), risk);
        Instant now = liveControlRepository.currentTime();
        LiveSession session = LiveSession.create(
                command.sessionId(), actor.userId(), command.exchangeAccountId(), command.strategyReleaseId(),
                command.releaseDigest(), command.releaseAdmissionRevision(), command.risk().riskLimitSetId(),
                command.risk().riskLimitSetDigest(), command.credentialReference(), command.symbolAllowlist(),
                command.capitalCap(), command.executionWindowStart(), command.executionWindowEnd(), actor.userId(), now
        );
        session.requireWithinRiskLimit(risk);
        PilotScopeBinding scope = canonicalScope(actor, command, authority.scopeBindings(), session, now);
        if (!constantTimeEquals(scope.pilotScopeHash(), command.expectedPilotScopeHash())) {
            throw new LiveControlException(
                    "PILOT_SCOPE_HASH_MISMATCH",
                    "client expected pilot scope hash does not match server reconstruction"
            );
        }
        PilotObservationSet observations = resolveTrustedObservationSet(session, scope, now);
        requireTrustedObservationSet(session, scope, observations, now);
        LiveSessionEvent createdEvent = new LiveSessionEvent(
                UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                "CREATE", actor.userId(), command.requestId(), command.traceId(), "SESSION_CREATED",
                command.idempotencyKey(), scope.pilotScopeHash(), "{}", now
        );
        PilotScopeBinding stored = transactionService.materialize(
                actor, session, risk, createdEvent, scope, observations);
        return new PilotScopeMaterializationResult(
                session.id(), stored.id(), observations.id(), stored.pilotScopeHash());
    }

    @Override
    public PilotScopeMaterializationResult materializeMinimal(
            AuthenticatedLiveControlActor actor,
            MinimalPilotMaterializationCommand command
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (!authorization.lockAndCheckRole(actor.userId(), OPERATOR_ROLE)) {
            throw new LiveControlException("LIVE_SESSION_OPERATOR_ROLE_REQUIRED", "operator role is required");
        }
        PilotScopeAuthorityResolver.ResolvedMinimalAuthority authority =
                authorityResolver.resolveMinimal(actor, command);
        RiskLimitSet risk = authority.riskLimitSet();
        var admission = authority.admission();
        PilotScopeMaterializationCommand resolved = new PilotScopeMaterializationCommand(
                command.sessionId(), command.pilotScopeId(), command.exchangeAccountId(),
                command.credentialReferenceId(), admission.publishRecordId(),
                admission.releaseArtifactDigest(), admission.admissionRevision(), selection(risk),
                List.of(command.instrument()), command.configuredPilotMaxNotional(),
                command.executionWindowStart(), command.executionWindowEnd(), ZERO_DIGEST,
                command.idempotencyKey(), command.requestId(), command.traceId());
        Instant now = liveControlRepository.currentTime();
        LiveSession session = LiveSession.create(
                resolved.sessionId(), actor.userId(), resolved.exchangeAccountId(), resolved.strategyReleaseId(),
                resolved.releaseDigest(), resolved.releaseAdmissionRevision(), risk.id(), risk.canonicalDigest(),
                resolved.credentialReference(), resolved.symbolAllowlist(), resolved.capitalCap(),
                resolved.executionWindowStart(), resolved.executionWindowEnd(), actor.userId(), now);
        session.requireWithinRiskLimit(risk);
        PilotScopeBinding scope = canonicalScope(actor, resolved, authority.scopeBindings(), session, now);
        PilotObservationSet observations = resolveTrustedObservationSet(session, scope, now);
        requireTrustedObservationSet(session, scope, observations, now);
        LiveSessionEvent createdEvent = new LiveSessionEvent(
                UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                "CREATE", actor.userId(), command.requestId(), command.traceId(), "SESSION_CREATED",
                command.idempotencyKey(), scope.pilotScopeHash(), "{}", now);
        PilotScopeBinding stored = transactionService.materialize(
                actor, session, risk, createdEvent, scope, observations);
        return new PilotScopeMaterializationResult(
                session.id(), stored.id(), observations.id(), stored.pilotScopeHash());
    }

    private static PilotScopeMaterializationCommand.RiskSelection selection(RiskLimitSet risk) {
        return new PilotScopeMaterializationCommand.RiskSelection(
                risk.id(), risk.canonicalDigest(), risk.version(), risk.capitalCap(), risk.maxOrderNotional(),
                risk.maxSymbolPositionNotional(), risk.maxDailyRealizedLoss(), risk.maxDailyTotalLoss(),
                risk.maxOpenOrders(), risk.maxIntradayOrders(), risk.symbolAllowlist(),
                risk.maxSessionDurationSeconds(), risk.spreadLimitBps(), risk.slippageLimitBps(),
                risk.maxMarketDataAgeMs(), risk.minDataCoverageBps());
    }

    private PilotObservationSet resolveTrustedObservationSet(
            LiveSession session,
            PilotScopeBinding scope,
            Instant resolvedAt
    ) {
        try {
            return observationAuthority.resolveTrustedObservationSet(session, scope, resolvedAt);
        } catch (LiveControlException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LiveControlException denied = new LiveControlException(
                    "TRUSTED_PREREQUISITE_OBSERVATION_INVALID",
                    "trusted prerequisite observation is invalid");
            denied.initCause(exception);
            throw denied;
        }
    }

    @Override
    public OperatorApproval approve(AuthenticatedLiveControlActor actor, PilotScopeApprovalCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (!authorization.lockAndCheckRole(actor.userId(), OperatorApproval.REQUIRED_ROLE)) {
            throw new LiveControlException("PILOT_APPROVAL_FORBIDDEN", "pilot approval authorization failed");
        }
        LiveSession session = liveControlRepository.findSession(command.sessionId())
                .orElseThrow(() -> new LiveControlException("LIVE_SESSION_NOT_FOUND", "live session was not found"));
        PilotScopeBinding scope = pilotScopeRepository.findBySessionId(command.sessionId())
                .orElseThrow(() -> new LiveControlException("PILOT_SCOPE_NOT_FOUND", "pilot scope was not found"));
        if (!scope.id().equals(command.pilotScopeId())
                || !constantTimeEquals(scope.pilotScopeHash(), command.expectedPilotScopeHash())) {
            throw new LiveControlException("PILOT_APPROVAL_SCOPE_MISMATCH", "approval does not bind the exact pilot scope");
        }
        OperatorApproval approval = new OperatorApproval(
                command.approvalId(), session.id(), OperatorApproval.PILOT_SCOPE_SCHEMA, scope.id(),
                scope.pilotScopeHash(), session.releaseDigest(), session.riskLimitSetDigest(), actor.userId(),
                OperatorApproval.REQUIRED_ROLE, OperatorApproval.Decision.APPROVED, command.reason(),
                command.approvedAt(), command.expiresAt()
        );
        return transactionService.approve(actor, approval);
    }

    @Override
    public PilotScopePreflightResult preflight(AuthenticatedLiveControlActor actor, UUID sessionId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (!authorization.lockAndCheckRole(actor.userId(), OPERATOR_ROLE)) {
            throw new LiveControlException("PILOT_PREFLIGHT_OPERATOR_ROLE_REQUIRED", "operator role is required");
        }
        LiveSession session = liveControlRepository.findSession(sessionId)
                .filter(value -> value.ownerId() == actor.userId())
                .orElseThrow(() -> new LiveControlException("LIVE_SESSION_NOT_FOUND", "live session was not found"));
        return transactionService.preflight(sessionId, session.capitalCap());
    }

    private static PilotScopeBinding canonicalScope(
            AuthenticatedLiveControlActor actor,
            PilotScopeMaterializationCommand command,
            PilotScopeAuthorityResolver.ResolvedScopeBindings value,
            LiveSession session,
            Instant now
    ) {
        Objects.requireNonNull(value, "scopeBindings must not be null");
        PilotScopeBinding draft = new PilotScopeBinding(
                command.pilotScopeId(), session.id(), value.instrumentMetadataDigest(),
                value.instrumentSourceIdentity(), value.instrumentSourceSchemaVersion(),
                value.instrumentMaximumAgeMs(), value.feeScheduleDigest(), value.feeTier(),
                value.feeEvidenceClass(), value.feeSourceIdentity(), value.feeSourceSchemaVersion(),
                value.feeMaximumAgeMs(), value.balanceSourceIdentity(), value.balanceSourceSchemaVersion(),
                value.balanceMaximumAgeMs(), value.clockSourceIdentity(), value.clockSourceSchemaVersion(),
                value.clockMaximumAgeMs(), value.signedTimestampSource(), value.maximumToleratedSkewMs(),
                value.endpointPolicyVersion(), value.endpointPolicyDigest(), value.providerContractIdentity(),
                value.providerArtifactDigest(), value.workerIdentity(), value.workerReleaseDigest(),
                ZERO_DIGEST, actor.userId(), now
        );
        return draft.withCanonicalHash(session);
    }

    private static void requireTrustedObservationSet(
            LiveSession session,
            PilotScopeBinding scope,
            PilotObservationSet observations,
            Instant resolvedAt
    ) {
        Objects.requireNonNull(observations, "trusted observations must not be null");
        var values = observations.observations();
        boolean exactEnvelope = values.stream().allMatch(value ->
                value.envelope().recordedAt().equals(resolvedAt)
                        && !value.envelope().observedAt().isAfter(resolvedAt)
                        && !value.envelope().observedAt().isAfter(value.envelope().recordedAt())
                        && value.envelope().recorderIdentity().equals(scope.workerIdentity()));
        boolean exactSources = observations.instrumentMetadata().envelope().sourceIdentity()
                        .equals(scope.instrumentSourceIdentity())
                && observations.instrumentMetadata().envelope().sourceSchemaVersion()
                        .equals(scope.instrumentSourceSchemaVersion())
                && observations.feeSchedule().envelope().sourceIdentity().equals(scope.feeSourceIdentity())
                && observations.feeSchedule().envelope().sourceSchemaVersion().equals(scope.feeSourceSchemaVersion())
                && observations.balanceSnapshot().envelope().sourceIdentity().equals(scope.balanceSourceIdentity())
                && observations.balanceSnapshot().envelope().sourceSchemaVersion()
                        .equals(scope.balanceSourceSchemaVersion())
                && observations.clockSync().envelope().sourceIdentity().equals(scope.clockSourceIdentity())
                && observations.clockSync().envelope().sourceSchemaVersion().equals(scope.clockSourceSchemaVersion());
        boolean exactSymbols = observations.instrumentMetadata().items().stream()
                .map(item -> item.symbol()).toList().equals(session.symbolAllowlist());
        PilotScopePreflightResult validation = new PilotScopeFreshnessPolicy()
                .evaluate(scope, observations, BigDecimal.ZERO, resolvedAt);
        if (!exactEnvelope || !exactSources || !exactSymbols || !validation.eligible()) {
            throw new LiveControlException(
                    "TRUSTED_PREREQUISITE_OBSERVATION_INVALID",
                    "trusted prerequisite observation does not match immutable pilot scope"
            );
        }
    }

    private static void requireExactRisk(PilotScopeMaterializationCommand.RiskSelection supplied, RiskLimitSet stored) {
        Objects.requireNonNull(supplied, "risk selection must not be null");
        boolean exact = stored.id().equals(supplied.riskLimitSetId())
                && constantTimeEquals(stored.canonicalDigest(), supplied.riskLimitSetDigest())
                && stored.version() == supplied.version()
                && moneyEquals(stored.capitalCap(), supplied.capitalCap())
                && moneyEquals(stored.maxOrderNotional(), supplied.maxOrderNotional())
                && moneyEquals(stored.maxSymbolPositionNotional(), supplied.maxSymbolPositionNotional())
                && moneyEquals(stored.maxDailyRealizedLoss(), supplied.maxDailyRealizedLoss())
                && moneyEquals(stored.maxDailyTotalLoss(), supplied.maxDailyTotalLoss())
                && stored.maxOpenOrders() == supplied.maxOpenOrders()
                && stored.maxIntradayOrders() == supplied.maxIntradayOrders()
                && stored.symbolAllowlist().equals(new RiskLimitSet(
                        stored.id(), supplied.version(), supplied.capitalCap(), supplied.maxOrderNotional(),
                        supplied.maxSymbolPositionNotional(), supplied.maxDailyRealizedLoss(),
                        supplied.maxDailyTotalLoss(), supplied.maxOpenOrders(), supplied.maxIntradayOrders(),
                        supplied.symbolAllowlist(), supplied.maxSessionDurationSeconds(), supplied.spreadLimitBps(),
                        supplied.slippageLimitBps(), supplied.maxMarketDataAgeMs(), supplied.minDataCoverageBps(),
                        stored.createdBy(), stored.createdAt()).symbolAllowlist())
                && stored.maxSessionDurationSeconds() == supplied.maxSessionDurationSeconds()
                && moneyEquals(stored.spreadLimitBps(), supplied.spreadLimitBps())
                && moneyEquals(stored.slippageLimitBps(), supplied.slippageLimitBps())
                && stored.maxMarketDataAgeMs() == supplied.maxMarketDataAgeMs()
                && stored.minDataCoverageBps() == supplied.minDataCoverageBps();
        if (!exact) {
            throw new LiveControlException("PILOT_RISK_REFERENCE_MISMATCH", "supplied risk facts differ from stored SoR");
        }
    }

    private static boolean moneyEquals(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
