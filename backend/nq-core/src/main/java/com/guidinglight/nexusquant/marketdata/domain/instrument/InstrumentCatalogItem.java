package com.guidinglight.nexusquant.marketdata.domain.instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * InstrumentCatalogItem 表示系统内部统一的 instrument/symbol 主数据条目。
 * <p>
 * Why:
 * PRE-2 需要把 `exchange symbol / internal symbol / price-qty precision / base-quote asset`
 * 收口成稳定事实，后续 trading、marketdata、frontend selector 都围绕这一条目工作。
 *
 * @param instrumentId   主键，可空；同步输入阶段为空，持久化读取后非空
 * @param exchangeCode   交易所编码，例如 OKX / BINANCE
 * @param instrumentType 产品类型，当前固定为 SPOT
 * @param exchangeSymbol 交易所原生 symbol
 * @param internalSymbol 系统统一 symbol
 * @param baseAsset      base 资产
 * @param quoteAsset     quote 资产
 * @param status         instrument 状态
 * @param tickSize       价格步长
 * @param stepSize       数量步长
 * @param minQuantity    最小下单数量
 * @param maxLimitQuantity 单笔限价单最大数量
 * @param maxMarketSize 单笔市价单最大数量
 * @param maxMarketSizeUnit 市价单最大数量单位；GateW-3 OKX Spot 仅允许 USDT
 * @param maxLimitNotionalUsd 单笔限价单最大 USD amount
 * @param maxMarketNotionalUsd 单笔市价单最大 USD amount
 * @param source         数据来源
 * @param sourceSchemaVersion NQ parser/schema contract 版本，可空
 * @param observedAt     完整公开响应成功解析后的本地观察时间，可空
 * @param syncedAt       最近同步时间
 * @param nextRuleEffectiveAt 下一相关 venue-rule change 的生效时间，可空
 * @param ruleChecksum   canonical venue-rule facts 的 lowercase SHA-256，可空
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 */
public record InstrumentCatalogItem(
        Long instrumentId,
        String exchangeCode,
        String instrumentType,
        String exchangeSymbol,
        String internalSymbol,
        String baseAsset,
        String quoteAsset,
        String status,
        BigDecimal tickSize,
        BigDecimal stepSize,
        BigDecimal minQuantity,
        BigDecimal maxLimitQuantity,
        BigDecimal maxMarketSize,
        String maxMarketSizeUnit,
        BigDecimal maxLimitNotionalUsd,
        BigDecimal maxMarketNotionalUsd,
        String source,
        String sourceSchemaVersion,
        Instant observedAt,
        Instant syncedAt,
        Instant nextRuleEffectiveAt,
        String ruleChecksum,
        Instant createdAt,
        Instant updatedAt
) {

    public InstrumentCatalogItem(
            String exchangeCode,
            String instrumentType,
            String exchangeSymbol,
            String internalSymbol,
            String baseAsset,
            String quoteAsset,
            String status,
            BigDecimal tickSize,
            BigDecimal stepSize,
            BigDecimal minQuantity,
            String source
    ) {
        this(
                null,
                exchangeCode,
                instrumentType,
                exchangeSymbol,
                internalSymbol,
                baseAsset,
                quoteAsset,
                status,
                tickSize,
                stepSize,
                minQuantity,
                null,
                null,
                null,
                null,
                null,
                source,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 兼容 GateW-3 之前的完整持久化构造器；legacy 行的新事实字段保持 null，不能伪造 readiness。
     */
    public InstrumentCatalogItem(
            Long instrumentId,
            String exchangeCode,
            String instrumentType,
            String exchangeSymbol,
            String internalSymbol,
            String baseAsset,
            String quoteAsset,
            String status,
            BigDecimal tickSize,
            BigDecimal stepSize,
            BigDecimal minQuantity,
            String source,
            Instant syncedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                instrumentId,
                exchangeCode,
                instrumentType,
                exchangeSymbol,
                internalSymbol,
                baseAsset,
                quoteAsset,
                status,
                tickSize,
                stepSize,
                minQuantity,
                null,
                null,
                null,
                null,
                null,
                source,
                null,
                null,
                syncedAt,
                null,
                null,
                createdAt,
                updatedAt
        );
    }

    public InstrumentCatalogItem {
        exchangeCode = normalizeRequired(exchangeCode, "exchangeCode");
        instrumentType = normalizeRequired(instrumentType, "instrumentType");
        exchangeSymbol = normalizeRequired(exchangeSymbol, "exchangeSymbol");
        internalSymbol = normalizeRequired(internalSymbol, "internalSymbol");
        baseAsset = normalizeRequired(baseAsset, "baseAsset");
        quoteAsset = normalizeRequired(quoteAsset, "quoteAsset");
        status = normalizeRequired(status, "status");
        source = normalizeRequired(source, "source");
        maxMarketSizeUnit = normalizeOptionalUpper(maxMarketSizeUnit);
        sourceSchemaVersion = normalizeOptional(sourceSchemaVersion);
        ruleChecksum = normalizeOptional(ruleChecksum);
        requirePositive(tickSize, "tickSize");
        requirePositive(stepSize, "stepSize");
        requirePositive(minQuantity, "minQuantity");
        requirePositive(maxLimitQuantity, "maxLimitQuantity");
        requirePositive(maxMarketSize, "maxMarketSize");
        requirePositive(maxLimitNotionalUsd, "maxLimitNotionalUsd");
        requirePositive(maxMarketNotionalUsd, "maxMarketNotionalUsd");
        if ((maxMarketSize == null) != (maxMarketSizeUnit == null)) {
            throw new IllegalArgumentException("maxMarketSize and maxMarketSizeUnit must both be null or present");
        }
        if (maxMarketSizeUnit != null && !"USDT".equals(maxMarketSizeUnit)) {
            throw new IllegalArgumentException("maxMarketSizeUnit must be USDT");
        }
        if (ruleChecksum != null && !ruleChecksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("ruleChecksum must be lowercase SHA-256 hex");
        }
        if (observedAt != null && syncedAt != null && observedAt.isAfter(syncedAt)) {
            throw new IllegalArgumentException("observedAt must not be after syncedAt");
        }
        if (nextRuleEffectiveAt != null
                && (observedAt == null || !nextRuleEffectiveAt.isAfter(observedAt))) {
            throw new IllegalArgumentException("nextRuleEffectiveAt must be after observedAt");
        }
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptionalUpper(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive when present");
        }
    }
}
