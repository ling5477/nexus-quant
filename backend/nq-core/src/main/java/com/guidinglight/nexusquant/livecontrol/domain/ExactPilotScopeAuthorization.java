package com.guidinglight.nexusquant.livecontrol.domain;

import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Operator 与独立 approver 对单次 exact pilot order scope 的 immutable authorization。
 *
 * <p>该事实只允许后续 materialize {@link ExactPilotBinding}，不表达 LIVE、PLACE 或通用交易授权。</p>
 */
public record ExactPilotScopeAuthorization(
        UUID bindingId,
        UUID sessionId,
        UUID pilotScopeId,
        ExactPilotBinding.DeploymentIdentity deployment,
        ExactPilotBinding.AccountIdentity account,
        ExactPilotBinding.OrderEnvelope order,
        ExactPilotBinding.RiskPolicyIdentity riskPolicy,
        Instant pilotWindowStart,
        Instant pilotWindowEnd,
        long creatorPrincipal,
        long approverPrincipal,
        ExactPilotBinding.Correlation bindingCorrelation,
        String scopeDigest
) {
    public static final String SCHEMA_VERSION = "exact-pilot-scope-authorization.v1";
    private static final String ZERO_DIGEST = "0".repeat(64);

    public ExactPilotScopeAuthorization {
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
        Objects.requireNonNull(deployment, "deployment must not be null");
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(riskPolicy, "riskPolicy must not be null");
        Objects.requireNonNull(pilotWindowStart, "pilotWindowStart must not be null");
        Objects.requireNonNull(pilotWindowEnd, "pilotWindowEnd must not be null");
        Objects.requireNonNull(bindingCorrelation, "bindingCorrelation must not be null");
        ExactPilotBinding.require(pilotWindowEnd.isAfter(pilotWindowStart), "pilot window must be non-empty");
        ExactPilotBinding.require(creatorPrincipal > 0 && approverPrincipal > 0,
                "creator and approver principals must be positive");
        ExactPilotBinding.require(creatorPrincipal != approverPrincipal,
                "creator and approver principals must be independent");
        ExactPilotBinding.require(creatorPrincipal == account.ownerId(),
                "creator principal must equal the exact account owner");
        ExactPilotBinding.requireDigest(scopeDigest, "scopeDigest");
    }

    public static ExactPilotScopeAuthorization approved(
            ExactPilotBinding.AuthoritativeFacts facts,
            ExactPilotBindingCommand command,
            long creatorPrincipal,
            long approverPrincipal
    ) {
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(command, "command must not be null");
        requireExactCommand(facts, command);
        ExactPilotScopeAuthorization draft = new ExactPilotScopeAuthorization(
                command.bindingId(), facts.sessionId(), facts.pilotScopeId(), facts.deployment(), facts.account(),
                facts.order(), facts.riskPolicy(), facts.pilotWindowStart(), facts.pilotWindowEnd(),
                creatorPrincipal, approverPrincipal, command.correlation(), ZERO_DIGEST
        );
        return draft.withDigest(ExactPilotScopeAuthorizationCanonicalEncoder.digest(draft));
    }

    public boolean hasCanonicalDigest() {
        return ExactPilotBindingCanonicalEncoder.constantTimeEquals(
                scopeDigest, ExactPilotScopeAuthorizationCanonicalEncoder.digest(this));
    }

    public boolean matches(
            ExactPilotBinding.AuthoritativeFacts facts,
            ExactPilotBindingCommand command,
            long expectedCreator,
            long expectedApprover
    ) {
        try {
            ExactPilotScopeAuthorization expected = approved(
                    facts, command, expectedCreator, expectedApprover);
            return scopeDigest.equals(expected.scopeDigest())
                    && ExactPilotScopeAuthorizationCanonicalEncoder.encode(this)
                    .equals(ExactPilotScopeAuthorizationCanonicalEncoder.encode(expected));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ExactPilotScopeAuthorization withDigest(String digest) {
        return new ExactPilotScopeAuthorization(
                bindingId, sessionId, pilotScopeId, deployment, account, order, riskPolicy,
                pilotWindowStart, pilotWindowEnd, creatorPrincipal, approverPrincipal,
                bindingCorrelation, digest
        );
    }

    private static void requireExactCommand(
            ExactPilotBinding.AuthoritativeFacts facts,
            ExactPilotBindingCommand command
    ) {
        ExactPilotBinding.require(command.sessionId().equals(facts.sessionId())
                        && command.pilotScopeId().equals(facts.pilotScopeId())
                        && command.observationSetId().equals(facts.observationSetId())
                        && command.order().equals(facts.order())
                        && command.pilotWindowStart().equals(facts.pilotWindowStart())
                        && command.pilotWindowEnd().equals(facts.pilotWindowEnd()),
                "operator exact scope differs from authoritative facts");
    }
}
