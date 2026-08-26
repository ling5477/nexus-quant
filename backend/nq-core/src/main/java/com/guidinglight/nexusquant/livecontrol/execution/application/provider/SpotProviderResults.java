package com.guidinglight.nexusquant.livecontrol.execution.application.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** GateY provider 的 normalized、sanitized 结果；不拥有 Order/Trade/Position/Ledger 真相。 */
public final class SpotProviderResults {
    private static final int MAX_FILL_REFERENCES = 100;

    private SpotProviderResults() {
    }

    public enum OrderState {
        NOT_FOUND,
        OPEN,
        PARTIALLY_FILLED,
        FILLED,
        CANCELED,
        REJECTED,
        UNKNOWN
    }

    public enum MutationOutcome {
        ACCEPTED,
        DEFINITIVELY_REJECTED,
        UNKNOWN
    }

    public enum CancelDisposition {
        MUTATION_ACCEPTED,
        DEFINITIVELY_REJECTED,
        UNKNOWN,
        QUERY_REQUIRED,
        NO_MUTATION_TERMINAL
    }

    public record FillReference(
            String exchangeTradeId,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal fee,
            String feeCurrency,
            Instant filledAt
    ) {
        public FillReference {
            exchangeTradeId = boundedText(exchangeTradeId, "exchangeTradeId", 128);
            requirePositive(price, "price");
            requirePositive(quantity, "quantity");
            Objects.requireNonNull(fee, "fee must not be null");
            feeCurrency = boundedText(feeCurrency, "feeCurrency", 16);
            Objects.requireNonNull(filledAt, "filledAt must not be null");
        }

        public FillReference(String exchangeTradeId, BigDecimal price, BigDecimal quantity, Instant filledAt) {
            this(exchangeTradeId, price, quantity, BigDecimal.ZERO, "USDT", filledAt);
        }
    }

    public record OrderObservation(
            OrderState state,
            String clientOrderId,
            String exchangeOrderId,
            BigDecimal originalQuantity,
            BigDecimal executedQuantity,
            BigDecimal remainingQuantity,
            List<FillReference> fillReferences,
            SpotProviderError error,
            Instant observedAt
    ) {
        public OrderObservation {
            Objects.requireNonNull(state, "state must not be null");
            clientOrderId = boundedText(clientOrderId, "clientOrderId", ProviderClientOrderId.OKX_MAX_LENGTH);
            exchangeOrderId = nullableBoundedText(exchangeOrderId, "exchangeOrderId", 128);
            requireNonNegative(originalQuantity, "originalQuantity");
            requireNonNegative(executedQuantity, "executedQuantity");
            requireNonNegative(remainingQuantity, "remainingQuantity");
            fillReferences = boundedFillReferences(fillReferences);
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            if (executedQuantity.add(remainingQuantity).compareTo(originalQuantity) != 0) {
                throw new IllegalArgumentException("executed + remaining must equal original quantity");
            }
            if (state != OrderState.NOT_FOUND && state != OrderState.UNKNOWN
                    && originalQuantity.signum() <= 0) {
                throw new IllegalArgumentException("known order state requires positive original quantity");
            }
            if (state == OrderState.OPEN && remainingQuantity.signum() <= 0) {
                throw new IllegalArgumentException("open order requires positive remaining quantity");
            }
            if (state == OrderState.PARTIALLY_FILLED
                    && (executedQuantity.signum() <= 0 || remainingQuantity.signum() <= 0)) {
                throw new IllegalArgumentException("partial fill requires executed and remaining quantity");
            }
            if (state == OrderState.FILLED
                    && (executedQuantity.signum() <= 0 || remainingQuantity.signum() != 0)) {
                throw new IllegalArgumentException("filled order requires executed quantity and zero remainder");
            }
            if (state == OrderState.NOT_FOUND
                    && (exchangeOrderId != null || originalQuantity.signum() != 0
                    || executedQuantity.signum() != 0 || remainingQuantity.signum() != 0
                    || !fillReferences.isEmpty())) {
                throw new IllegalArgumentException("not-found observation must not carry venue order facts");
            }
            if (state == OrderState.UNKNOWN && error == null) {
                throw new IllegalArgumentException("unknown observation requires a typed error");
            }
        }
    }

