package com.guidinglight.nexusquant.livecontrol.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * `exact-pilot-binding.v2` 固定字段顺序、UTF-8、数值和 UTC 时间 canonical encoder。
 */
public final class ExactPilotBindingCanonicalEncoder {

    private ExactPilotBindingCanonicalEncoder() {
    }

    public static String digest(ExactPilotBinding binding) {
        return CanonicalDigestSupport.sha256(encode(binding));
    }

    public static String eventDigest(String command, String bindingDigest, String idempotencyKey) {
        ExactPilotBinding.requireExactText(command, 64, "command");
        ExactPilotBinding.requireDigest(bindingDigest, "bindingDigest");
        ExactPilotBinding.requireExactText(idempotencyKey, 128, "idempotencyKey");
        return CanonicalDigestSupport.sha256("{" +
                "\"schemaVersion\":\"live-session-command.v1\"" +
                ",\"command\":" + CanonicalDigestSupport.quote(command) +
                ",\"bindingDigest\":" + CanonicalDigestSupport.quote(bindingDigest) +
                ",\"idempotencyKey\":" + CanonicalDigestSupport.quote(idempotencyKey) + "}");
    }

    public static String encode(ExactPilotBinding value) {
        Objects.requireNonNull(value, "binding must not be null");
        var deployment = value.deployment();
        var account = value.account();
        var order = value.order();
        var observations = value.observations();
        var risk = value.riskPolicy();
        var operatorAuthority = value.operatorPilotAuthority();
        var correlation = value.correlation();
        String authority = risk != null
                ? ",\"riskLimitSetId\":" + quote(risk.riskLimitSetId().toString()) +
                ",\"riskPolicyVersion\":" + risk.riskPolicyVersion() +
                ",\"riskPolicyDigest\":" + quote(risk.riskPolicyDigest()) +
                ",\"killSwitchState\":" + quote(risk.killSwitchState())
                : ",\"authorityType\":\"OPERATOR_PILOT\"" +
                ",\"operatorPilotAuthorityId\":" + quote(operatorAuthority.authorityId().toString()) +
                ",\"operatorPilotAuthorityDigest\":" + quote(operatorAuthority.authorityDigest()) +
                ",\"operatorPilotInstrument\":" + quote(operatorAuthority.instrument()) +
                ",\"operatorPilotSide\":" + quote(operatorAuthority.side().name()) +
                ",\"operatorPilotOrderType\":" + quote(operatorAuthority.orderType().name()) +
                ",\"operatorPilotMaxNotional\":" +
                CanonicalDigestSupport.decimal(operatorAuthority.maxNotional()) +
                ",\"operatorPilotMaxPlaceCount\":" + operatorAuthority.maxPlaceCount() +
                ",\"operatorPilotMaxCancelCount\":" + operatorAuthority.maxCancelCount() +
                ",\"operatorPilotTransferAllowed\":" + operatorAuthority.transferAllowed() +
                ",\"operatorPilotWithdrawAllowed\":" + operatorAuthority.withdrawAllowed() +
                ",\"killSwitchState\":" + quote(operatorAuthority.killSwitchState());
        String schema = risk != null
                ? ExactPilotBinding.SCHEMA_VERSION : ExactPilotBinding.OPERATOR_PILOT_SCHEMA_VERSION;
        return "{" +
                "\"schemaVersion\":" + quote(schema) +
                ",\"bindingId\":" + quote(value.id().toString()) +
                ",\"sessionId\":" + quote(value.sessionId().toString()) +
                ",\"pilotScopeId\":" + quote(value.pilotScopeId().toString()) +
                ",\"observationSetId\":" + quote(value.observationSetId().toString()) +
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
                ",\"instrumentSnapshotIdentity\":" + quote(observations.instrumentSnapshotIdentity().toString()) +
                ",\"feeSnapshotIdentity\":" + quote(observations.feeSnapshotIdentity().toString()) +
                ",\"balanceSnapshotIdentity\":" + quote(observations.balanceSnapshotIdentity().toString()) +
                ",\"exchangeTimeSnapshotIdentity\":" + quote(observations.exchangeTimeSnapshotIdentity().toString()) +
                ",\"marketSnapshotIdentity\":" + quote(observations.marketSnapshotIdentity().toString()) +
                ",\"marketSnapshotDigest\":" + quote(observations.marketSnapshotDigest()) + authority +
                ",\"pilotWindowStart\":" + CanonicalDigestSupport.instant(value.pilotWindowStart()) +
                ",\"pilotWindowEnd\":" + CanonicalDigestSupport.instant(value.pilotWindowEnd()) +
                ",\"requestId\":" + quote(correlation.requestId()) +
                ",\"traceId\":" + quote(correlation.traceId()) +
                ",\"idempotencyKey\":" + quote(correlation.idempotencyKey()) +
                ",\"bindingCreatedAt\":" + CanonicalDigestSupport.instant(value.bindingCreatedAt()) +
                ",\"bindingExpiresAt\":" + CanonicalDigestSupport.instant(value.bindingExpiresAt()) + "}";
    }

    public static boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(String value) {
        return CanonicalDigestSupport.quote(value);
    }
}
