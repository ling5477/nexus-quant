package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.stream.Collectors;

/**
 * `approval-scope.v1` 确定性 canonical encoder。
 */
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
        String authority = value.authorityType() == LiveSessionAuthorityType.STRATEGY
                ? ",\"strategyReleaseId\":" + CanonicalDigestSupport.quote(value.strategyReleaseId()) +
                ",\"releaseArtifactDigest\":" + CanonicalDigestSupport.quote(value.releaseDigest()) +
                ",\"releaseAdmissionRevision\":" + value.releaseAdmissionRevision() +
                ",\"riskLimitSetId\":" + CanonicalDigestSupport.quote(value.riskLimitSetId().toString()) +
                ",\"riskLimitSetDigest\":" + CanonicalDigestSupport.quote(value.riskLimitSetDigest())
                : ",\"authorityType\":\"OPERATOR_PILOT\"" +
                ",\"operatorPilotAuthorityId\":" +
                CanonicalDigestSupport.quote(value.operatorPilotAuthorityId().toString()) +
                ",\"operatorPilotAuthorityDigest\":" +
                CanonicalDigestSupport.quote(value.operatorPilotAuthorityDigest());
        return "{" +
                "\"schemaVersion\":" + CanonicalDigestSupport.quote(value.approvalScopeSchemaVersion()) +
                ",\"sessionId\":" + CanonicalDigestSupport.quote(value.id().toString()) +
                ",\"ownerId\":" + value.ownerId() +
                ",\"exchangeAccountId\":" + value.exchangeAccountId() +
                ",\"venue\":" + CanonicalDigestSupport.quote(value.venue()) + authority +
                ",\"credentialReference\":" + value.credentialReference() +
                ",\"symbolAllowlist\":" + symbols +
                ",\"capitalCap\":" + CanonicalDigestSupport.decimal(value.capitalCap()) +
                ",\"executionWindowStart\":" + CanonicalDigestSupport.instant(value.executionWindowStart()) +
                ",\"executionWindowEnd\":" + CanonicalDigestSupport.instant(value.executionWindowEnd()) + "}";
    }
}
