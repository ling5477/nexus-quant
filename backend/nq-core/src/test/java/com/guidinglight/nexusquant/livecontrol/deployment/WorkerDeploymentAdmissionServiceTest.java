package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.ArtifactEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.EndpointEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.ProcessEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.ReleaseEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.WorkerPackageEvidence;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.SessionBindingEvidence;
import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Immutable package/process/kill/credential/endpoint start admission regression。 */
class WorkerDeploymentAdmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T02:00:00Z");
    private final MutableRepository repository = new MutableRepository(KillSwitchStatus.DISENGAGED);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final KillSwitchService killService = new KillSwitchService(repository, clock);
    private final WorkerDeploymentAdmissionService service = new WorkerDeploymentAdmissionService(
            killService,
            new KillSwitchPropagationPolicy(Duration.ofSeconds(30)),
            new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)),
            clock
    );

    @Test
    void shouldAdmitOnlyDiagnosticStartWithoutTradingAuthorization() {
        WorkerDeploymentEvidence evidence = evidence(
                ScopedCredentialCapabilityPolicyTest.reference(
                        ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC));

        WorkerDeploymentAdmissionService.AdmissionDecision decision = service.evaluate(evidence);

        assertEquals(WorkerDeploymentAdmissionService.Status.START_ADMITTED, decision.status());
        assertFalse(decision.tradingAuthorization());
    }

    @Test
    void shouldDenyFutureMicroLiveWritableReleaseUnknownArtifactAndEngagedKill() {
        assertDenied(evidence(ScopedCredentialCapabilityPolicyTest.reference(
                        ScopedCredentialCapability.FUTURE_MICRO_LIVE)),
                WorkerDeploymentAdmissionService.Reason.CREDENTIAL_CAPABILITY_DENIED);

        WorkerDeploymentEvidence valid = evidence(ScopedCredentialCapabilityPolicyTest.reference(
                ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC));
        assertDenied(new WorkerDeploymentEvidence(
                new ReleaseEvidence(true, "release-1", "manifest-1", true, true, true),
                        valid.artifact(), valid.workerPackage(), valid.process(), valid.sessionBinding(),
                        valid.endpoints(), valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.RELEASE_NOT_VERIFIED_OR_IMMUTABLE);
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), new ArtifactEvidence("UNKNOWN", "artifact-1", "artifact-1", false),
                        valid.workerPackage(), valid.process(), valid.sessionBinding(), valid.endpoints(),
                        valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.STABLE_ARTIFACT_NOT_CLOSED);

        repository.status = KillSwitchStatus.ENGAGED;
        assertDenied(valid, WorkerDeploymentAdmissionService.Reason.KILL_SWITCH_DENIED);
    }

    @Test
    void shouldDenyTamperedDigestWrongIdentityForbiddenEndpointStaleKillAndInvalidReference() {
        WorkerDeploymentEvidence valid = evidence(ScopedCredentialCapabilityPolicyTest.reference(
                ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC));
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), new ArtifactEvidence(
                        "SUPPORTED_RUNTIME_CLOSED", "artifact-1", "tampered", true),
                        valid.workerPackage(), valid.process(), valid.sessionBinding(), valid.endpoints(),
                        valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.STABLE_ARTIFACT_NOT_CLOSED);
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), valid.artifact(), valid.workerPackage(),
                        new ProcessEvidence("nq-live-worker", "wrong-user", "root", "release-1", "manifest-1",
                                true, true),
                        valid.sessionBinding(), valid.endpoints(), valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.PROCESS_OR_CONFIG_IDENTITY_MISMATCH);
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), valid.artifact(), valid.workerPackage(), valid.process(), valid.sessionBinding(),
                        new EndpointEvidence(true, true, valid.endpoints().allowedOperations(),
                                "endpoint-digest", true),
                        valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.ENDPOINT_POLICY_DENIED);
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), valid.artifact(), valid.workerPackage(), valid.process(), valid.sessionBinding(),
                        new EndpointEvidence(true, true, valid.endpoints().allowedOperations(),
                                "0".repeat(64), false),
                        valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.ENDPOINT_POLICY_DENIED);
        KillSwitchSnapshot staleSnapshot = new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING, KillSwitchStatus.DISENGAGED, 1, "TEST", "DURABLE_STORE",
                NOW.minusSeconds(40), NOW.minusSeconds(31), "trace-kill");
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), valid.artifact(), valid.workerPackage(), valid.process(), valid.sessionBinding(),
                        valid.endpoints(), valid.credential(),
                        KillSwitchPropagationEnvelope.fromSnapshot(staleSnapshot)),
                WorkerDeploymentAdmissionService.Reason.KILL_SWITCH_DENIED);
        ScopedCredentialReference source = valid.credential();
        ScopedCredentialReference invalid = new ScopedCredentialReference(
                source.ownerId(), source.exchangeAccountId(), 0, source.venue(), source.credentialType(),
                source.capability(), source.lifecycleStatus(), source.active(), source.verificationStatus(),
                source.permissionProbeStatus(), source.permissionScopeDigest(), source.remotelyVerifiedReadOnly(),
                source.withdrawEnabled(), source.ipAllowlistConfigured(), source.remoteIpVerificationStatus(),
                source.lastPermissionProbeAt(), source.revokedAt(), source.rotatedAt());
        assertDenied(evidence(invalid), WorkerDeploymentAdmissionService.Reason.CREDENTIAL_CAPABILITY_DENIED);

        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), valid.artifact(),
                        new WorkerPackageEvidence(true, "release-1", "manifest-1", "artifact-1",
                                true, false, false, false, false),
                        valid.process(), valid.sessionBinding(), valid.endpoints(), valid.credential(),
                        valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.WORKER_PACKAGE_POLICY_DENIED);
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), valid.artifact(), valid.workerPackage(), valid.process(),
                        new SessionBindingEvidence("session-1", 99, 11, "OKX_SPOT", true),
                        valid.endpoints(), valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.SESSION_CREDENTIAL_BINDING_MISMATCH);
        assertDenied(new WorkerDeploymentEvidence(
                        valid.release(), valid.artifact(), valid.workerPackage(), valid.process(),
                        new SessionBindingEvidence("session-1", 13, 11, null, true),
                        valid.endpoints(), valid.credential(), valid.killSwitchEnvelope()),
                WorkerDeploymentAdmissionService.Reason.SESSION_CREDENTIAL_BINDING_MISMATCH);
    }

    private void assertDenied(WorkerDeploymentEvidence evidence, WorkerDeploymentAdmissionService.Reason reason) {
        assertEquals(reason, service.evaluate(evidence).reason());
    }

    private WorkerDeploymentEvidence evidence(ScopedCredentialReference credential) {
        return new WorkerDeploymentEvidence(
                new ReleaseEvidence(true, "release-1", "manifest-1", true, true, false),
                new ArtifactEvidence("SUPPORTED_RUNTIME_CLOSED", "artifact-1", "artifact-1", true),
                new WorkerPackageEvidence(true, "release-1", "manifest-1", "artifact-1",
                        false, false, false, false, false),
                new ProcessEvidence("nq-live-worker", "nq-live-worker", "root", "release-1", "manifest-1",
                        true, true),
                new SessionBindingEvidence("session-1", credential.credentialReference(),
                        credential.exchangeAccountId(), credential.venue(), true),
                new EndpointEvidence(true, true,
                        PrivateReadonlyDiagnosticEndpointContract.allowedOperations(),
                        PrivateReadonlyDiagnosticEndpointContract.policyDigest(), false),
                credential,
                KillSwitchPropagationEnvelope.fromSnapshot(killService.snapshot())
        );
    }

    private static final class MutableRepository implements KillSwitchStateRepository {
        private KillSwitchStatus status;
        private MutableRepository(KillSwitchStatus status) { this.status = status; }
        @Override public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            long version = status == KillSwitchStatus.ENGAGED ? 2 : 1;
            return Optional.of(new KillSwitchState(
                    scope, status, version, "TEST", "DURABLE_STORE", NOW.minusSeconds(2),
                    "operator", "trace-kill"));
        }
        @Override public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException("test read-only repository");
        }
    }
}
