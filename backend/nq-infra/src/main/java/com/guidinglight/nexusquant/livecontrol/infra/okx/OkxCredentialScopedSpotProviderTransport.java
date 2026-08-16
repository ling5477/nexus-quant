package com.guidinglight.nexusquant.livecontrol.infra.okx;

import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * 绑定 exact owner/account/credential 的 provider transport capability。
 *
 * <p>无 Spring annotation、无默认构造器、无 worker caller；每个调用仅在 JIT callback 内访问 credential。</p>
 */
public final class OkxCredentialScopedSpotProviderTransport implements OkxSpotProviderTransport {

    private final OkxPrivateCredentialExecutor credentialExecutor;
    private final long ownerId;
    private final long exchangeAccountId;
    private final long credentialReference;
    private final UUID sessionId;
    private final List<String> symbolAllowlist;

    public OkxCredentialScopedSpotProviderTransport(
            OkxPrivateCredentialExecutor credentialExecutor,
            long ownerId,
            long exchangeAccountId,
            long credentialReference,
            UUID sessionId,
            List<String> symbolAllowlist
    ) {
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
        if (ownerId <= 0 || exchangeAccountId <= 0 || credentialReference <= 0) {
            throw new IllegalArgumentException("exact provider identity references must be positive");
        }
        this.ownerId = ownerId;
        this.exchangeAccountId = exchangeAccountId;
        this.credentialReference = credentialReference;
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.symbolAllowlist = List.copyOf(Objects.requireNonNull(symbolAllowlist, "symbolAllowlist must not be null"));
        if (this.symbolAllowlist.isEmpty() || this.symbolAllowlist.size() > 2
                || this.symbolAllowlist.stream().anyMatch(value ->
                value == null || !value.matches("[A-Z0-9]{2,20}-USDT"))
                || !this.symbolAllowlist.equals(this.symbolAllowlist.stream().distinct().sorted().toList())) {
            throw new IllegalArgumentException("one or two exact scope symbols are required");
        }
    }

    @Override
    public PlaceResponse placeLimit(PlaceCommand command) {
        requireScope(command.instrument(), command.context().sessionId());
        return withCredential(session -> session.placeLimit(command, OkxPrivateEnvironment.PRODUCTION));
    }

    @Override
    public OrderResponse queryOrder(OrderCommand command) {
        requireScope(command.instrument(), command.context().sessionId());
        return withCredential(session -> session.queryOrder(command, OkxPrivateEnvironment.PRODUCTION));
    }

    @Override
    public CancelResponse cancelOrder(CancelCommand command) {
        requireScope(command.instrument(), command.context().sessionId());
        return withCredential(session -> session.cancelOrder(command, OkxPrivateEnvironment.PRODUCTION));
    }

    @Override
    public OrderResponse readOrder(OrderCommand command) {
        requireScope(command.instrument(), command.context().sessionId());
        return withCredential(session -> session.readOrder(command, OkxPrivateEnvironment.PRODUCTION));
    }

    @Override
    public FillResponse readFills(FillCommand command) {
        requireScope(command.instrument(), command.context().sessionId());
        return withCredential(session -> session.readFills(command, OkxPrivateEnvironment.PRODUCTION));
    }

    private <T> T withCredential(OkxPrivateCredentialExecutor.CredentialCallback<T> callback) {
        return credentialExecutor.withActiveCredential(
                ownerId,
                exchangeAccountId,
                credentialReference,
                JdbcOkxPrivateCredentialExecutor.OKX_API_V5,
                callback
        );
    }

    private void requireScope(String instrument, UUID requestSessionId) {
        if (!sessionId.equals(requestSessionId) || !symbolAllowlist.contains(instrument)) {
            throw new IllegalArgumentException("provider command is outside the exact pilot scope");
        }
    }
}
