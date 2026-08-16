package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** `pilot-scope.v1` byte-stable encoder；字段顺序由 GateY-6D 合同冻结。 */
public final class PilotScopeCanonicalEncoder {

    private PilotScopeCanonicalEncoder() {
    }

    public static String digest(LiveSession session, PilotScopeBinding scope) {
        return CanonicalDigestSupport.sha256(encode(session, scope));
    }

    public static String encode(LiveSession session, PilotScopeBinding scope) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        if (!session.id().equals(scope.sessionId())) {
            throw new IllegalArgumentException("session identity mismatch");
        }
        requireCanonicalSymbols(session.symbolAllowlist());
        String symbols = session.symbolAllowlist().stream()
                .map(CanonicalDigestSupport::quote)
                .collect(Collectors.joining(",", "[", "]"));
        return "{" +
                "\"schemaVersion\":" + CanonicalDigestSupport.quote(PilotScopeBinding.SCHEMA_VERSION) +
                ",\"sessionId\":" + CanonicalDigestSupport.quote(session.id().toString()) +
                ",\"ownerId\":" + session.ownerId() +
                ",\"exchangeAccountId\":" + session.exchangeAccountId() +
                ",\"venue\":" + CanonicalDigestSupport.quote(session.venue()) +
                ",\"strategyReleaseId\":" + CanonicalDigestSupport.quote(session.strategyReleaseId()) +
                ",\"releaseArtifactDigest\":" + CanonicalDigestSupport.quote(session.releaseDigest()) +
                ",\"releaseAdmissionRevision\":" + session.releaseAdmissionRevision() +
                ",\"riskLimitSetId\":" + CanonicalDigestSupport.quote(session.riskLimitSetId().toString()) +
                ",\"riskLimitSetDigest\":" + CanonicalDigestSupport.quote(session.riskLimitSetDigest()) +
                ",\"credentialReference\":" + session.credentialReference() +
                ",\"symbolAllowlist\":" + symbols +
                ",\"capitalCap\":" + CanonicalDigestSupport.decimal(session.capitalCap()) +
                ",\"executionWindowStart\":" + CanonicalDigestSupport.instant(session.executionWindowStart()) +
                ",\"executionWindowEnd\":" + CanonicalDigestSupport.instant(session.executionWindowEnd()) +
                ",\"instrumentMetadataDigest\":" + CanonicalDigestSupport.quote(scope.instrumentMetadataDigest()) +
                ",\"instrumentSourceIdentity\":" + CanonicalDigestSupport.quote(scope.instrumentSourceIdentity()) +
                ",\"instrumentSourceSchemaVersion\":" + CanonicalDigestSupport.quote(scope.instrumentSourceSchemaVersion()) +
                ",\"instrumentMaximumAgeMs\":" + scope.instrumentMaximumAgeMs() +
                ",\"feeScheduleDigest\":" + CanonicalDigestSupport.quote(scope.feeScheduleDigest()) +
                ",\"feeTier\":" + CanonicalDigestSupport.quote(scope.feeTier()) +
                ",\"feeEvidenceClass\":" + CanonicalDigestSupport.quote(scope.feeEvidenceClass().name()) +
                ",\"feeSourceIdentity\":" + CanonicalDigestSupport.quote(scope.feeSourceIdentity()) +
                ",\"feeSourceSchemaVersion\":" + CanonicalDigestSupport.quote(scope.feeSourceSchemaVersion()) +
                ",\"feeMaximumAgeMs\":" + scope.feeMaximumAgeMs() +
                ",\"balanceSourceIdentity\":" + CanonicalDigestSupport.quote(scope.balanceSourceIdentity()) +
                ",\"balanceSourceSchemaVersion\":" + CanonicalDigestSupport.quote(scope.balanceSourceSchemaVersion()) +
                ",\"balanceMaximumAgeMs\":" + scope.balanceMaximumAgeMs() +
                ",\"clockSourceIdentity\":" + CanonicalDigestSupport.quote(scope.clockSourceIdentity()) +
                ",\"clockSourceSchemaVersion\":" + CanonicalDigestSupport.quote(scope.clockSourceSchemaVersion()) +
                ",\"clockMaximumAgeMs\":" + scope.clockMaximumAgeMs() +
                ",\"signedTimestampSource\":" + CanonicalDigestSupport.quote(scope.signedTimestampSource()) +
                ",\"maximumToleratedSkewMs\":" + scope.maximumToleratedSkewMs() +
                ",\"endpointPolicyVersion\":" + CanonicalDigestSupport.quote(scope.endpointPolicyVersion()) +
                ",\"endpointPolicyDigest\":" + CanonicalDigestSupport.quote(scope.endpointPolicyDigest()) +
                ",\"providerContractIdentity\":" + CanonicalDigestSupport.quote(scope.providerContractIdentity()) +
                ",\"providerArtifactDigest\":" + CanonicalDigestSupport.quote(scope.providerArtifactDigest()) +
                ",\"workerIdentity\":" + CanonicalDigestSupport.quote(scope.workerIdentity()) +
                ",\"workerReleaseDigest\":" + CanonicalDigestSupport.quote(scope.workerReleaseDigest()) + "}";
    }

    public static void requireCanonicalSymbols(List<String> symbols) {
        Objects.requireNonNull(symbols, "symbols must not be null");
        if (symbols.size() < 1 || symbols.size() > 2
                || symbols.stream().anyMatch(value -> value == null || !value.matches("[A-Z0-9]{2,20}-USDT"))
                || !symbols.equals(symbols.stream().distinct().sorted().toList())) {
            throw new IllegalArgumentException("pilot scope symbols must be uppercase, sorted and unique");
        }
    }
}
