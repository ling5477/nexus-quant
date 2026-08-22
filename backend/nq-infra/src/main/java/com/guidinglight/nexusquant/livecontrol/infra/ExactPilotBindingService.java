package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingAuthority;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingConsumption;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingConsumptionCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingValidation;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exact binding control/admission transaction owner；只读权威事实并追加 binding lifecycle event。
 */
public final class ExactPilotBindingService implements ExactPilotBindingControlPlane {

    private static final String OPERATOR_ROLE = "OPERATOR";

    private final ExactPilotBindingAuthority authority;
    private final ExactPilotBindingRepository repository;
    private final LiveControlAuthorizationPort authorization;
    private final TransactionTemplate transactions;

    public ExactPilotBindingService(
            ExactPilotBindingAuthority authority,
            ExactPilotBindingRepository repository,
            LiveControlAuthorizationPort authorization,
            PlatformTransactionManager transactionManager
    ) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public ExactPilotBinding create(
            AuthenticatedLiveControlActor actor,
            ExactPilotBindingCommand command
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        return transactions.execute(status -> {
            LiveSession session = lockAuthorizedSession(actor, command.sessionId());
            Instant decisionAt = repository.currentTransactionTime();
            var replay = repository.find(command.sessionId(), command.bindingId());
            if (replay.isPresent()) {
                requireReplayMatches(command, replay.get());
                requireVerifiedCurrentFacts(actor, replay.get(), decisionAt);
                return replay.get();
            }
            ExactPilotBinding.AuthoritativeFacts facts = authority.resolveForCreation(actor, command, decisionAt);
            requireCreationMatchesCommand(actor, command, facts);
            ExactPilotBinding binding = ExactPilotBinding.verified(
                    command.bindingId(), command.sessionId(), command.pilotScopeId(), command.observationSetId(),
                    facts.deployment(), facts.account(), facts.order(), facts.observations(), facts.riskPolicy(),
                    facts.pilotWindowStart(), facts.pilotWindowEnd(), command.correlation(), decisionAt,
                    command.bindingExpiresAt()
            );
            return repository.createOrGet(binding, session);
        });
    }

    @Override
    public ExactPilotBindingValidation validate(
            AuthenticatedLiveControlActor actor,
            UUID sessionId,
            UUID bindingId
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        return transactions.execute(status -> {
            lockAuthorizedSession(actor, sessionId);
            ExactPilotBinding binding = repository.find(sessionId, bindingId).orElseThrow(() ->
                    new LiveControlException("EXACT_PILOT_BINDING_NOT_FOUND", "exact pilot binding was not found"));
            return validateLocked(actor, binding, repository.currentTransactionTime());
        });
    }

    @Override
    public ExactPilotBindingConsumption consume(
            AuthenticatedLiveControlActor actor,
            ExactPilotBindingConsumptionCommand command
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        return transactions.execute(status -> {
            LiveSession session = lockAuthorizedSession(actor, command.sessionId());
            ExactPilotBinding binding = repository.find(command.sessionId(), command.bindingId()).orElseThrow(() ->
                    new LiveControlException("EXACT_PILOT_BINDING_NOT_FOUND", "exact pilot binding was not found"));
            if (!binding.order().equals(command.order())) {
                throw new LiveControlException(
                        "EXACT_PILOT_ATTEMPT_ORDER_MISMATCH",
                        "pilot attempt order differs from the exact binding"
                );
            }
            Instant decisionAt = repository.currentTransactionTime();
            ExactPilotBindingValidation validation = validateLocked(actor, binding, decisionAt);
            if (validation.lifecycle() != ExactPilotBinding.Lifecycle.VERIFIED) {
                throw new LiveControlException(
                        consumptionError(validation.lifecycle()),
                        "exact pilot binding cannot be consumed"
                );
            }
            return repository.consume(binding, session, command.correlation(), decisionAt);
        });
    }

