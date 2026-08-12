package com.guidinglight.nexusquant.livecontrol.domain;

import java.util.stream.Collectors;

/** `risk-limit-set.v1` 的确定性 canonical encoder。 */
public final class RiskLimitSetCanonicalEncoder {

    private RiskLimitSetCanonicalEncoder() {
    }

    public static String digest(RiskLimitSet value) {
        return CanonicalDigestSupport.sha256(encode(value));
    }

    public static String encode(RiskLimitSet value) {
        String symbols = value.symbolAllowlist().stream()
                .map(CanonicalDigestSupport::quote)
                .collect(Collectors.joining(",", "[", "]"));
        return "{" +
                "\"schemaVersion\":" + CanonicalDigestSupport.quote(RiskLimitSet.DIGEST_SCHEMA) +
                ",\"effectiveScope\":" + CanonicalDigestSupport.quote(RiskLimitSet.EFFECTIVE_SCOPE) +
                ",\"version\":" + value.version() +
                ",\"quoteCurrency\":" + CanonicalDigestSupport.quote(RiskLimitSet.QUOTE_CURRENCY) +
                ",\"capitalCap\":" + CanonicalDigestSupport.decimal(value.capitalCap()) +
                ",\"maxOrderNotional\":" + CanonicalDigestSupport.decimal(value.maxOrderNotional()) +
                ",\"maxSymbolPositionNotional\":"
                + CanonicalDigestSupport.decimal(value.maxSymbolPositionNotional()) +
                ",\"maxDailyRealizedLoss\":" + CanonicalDigestSupport.decimal(value.maxDailyRealizedLoss()) +
                ",\"maxDailyTotalLoss\":" + CanonicalDigestSupport.decimal(value.maxDailyTotalLoss()) +
                ",\"maxOpenOrders\":" + value.maxOpenOrders() +
                ",\"maxIntradayOrders\":" + value.maxIntradayOrders() +
                ",\"symbolAllowlist\":" + symbols +
                ",\"orderTypeAllowlist\":[\"LIMIT\"]" +
                ",\"maxSessionDurationSeconds\":" + value.maxSessionDurationSeconds() +
                ",\"spreadLimitBps\":" + CanonicalDigestSupport.decimal(value.spreadLimitBps()) +
                ",\"slippageLimitBps\":" + CanonicalDigestSupport.decimal(value.slippageLimitBps()) +
                ",\"maxMarketDataAgeMs\":" + value.maxMarketDataAgeMs() +
                ",\"minDataCoverageBps\":" + value.minDataCoverageBps() +
                ",\"requiredDataSource\":\"OKX_PRIMARY\"" +
                ",\"dataQualityAction\":\"BLOCK\"}";
    }
}
