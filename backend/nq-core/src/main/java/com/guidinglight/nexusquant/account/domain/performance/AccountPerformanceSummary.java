package com.guidinglight.nexusquant.account.domain.performance;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 收益、回撤与图表使用同一账户、同一币种和同一已采样覆盖区间。 */
public record AccountPerformanceSummary(
        long exchangeAccountId,
        String quoteCurrency,
        String scope,
        String calculation,
        Instant requestedStart,
        Instant requestedEnd,
        Instant coverageStart,
        Instant coverageEnd,
        String status,
        BigDecimal initialEquity,
        BigDecimal finalEquity,
        BigDecimal netFlow,
        BigDecimal pnl,
        BigDecimal returnRatio,
        BigDecimal maxDrawdown,
        BigDecimal maxDrawdownRatio,
        List<AccountEquitySample> samples) {
    /** 明确账户金额与比例的单位边界；旧quoteCurrency响应继续兼容原调用方。 */
    @JsonProperty("valuationCurrency")
    public String valuationCurrency() {
        return quoteCurrency;
    }
}
