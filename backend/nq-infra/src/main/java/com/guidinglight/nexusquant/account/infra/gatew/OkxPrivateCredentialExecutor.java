package com.guidinglight.nexusquant.account.infra.gatew;

import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;

/**
 * infrastructure 内同步 scoped decrypt executor；callback 只获得线程绑定、到期失效的 typed read session，
 * 不接触 credential context 或 material。
 */
public interface OkxPrivateCredentialExecutor {

    <T> T withActiveCredential(
            Long ownerId,
            Long exchangeAccountId,
            String credentialType,
            CredentialCallback<T> callback
    );

    @FunctionalInterface
    interface CredentialCallback<T> {
        T execute(CredentialSession session);
    }

    /** 只能在 callback 所在线程和 callback 生命周期内执行 typed private read。 */
    @FunctionalInterface
    interface CredentialSession {
        OkxPrivateReadResult execute(OkxPrivateReadRequest request, OkxPrivateEnvironment environment);
    }
}
