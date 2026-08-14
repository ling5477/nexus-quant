package com.guidinglight.nexusquant.livecontrol.execution.application.provider;

import java.util.Objects;

/** GateY provider 的脱敏错误决策；不携带 raw body、header、URL 或 credential。 */
public record SpotProviderError(
        Category category,
        Certainty certainty,
        boolean queryRetryable,
        boolean mutationRetryable,
        Recommendation recommendation,
        String auditCode
) {
    public SpotProviderError {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(certainty, "certainty must not be null");
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        if (auditCode == null || !auditCode.matches("REAL_[A-Z0-9_]{2,64}")) {
            throw new IllegalArgumentException("auditCode must be a sanitized REAL_* code");
        }
        // GateY-6B 不允许任何自动 mutation retry。
        mutationRetryable = false;
    }

    public static SpotProviderError classify(Category category, boolean mutationMayHaveReachedVenue) {
        Objects.requireNonNull(category, "category must not be null");
        Certainty certainty = indeterminateWhenSent(category) && mutationMayHaveReachedVenue
                ? Certainty.INDETERMINATE : Certainty.DEFINITIVE;
        boolean queryRetryable = certainty == Certainty.INDETERMINATE
                || category == Category.TRANSPORT_TIMEOUT
                || category == Category.HTTP_ERROR
                || category == Category.RATE_LIMITED
                || category == Category.CANCEL_RACE;
        Recommendation recommendation = switch (category) {
            case PERMISSION_DENIED, IP_RESTRICTION -> Recommendation.ENGAGE_KILL_AND_REVIEW;
            case CLOCK_SKEW -> Recommendation.PAUSE_REPAIR_CLOCK_AND_REPREFLIGHT;
            case RATE_LIMITED -> Recommendation.PAUSE_AND_BACKOFF_READS;
            case INSTRUMENT_RESTRICTED, INVALID_PRICE_OR_SIZE -> Recommendation.FREEZE_SYMBOL_AND_REVIEW;
            case INSUFFICIENT_BALANCE -> Recommendation.PAUSE_AND_RECONCILE;
            default -> certainty == Certainty.INDETERMINATE
                    ? Recommendation.PAUSE_AND_QUERY
                    : Recommendation.PAUSE_AND_REVIEW;
        };
        return new SpotProviderError(
                category,
                certainty,
                queryRetryable,
                false,
                recommendation,
                auditCode(category)
        );
    }

    private static boolean indeterminateWhenSent(Category category) {
        return switch (category) {
            case TRANSPORT_TIMEOUT, HTTP_ERROR, RATE_LIMITED, RESPONSE_TOO_LARGE,
                    MALFORMED_RESPONSE, UNKNOWN_RESULT, CANCEL_RACE -> true;
            default -> false;
        };
    }

    private static String auditCode(Category category) {
        return switch (category) {
            case TRANSPORT_TIMEOUT -> "REAL_TRANSPORT_TIMEOUT";
            case HTTP_ERROR -> "REAL_HTTP_ERROR";
            case EXCHANGE_BUSINESS_REJECTION -> "REAL_BUSINESS_REJECTED";
            case PERMISSION_DENIED -> "REAL_PERMISSION_DENIED";
            case IP_RESTRICTION -> "REAL_IP_RESTRICTED";
            case CLOCK_SKEW -> "REAL_CLOCK_SKEW";
            case RATE_LIMITED -> "REAL_RATE_LIMITED";
            case INSUFFICIENT_BALANCE -> "REAL_BALANCE_INSUFFICIENT";
            case INSTRUMENT_RESTRICTED -> "REAL_INSTRUMENT_RESTRICTED";
            case INVALID_PRICE_OR_SIZE -> "REAL_PRICE_SIZE_INVALID";
            case RESPONSE_TOO_LARGE -> "REAL_RESPONSE_TOO_LARGE";
            case MALFORMED_RESPONSE -> "REAL_MALFORMED_RESPONSE";
            case UNKNOWN_RESULT -> "REAL_RESULT_UNKNOWN";
            case CANCEL_RACE -> "REAL_CANCEL_RACE";
        };
    }

    public enum Category {
        TRANSPORT_TIMEOUT,
        HTTP_ERROR,
        EXCHANGE_BUSINESS_REJECTION,
        PERMISSION_DENIED,
        IP_RESTRICTION,
        CLOCK_SKEW,
        RATE_LIMITED,
        INSUFFICIENT_BALANCE,
        INSTRUMENT_RESTRICTED,
        INVALID_PRICE_OR_SIZE,
        RESPONSE_TOO_LARGE,
        MALFORMED_RESPONSE,
        UNKNOWN_RESULT,
        CANCEL_RACE
    }

    public enum Certainty {
        DEFINITIVE,
        INDETERMINATE
    }

    public enum Recommendation {
        PAUSE_AND_QUERY,
        PAUSE_AND_REVIEW,
        PAUSE_AND_RECONCILE,
        PAUSE_AND_BACKOFF_READS,
        PAUSE_REPAIR_CLOCK_AND_REPREFLIGHT,
        FREEZE_SYMBOL_AND_REVIEW,
        ENGAGE_KILL_AND_REVIEW
    }
}
