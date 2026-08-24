package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;

/**
 * 重新解析 account、credential、release、risk 与 runtime immutable authority 的 server-side port。
 */
public interface PilotScopeAuthorityResolver {

    ResolvedAuthority resolve(
            AuthenticatedLiveControlActor actor,
            PilotScopeMaterializationCommand command
    );

    default ResolvedMinimalAuthority resolveMinimal(
            AuthenticatedLiveControlActor actor,
            MinimalPilotMaterializationCommand command
    ) {
        throw new UnsupportedOperationException("minimal pilot authority is not implemented");
    }

    record ResolvedMinimalAuthority(
            long ownerId,
            StrategyReleaseAdmissionState admission,
            RiskLimitSet riskLimitSet,
            ResolvedScopeBindings scopeBindings
    ) {
        public ResolvedMinimalAuthority {
            if (ownerId <= 0) throw new IllegalArgumentException("ownerId must be positive");
            java.util.Objects.requireNonNull(admission, "admission must not be null");
            java.util.Objects.requireNonNull(riskLimitSet, "riskLimitSet must not be null");
            java.util.Objects.requireNonNull(scopeBindings, "scopeBindings must not be null");
        }
    }

    record ResolvedAuthority(RiskLimitSet riskLimitSet, ResolvedScopeBindings scopeBindings) {
        public ResolvedAuthority {
            java.util.Objects.requireNonNull(riskLimitSet, "riskLimitSet must not be null");
            java.util.Objects.requireNonNull(scopeBindings, "scopeBindings must not be null");
        }
    }

    /** 只由 server-owned authority resolver 产生的 immutable pilot scope contract。 */
    record ResolvedScopeBindings(
            String instrumentMetadataDigest,
            String instrumentSourceIdentity,
            String instrumentSourceSchemaVersion,
            long instrumentMaximumAgeMs,
            String feeScheduleDigest,
            String feeTier,
            PilotScopeBinding.FeeEvidenceClass feeEvidenceClass,
            String feeSourceIdentity,
            String feeSourceSchemaVersion,
            long feeMaximumAgeMs,
            String balanceSourceIdentity,
            String balanceSourceSchemaVersion,
            long balanceMaximumAgeMs,
            String clockSourceIdentity,
            String clockSourceSchemaVersion,
            long clockMaximumAgeMs,
            String signedTimestampSource,
            long maximumToleratedSkewMs,
            String endpointPolicyVersion,
            String endpointPolicyDigest,
            String providerContractIdentity,
            String providerArtifactDigest,
            String workerIdentity,
            String workerReleaseDigest
    ) {
    }
}
