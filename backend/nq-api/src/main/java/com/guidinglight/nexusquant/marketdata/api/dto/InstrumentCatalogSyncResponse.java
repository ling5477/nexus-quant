package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * InstrumentCatalogSyncResponse 描述 instrument sync 的最小返回体。
 */
@Schema(name = "InstrumentCatalogSyncResponse", description = "instrument catalog 同步结果")
public record InstrumentCatalogSyncResponse(
        List<String> exchangeCodes,
        int rowsRead,
        int rowsInserted,
        int rowsUpdated,
        Instant startedAt,
        Instant finishedAt
) {

    public static InstrumentCatalogSyncResponse from(InstrumentCatalogSyncResult result) {
        return new InstrumentCatalogSyncResponse(
                result.exchangeCodes(),
                result.rowsRead(),
                result.rowsInserted(),
                result.rowsUpdated(),
                result.startedAt(),
                result.finishedAt()
        );
    }
}
