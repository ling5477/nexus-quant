package com.guidinglight.nexusquant.livecontrol.deployment;

import java.util.Objects;
import java.util.Set;

/**
 * Future worker deployment admission 的不可变、非敏感证据。
 */
public record WorkerDeploymentEvidence(
        ReleaseEvidence release,
        ArtifactEvidence artifact,
        WorkerPackageEvidence workerPackage,
        ProcessEvidence process,
        SessionBindingEvidence sessionBinding,
        EndpointEvidence endpoints,
        ScopedCredentialReference credential,
        KillSwitchPropagationEnvelope killSwitchEnvelope
) {
    public WorkerDeploymentEvidence {
        Objects.requireNonNull(release);
        Objects.requireNonNull(artifact);
        Objects.requireNonNull(workerPackage);
        Objects.requireNonNull(process);
        Objects.requireNonNull(sessionBinding);
        Objects.requireNonNull(endpoints);
    }

    public record ReleaseEvidence(
            boolean existingGateWVerifierPassed,
            String releaseId,
            String manifestDigest,
            boolean exactCommit,
            boolean rootOwned,
            boolean writableByWorker
    ) { }

    public record ArtifactEvidence(
            String closureStatus,
            String artifactDigest,
            String packageArtifactDigest,
            boolean sameVerifiedObjectConsumed
    ) { }

    public record WorkerPackageEvidence(
            boolean immutable,
            String releaseId,
            String manifestDigest,
            String artifactDigest,
            boolean containsCredentialMaterial,
            boolean containsLiveApproval,
            boolean containsStrategyAuthority,
            boolean containsRiskRuleAuthoring,
            boolean containsArbitraryEndpoints
    ) { }

    public record ProcessEvidence(
            String serviceUser,
            String runtimeUser,
            String releaseOwner,
            String configuredReleaseId,
            String configuredManifestDigest,
            boolean startCommandExact,
            boolean unitDisabledByDefault
    ) { }

    public record SessionBindingEvidence(
            String sessionId,
            long credentialReference,
            long exchangeAccountId,
            String venue,
            boolean currentLiveSessionFactVerified
    ) { }

    public record EndpointEvidence(
            boolean existingTypedGuardReused,
            boolean exactAllowlistVerified,
            Set<String> allowedOperations,
            String policyDigest,
            boolean forbiddenEndpointReachable
    ) {
        public EndpointEvidence {
            allowedOperations = Set.copyOf(allowedOperations == null ? Set.of() : allowedOperations);
        }
    }
}
