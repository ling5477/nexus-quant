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
 * @param source         数据来源
 * @param syncedAt       最近同步时间
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
        String source,
        Instant syncedAt,
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
                source,
                null,
                null,
                null
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
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
