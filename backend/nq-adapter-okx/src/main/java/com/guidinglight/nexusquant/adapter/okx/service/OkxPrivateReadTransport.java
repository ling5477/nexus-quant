package com.guidinglight.nexusquant.adapter.okx.service;

/** 仅接受 typed operation 与 scoped credential 的 OKX private read-only port。 */
public interface OkxPrivateReadTransport {

    OkxPrivateReadResult execute(
            OkxPrivateReadRequest request,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );
}
