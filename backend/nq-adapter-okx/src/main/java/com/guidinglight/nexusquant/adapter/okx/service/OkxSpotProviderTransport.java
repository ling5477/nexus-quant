package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * GateY provider 的可注入 transport port。
 *
 * <p>接口只有五个 typed operation；没有 host、URL、method、path、header、credential 或通用
 * execute escape hatch。每个 command 都携带 response read limit；real transport 在分配或完整
 * 读取响应 body 前执行 byte/fill cap，metadata post-check 只作为第二道防线。GateY-6E 已提供
 * credential-scoped typed capability，但默认 Spring/worker runtime 仍不装配。</p>
 */
public interface OkxSpotProviderTransport {

    PlaceResponse placeLimit(PlaceCommand command);

    OrderResponse queryOrder(OrderCommand command);

    CancelResponse cancelOrder(CancelCommand command);

    OrderResponse readOrder(OrderCommand command);

    FillResponse readFills(FillCommand command);

    record TransportContext(
            UUID sessionId,
            String referenceId,
            String traceId,
            String correlationId,
            Instant requestTimestamp
    ) {
        public TransportContext {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            referenceId = requireText(referenceId, "referenceId");
            traceId = requireText(traceId, "traceId");
            correlationId = requireText(correlationId, "correlationId");
            Objects.requireNonNull(requestTimestamp, "requestTimestamp must not be null");
        }
    }

    record PlaceCommand(
            String clientOrderId,
            String instrument,
            Side side,
            BigDecimal price,
            BigDecimal quantity,
            TransportContext context,
            ResponseReadLimit responseLimit
    ) {
        public PlaceCommand {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(responseLimit, "responseLimit must not be null");
        }
    }

    record OrderCommand(
            String clientOrderId,
            String instrument,
            TransportContext context,
            ResponseReadLimit responseLimit
    ) {
        public OrderCommand {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(responseLimit, "responseLimit must not be null");
        }
    }

    record CancelCommand(
            String clientOrderId,
            String instrument,
            BigDecimal confirmedRemainingQuantity,
            TransportContext context,
            ResponseReadLimit responseLimit
    ) {
        public CancelCommand {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(responseLimit, "responseLimit must not be null");
        }
    }

    record FillCommand(
            String clientOrderId,
            String instrument,
            Instant begin,
            Instant end,
            int maxRecords,
            TransportContext context,
            ResponseReadLimit responseLimit
    ) {
        public FillCommand {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(responseLimit, "responseLimit must not be null");
            if (maxRecords <= 0 || maxRecords > responseLimit.maximumFillRecords()) {
                throw new IllegalArgumentException("maxRecords exceeds response read limit");
            }
        }
    }

    /**
     * Future transport 在读取响应内容前必须执行的硬上限。
     */
    record ResponseReadLimit(int maximumResponseBytes, int maximumFillRecords) {
        public ResponseReadLimit {
            if (maximumResponseBytes <= 0) {
                throw new IllegalArgumentException("maximumResponseBytes must be positive");
            }
            if (maximumFillRecords <= 0 || maximumFillRecords > 100) {
                throw new IllegalArgumentException("maximumFillRecords must be between 1 and 100");
            }
        }
    }

    record ResponseMetadata(
            OkxSpotProviderOperation operation,
            int responseBytes,
            String exchangeRequestId,
            Instant observedAt
    ) {
        public ResponseMetadata {
            Objects.requireNonNull(operation, "operation must not be null");
            if (responseBytes < 0) throw new IllegalArgumentException("responseBytes must not be negative");
            exchangeRequestId = nullableBoundedText(exchangeRequestId, "exchangeRequestId", 128);
            Objects.requireNonNull(observedAt, "observedAt must not be null");
        }
    }

    record TransportFailure(SpotProviderError.Category category, boolean mutationMayHaveReachedVenue) {
        public TransportFailure {
            Objects.requireNonNull(category, "category must not be null");
        }
    }

    record RawFill(
            String exchangeTradeId,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal fee,
            String feeCurrency,
            Instant filledAt
    ) {
        public RawFill(String exchangeTradeId, BigDecimal price, BigDecimal quantity, Instant filledAt) {
            this(exchangeTradeId, price, quantity, BigDecimal.ZERO, "USDT", filledAt);
        }
    }

    record RawOrder(
            String clientOrderId,
            String exchangeOrderId,
            String rawState,
            BigDecimal originalQuantity,
            BigDecimal executedQuantity,
            BigDecimal remainingQuantity,
            List<RawFill> fills
    ) {
        public RawOrder {
            fills = List.copyOf(fills == null ? List.of() : fills);
        }
    }

    enum ResponseOutcome {
        ACCEPTED,
        REJECTED,
        ERROR
    }

    record PlaceResponse(
            ResponseMetadata metadata,
            ResponseOutcome outcome,
            RawOrder order,
            TransportFailure failure
    ) {
    }

    record OrderResponse(ResponseMetadata metadata, RawOrder order, TransportFailure failure) {
    }

    record CancelResponse(
            ResponseMetadata metadata,
            ResponseOutcome outcome,
            RawOrder order,
            TransportFailure failure
    ) {
    }

    record FillResponse(
            ResponseMetadata metadata,
            List<RawFill> fills,
            boolean complete,
            TransportFailure failure
    ) {
        public FillResponse {
            fills = List.copyOf(fills == null ? List.of() : fills);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must be a nonblank single-line value");
        }
        return value;
    }

    private static String nullableText(String value, String name) {
        return value == null ? null : requireText(value, name);
    }

    private static String nullableBoundedText(String value, String name, int maximumLength) {
        String normalized = nullableText(value, name);
        if (normalized != null && normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds maximum length");
        }
        return normalized;
    }
}
