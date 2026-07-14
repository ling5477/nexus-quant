package com.guidinglight.nexusquant.adapter.okx.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 类型化 OKX private read-only 请求；每个 operation 的 query schema 在本类封闭生成。
 * 调用方不能提供 host、path、method、body 或任意 query map。
 */
public record OkxPrivateReadRequest(
        OkxPrivateReadOperation operation,
        List<String> currencies,
        String instrumentId,
        Instant begin,
        Instant end,
        int limit
) {
    private static final Pattern CURRENCY = Pattern.compile("[A-Z0-9]{2,12}");
    private static final Pattern SPOT_SYMBOL = Pattern.compile("[A-Z0-9]{2,12}-[A-Z0-9]{2,12}");
    private static final int MAX_CURRENCIES = 3;
    private static final int MAX_RECORDS = 100;
    private static final Duration MAX_WINDOW = Duration.ofHours(24);

    public OkxPrivateReadRequest {
        Objects.requireNonNull(operation, "operation must not be null");
        currencies = canonicalCurrencies(operation, currencies);
        instrumentId = canonicalInstrument(operation, instrumentId);
        validateWindow(operation, begin, end);
        limit = canonicalLimit(operation, limit);
    }

    /** 保留 GateW-2 构造合同；reconciliation operation 必须使用命名工厂。 */
    public OkxPrivateReadRequest(OkxPrivateReadOperation operation, List<String> currencies) {
        this(operation, currencies, null, null, null, 0);
    }

    public static OkxPrivateReadRequest accountConfiguration() {
        return new OkxPrivateReadRequest(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ, List.of());
    }

    public static OkxPrivateReadRequest accountBalance(Collection<String> currencies) {
        return new OkxPrivateReadRequest(
                OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ,
                currencies == null ? List.of() : List.copyOf(currencies)
        );
    }

    public static OkxPrivateReadRequest openOrders(String instrumentId, int limit) {
        return new OkxPrivateReadRequest(
                OkxPrivateReadOperation.OKX_SPOT_OPEN_ORDERS_READ,
                List.of(), instrumentId, null, null, limit
        );
    }

    public static OkxPrivateReadRequest orderHistory(String instrumentId, Instant begin, Instant end, int limit) {
        return new OkxPrivateReadRequest(
                OkxPrivateReadOperation.OKX_SPOT_ORDER_HISTORY_READ,
                List.of(), instrumentId, begin, end, limit
        );
    }

    public static OkxPrivateReadRequest recentFills(String instrumentId, Instant begin, Instant end, int limit) {
        return new OkxPrivateReadRequest(
                OkxPrivateReadOperation.OKX_SPOT_RECENT_FILLS_READ,
                List.of(), instrumentId, begin, end, limit
        );
    }

    /** 返回同时参与签名和发送的确定性 path；值已通过窄 schema 校验，无通用 URL encoder。 */
    public String pathWithQuery() {
        return switch (operation) {
            case OKX_ACCOUNT_CONFIGURATION_READ -> operation.path();
            case OKX_ACCOUNT_BALANCE_READ -> operation.path() + "?ccy=" + String.join(",", currencies);
            case OKX_SPOT_OPEN_ORDERS_READ -> operation.path()
                    + "?instType=SPOT&instId=" + instrumentId + "&limit=" + limit;
            case OKX_SPOT_ORDER_HISTORY_READ, OKX_SPOT_RECENT_FILLS_READ -> operation.path()
                    + "?instType=SPOT&instId=" + instrumentId
                    + "&begin=" + begin.toEpochMilli() + "&end=" + end.toEpochMilli() + "&limit=" + limit;
        };
    }

    public boolean reconciliationOperation() {
        return switch (operation) {
            case OKX_SPOT_OPEN_ORDERS_READ, OKX_SPOT_ORDER_HISTORY_READ, OKX_SPOT_RECENT_FILLS_READ -> true;
            default -> false;
        };
    }

    private static List<String> canonicalCurrencies(
            OkxPrivateReadOperation operation,
            Collection<String> candidates
    ) {
        if (operation != OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ) {
            if (candidates != null && !candidates.isEmpty()) {
                throw new IllegalArgumentException("operation does not accept currencies");
            }
            return List.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate == null || candidate.isBlank()) {
                    throw new IllegalArgumentException("currency must not be blank");
                }
                String value = candidate.trim().toUpperCase(Locale.ROOT);
                if (!CURRENCY.matcher(value).matches()) {
                    throw new IllegalArgumentException("currency identifier is invalid");
                }
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("at least one server-allowlisted currency is required");
        }
        if (normalized.size() > MAX_CURRENCIES) {
            throw new IllegalArgumentException("at most 3 currencies are allowed");
        }
        return List.copyOf(normalized);
    }

    private static String canonicalInstrument(OkxPrivateReadOperation operation, String candidate) {
        boolean required = switch (operation) {
            case OKX_SPOT_OPEN_ORDERS_READ, OKX_SPOT_ORDER_HISTORY_READ, OKX_SPOT_RECENT_FILLS_READ -> true;
            default -> false;
        };
        if (!required) {
            if (candidate != null) throw new IllegalArgumentException("operation does not accept instrumentId");
            return null;
        }
        if (candidate == null || candidate.isBlank()) throw new IllegalArgumentException("instrumentId is required");
        String value = candidate.trim().toUpperCase(Locale.ROOT);
        if (!SPOT_SYMBOL.matcher(value).matches()) throw new IllegalArgumentException("SPOT instrumentId is invalid");
        return value;
    }

    private static void validateWindow(OkxPrivateReadOperation operation, Instant begin, Instant end) {
        boolean required = operation == OkxPrivateReadOperation.OKX_SPOT_ORDER_HISTORY_READ
                || operation == OkxPrivateReadOperation.OKX_SPOT_RECENT_FILLS_READ;
        if (!required) {
            if (begin != null || end != null) throw new IllegalArgumentException("operation does not accept time window");
            return;
        }
        Objects.requireNonNull(begin, "begin must not be null");
        Objects.requireNonNull(end, "end must not be null");
        Duration duration = Duration.between(begin, end);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("time window exceeds 24 hours");
        }
    }

    private static int canonicalLimit(OkxPrivateReadOperation operation, int candidate) {
        if (operation == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ
                || operation == OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ) {
            if (candidate != 0) throw new IllegalArgumentException("operation does not accept record limit");
            return 0;
        }
        if (candidate <= 0 || candidate > MAX_RECORDS) {
            throw new IllegalArgumentException("record limit must be between 1 and 100");
        }
        return candidate;
    }
}
