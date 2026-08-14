package com.guidinglight.nexusquant.adapter.okx.service;

/** typed endpoint contract decision；永不授予 runtime 或 trading authorization。 */
public record OkxSpotProviderContractDecision(
        boolean contractAllowed,
        OkxSpotProviderOperation operation,
        Reason reason,
        boolean runtimeAuthorized,
        boolean tradingAuthorized
) {
    public OkxSpotProviderContractDecision {
        runtimeAuthorized = false;
        tradingAuthorized = false;
    }

    static OkxSpotProviderContractDecision allowContractOnly(OkxSpotProviderOperation operation) {
        return new OkxSpotProviderContractDecision(
                true, operation, Reason.ALLOW_TYPED_CONTRACT_ONLY, false, false);
    }

    static OkxSpotProviderContractDecision deny(OkxSpotProviderOperation operation) {
        return new OkxSpotProviderContractDecision(
                false, operation, Reason.DENY_NOT_EXACTLY_ALLOWLISTED, false, false);
    }

    public enum Reason {
        ALLOW_TYPED_CONTRACT_ONLY,
        DENY_NOT_EXACTLY_ALLOWLISTED
    }
}
