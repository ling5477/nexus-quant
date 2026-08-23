package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.RawFill;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport.RawOrder;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderError;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillReference;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderObservation;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** OKX raw state 到 NQ provider observation 的 fail-closed translation。 */
final class OkxVenueStateTranslator {

    OrderObservation translate(
            RawOrder raw,
            String expectedClientOrderId,
            Instant observedAt,
            boolean mutationMayHaveReachedVenue,
            int maximumFillRecords
    ) {
        if (maximumFillRecords <= 0 || maximumFillRecords > 100) {
            throw new IllegalArgumentException("maximumFillRecords must be between 1 and 100");
        }
        if (raw == null || raw.clientOrderId() == null
                || !expectedClientOrderId.equals(raw.clientOrderId()) || raw.rawState() == null) {
            return unknown(expectedClientOrderId, observedAt, mutationMayHaveReachedVenue,
                    SpotProviderError.Category.MALFORMED_RESPONSE);
        }
        if (raw.fills().size() > maximumFillRecords) {
            return unknown(expectedClientOrderId, observedAt, mutationMayHaveReachedVenue,
                    SpotProviderError.Category.RESPONSE_TOO_LARGE);
        }
        OrderState state = switch (raw.rawState().trim().toLowerCase(Locale.ROOT)) {
            case "live", "open" -> OrderState.OPEN;
            case "partially_filled", "partial_fill" -> OrderState.PARTIALLY_FILLED;
            case "filled" -> OrderState.FILLED;
            case "canceled", "cancelled" -> OrderState.CANCELED;
            case "rejected", "order_failed" -> OrderState.REJECTED;
            default -> OrderState.UNKNOWN;
        };
        if (state == OrderState.UNKNOWN) {
            return unknown(expectedClientOrderId, observedAt, mutationMayHaveReachedVenue,
                    SpotProviderError.Category.UNKNOWN_RESULT);
        }
        try {
            return new OrderObservation(
                    state,
                    expectedClientOrderId,
                    raw.exchangeOrderId(),
                    nonNegative(raw.originalQuantity()),
                    nonNegative(raw.executedQuantity()),
                    nonNegative(raw.remainingQuantity()),
                    fillReferences(raw.fills()),
                    null,
                    observedAt
            );
        } catch (IllegalArgumentException ex) {
            return unknown(expectedClientOrderId, observedAt, mutationMayHaveReachedVenue,
                    SpotProviderError.Category.MALFORMED_RESPONSE);
        }
    }

    OrderObservation notFound(String clientOrderId, Instant observedAt) {
        return new OrderObservation(
                OrderState.NOT_FOUND,
                clientOrderId,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                null,
                observedAt
        );
    }

    private OrderObservation unknown(
            String clientOrderId,
            Instant observedAt,
            boolean mutationMayHaveReachedVenue,
            SpotProviderError.Category category
    ) {
        return new OrderObservation(
                OrderState.UNKNOWN,
                clientOrderId,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                SpotProviderError.classify(
                        category,
                        mutationMayHaveReachedVenue),
                observedAt
        );
    }

    private static List<FillReference> fillReferences(List<RawFill> fills) {
        return (fills == null ? List.<RawFill>of() : fills).stream()
                .map(fill -> new FillReference(
                        fill.exchangeTradeId(),
                        nonNegative(fill.price()),
                        nonNegative(fill.quantity()),
                        java.util.Objects.requireNonNull(fill.fee(), "fee must not be null"),
                        fill.feeCurrency(),
                        fill.filledAt()))
                .toList();
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        return value;
    }
}
