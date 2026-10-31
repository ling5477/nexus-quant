package com.guidinglight.nexusquant.account.domain.performance;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 派生测量，不拥有余额、持仓或账务事实；来源明细保存实际投影及估值身份。 */
public record AccountEquitySample(
        UUID sampleId,
        long exchangeAccountId,
        String quoteCurrency,
        Instant sampledAt,
        BigDecimal equity,
        BigDecimal cumulativeNetFlow,
        String status,
        String sourceJson) {
    public AccountEquitySample {
        if (sampleId == null
                || exchangeAccountId <= 0
                || quoteCurrency == null
                || !quoteCurrency.matches("[A-Z0-9]{2,32}")
                || sampledAt == null
                || status == null
                || sourceJson == null)
            throw new IllegalArgumentException("invalid equity measurement identity");
        if ("AVAILABLE".equals(status) && (equity == null || cumulativeNetFlow == null))
            throw new IllegalArgumentException(
                    "available measurement requires equity and funding flow");
    }

    /** 原quoteCurrency字段为已知的账户测量单位；保留兼容别名，不把它当交易对报价币。 */
    @JsonProperty("valuationCurrency")
    public String valuationCurrency() {
        return quoteCurrency;
    }
}
