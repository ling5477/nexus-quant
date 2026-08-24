package com.guidinglight.nexusquant.livecontrol.domain;

import java.time.Instant;
import java.util.Objects;

/** exact pilot operator scope 的固定 UTF-8 / BigDecimal / UTC canonical encoder。 */
public final class ExactPilotScopeAuthorizationCanonicalEncoder {

    private ExactPilotScopeAuthorizationCanonicalEncoder() {
    }

    public static String digest(ExactPilotScopeAuthorization authorization) {
        return CanonicalDigestSupport.sha256(encode(authorization));
    }

    public static String digestCanonical(String canonicalScope) {
        return CanonicalDigestSupport.sha256(
                Objects.requireNonNull(canonicalScope, "canonicalScope must not be null"));
    }

    public static String eventDigest(
            String command,
            String scopeDigest,
            String idempotencyKey,
            Instant expiresAt
    ) {
        ExactPilotBinding.requireExactText(command, 64, "command");
        ExactPilotBinding.requireDigest(scopeDigest, "scopeDigest");
        ExactPilotBinding.requireExactText(idempotencyKey, 128, "idempotencyKey");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        return CanonicalDigestSupport.sha256("{" +
                "\"schemaVersion\":\"live-session-command.v1\"" +
                ",\"command\":" + quote(command) +
                ",\"scopeDigest\":" + quote(scopeDigest) +
                ",\"idempotencyKey\":" + quote(idempotencyKey) +
                ",\"expiresAt\":" + CanonicalDigestSupport.instant(expiresAt) + "}");
    }

    public static String encode(ExactPilotScopeAuthorization value) {
        Objects.requireNonNull(value, "authorization must not be null");
        var deployment = value.deployment();
        var account = value.account();
        var order = value.order();
        var risk = value.riskPolicy();
        var correlation = value.bindingCorrelation();
        return "{" +
                "\"schemaVersion\":" + quote(ExactPilotScopeAuthorization.SCHEMA_VERSION) +
                ",\"bindingId\":" + quote(value.bindingId().toString()) +
                ",\"sessionId\":" + quote(value.sessionId().toString()) +
                ",\"pilotScopeId\":" + quote(value.pilotScopeId().toString()) +
                ",\"sourceCommit\":" + quote(deployment.sourceCommit()) +
                ",\"releaseId\":" + quote(deployment.releaseId()) +
                ",\"manifestSha256\":" + quote(deployment.manifestSha256()) +
                ",\"serverIdentity\":" + quote(deployment.serverIdentity()) +
                ",\"runtimeProfile\":" + quote(deployment.runtimeProfile()) +
                ",\"exchange\":" + quote(account.exchange()) +
                ",\"environment\":" + quote(account.environment()) +
                ",\"ownerId\":" + account.ownerId() +
                ",\"exchangeAccountId\":" + account.exchangeAccountId() +
                ",\"credentialReferenceId\":" + account.credentialReferenceId() +
                ",\"instrumentId\":" + order.instrumentId() +
                ",\"exchangeInstrumentId\":" + quote(order.exchangeInstrumentId()) +
                ",\"side\":" + quote(order.side().name()) +
                ",\"orderType\":" + quote(order.orderType().name()) +
                ",\"price\":" + CanonicalDigestSupport.decimal(order.price()) +
                ",\"quantity\":" + CanonicalDigestSupport.decimal(order.quantity()) +
                ",\"notional\":" + CanonicalDigestSupport.decimal(order.notional()) +
                ",\"riskLimitSetId\":" + quote(risk.riskLimitSetId().toString()) +
                ",\"riskPolicyVersion\":" + risk.riskPolicyVersion() +
                ",\"riskPolicyDigest\":" + quote(risk.riskPolicyDigest()) +
                ",\"killSwitchState\":" + quote(risk.killSwitchState()) +
                ",\"pilotWindowStart\":" + CanonicalDigestSupport.instant(value.pilotWindowStart()) +
                ",\"pilotWindowEnd\":" + CanonicalDigestSupport.instant(value.pilotWindowEnd()) +
                ",\"creatorPrincipal\":" + value.creatorPrincipal() +
                ",\"approverPrincipal\":" + value.approverPrincipal() +
                ",\"requestId\":" + quote(correlation.requestId()) +
                ",\"traceId\":" + quote(correlation.traceId()) +
                ",\"idempotencyKey\":" + quote(correlation.idempotencyKey()) + "}";
    }

    private static String quote(String value) {
        return CanonicalDigestSupport.quote(value);
    }
}
