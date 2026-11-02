package com.guidinglight.nexusquant.adapter.okx.privateread.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.provider.OkxSpotProviderTransport;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 只暴露固定 GET 与公开时钟读的 runtime 包装。JIT executor 无法将此对象转为带 mutation 的 transport。
 */
public final class OkxAccountFactsReadTransport implements OkxPrivateReadTransport {
    private final JdkOkxPrivateReadTransport delegate;
    private final Clock clock;

    public OkxAccountFactsReadTransport(ObjectMapper mapper, Clock clock) {
        this.delegate = new JdkOkxPrivateReadTransport(mapper, clock);
        this.clock = clock;
    }

    @Override
    public OkxPrivateReadResult execute(
            OkxPrivateReadRequest request,
            OkxPrivateCredentialContext credential,
            OkxPrivateEnvironment environment
    ) {
        return delegate.execute(request, credential, environment);
    }

    public Instant readServerTime() {
        var context = new OkxSpotProviderTransport.TransportContext(
                UUID.randomUUID(), "account-facts", "account-facts", "account-facts", clock.instant());
        return delegate.readClock(new OkxSpotProviderTransport.ClockCommand(
                context, new OkxSpotProviderTransport.ResponseReadLimit(4096, 1))).serverTime();
    }

    @Override
    public String toString() {
        return "OkxAccountFactsReadTransport[REDACTED]";
    }
}
