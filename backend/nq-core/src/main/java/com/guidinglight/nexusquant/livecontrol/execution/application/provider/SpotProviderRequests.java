package com.guidinglight.nexusquant.livecontrol.execution.application.provider;

import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentAction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** GateY provider application port 的封闭 typed request 集合。 */
public final class SpotProviderRequests {
    private static final Pattern INSTRUMENT = Pattern.compile("[A-Z0-9]{2,12}-[A-Z0-9]{2,12}");
    private static final int MAX_FILL_RECORDS = 100;
    private static final Duration MAX_FILL_WINDOW = Duration.ofHours(24);

    private SpotProviderRequests() {
    }

    public enum Venue {
        OKX_SPOT;

        public static Venue require(String value) {
            if (!"OKX_SPOT".equals(value)) {
                throw new IllegalArgumentException("only OKX_SPOT is supported");
            }
            return OKX_SPOT;
        }
    }

    public enum OrderType {
        LIMIT;

        public static OrderType require(String value) {
            if (!"LIMIT".equals(value)) {
                throw new IllegalArgumentException("only LIMIT is supported");
            }
            return LIMIT;
        }
    }

    public enum Side {
        BUY,
        SELL;

        public static Side require(String value) {
            try {
                return valueOf(requireText(value, "side").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("side must be BUY or SELL", ex);
            }
        }
    }

    public enum TimestampSource {
        SYSTEM_UTC,
        TRUSTED_UTC_CLOCK,
        MONOTONIC_DERIVED_UTC
    }

    /**
     * 时钟合同只消费调用方提供的 observation 与阈值；本层不读取远端时间，也不内置 pilot 阈值。
     * null observedSkew 表示尚未观测，必须 fail closed。
     */
    public record ClockContract(
            TimestampSource timestampSource,
            Instant requestTimestamp,
            Instant observationAt,
            Duration observedSkew,
            Duration maximumSkew,
            Duration maximumObservationAge
    ) {
        public ClockContract {
            Objects.requireNonNull(timestampSource, "timestampSource must not be null");
            Objects.requireNonNull(requestTimestamp, "requestTimestamp must not be null");
            Objects.requireNonNull(observationAt, "observationAt must not be null");
            requirePositive(maximumSkew, "maximumSkew");
            requirePositive(maximumObservationAge, "maximumObservationAge");
        }

        public boolean healthyAt(Instant now) {
            Objects.requireNonNull(now, "now must not be null");
            if (observedSkew == null || observedSkew.abs().compareTo(maximumSkew) > 0) {
                return false;
            }
            Duration observationAge = Duration.between(observationAt, now);
            if (observationAge.isNegative() || observationAge.compareTo(maximumObservationAge) > 0) {
                return false;
            }
            return Duration.between(requestTimestamp, now).abs().compareTo(maximumSkew) <= 0;
        }
    }

    public record RequestContext(
            UUID sessionId,
            String referenceId,
            String traceId,
            String correlationId,
            ClockContract clock
    ) {
        public RequestContext {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            referenceId = boundedText(referenceId, "referenceId", 128);
            traceId = boundedText(traceId, "traceId", 128);
            correlationId = boundedText(correlationId, "correlationId", 128);
            Objects.requireNonNull(clock, "clock must not be null");
        }
    }