    public record MutationResult(
            MutationOutcome outcome,
            OrderObservation observation,
            SpotProviderError error,
            boolean queryByClientOrderIdRequired
    ) {
        public MutationResult {
            Objects.requireNonNull(outcome, "outcome must not be null");
            switch (outcome) {
                case ACCEPTED -> {
                    if (observation == null || observation.state() == OrderState.UNKNOWN || error != null) {
                        throw new IllegalArgumentException("accepted mutation requires a known sanitized observation");
                    }
                    queryByClientOrderIdRequired = false;
                }
                case DEFINITIVELY_REJECTED -> {
                    if (error == null || error.certainty() != SpotProviderError.Certainty.DEFINITIVE) {
                        throw new IllegalArgumentException("definitive rejection requires a definitive typed error");
                    }
                    queryByClientOrderIdRequired = false;
                }
                case UNKNOWN -> {
                    queryByClientOrderIdRequired = true;
                    if (error == null || error.certainty() != SpotProviderError.Certainty.INDETERMINATE) {
                        throw new IllegalArgumentException("unknown mutation requires indeterminate typed error");
                    }
                }
            }
        }
    }

    public record CancelResult(
            CancelDisposition disposition,
            OrderObservation observation,
            SpotProviderError error,
            boolean queryByClientOrderIdRequired
    ) {
        public CancelResult {
            Objects.requireNonNull(disposition, "disposition must not be null");
            switch (disposition) {
                case MUTATION_ACCEPTED -> {
                    if (observation == null || observation.state() != OrderState.CANCELED || error != null) {
                        throw new IllegalArgumentException("accepted cancel requires a canceled observation");
                    }
                    queryByClientOrderIdRequired = false;
                }
                case DEFINITIVELY_REJECTED -> {
                    if (error == null || error.certainty() != SpotProviderError.Certainty.DEFINITIVE) {
                        throw new IllegalArgumentException("definitive cancel rejection requires a definitive error");
                    }
                }
                case UNKNOWN -> {
                    if (error == null || error.certainty() != SpotProviderError.Certainty.INDETERMINATE) {
                        throw new IllegalArgumentException("unknown cancel requires an indeterminate typed error");
                    }
                    queryByClientOrderIdRequired = true;
                }
                case QUERY_REQUIRED -> queryByClientOrderIdRequired = true;
                case NO_MUTATION_TERMINAL -> {
                    if (observation == null || !Set.of(
                            OrderState.FILLED, OrderState.CANCELED,
                            OrderState.REJECTED, OrderState.NOT_FOUND).contains(observation.state())) {
                        throw new IllegalArgumentException("terminal cancel result requires a terminal observation");
                    }
                    queryByClientOrderIdRequired = false;
                }
            }
        }
    }

    public record FillPage(
            String clientOrderId,
            List<FillReference> fills,
            boolean complete,
            SpotProviderError error,
            Instant observedAt
    ) {
        public FillPage {
            clientOrderId = boundedText(clientOrderId, "clientOrderId", ProviderClientOrderId.OKX_MAX_LENGTH);
            fills = boundedFillReferences(fills);
            Objects.requireNonNull(observedAt, "observedAt must not be null");
        }
    }

    /** 当前venue clock的只读观测；不携带或刷新交易授权、余额、费率与市场事实。 */
    public record ClockObservation(
            Instant serverTime,
            Instant localClockMidpoint,
            java.time.Duration observedSkew,
            SpotProviderError error,
            Instant observedAt
    ) {
        public ClockObservation {
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            if (error == null) {
                Objects.requireNonNull(serverTime, "serverTime must not be null");
                Objects.requireNonNull(localClockMidpoint, "localClockMidpoint must not be null");
                Objects.requireNonNull(observedSkew, "observedSkew must not be null");
                if (!java.time.Duration.between(localClockMidpoint, serverTime).equals(observedSkew)) {
                    throw new IllegalArgumentException("clock observation is internally inconsistent");
                }
            } else if (serverTime != null || localClockMidpoint != null || observedSkew != null) {
                throw new IllegalArgumentException("failed clock observation must not carry venue clock facts");
            }
        }
    }

    private static List<FillReference> boundedFillReferences(List<FillReference> values) {
        List<FillReference> copied = List.copyOf(values == null ? List.of() : values);
        if (copied.size() > MAX_FILL_REFERENCES) {
            throw new IllegalArgumentException("fill references exceed maximum of 100");
        }
        Set<String> identities = new HashSet<>();
        for (FillReference fill : copied) {
            Objects.requireNonNull(fill, "fill reference must not be null");
            if (!identities.add(fill.exchangeTradeId())) {
                throw new IllegalArgumentException("duplicate exchangeTradeId is not allowed");
            }
        }
        return copied;
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String boundedText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must be a bounded single-line value");
        }
        return value;
    }

    private static String nullableBoundedText(String value, String name, int maxLength) {
        return value == null ? null : boundedText(value, name, maxLength);
    }
}
