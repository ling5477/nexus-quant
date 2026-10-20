package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变、版本化的 LIVE 会话风险规则定义。运行期 risk decision 继续归既有 risk_events owner。
 */
public record RiskLimitSet(
        UUID id,
        int version,
        BigDecimal capitalCap,
        BigDecimal maxOrderNotional,
        BigDecimal maxSymbolPositionNotional,
        BigDecimal maxDailyRealizedLoss,
        BigDecimal maxDailyTotalLoss,
        int maxOpenOrders,
        int maxIntradayOrders,
        List<String> symbolAllowlist,
        int maxSessionDurationSeconds,
        BigDecimal spreadLimitBps,
        BigDecimal slippageLimitBps,
        int maxMarketDataAgeMs,
        int minDataCoverageBps,
        long createdBy,
        Instant createdAt
) {
    public static final String DIGEST_SCHEMA = "risk-limit-set.v1";
    public static final String EFFECTIVE_SCOPE = "LIVE_SESSION_OKX_SPOT";
    public static final String QUOTE_CURRENCY = "USDT";

    public RiskLimitSet {
        Objects.requireNonNull(id, "id must not be null");
        capitalCap = CanonicalDigestSupport.money(capitalCap, "capitalCap");
        maxOrderNotional = CanonicalDigestSupport.money(maxOrderNotional, "maxOrderNotional");
        maxSymbolPositionNotional = CanonicalDigestSupport.money(
                maxSymbolPositionNotional, "maxSymbolPositionNotional");
        maxDailyRealizedLoss = CanonicalDigestSupport.money(maxDailyRealizedLoss, "maxDailyRealizedLoss");
        maxDailyTotalLoss = CanonicalDigestSupport.money(maxDailyTotalLoss, "maxDailyTotalLoss");
        spreadLimitBps = CanonicalDigestSupport.money(spreadLimitBps, "spreadLimitBps");
        slippageLimitBps = CanonicalDigestSupport.money(slippageLimitBps, "slippageLimitBps");
        symbolAllowlist = normalizeSymbols(symbolAllowlist);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        require(version > 0, "version must be positive");
        require(createdBy > 0, "createdBy must be positive");
        require(capitalCap.signum() > 0 && capitalCap.compareTo(new BigDecimal("10000.00000000")) <= 0,
                "capitalCap is out of range");
        require(maxOrderNotional.signum() > 0 && maxOrderNotional.compareTo(capitalCap) <= 0
                        && maxOrderNotional.compareTo(new BigDecimal("1000.00000000")) <= 0,
                "maxOrderNotional is out of range");
        require(maxSymbolPositionNotional.signum() > 0
                        && maxSymbolPositionNotional.compareTo(capitalCap) <= 0,
                "maxSymbolPositionNotional is out of range");
        require(maxDailyRealizedLoss.signum() > 0 && maxDailyRealizedLoss.compareTo(capitalCap) <= 0,
                "maxDailyRealizedLoss is out of range");
        require(maxDailyTotalLoss.compareTo(maxDailyRealizedLoss) >= 0
                        && maxDailyTotalLoss.compareTo(capitalCap) <= 0,
                "maxDailyTotalLoss is out of range");
        require(maxOpenOrders >= 1 && maxOpenOrders <= 20, "maxOpenOrders is out of range");
        require(maxIntradayOrders >= maxOpenOrders && maxIntradayOrders <= 200,
                "maxIntradayOrders is out of range");
        require(maxSessionDurationSeconds >= 60 && maxSessionDurationSeconds <= 14400,
                "maxSessionDurationSeconds is out of range");
        require(spreadLimitBps.signum() >= 0 && spreadLimitBps.compareTo(new BigDecimal("1000.00000000")) <= 0,
                "spreadLimitBps is out of range");
        require(slippageLimitBps.signum() >= 0
                        && slippageLimitBps.compareTo(new BigDecimal("1000.00000000")) <= 0,
                "slippageLimitBps is out of range");
        require(maxMarketDataAgeMs >= 1 && maxMarketDataAgeMs <= 5000,
                "maxMarketDataAgeMs is out of range");
        require(minDataCoverageBps >= 1 && minDataCoverageBps <= 10000,
                "minDataCoverageBps is out of range");
    }

    public String canonicalDigest() {
        return RiskLimitSetCanonicalEncoder.digest(this);
    }

    private static List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            throw new IllegalArgumentException("symbolAllowlist must not be null");
        }
        List<String> normalized = symbols.stream()
                .map(value -> Objects.requireNonNull(value, "symbol must not be null").trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        require(normalized.size() >= 1 && normalized.size() <= 2,
                "symbolAllowlist must contain one or two unique symbols");
        require(normalized.stream().allMatch(value -> value.matches("[A-Z0-9]{2,20}-USDT")),
                "symbolAllowlist contains an invalid OKX Spot symbol");
        return normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
