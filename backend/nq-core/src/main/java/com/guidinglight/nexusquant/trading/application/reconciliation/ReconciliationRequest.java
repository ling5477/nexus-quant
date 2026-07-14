package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * GateW-3 internal-only 请求。交易所、产品类型、symbol/page/record/window 上限均在构造时固定校验。
 */
public record ReconciliationRequest(
        long accountId,
        long ownerId,
        long exchangeAccountId,
        String exchange,
        String instrumentType,
        String tradeEnvironment,
        List<String> symbols,
        Instant windowStart,
        Instant windowEnd,
        int pageLimit,
        int recordLimit
) {
    public static final int MAX_SYMBOLS = 3;
    public static final int MAX_PAGES = 1;
    public static final int MAX_RECORDS_PER_PAGE = 100;
    public static final Duration MAX_WINDOW = Duration.ofHours(24);
    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9]{2,12}-[A-Z0-9]{2,12}");

    public ReconciliationRequest {
        if (accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (ownerId <= 0 || exchangeAccountId <= 0) {
            throw new IllegalArgumentException("ownerId and exchangeAccountId must be positive");
        }
        if (!"OKX".equals(exchange == null ? null : exchange.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("only OKX is supported");
        }
        exchange = "OKX";
        if (!"SPOT".equals(instrumentType == null ? null : instrumentType.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("only SPOT is supported");
        }
        instrumentType = "SPOT";
        String environment = tradeEnvironment == null ? null : tradeEnvironment.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SIM", "LIVE").contains(environment)) {
            throw new IllegalArgumentException("tradeEnvironment must be SIM or LIVE");
        }
        tradeEnvironment = environment;
        symbols = canonicalSymbols(symbols);
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        Duration window = Duration.between(windowStart, windowEnd);
        if (window.isZero() || window.isNegative() || window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("time window exceeds 24 hours");
        }
        if (pageLimit != MAX_PAGES) {
            throw new IllegalArgumentException("pageLimit must be exactly 1");
        }
        if (recordLimit <= 0 || recordLimit > MAX_RECORDS_PER_PAGE) {
            throw new IllegalArgumentException("recordLimit must be between 1 and 100");
        }
    }

    private static List<String> canonicalSymbols(Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("at least one server-allowlisted symbol is required");
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                throw new IllegalArgumentException("symbol must not be blank");
            }
            String value = candidate.trim().toUpperCase(Locale.ROOT);
            if (!SYMBOL.matcher(value).matches()) {
                throw new IllegalArgumentException("symbol is invalid");
            }
            normalized.add(value);
        }
        if (normalized.size() > MAX_SYMBOLS) {
            throw new IllegalArgumentException("at most 3 symbols are allowed");
        }
        return List.copyOf(normalized);
    }
}
