package com.guidinglight.nexusquant.common.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 有源单步估值；不获取行情、不推断稳定币等价，也不修改原币余额或账务。 */
public final class CurrencyValuationService {
    public static final String METHOD = "VISIBLE_DIRECT_PRICE_V1";
    public static final int AMOUNT_SCALE = 16;
    private final CurrencyRegistry registry;
    private volatile List<CurrencyDefinition> definitions;

    public CurrencyValuationService(CurrencyRegistry registry) {
        // 组件装配不能读取数据库或凭据；实际估值时才读取目录，避免破坏只读诊断的零读取启动边界。
        this.registry = Objects.requireNonNull(registry);
    }

    /** 目录定义不代表算法支持；V1只接受USDT目标，不开放无来源的展示币种切换。 */
    public boolean supportsTarget(String currency) {
        CurrencyCode.require(currency);
        return "USDT".equals(currency)
                && definitions().stream().anyMatch(c -> c.code().equals(currency));
    }

    private List<CurrencyDefinition> definitions() {
        var result = definitions;
        if (result == null) {
            synchronized (this) {
                result = definitions;
                if (result == null) {
                    result = List.copyOf(registry.list());
                    definitions = result;
                }
            }
        }
        return result;
    }

    /** 原币同目标币的恒等计价不需要汇率；不同币种即使名称相近也必须提供同向真实价格。 */
    public AssetValue value(
            String currency,
            BigDecimal amount,
            String target,
            Instant asOf,
            Duration maxPriceAge,
            Price price) {
        CurrencyCode.require(currency);
        CurrencyCode.require(target);
        if (amount == null
                || amount.precision() > 78
                || Math.abs((long) amount.scale()) > 36
                || asOf == null
                || maxPriceAge == null
                || maxPriceAge.isNegative()
                || maxPriceAge.isZero()
                || maxPriceAge.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("INVALID_VALUATION_INPUT");
        }
        if (!supportsTarget(target)) return missing(currency, amount, target, "TARGET_UNSUPPORTED");
        if (currency.equals(target)) {
            return new AssetValue(
                    currency, amount, target, amount, BigDecimal.ONE, "IDENTITY", null);
        }
        if (amount.signum() == 0) {
            return new AssetValue(
                    currency, amount, target, BigDecimal.ZERO, null, "ZERO_BALANCE", null);
        }
        if (price == null) return missing(currency, amount, target, "PRICE_MISSING");
        if (!currency.equals(price.baseCurrency())
                || !target.equals(price.quoteCurrency())
                || price.rate() == null
                || price.rate().signum() <= 0
                || price.rate().precision() > 78
                || Math.abs((long) price.rate().scale()) > 36
                || price.sourceId() == null
                || price.sourceId().isBlank()
                || price.sourceId().length() > 128
                || price.source() == null
                || price.source().isBlank()
                || price.source().length() > 128
                || price.observedAt() == null
                || price.visibleAt() == null
                || price.visibleAt().isBefore(price.observedAt())) {
            return missing(currency, amount, target, "PRICE_INVALID");
        }
        if (price.observedAt().isAfter(asOf) || price.visibleAt().isAfter(asOf)) {
            return missing(currency, amount, target, "PRICE_NOT_VISIBLE");
        }
        if (price.observedAt().isBefore(asOf.minus(maxPriceAge))) {
            return missing(currency, amount, target, "PRICE_EXPIRED");
        }
        // 逐项保留精确乘积；总额只在落入正式numeric(78,16)边界前统一舍入一次。
        return new AssetValue(
                currency,
                amount,
                target,
                amount.multiply(price.rate()),
                price.rate(),
                "DIRECT_PRICE",
                price);
    }

    /** 缺失项令总额不可用；不把缺价资产按零计入，也不容许混合目标币种。 */
    public BigDecimal total(String target, List<AssetValue> values) {
        CurrencyCode.require(target);
        if (!supportsTarget(target)) return null;
        if (values.isEmpty() || values.size() > 100) return null;
        if (values.stream().anyMatch(v -> !target.equals(v.valuationCurrency()))) {
            throw new IllegalArgumentException("VALUATION_CURRENCY_MISMATCH");
        }
        if (values.stream().anyMatch(v -> v.value() == null)) return null;
        BigDecimal result =
                values.stream()
                        .map(AssetValue::value)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (result.precision() > 78)
            throw new IllegalArgumentException("VALUATION_AMOUNT_OVERFLOW");
        return result;
    }

    private static AssetValue missing(
            String currency, BigDecimal amount, String target, String status) {
        return new AssetValue(currency, amount, target, null, null, status, null);
    }

    /** sourceId指向实际价格事实；原币、目标币、事件与可见时间不可互换。 */
    public record Price(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            Instant observedAt,
            Instant visibleAt,
            String source,
            String sourceId) {}

    public record AssetValue(
            String currency,
            BigDecimal amount,
            String valuationCurrency,
            BigDecimal value,
            BigDecimal conversionRate,
            String status,
            Price price) {}
}
