package com.guidinglight.nexusquant.account.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.exception.ExchangeAccountNotFoundException;
import com.guidinglight.nexusquant.account.domain.performance.AccountEquitySample;
import com.guidinglight.nexusquant.account.domain.performance.AccountPerformanceSummary;
import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;
import com.guidinglight.nexusquant.account.domain.performance.PerformanceStatistics;
import com.guidinglight.nexusquant.account.domain.port.AccountPerformanceRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.common.currency.CurrencyValuationService;
import com.guidinglight.nexusquant.common.currency.CurrencyValuationService.Price;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 采样只写派生测量，读取不触发采样或任何交易副作用。 */
public class AccountPerformanceService {
    private final ExchangeAccountRepository accounts;
    private final AccountPerformanceRepository repository;
    private final ObjectMapper mapper;
    private final CurrencyValuationService valuations;
    private final AccountPerformanceCalculator calculator = new AccountPerformanceCalculator();

    public AccountPerformanceService(
            ExchangeAccountRepository accounts,
            AccountPerformanceRepository repository,
            ObjectMapper mapper,
            CurrencyValuationService valuations) {
        this.accounts = accounts;
        this.repository = repository;
        this.mapper = mapper;
        this.valuations = valuations;
    }

    /** 限制范围为有序且不超过366天；只读授权账户的真实样本，超过1000条明确返回截断状态。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public View read(long ownerUserId, long accountId, String currency, Instant from, Instant to) {
        checkCurrency(currency);
        if (from == null
                || to == null
                || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) {
            throw new IllegalArgumentException(
                    "performance range must be ordered and within 366 days");
        }
        var account =
                accounts.findByIdForOwner(ownerUserId, accountId)
                        .orElseThrow(() -> new ExchangeAccountNotFoundException(accountId));
        var rows = repository.findRange(accountId, currency, from, to, 1001);
        boolean limited = rows.size() > 1000;
        var summary =
                calculator.summarize(
                        accountId, currency, from, to, rows.stream().limit(1000).toList());
        var sharpe =
                PerformanceStatistics.sharpe(
                        summary.samples().stream()
                                .map(
                                        s ->
                                                new PerformanceStatistics.Point(
                                                        s.sampledAt(),
                                                        "AVAILABLE".equals(s.status())
                                                                ? s.equity()
                                                                : null,
                                                        s.cumulativeNetFlow()))
                                .toList(),
                        900);
        var outcomes = ClosedTradeStatistics.unavailable("INSUFFICIENT_SAMPLES");
        if (summary.coverageStart() != null && summary.coverageEnd() != null) {
            var history = repository.readTradeHistory(account, currency, summary.coverageEnd());
            outcomes =
                    ClosedTradeStatistics.calculate(
                            history.trades(),
                            currency,
                            summary.coverageStart(),
                            summary.coverageEnd(),
                            history.completeBasis());
        }
        return new View(summary, limited, 900, sharpe, outcomes);
    }

    /** 在一致数据库快照中形成单账户估值与资金流测量；缺少可靠来源时记录缺失状态，不输出假收益。 */
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public void sample(long accountId, String currency, Instant now) {
        checkCurrency(currency);
        // PG时间事实为微秒；向下截断避免写入四舍五入到查询截止时间之后的采样。
        now = now.truncatedTo(ChronoUnit.MICROS);
        var account = accounts.findById(accountId).orElse(null);
        if (account == null
                || !"ACTIVE".equals(account.status())
                || account.legacyAccountId() == null
                || repository.recentlySampled(accountId, currency, now)) return;
        var input = repository.readValuation(account, currency, now);
        final Instant asOf = now;
        var values =
                input.assets().stream()
                        .map(
                                asset ->
                                        valuations.value(
                                                asset.currency(),
                                                asset.balance(),
                                                currency,
                                                asOf,
                                                Duration.ofSeconds(7200),
                                                asset.barId() == null
                                                        ? null
                                                        : new Price(
                                                                asset.currency(),
                                                                currency,
                                                                asset.price(),
                                                                asset.priceAt(),
                                                                asset.availableAt(),
                                                                asset.priceSource(),
                                                                "marketdata_bar:" + asset.barId())))
                        .toList();
        BigDecimal total = valuations.total(currency, values);
        boolean valued = input.projectionComplete() && total != null;
        BigDecimal equity = valued ? total : null;
        String status =
                !valued
                        ? (input.assets().isEmpty() ? "NO_BALANCE" : "VALUATION_INCOMPLETE")
                        : input.cumulativeNetFlow() == null ? "FLOW_INCOMPLETE" : "AVAILABLE";
        try {
            // 原投影与资金链原样保留；追加原币换算、可见时点和显式舍入依据，不把未知估值伪装完整。
            var provenance =
                    new LinkedHashMap<String, Object>(
                            Map.of(
                                    "scope",
                                    "NQ_MANAGED_ACCOUNT",
                                    "tradeEnv",
                                    account.tradeEnv(),
                                    "legacyAccountId",
                                    account.legacyAccountId(),
                                    "projectionComplete",
                                    input.projectionComplete(),
                                    "assets",
                                    input.assets(),
                                    "flowStatus",
                                    input.flowStatus(),
                                    "valuationMaxAgeSeconds",
                                    7200,
                                    "fundingEntries",
                                    input.fundingEntries(),
                                    "fundingEntryCount",
                                    input.fundingEntryCount()));
            provenance.put("valuationCurrency", currency);
            provenance.put("valuationTime", now);
            provenance.put("valuationSource", "NQ_MANAGED_ACCOUNT_PROJECTIONS");
            provenance.put("valuationMethod", CurrencyValuationService.METHOD);
            provenance.put("valuationStatus", valued ? "VERIFIED" : "PARTIAL");
            provenance.put(
                    "valuationDetails",
                    Map.of(
                            "assets",
                            values,
                            "amountScale",
                            CurrencyValuationService.AMOUNT_SCALE,
                            "rounding",
                            "HALF_UP",
                            "roundingPoint",
                            "FINAL_TOTAL"));
            String source = mapper.writeValueAsString(provenance);
            repository.save(
                    new AccountEquitySample(
                            UUID.randomUUID(),
                            accountId,
                            currency,
                            now,
                            equity,
                            input.cumulativeNetFlow(),
                            status,
                            source));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "account valuation provenance serialization failed", ex);
        }
    }

    private void checkCurrency(String currency) {
        // 既有隔离SIM注资以USDT计价；跨币种历史资金流没有可靠估值时不换算成假收益。
        if (!valuations.supportsTarget(currency))
            throw new IllegalArgumentException("supported performance quote currency is USDT");
    }

    public record View(
            AccountPerformanceSummary summary,
            boolean historyLimited,
            int samplingIntervalSeconds,
            PerformanceStatistics.Sharpe sharpe,
            ClosedTradeStatistics.Result outcomes) {}
}
