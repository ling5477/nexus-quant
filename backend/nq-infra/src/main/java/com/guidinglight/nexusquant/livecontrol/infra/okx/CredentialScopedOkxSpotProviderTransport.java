package com.guidinglight.nexusquant.livecontrol.infra.okx;

import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateRealTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** 每次typed operation按session exact credential reference执行一次JIT callback；没有fallback。 */
public final class CredentialScopedOkxSpotProviderTransport implements OkxSpotProviderTransport {

    private final LiveControlRepository sessions;
    private final OkxPrivateCredentialExecutor credentials;
    private final OkxPrivateRealTransport publicClockTransport;

    public CredentialScopedOkxSpotProviderTransport(
            LiveControlRepository sessions,
            OkxPrivateCredentialExecutor credentials,
            OkxPrivateReadTransport transport
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.credentials = Objects.requireNonNull(credentials, "credentials must not be null");
        if (!(Objects.requireNonNull(transport, "transport must not be null")
                instanceof OkxPrivateRealTransport realTransport)) {
            throw new IllegalArgumentException("real typed transport is required");
        }
        this.publicClockTransport = realTransport;
    }

    @Override
    public PlaceResponse placeLimit(PlaceCommand command) {
        return execute(command.context().sessionId(), session -> session.placeLimit(command, production()));
    }

    @Override
    public OrderResponse queryOrder(OrderCommand command) {
        return execute(command.context().sessionId(), session -> session.queryOrder(command, production()));
    }

    @Override
    public CancelResponse cancelOrder(CancelCommand command) {
        return execute(command.context().sessionId(), session -> session.cancelOrder(command, production()));
    }

    @Override
    public OrderResponse readOrder(OrderCommand command) {
        return execute(command.context().sessionId(), session -> session.readOrder(command, production()));
    }

    @Override
    public FillResponse readFills(FillCommand command) {
        return execute(command.context().sessionId(), session -> session.readFills(command, production()));
    }

    @Override
    public ClockResponse readClock(ClockCommand command) {
        requireSession(command.context().sessionId());
        return publicClockTransport.readClock(command);
    }

    private <T> T execute(UUID sessionId, Function<OkxPrivateCredentialExecutor.CredentialSession, T> operation) {
        LiveSession session = requireSession(sessionId);
        return credentials.withActiveCredential(
                session.ownerId(), session.exchangeAccountId(), session.credentialReference(),
                JdbcOkxPrivateCredentialExecutor.OKX_API_V5, operation::apply);
    }

    private LiveSession requireSession(UUID sessionId) {
        return sessions.findSession(sessionId)
                .orElseThrow(() -> new LiveControlException("LIVE_SESSION_NOT_FOUND", "pilot session not found"));
    }

    private static OkxPrivateEnvironment production() {
        return OkxPrivateEnvironment.PRODUCTION;
    }
}
