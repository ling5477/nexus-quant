package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetPage;

import java.util.List;

/** 数据集单行沿用正式响应，不以另造简化对象丢失来源、覆盖范围或质量事实。 */
public record MarketdataDatasetPageResponse(
        List<MarketdataDatasetResponse> items, long total, int limit, int offset) {
    public static MarketdataDatasetPageResponse from(MarketdataDatasetPage page) {
        return new MarketdataDatasetPageResponse(
                page.items().stream().map(MarketdataDatasetResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
