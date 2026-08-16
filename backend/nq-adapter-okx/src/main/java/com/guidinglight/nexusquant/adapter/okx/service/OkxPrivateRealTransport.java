package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * 复用既有 signer/client 的 credential-scoped GateY-6E capability。
 *
 * <p>该 port 不包含 credential、host、path、method 或 generic execute escape hatch。</p>
 */
public interface OkxPrivateRealTransport extends OkxPrivateReadTransport {

    OkxPilotPrerequisiteSnapshot observePrerequisites(
            OkxPilotPrerequisiteRequest request,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );

    OkxSpotProviderTransport.PlaceResponse placeLimit(
            OkxSpotProviderTransport.PlaceCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );

    OkxSpotProviderTransport.OrderResponse queryOrder(
            OkxSpotProviderTransport.OrderCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );

    OkxSpotProviderTransport.CancelResponse cancelOrder(
            OkxSpotProviderTransport.CancelCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );

    OkxSpotProviderTransport.OrderResponse readOrder(
            OkxSpotProviderTransport.OrderCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );

    OkxSpotProviderTransport.FillResponse readFills(
            OkxSpotProviderTransport.FillCommand command,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );
}
