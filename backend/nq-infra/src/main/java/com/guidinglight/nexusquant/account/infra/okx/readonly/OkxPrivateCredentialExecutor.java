package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport;

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

    /**
     * 按服务端 credential reference 精确选择 active credential。
     *
     * <p>未实现 exact-reference 查询的旧 executor 必须 fail-closed，不能静默退化为 account/type 查询，
     * 否则 scoped request 可能使用同账户下错误的 credential。</p>
     */
    default <T> T withActiveCredential(
            Long ownerId,
            Long exchangeAccountId,
            Long credentialId,
            String credentialType,
            CredentialCallback<T> callback
    ) {
        throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
    }

    @FunctionalInterface
    interface CredentialCallback<T> {
        T execute(CredentialSession session);
    }

    /**
     * 只能在 callback 所在线程和 callback 生命周期内执行 typed private read。
     */
    @FunctionalInterface
    interface CredentialSession {
        OkxPrivateReadResult execute(OkxPrivateReadRequest request, OkxPrivateEnvironment environment);

        default OkxPilotPrerequisiteSnapshot observePrerequisites(
                OkxPilotPrerequisiteRequest request,
                OkxPrivateEnvironment environment
        ) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }

        default OkxSpotProviderTransport.PlaceResponse placeLimit(
                OkxSpotProviderTransport.PlaceCommand command,
                OkxPrivateEnvironment environment
        ) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }

        default OkxSpotProviderTransport.OrderResponse queryOrder(
                OkxSpotProviderTransport.OrderCommand command,
                OkxPrivateEnvironment environment
        ) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }

        default OkxSpotProviderTransport.CancelResponse cancelOrder(
                OkxSpotProviderTransport.CancelCommand command,
                OkxPrivateEnvironment environment
        ) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }

        default OkxSpotProviderTransport.OrderResponse readOrder(
                OkxSpotProviderTransport.OrderCommand command,
                OkxPrivateEnvironment environment
        ) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }

        default OkxSpotProviderTransport.FillResponse readFills(
                OkxSpotProviderTransport.FillCommand command,
                OkxPrivateEnvironment environment
        ) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }

        default OkxSpotProviderTransport.ClockResponse readClock(
                OkxSpotProviderTransport.ClockCommand command
        ) {
            throw new OkxPrivateReadException(OkxPrivateReadError.CREDENTIAL_UNAVAILABLE);
        }
    }
}
