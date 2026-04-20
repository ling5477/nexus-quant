package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.port.TradingCancelGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayFailure;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayResultCategory;
import com.guidinglight.nexusquant.trading.application.port.TradingOrderStatusSnapshot;
import com.guidinglight.nexusquant.trading.application.port.TradingPlaceGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AdapterBackedTradingVenueGateway 把 `adapter-api` 契约映射成 trading application 可消费的内部语义。
 * <p>
 * Why:
 * PRE-1 的目标不是删除 adapter，而是把 adapter request/ack/query 细节从 `nq-core` 隔离出去。
 * 该实现作为 anti-corruption boundary 留在 runtime 侧，负责：
 * 1) 按 venue 路由到具体 TradingAdapter
 * 2) 组装 adapter request/query
 * 3) 把 adapter result 映射成 core 内部 gateway 结果
 * 4) 在远端异常时统一降级为可审计的 `REMOTE_UNAVAILABLE`
 */
@Component
public class AdapterBackedTradingVenueGateway implements TradingVenueGateway {

    private final Map<String, TradingAdapter> tradingAdapters;
    private final Clock clock;

    @Autowired
    public AdapterBackedTradingVenueGateway(Collection<TradingAdapter> tradingAdapters) {
        this(tradingAdapters, Clock.systemUTC());
    }

