package com.guidinglight.nexusquant.livecontrol.deployment.infra.okx;

import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotEndpointGuard;
import com.guidinglight.nexusquant.livecontrol.deployment.PrivateReadonlyDiagnosticEndpointContract;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.EndpointEvidence;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Existing typed endpoint guard reuse and forbidden endpoint negatives。 */
class OkxPrivateReadonlyEndpointPolicyEvidenceFactoryTest {

    @Test
    void shouldReuseExactTypedGuardForOnlyReviewedDiagnosticOperations() {
        EndpointEvidence evidence = new OkxPrivateReadonlyEndpointPolicyEvidenceFactory(
                new OkxSpotEndpointGuard()).createPrivateReadonlyDiagnosticEvidence();

        assertTrue(evidence.existingTypedGuardReused());
        assertTrue(evidence.exactAllowlistVerified());
        assertEquals(Set.of("OKX_ACCOUNT_CONFIGURATION_READ", "OKX_ACCOUNT_BALANCE_READ"),
                evidence.allowedOperations());
        assertEquals(PrivateReadonlyDiagnosticEndpointContract.policyDigest(), evidence.policyDigest());
        assertFalse(evidence.forbiddenEndpointReachable());
        assertEquals(64, evidence.policyDigest().length());
    }
}
