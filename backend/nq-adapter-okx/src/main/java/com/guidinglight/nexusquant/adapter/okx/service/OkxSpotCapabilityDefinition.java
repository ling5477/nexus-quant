package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.EndpointAccessClass;
import com.guidinglight.nexusquant.adapter.api.model.EndpointGuardReason;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;

import java.util.Objects;

/**
 * OkxSpotCapabilityDefinition 是 OKX Spot capability matrix 的单行不可变合同。
 *
 * <p>该类型不包含 endpoint URL、credential material、账户标识或 transport；它只描述 GateW-1
 * 的能力分类和默认拒绝状态。</p>
 */
public record OkxSpotCapabilityDefinition(
        ExchangeCapability capability,
        EndpointAccessClass endpointAccessClass,
        boolean implemented,
        boolean runtimeEnabled,
        boolean credentialRequired,
        boolean networkRequired,
        boolean tradingAuthorization,
        EndpointGuardReason reasonCode
) {

    public OkxSpotCapabilityDefinition {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(endpointAccessClass, "endpointAccessClass must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        // Matrix 是描述性合同，不能被当作 LIVE 或下单授权。
        tradingAuthorization = false;
    }
}
