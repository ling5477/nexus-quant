package com.guidinglight.nexusquant.livecontrol.deployment.infra.okx;

import com.guidinglight.nexusquant.adapter.api.model.EndpointPolicyDecision;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotEndpointGuard;
import com.guidinglight.nexusquant.livecontrol.deployment.PrivateReadonlyDiagnosticEndpointContract;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentEvidence.EndpointEvidence;

/**
 * 复用既有 {@link OkxSpotEndpointGuard} 生成 private-read exact endpoint evidence；不维护第二份 endpoint source。
 */
public final class OkxPrivateReadonlyEndpointPolicyEvidenceFactory {

    private final OkxSpotEndpointGuard guard;

    public OkxPrivateReadonlyEndpointPolicyEvidenceFactory(OkxSpotEndpointGuard guard) {
        this.guard = java.util.Objects.requireNonNull(guard);
    }

    public EndpointEvidence createPrivateReadonlyDiagnosticEvidence() {
        boolean configAllowed = allowed(guard.evaluatePrivateRead(OkxPrivateReadRequest.accountConfiguration()));
        boolean balanceAllowed = allowed(guard.evaluatePrivateRead(
                OkxPrivateReadRequest.accountBalance(java.util.List.of("USDT"))));
        boolean mutatingDenied = !guard.evaluate(
                ExchangeCapability.ORDER_SUBMISSION, "POST", "/api/v5/trade/order").allowed();
        boolean cancelDenied = !guard.evaluate(
                ExchangeCapability.ORDER_CANCEL, "POST", "/api/v5/trade/cancel-order").allowed();
        boolean transferDenied = !guard.evaluate(
                ExchangeCapability.TRANSFER, "POST", "/api/v5/asset/transfer").allowed();
        boolean withdrawDenied = !guard.evaluate(
                ExchangeCapability.WITHDRAW, "POST", "/api/v5/asset/withdrawal").allowed();
        boolean exact = configAllowed && balanceAllowed;
        return new EndpointEvidence(
                true,
                exact,
                exact ? PrivateReadonlyDiagnosticEndpointContract.allowedOperations() : java.util.Set.of(),
                PrivateReadonlyDiagnosticEndpointContract.policyDigest(),
                !(mutatingDenied && cancelDenied && transferDenied && withdrawDenied)
        );
    }

    private static boolean allowed(EndpointPolicyDecision decision) {
        return decision.allowed() && !decision.tradingAuthorization();
    }

}