    public record PlaceLimit(
            UUID intentId,
            ProviderClientOrderId clientOrderId,
            Venue venue,
            String instrument,
            Side side,
            OrderType orderType,
            BigDecimal price,
            BigDecimal quantity,
            RequestContext context
    ) {
        public PlaceLimit {
            Objects.requireNonNull(intentId, "intentId must not be null");
            Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
            if (!intentId.equals(clientOrderId.intentId())) {
                throw new IllegalArgumentException("clientOrderId belongs to a different intent");
            }
            if (venue != Venue.OKX_SPOT) throw new IllegalArgumentException("only OKX_SPOT is supported");
            instrument = canonicalInstrument(instrument);
            Objects.requireNonNull(side, "side must not be null");
            if (orderType != OrderType.LIMIT) throw new IllegalArgumentException("only LIMIT is supported");
            requirePositive(price, "price");
            requirePositive(quantity, "quantity");
            Objects.requireNonNull(context, "context must not be null");
        }

        public static PlaceLimit fromIntent(ExecutionIntent intent, RequestContext context) {
            Objects.requireNonNull(intent, "intent must not be null");
            if (intent.action() != ExecutionIntentAction.PLACE) {
                throw new IllegalArgumentException("PLACE intent is required");
            }
            if (!intent.sessionId().equals(context.sessionId())) {
                throw new IllegalArgumentException("context session does not match intent session");
            }
            return new PlaceLimit(
                    intent.intentId(),
                    ProviderClientOrderId.fromIntent(intent),
                    Venue.OKX_SPOT,
                    intent.symbol(),
                    Side.require(intent.side()),
                    OrderType.require(intent.orderType()),
                    intent.limitPrice(),
                    intent.quantity(),
                    context
            );
        }
    }

    public record OrderQuery(
            ProviderClientOrderId clientOrderId,
            Venue venue,
            String instrument,
            RequestContext context
    ) {
        public OrderQuery {
            Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
            if (venue != Venue.OKX_SPOT) throw new IllegalArgumentException("only OKX_SPOT is supported");
            instrument = canonicalInstrument(instrument);
            Objects.requireNonNull(context, "context must not be null");
        }
    }

    public record Cancel(
            OrderQuery order,
            SpotProviderResults.OrderObservation confirmedObservation,
            PartialCancelPolicy partialFillPolicy
    ) {
        public Cancel {
            Objects.requireNonNull(order, "order must not be null");
            Objects.requireNonNull(confirmedObservation, "confirmedObservation must not be null");
            Objects.requireNonNull(partialFillPolicy, "partialFillPolicy must not be null");
            if (!order.clientOrderId().value().equals(confirmedObservation.clientOrderId())) {
                throw new IllegalArgumentException("confirmed observation belongs to another order");
            }
        }
    }

    public enum PartialCancelPolicy {
        QUERY_FIRST,
        ALLOW_CONFIRMED_REMAINDER
    }

    public record FillQuery(
            OrderQuery order,
            Instant begin,
            Instant end,
            int maxRecords
    ) {
        public FillQuery {
            Objects.requireNonNull(order, "order must not be null");
            Objects.requireNonNull(begin, "begin must not be null");
            Objects.requireNonNull(end, "end must not be null");
            Duration window = Duration.between(begin, end);
            if (window.isZero() || window.isNegative() || window.compareTo(MAX_FILL_WINDOW) > 0) {
                throw new IllegalArgumentException("fill window must be positive and no longer than 24 hours");
            }
            if (maxRecords <= 0 || maxRecords > MAX_FILL_RECORDS) {
                throw new IllegalArgumentException("maxRecords must be between 1 and 100");
            }
        }
    }

    public record ResponseBounds(int maximumResponseBytes, int maximumFillRecords) {
        public ResponseBounds {
            if (maximumResponseBytes <= 0) {
                throw new IllegalArgumentException("maximumResponseBytes must be positive");
            }
            if (maximumFillRecords <= 0 || maximumFillRecords > MAX_FILL_RECORDS) {
                throw new IllegalArgumentException("maximumFillRecords must be between 1 and 100");
            }
        }
    }

    private static String canonicalInstrument(String value) {
        String normalized = requireText(value, "instrument").trim().toUpperCase(Locale.ROOT);
        if (!INSTRUMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("instrument must be an OKX SPOT identifier");
        }
        return normalized;
    }

    private static String boundedText(String value, String name, int maxLength) {
        String normalized = requireText(value, name);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds maximum length");
        }
        return normalized;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must be a nonblank single-line value");
        }
        return value;
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
