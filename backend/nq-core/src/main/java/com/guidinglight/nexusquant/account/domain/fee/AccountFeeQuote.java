package com.guidinglight.nexusquant.account.domain.fee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 供应方原始符号与研究采用的费用符号分别保存，负费用表示返佣。 */
public record AccountFeeQuote(
        BigDecimal makerProviderRate,
        BigDecimal takerProviderRate,
        String tier,
        Instant providerAt,
        Instant observedAt,
        String source,
        String rateConvention,
        String providerTimeBasis,
        SpotCommissionTerms commissionTerms) {
    public static final String BINANCE_SOURCE = "BINANCE_PRIVATE_SPOT_COMMISSION";
    public static final String BINANCE_CONVENTION = "BINANCE_POSITIVE_COST_V1";

    /** 旧OKX构造及符号/时间合同保持原样，兼容已保存V1观察和冻结快照。 */
    public AccountFeeQuote(
            BigDecimal makerProviderRate,
            BigDecimal takerProviderRate,
            String tier,
            Instant providerAt,
            Instant observedAt) {
        this(
                makerProviderRate,
                takerProviderRate,
                tier,
                providerAt,
                observedAt,
                AccountFeeObservation.SOURCE,
                AccountFeeObservation.CONVENTION,
                "PROVIDER_FEE_TIMESTAMP",
                null);
    }

    /** 原生commission不能压成两个全局费率；未提供等级/费率变更时点保持缺失。 */
    public static AccountFeeQuote binance(
            SpotCommissionTerms terms, Instant responseAt, Instant observedAt) {
        return new AccountFeeQuote(
                null,
                null,
                null,
                responseAt,
                observedAt,
                BINANCE_SOURCE,
                BINANCE_CONVENTION,
                responseAt == null ? "NOT_PROVIDED" : "HTTP_RESPONSE_DATE",
                Objects.requireNonNull(terms));
    }

    /** 旧费率时点不允许未来；HTTP响应Date允许服务器30秒时差，且始终与费率变更时间分开，数据库统一微秒精度。 */
    public AccountFeeQuote {
        Objects.requireNonNull(observedAt);
        if (commissionTerms == null) {
            Objects.requireNonNull(makerProviderRate);
            Objects.requireNonNull(takerProviderRate);
            Objects.requireNonNull(providerAt);
            if (!AccountFeeObservation.SOURCE.equals(source)
                    || !AccountFeeObservation.CONVENTION.equals(rateConvention)
                    || !"PROVIDER_FEE_TIMESTAMP".equals(providerTimeBasis)
                    || makerProviderRate.abs().compareTo(BigDecimal.ONE) > 0
                    || takerProviderRate.abs().compareTo(BigDecimal.ONE) > 0
                    || makerProviderRate.scale() > 12
                    || takerProviderRate.scale() > 12
                    || tier == null
                    || !tier.matches("[A-Za-z0-9_-]{1,32}")
                    || providerAt.isAfter(observedAt)
                    || providerAt.isBefore(observedAt.minusSeconds(3600))) {
                throw new IllegalArgumentException("account fee source fact is invalid or stale");
            }
            makerProviderRate = makerProviderRate.setScale(12);
            takerProviderRate = takerProviderRate.setScale(12);
        } else if (!BINANCE_SOURCE.equals(source)
                || !BINANCE_CONVENTION.equals(rateConvention)
                || makerProviderRate != null
                || takerProviderRate != null
                || tier != null
                || providerAt == null && !"NOT_PROVIDED".equals(providerTimeBasis)
                || providerAt != null
                        && (!"HTTP_RESPONSE_DATE".equals(providerTimeBasis)
                                || providerAt.isAfter(observedAt.plusSeconds(30))
                                || providerAt.isBefore(observedAt.minusSeconds(3600)))) {
            throw new IllegalArgumentException(
                    "account commission source fact is invalid or stale");
        }
        // 与PostgreSQL的精确存储一致，首响应与重启后的历史回执保持同一数值和时间表示。
        if (providerAt != null) providerAt = providerAt.truncatedTo(ChronoUnit.MICROS);
        observedAt = observedAt.truncatedTo(ChronoUnit.MICROS);
    }

    /** OKX正值为返佣、负值为收费，成本取反；条件commission没有全局标量，保持null而非伪造零。 */
    public BigDecimal makerCostRate() {
        return makerProviderRate == null ? null : makerProviderRate.negate();
    }

    /** 与挂单费率保持相同符号转换；零费率保留零，不替换为默认收费。 */
    public BigDecimal takerCostRate() {
        return takerProviderRate == null ? null : takerProviderRate.negate();
    }

    @Override
    public String toString() {
        return "AccountFeeQuote[REDACTED]";
    }
}
