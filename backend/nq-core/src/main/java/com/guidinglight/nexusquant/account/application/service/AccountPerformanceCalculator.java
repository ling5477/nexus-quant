package com.guidinglight.nexusquant.account.application.service;

import com.guidinglight.nexusquant.account.domain.performance.AccountEquitySample;
import com.guidinglight.nexusquant.account.domain.performance.AccountPerformanceSummary;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 以期初权益为基准扣除外部净注资；不是TWR，不把独立SIM或其他期间指标相加。 */
public final class AccountPerformanceCalculator {
    private static final MathContext DIVISION = MathContext.DECIMAL128;

    /** 已验证的一次性现金注资作为明确基准；它不是市场历史样本，也不能让缺失估值变为零收益。 */
    public AccountPerformanceSummary summarizeFromFunding(
            long accountId, String currency, Instant fundedAt, BigDecimal funding,
            Instant to, List<AccountEquitySample> valuedSamples) {
        if (fundedAt == null || funding == null || funding.signum() <= 0
                || to == null || !fundedAt.isBefore(to) || valuedSamples.size() > 999
                || valuedSamples.stream().anyMatch(s -> !s.sampledAt().isAfter(fundedAt)
                    || s.cumulativeNetFlow() == null || s.cumulativeNetFlow().compareTo(funding) != 0))
            throw new IllegalArgumentException("invalid single funding performance basis");
        if (valuedSamples.isEmpty())
            return summarize(accountId, currency, fundedAt, to, List.of());
        var samples = new java.util.ArrayList<AccountEquitySample>();
        samples.add(new AccountEquitySample(new java.util.UUID(0, 0), accountId, currency,
                fundedAt, funding, funding, "AVAILABLE", "{\"basis\":\"CANONICAL_CASH_FUNDING\"}"));
        samples.addAll(valuedSamples);
        return summarize(accountId, currency, fundedAt, to, samples);
    }

    /** 只计算同账户同币种的有界样本；完整样本不足两点或资金流缺失时，收益与回撤保持不可用。 */
    public AccountPerformanceSummary summarize(
            long accountId,
            String currency,
            Instant from,
            Instant to,
            List<AccountEquitySample> input) {
        if (from == null || to == null || !from.isBefore(to) || input.size() > 1000)
            throw new IllegalArgumentException("invalid bounded performance range");
        if (input.stream()
                .anyMatch(
                        sample ->
                                sample.exchangeAccountId() != accountId
                                        || !sample.quoteCurrency().equals(currency))) {
            throw new IllegalArgumentException("performance measurement scope mismatch");
        }
        var samples =
                input.stream()
                        .filter(
                                sample ->
                                        !sample.sampledAt().isBefore(from)
                                                && !sample.sampledAt().isAfter(to))
                        .sorted(Comparator.comparing(AccountEquitySample::sampledAt))
                        .toList();
        var first = samples.isEmpty() ? null : samples.getFirst();
        var last = samples.isEmpty() ? null : samples.getLast();
        boolean complete =
                samples.size() >= 2
                        && samples.stream()
                                .allMatch(
                                        sample ->
                                                "AVAILABLE".equals(sample.status())
                                                        && sample.equity() != null
                                                        && sample.cumulativeNetFlow() != null);
        if (!complete)
            return new AccountPerformanceSummary(
                    accountId,
                    currency,
                    "NQ_MANAGED_ACCOUNT",
                    "INITIAL_EQUITY_FLOW_ADJUSTED",
                    from,
                    to,
                    first == null ? null : first.sampledAt(),
                    last == null ? null : last.sampledAt(),
                    samples.isEmpty() ? "NO_DATA" : "INSUFFICIENT_OR_PARTIAL",
                    first == null ? null : first.equity(),
                    last == null ? null : last.equity(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    samples);
        // 把净注资从权益变化中扣除，避免把充值当作盈利；期初权益非正时不计算收益比例。
        BigDecimal flow = last.cumulativeNetFlow().subtract(first.cumulativeNetFlow());
        BigDecimal pnl = last.equity().subtract(first.equity()).subtract(flow);
        BigDecimal ratio =
                first.equity().signum() > 0 ? pnl.divide(first.equity(), DIVISION) : null;
        BigDecimal peak = first.equity(),
                drawdown = BigDecimal.ZERO,
                drawdownRatio = BigDecimal.ZERO;
        // 每个样本按相对期初的资金流调整后找峰值；不把原始权益波动与外部资金流混算回撤。
        for (var sample : samples) {
            BigDecimal adjusted =
                    sample.equity()
                            .subtract(
                                    sample.cumulativeNetFlow().subtract(first.cumulativeNetFlow()));
            peak = peak.max(adjusted);
            BigDecimal loss = peak.subtract(adjusted);
            drawdown = drawdown.max(loss);
            if (peak.signum() > 0) drawdownRatio = drawdownRatio.max(loss.divide(peak, DIVISION));
        }
        return new AccountPerformanceSummary(
                accountId,
                currency,
                "NQ_MANAGED_ACCOUNT",
                "INITIAL_EQUITY_FLOW_ADJUSTED",
                from,
                to,
                first.sampledAt(),
                last.sampledAt(),
                "AVAILABLE",
                first.equity(),
                last.equity(),
                flow,
                pnl,
                ratio,
                drawdown,
                first.equity().signum() > 0 ? drawdownRatio : null,
                samples);
    }
}
