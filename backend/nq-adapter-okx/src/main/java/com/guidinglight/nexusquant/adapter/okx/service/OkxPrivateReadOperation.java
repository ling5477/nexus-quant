package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.EndpointAccessClass;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;

/**
 * GateW-2 唯一允许的 OKX private read-only operation 集合。
 *
 * <p>method/path/access class 均由枚举固定，生产调用方不能注入任意 endpoint。</p>
 */
public enum OkxPrivateReadOperation {

    OKX_ACCOUNT_CONFIGURATION_READ(
            ExchangeCapability.PRIVATE_ACCOUNT_CONFIGURATION_READ,
            "/api/v5/account/config"
    ),
    OKX_ACCOUNT_BALANCE_READ(
            ExchangeCapability.PRIVATE_ACCOUNT_BALANCE_READ,
            "/api/v5/account/balance"
    );

    private final ExchangeCapability capability;
    private final String path;

    OkxPrivateReadOperation(ExchangeCapability capability, String path) {
        this.capability = capability;
        this.path = path;
    }

    public ExchangeCapability capability() {
        return capability;
    }

    public String method() {
        return "GET";
    }

    public String path() {
        return path;
    }

    public EndpointAccessClass endpointAccessClass() {
        return EndpointAccessClass.PRIVATE_READ_ONLY;
    }
}
