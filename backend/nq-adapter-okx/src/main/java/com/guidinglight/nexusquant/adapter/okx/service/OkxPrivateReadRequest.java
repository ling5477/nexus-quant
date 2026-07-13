package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 类型化 OKX private read-only 请求；query 只能由 balance 的 ccy schema 生成。
 */
public record OkxPrivateReadRequest(OkxPrivateReadOperation operation, List<String> currencies) {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z0-9]{2,12}");
    private static final int MAX_CURRENCIES = 3;

    public OkxPrivateReadRequest {
        Objects.requireNonNull(operation, "operation must not be null");
        currencies = canonicalCurrencies(operation, currencies);
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

    /**
     * 返回参与签名和发送的确定性 path；不接受调用方提供的 query map。
     */
    public String pathWithQuery() {
        if (currencies.isEmpty()) {
            return operation.path();
        }
        return operation.path() + "?ccy=" + String.join(",", currencies);
    }

    private static List<String> canonicalCurrencies(
            OkxPrivateReadOperation operation,
            Collection<String> candidates
    ) {
        if (operation == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ) {
            if (candidates != null && !candidates.isEmpty()) {
                throw new IllegalArgumentException("account configuration does not accept query parameters");
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
}
