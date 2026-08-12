package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.stream.Collectors;

/** `approval-scope.v1` 确定性 canonical encoder。 */
public final class LiveSessionApprovalScopeEncoder {

    private LiveSessionApprovalScopeEncoder() {
    }

    public static String digest(LiveSession value) {
        return CanonicalDigestSupport.sha256(encode(value));
    }

    public static String encode(LiveSession value) {
        String symbols = value.symbolAllowlist().stream()
                .map(CanonicalDigestSupport::quote)
                .collect(Collectors.joining(",", "[", "]"));
        return "{" +
                "\"schemaVersion\":" + CanonicalDigestSupport.quote(LiveSession.APPROVAL_SCOPE_SCHEMA) +
                ",\"sessionId\":" + CanonicalDigestSupport.quote(value.id().toString()) +
                ",\"ownerId\":" + value.ownerId() +
                ",\"exchangeAccountId\":" + value.exchangeAccountId() +
                ",\"venue\":" + CanonicalDigestSupport.quote(value.venue()) +
                ",\"strategyReleaseId\":" + CanonicalDigestSupport.quote(value.strategyReleaseId()) +
                ",\"releaseArtifactDigest\":" + CanonicalDigestSupport.quote(value.releaseDigest()) +
                ",\"releaseAdmissionRevision\":" + value.releaseAdmissionRevision() +
                ",\"riskLimitSetId\":" + CanonicalDigestSupport.quote(value.riskLimitSetId().toString()) +
                ",\"riskLimitSetDigest\":" + CanonicalDigestSupport.quote(value.riskLimitSetDigest()) +
                ",\"credentialReference\":" + value.credentialReference() +
                ",\"symbolAllowlist\":" + symbols +
                ",\"capitalCap\":" + CanonicalDigestSupport.decimal(value.capitalCap()) +
                ",\"executionWindowStart\":" + CanonicalDigestSupport.instant(value.executionWindowStart()) +
                ",\"executionWindowEnd\":" + CanonicalDigestSupport.instant(value.executionWindowEnd()) + "}";
    }
}
