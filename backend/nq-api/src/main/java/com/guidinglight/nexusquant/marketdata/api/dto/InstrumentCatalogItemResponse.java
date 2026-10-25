package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * InstrumentCatalogItemResponse 描述前端 selector / instruments 页消费的正式 instrument 条目。
 */
@Schema(name = "InstrumentCatalogItemResponse", description = "正式 instrument catalog 条目")
public record InstrumentCatalogItemResponse(
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

    public static InstrumentCatalogItemResponse from(InstrumentCatalogItem item) {
        return new InstrumentCatalogItemResponse(
                item.instrumentId(),
                item.exchangeCode(),
                item.instrumentType(),
                item.exchangeSymbol(),
                item.internalSymbol(),
                item.baseAsset(),
                item.quoteAsset(),
                item.status(),
                item.tickSize(),
                item.stepSize(),
                item.minQuantity(),
                item.source(),
                item.syncedAt(),
                item.createdAt(),
                item.updatedAt()
        );
    }
}