    private LiveSession lockAuthorizedSession(AuthenticatedLiveControlActor actor, UUID sessionId) {
        if (!authorization.lockAndCheckRole(actor.userId(), OPERATOR_ROLE)) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_OPERATOR_ROLE_REQUIRED",
                    "authenticated actor does not currently hold the required role"
            );
        }
        LiveSession session = repository.lockSession(sessionId);
        if (session.ownerId() != actor.userId()) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_OWNER_MISMATCH",
                    "exact pilot binding session owner does not match the authenticated actor"
            );
        }
        return session;
    }

    private ExactPilotBindingValidation validateLocked(
            AuthenticatedLiveControlActor actor,
            ExactPilotBinding binding,
            Instant decisionAt
    ) {
        if (repository.isConsumed(binding.sessionId(), binding.id())) {
            return validation(binding, ExactPilotBinding.Lifecycle.CONSUMED, decisionAt,
                    ExactPilotBindingValidation.Violation.BINDING_ALREADY_CONSUMED);
        }
        if (!decisionAt.isBefore(binding.bindingExpiresAt())) {
            return validation(binding, ExactPilotBinding.Lifecycle.EXPIRED, decisionAt,
                    ExactPilotBindingValidation.Violation.BINDING_EXPIRED);
        }
        if (!binding.hasCanonicalDigest()) {
            return validation(binding, ExactPilotBinding.Lifecycle.INVALID, decisionAt,
                    ExactPilotBindingValidation.Violation.BINDING_DIGEST_MISMATCH);
        }
        try {
            requireVerifiedCurrentFacts(actor, binding, decisionAt);
            return new ExactPilotBindingValidation(
                    binding.id(), ExactPilotBinding.Lifecycle.VERIFIED, decisionAt, List.of(), false);
        } catch (RuntimeException exception) {
            return validation(binding, ExactPilotBinding.Lifecycle.INVALID, decisionAt,
                    ExactPilotBindingValidation.Violation.AUTHORITATIVE_FACT_DRIFT);
        }
    }

    private void requireVerifiedCurrentFacts(
            AuthenticatedLiveControlActor actor,
            ExactPilotBinding binding,
            Instant decisionAt
    ) {
        ExactPilotBinding.AuthoritativeFacts current = authority.resolveCurrent(actor, binding, decisionAt);
        if (!binding.matchesAuthoritativeFacts(current)) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_FACT_DRIFT",
                    "authoritative exact pilot facts changed after binding"
            );
        }
    }

    private static void requireCreationMatchesCommand(
            AuthenticatedLiveControlActor actor,
            ExactPilotBindingCommand command,
            ExactPilotBinding.AuthoritativeFacts facts
    ) {
        boolean exact = command.sessionId().equals(facts.sessionId())
                && command.pilotScopeId().equals(facts.pilotScopeId())
                && command.observationSetId().equals(facts.observationSetId())
                && command.order().equals(facts.order())
                && command.pilotWindowStart().equals(facts.pilotWindowStart())
                && command.pilotWindowEnd().equals(facts.pilotWindowEnd())
                && facts.account().ownerId() == actor.userId();
        if (!exact) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_SCOPE_EXPANSION_REJECTED",
                    "resolved authoritative facts differ from the operator exact selection"
            );
        }
    }

    private static void requireReplayMatches(ExactPilotBindingCommand command, ExactPilotBinding binding) {
        boolean exact = command.bindingId().equals(binding.id())
                && command.sessionId().equals(binding.sessionId())
                && command.pilotScopeId().equals(binding.pilotScopeId())
                && command.observationSetId().equals(binding.observationSetId())
                && command.order().equals(binding.order())
                && command.pilotWindowStart().equals(binding.pilotWindowStart())
                && command.pilotWindowEnd().equals(binding.pilotWindowEnd())
                && command.correlation().equals(binding.correlation())
                && command.bindingExpiresAt().equals(binding.bindingExpiresAt());
        if (!exact) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_IDEMPOTENCY_CONFLICT",
                    "binding identity was reused with different exact facts"
            );
        }
    }

    private static ExactPilotBindingValidation validation(
            ExactPilotBinding binding,
            ExactPilotBinding.Lifecycle lifecycle,
            Instant decisionAt,
            ExactPilotBindingValidation.Violation violation
    ) {
        return new ExactPilotBindingValidation(
                binding.id(), lifecycle, decisionAt, List.of(violation), false);
    }

    private static String consumptionError(ExactPilotBinding.Lifecycle lifecycle) {
        return switch (lifecycle) {
            case CONSUMED -> "EXACT_PILOT_BINDING_ALREADY_CONSUMED";
            case EXPIRED -> "EXACT_PILOT_BINDING_EXPIRED";
            case INVALID -> "EXACT_PILOT_BINDING_INVALID";
            case VERIFIED -> throw new IllegalArgumentException("verified binding has no consumption error");
        };
    }
}
