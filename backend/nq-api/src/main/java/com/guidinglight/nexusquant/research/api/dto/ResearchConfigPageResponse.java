package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.ResearchConfigPage;

import java.util.List;

/** 返回真实总数及当前读取窗口；旧数组响应仍由原接口提供。 */
public record ResearchConfigPageResponse(
        List<ResearchConfigResponse> items, long total, int limit, int offset) {
    public static ResearchConfigPageResponse from(ResearchConfigPage page) {
        return new ResearchConfigPageResponse(
                page.items().stream().map(ResearchConfigResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
