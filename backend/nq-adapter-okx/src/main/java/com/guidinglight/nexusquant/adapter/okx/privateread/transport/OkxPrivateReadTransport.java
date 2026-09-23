package com.guidinglight.nexusquant.adapter.okx.privateread.transport;

import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxPrivateCredentialContext;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;

/** 仅接受 typed operation 与 scoped credential 的 OKX private read-only port。 */
public interface OkxPrivateReadTransport {

    OkxPrivateReadResult execute(
            OkxPrivateReadRequest request,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    );
}
