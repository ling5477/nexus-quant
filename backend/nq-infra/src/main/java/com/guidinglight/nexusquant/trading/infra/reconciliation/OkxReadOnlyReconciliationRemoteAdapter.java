package com.guidinglight.nexusquant.trading.infra.reconciliation;

import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateFillSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationRequest;
import com.guidinglight.nexusquant.trading.application.reconciliation.RemoteOrderSnapshot;
import com.guidinglight.nexusquant.trading.application.reconciliation.RemoteOrderSnapshotReadPort;
import com.guidinglight.nexusquant.trading.application.reconciliation.RemoteSnapshotBatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * GateW-3 typed OKX remote read adapter。
 *
 * <p>复用 GateW-2 scoped credential executor 和 guarded GET transport；先核验 exact READ_ONLY，
 * 再按每个 allowlisted symbol 各读取一页 pending/history/recent fills。该类不注册为 bean，默认/CI
 * 不会读取 credential 或发起网络；调用方也不能提供 path、method、body 或 retry 策略。</p>
 */
public final class OkxReadOnlyReconciliationRemoteAdapter implements RemoteOrderSnapshotReadPort {
    private static final Set<String> SAFE_PERMISSION = Set.of("READ_ONLY");

    private final OkxPrivateCredentialExecutor credentialExecutor;
    private final String credentialType;
    private final OkxPrivateEnvironment environment;

    public OkxReadOnlyReconciliationRemoteAdapter(
            OkxPrivateCredentialExecutor credentialExecutor,
            String credentialType,
            OkxPrivateEnvironment environment
    ) {
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
        this.credentialType = Objects.requireNonNull(credentialType, "credentialType must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @Override
    public RemoteSnapshotBatch read(ReconciliationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!environment.accountTradeEnvironment().equals(request.tradeEnvironment())) {
            throw new IllegalArgumentException("request and credential environment must match");
        }
        return credentialExecutor.withActiveCredential(
                request.ownerId(), request.exchangeAccountId(), credentialType,
                session -> readScoped(session, request)
        );
    }

    private RemoteSnapshotBatch readScoped(
            OkxPrivateCredentialExecutor.CredentialSession session,
            ReconciliationRequest request
    ) {
        OkxPrivateReadResult configuration = session.execute(
                OkxPrivateReadRequest.accountConfiguration(), environment
        );
        Instant observedAt = configuration.observedAt();
        if (!configuration.complete() || !SAFE_PERMISSION.equals(configuration.normalizedPermissions())) {
            return new RemoteSnapshotBatch(List.of(), false, false, 0, 0, observedAt);
        }

        Map<String, OkxPrivateOrderSnapshot> orders = new LinkedHashMap<>();
        Map<String, String> clientToExchange = new HashMap<>();
        List<OkxPrivateFillSnapshot> fills = new ArrayList<>();
        boolean complete = true;
        int recordCount = 0;
        for (String symbol : request.symbols()) {
            for (OkxPrivateReadRequest typedRequest : List.of(
                    OkxPrivateReadRequest.openOrders(symbol, request.recordLimit()),
                    OkxPrivateReadRequest.orderHistory(
                            symbol, request.windowStart(), request.windowEnd(), request.recordLimit()),
                    OkxPrivateReadRequest.recentFills(
                            symbol, request.windowStart(), request.windowEnd(), request.recordLimit())
            )) {
                OkxPrivateReadResult result = session.execute(typedRequest, environment);
                observedAt = latest(observedAt, result.observedAt());
                complete &= result.complete();
                recordCount += result.orders().size() + result.fills().size();
                for (OkxPrivateOrderSnapshot order : result.orders()) {
                    OkxPrivateOrderSnapshot previous = orders.putIfAbsent(order.exchangeOrderId(), order);
                    if (previous != null && !previous.equals(order)) complete = false;
                    if (order.clientOrderId() != null) {
                        String previousExchangeId = clientToExchange.putIfAbsent(
                                order.clientOrderId(), order.exchangeOrderId());
                        if (previousExchangeId != null && !previousExchangeId.equals(order.exchangeOrderId())) {
                            complete = false;
                        }
                    }
                }
                fills.addAll(result.fills());
            }
        }
        for (OkxPrivateFillSnapshot fill : fills) {
            // orphan fill 无法构造完整订单事实，必须标记 partial，不以价格/数量/时间猜测身份。
            if (!orders.containsKey(fill.exchangeOrderId())) complete = false;
        }
        List<RemoteOrderSnapshot> normalized = orders.values().stream().map(order -> new RemoteOrderSnapshot(
                order.exchangeOrderId(), order.clientOrderId(), order.instrumentId(), order.side(), order.orderType(),
                order.price(), order.originalQuantity(), order.filledQuantity(), order.status(), order.observedAt(),
                order.sourceOperation().name()
        )).toList();
        return new RemoteSnapshotBatch(normalized, true, complete, 1, recordCount, observedAt);
    }

    private static Instant latest(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }
}
