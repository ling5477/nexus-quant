package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.BacktestConfigPage;

import java.util.List;

/** 保留原配置字段与金额精度，只新增真实总数及读取窗口，不替换旧数组合同。 */
public record BacktestConfigPageResponse(
        List<BacktestConfigResponse> items, long total, int limit, int offset) {
    public static BacktestConfigPageResponse from(BacktestConfigPage page) {
        return new BacktestConfigPageResponse(
                page.items().stream().map(BacktestConfigResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