    AdapterBackedTradingVenueGateway(Collection<TradingAdapter> tradingAdapters, Clock clock) {
        this.tradingAdapters = tradingAdapters.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        adapter -> normalizeVenue(adapter.venue()),
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("duplicate trading adapter for venue: " + left.venue());
                        }
                ));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public TradingPlaceGatewayResult placeOrder(OrderRecord order, PlaceOrderRequest request) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(request, "request must not be null");
        TradingAdapter tradingAdapter = requiredTradingAdapter(order.venue());
        try {
            AdapterOrderAck adapterAck = tradingAdapter.placeOrder(new AdapterOrderRequest(
                    request.requestId(),
                    order.orderId(),
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    request.idempotencyKey(),
                    order.side(),
                    order.type(),
                    order.price(),
                    order.qty(),
                    null,
                    request.timeInForce(),
                    request.source(),
                    order.strategyRunId(),
                    request.traceId()
            ));
            return new TradingPlaceGatewayResult(
                    adapterAck.accepted(),
                    adapterAck.exchangeOrderId(),
                    adapterAck.externalStatus(),
                    mapCategory(resolveAckCategory(adapterAck)),
                    toFailure(adapterAck.error()),
                    adapterAck.ackTs(),
                    adapterAck.tradeEnv()
            );
        } catch (RuntimeException ex) {
            return unavailablePlaceResult(ex);
        }
    }

    @Override
    public TradingCancelGatewayResult cancelOrder(OrderRecord order, CancelOrderRequest request) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(request, "request must not be null");
        TradingAdapter tradingAdapter = requiredTradingAdapter(order.venue());
        try {
            AdapterCancelAck cancelAck = tradingAdapter.cancelOrder(new AdapterCancelRequest(
                    request.requestId(),
                    order.orderId(),
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    order.externalOrderId(),
                    request.reason(),
                    request.traceId()
            ));
            return new TradingCancelGatewayResult(
                    cancelAck.accepted(),
                    mapCategory(resolveAckCategory(cancelAck)),
                    toFailure(cancelAck.error()),
                    cancelAck.ts(),
                    cancelAck.tradeEnv()
            );
        } catch (RuntimeException ex) {
            return unavailableCancelResult(ex);
        }
    }

    @Override
    public TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String traceId) {
        Objects.requireNonNull(order, "order must not be null");
        TradingAdapter tradingAdapter = requiredTradingAdapter(order.venue());
        try {
            AdapterOrderSnapshot snapshot = tradingAdapter.getOrder(new AdapterOrderQuery(
                    order.accountId(),
                    order.venue(),
                    order.symbol(),
                    order.clientOrderId(),
                    order.externalOrderId(),
                    traceId
            ));
            return new TradingOrderStatusSnapshot(
                    snapshot.exchangeOrderId(),
                    snapshot.externalStatus(),
                    mapCategory(resolveSnapshotCategory(snapshot)),
                    toFailure(snapshot.error()),
                    snapshot.updateTs(),
                    snapshot.tradeEnv()
            );
        } catch (RuntimeException ex) {
            return new TradingOrderStatusSnapshot(
                    order.externalOrderId(),
                    "UNKNOWN",
                    TradingGatewayResultCategory.REMOTE_UNAVAILABLE,
                    failureFromException(ex),
                    Instant.now(clock),
                    null
            );
        }
    }

    private TradingAdapter requiredTradingAdapter(String venue) {
        TradingAdapter tradingAdapter = tradingAdapters.get(normalizeVenue(venue));
        if (tradingAdapter == null) {
            throw new IllegalArgumentException("TradingAdapter not configured for venue: " + venue);
        }
        return tradingAdapter;
    }

    private TradingPlaceGatewayResult unavailablePlaceResult(RuntimeException ex) {
        return new TradingPlaceGatewayResult(
                false,
                null,
                "UNKNOWN",
                TradingGatewayResultCategory.REMOTE_UNAVAILABLE,
                failureFromException(ex),
                Instant.now(clock),
                null
        );
    }

    private TradingCancelGatewayResult unavailableCancelResult(RuntimeException ex) {
        return new TradingCancelGatewayResult(
                false,
                TradingGatewayResultCategory.REMOTE_UNAVAILABLE,
                failureFromException(ex),
                Instant.now(clock),
                null
        );
    }

    private TradingGatewayFailure failureFromException(RuntimeException ex) {
        return new TradingGatewayFailure(
                "ADAPTER_CALL_FAILED",
                ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage(),
                true
        );
    }

    private TradingGatewayFailure toFailure(AdapterError error) {
        if (error == null) {
            return null;
        }
        return new TradingGatewayFailure(error.code(), error.message(), error.retryable());
    }

    private AdapterResultCategory resolveAckCategory(AdapterOrderAck adapterAck) {
        if (adapterAck.resultCategory() != null) {
            return adapterAck.resultCategory();
        }
        return adapterAck.accepted() ? AdapterResultCategory.ACCEPTED : resolveErrorCategory(adapterAck.error());
    }

    private AdapterResultCategory resolveAckCategory(AdapterCancelAck cancelAck) {
        if (cancelAck.resultCategory() != null) {
            return cancelAck.resultCategory();
        }
        return cancelAck.accepted() ? AdapterResultCategory.ACCEPTED : resolveErrorCategory(cancelAck.error());
    }

    private AdapterResultCategory resolveSnapshotCategory(AdapterOrderSnapshot snapshot) {
        if (snapshot.resultCategory() != null) {
            return snapshot.resultCategory();
        }
        return snapshot.error() == null ? AdapterResultCategory.SUCCESS : resolveErrorCategory(snapshot.error());
    }

    private AdapterResultCategory resolveErrorCategory(AdapterError error) {
        return error == null || error.category() == null ? AdapterResultCategory.FATAL_FAILURE : error.category();
    }

    private TradingGatewayResultCategory mapCategory(AdapterResultCategory category) {
        if (category == null) {
            return TradingGatewayResultCategory.FATAL_FAILURE;
        }
        return switch (category) {
            case SUCCESS -> TradingGatewayResultCategory.SUCCESS;
            case ACCEPTED -> TradingGatewayResultCategory.ACCEPTED;
            case NOT_FOUND -> TradingGatewayResultCategory.NOT_FOUND;
            case DEFERRED -> TradingGatewayResultCategory.DEFERRED;
            case RETRYABLE_FAILURE -> TradingGatewayResultCategory.RETRYABLE_FAILURE;
            case FATAL_FAILURE -> TradingGatewayResultCategory.FATAL_FAILURE;
            case THROTTLED -> TradingGatewayResultCategory.THROTTLED;
            case AUTH_FAILURE -> TradingGatewayResultCategory.AUTH_FAILURE;
            case REMOTE_UNAVAILABLE -> TradingGatewayResultCategory.REMOTE_UNAVAILABLE;
        };
    }

    private String normalizeVenue(String venue) {
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException("venue must not be blank");
        }
        return venue.trim().toUpperCase(Locale.ROOT);
    }
}
