package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.livecontrol.deployment.KillSwitchPropagationPolicy.Decision;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.ArtifactEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.EndpointEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.ProcessEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.ReleaseEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.WorkerPackageEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.SessionBindingEvidence;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import java.time.Clock;
import java.util.Objects;

/**
 * Future worker start admission；该结果不产生 trading authorization。
 *
 * <p>release、stable artifact、process/config identity、current kill、credential capability 与 typed endpoint
 * allowlist 任一未知或不一致即 START_DENIED。服务只评估启动边界，不启动 systemd/process，不调用交易所。</p>
 */
public final class WorkerDeploymentAdmissionService {

    private final KillSwitchService killSwitchService;
    private final KillSwitchPropagationPolicy killPolicy;
    private final ScopedCredentialCapabilityPolicy credentialPolicy;
    private final Clock clock;

    public WorkerDeploymentAdmissionService(
            KillSwitchService killSwitchService,
            KillSwitchPropagationPolicy killPolicy,
            ScopedCredentialCapabilityPolicy credentialPolicy,
            Clock clock
    ) {
        this.killSwitchService = Objects.requireNonNull(killSwitchService);
        this.killPolicy = Objects.requireNonNull(killPolicy);
        this.credentialPolicy = Objects.requireNonNull(credentialPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    public AdmissionDecision evaluate(WorkerDeploymentEvidence evidence) {
        if (evidence == null) return AdmissionDecision.denied(Reason.EVIDENCE_MISSING);
        Reason releaseFailure = releaseFailure(evidence.release());
        if (releaseFailure != null) return AdmissionDecision.denied(releaseFailure);
        Reason artifactFailure = artifactFailure(evidence.artifact());
        if (artifactFailure != null) return AdmissionDecision.denied(artifactFailure);
        Reason packageFailure = packageFailure(evidence.workerPackage(), evidence.release(), evidence.artifact());
        if (packageFailure != null) return AdmissionDecision.denied(packageFailure);
        Reason processFailure = processFailure(evidence.process(), evidence.release());
        if (processFailure != null) return AdmissionDecision.denied(processFailure);
        Reason sessionFailure = sessionFailure(evidence.sessionBinding(), evidence.credential());
        if (sessionFailure != null) return AdmissionDecision.denied(sessionFailure);
        Reason endpointFailure = endpointFailure(evidence.endpoints(), evidence.credential());
        if (endpointFailure != null) return AdmissionDecision.denied(endpointFailure);

        ScopedCredentialCapabilityPolicy.Decision credential =
                credentialPolicy.evaluate(evidence.credential(), clock.instant());
        if (credential.status() != ScopedCredentialCapabilityPolicy.Status.ALLOWED) {
            return AdmissionDecision.denied(Reason.CREDENTIAL_CAPABILITY_DENIED);
        }
        Decision kill = killPolicy.evaluate(
                evidence.killSwitchEnvelope(), killSwitchService.snapshot(), clock.instant());
        if (kill.status() != KillSwitchPropagationPolicy.Status.ALLOWED) {
            return AdmissionDecision.denied(Reason.KILL_SWITCH_DENIED);
        }
        return AdmissionDecision.admitted();
    }

    private static Reason releaseFailure(ReleaseEvidence value) {
        if (value == null || !value.existingGateWVerifierPassed() || !value.exactCommit()
                || !value.rootOwned() || value.writableByWorker()
                || blank(value.releaseId()) || blank(value.manifestDigest())) {
            return Reason.RELEASE_NOT_VERIFIED_OR_IMMUTABLE;
        }
        return null;
    }

    private static Reason artifactFailure(ArtifactEvidence value) {
        if (value == null || !"SUPPORTED_RUNTIME_CLOSED".equals(value.closureStatus())
                || !value.sameVerifiedObjectConsumed() || blank(value.artifactDigest())
                || !value.artifactDigest().equals(value.packageArtifactDigest())) {
            return Reason.STABLE_ARTIFACT_NOT_CLOSED;
        }
        return null;
    }

    private static Reason processFailure(ProcessEvidence value, ReleaseEvidence release) {
        if (value == null || blank(value.serviceUser()) || !value.serviceUser().equals(value.runtimeUser())
                || !"root".equals(value.releaseOwner()) || "root".equals(value.runtimeUser())
                || !value.startCommandExact() || !value.unitDisabledByDefault()
                || !release.releaseId().equals(value.configuredReleaseId())
                || !release.manifestDigest().equals(value.configuredManifestDigest())) {
            return Reason.PROCESS_OR_CONFIG_IDENTITY_MISMATCH;
        }
        return null;
    }

    private static Reason packageFailure(
            WorkerPackageEvidence value,
            ReleaseEvidence release,
            ArtifactEvidence artifact
    ) {
        if (value == null || !value.immutable()
                || !release.releaseId().equals(value.releaseId())
                || !release.manifestDigest().equals(value.manifestDigest())
                || !artifact.artifactDigest().equals(value.artifactDigest())
                || value.containsCredentialMaterial() || value.containsLiveApproval()
                || value.containsStrategyAuthority() || value.containsRiskRuleAuthoring()
                || value.containsArbitraryEndpoints()) {
            return Reason.WORKER_PACKAGE_POLICY_DENIED;
        }
        return null;
    }

    private static Reason sessionFailure(
            SessionBindingEvidence value,
            ScopedCredentialReference credential
    ) {
        if (value == null || credential == null || blank(value.sessionId())
                || blank(value.venue())
                || !value.currentLiveSessionFactVerified()
                || value.credentialReference() != credential.credentialReference()
                || value.exchangeAccountId() != credential.exchangeAccountId()
                || !value.venue().equals(credential.venue())) {
            return Reason.SESSION_CREDENTIAL_BINDING_MISMATCH;
        }
        return null;
    }

    private static Reason endpointFailure(EndpointEvidence value, ScopedCredentialReference credential) {
        if (value == null || credential == null || !value.existingTypedGuardReused()
                || !value.exactAllowlistVerified() || value.forbiddenEndpointReachable()
                || blank(value.policyDigest())) return Reason.ENDPOINT_POLICY_DENIED;
        if (credential.capability() == ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC
                && !PrivateReadonlyDiagnosticEndpointContract.matches(
                value.allowedOperations(), value.policyDigest())) {
            return Reason.ENDPOINT_POLICY_DENIED;
        }
        return null;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record AdmissionDecision(Status status, Reason reason, boolean tradingAuthorization) {
        public AdmissionDecision {
            Objects.requireNonNull(status);
            tradingAuthorization = false;
            if ((status == Status.START_ADMITTED) != (reason == null)) {
                throw new IllegalArgumentException("admission status and reason are inconsistent");
            }
        }
        public static AdmissionDecision admitted() {
            return new AdmissionDecision(Status.START_ADMITTED, null, false);
        }
        public static AdmissionDecision denied(Reason reason) {
            return new AdmissionDecision(Status.START_DENIED, Objects.requireNonNull(reason), false);
        }
    }

    public enum Status { START_ADMITTED, START_DENIED }
    public enum Reason {
        EVIDENCE_MISSING,
        RELEASE_NOT_VERIFIED_OR_IMMUTABLE,
        STABLE_ARTIFACT_NOT_CLOSED,
        WORKER_PACKAGE_POLICY_DENIED,
        PROCESS_OR_CONFIG_IDENTITY_MISMATCH,
        SESSION_CREDENTIAL_BINDING_MISMATCH,
        ENDPOINT_POLICY_DENIED,
        CREDENTIAL_CAPABILITY_DENIED,
        KILL_SWITCH_DENIED
    }
}
