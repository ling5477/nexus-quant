package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * ShadowRunListResponse 是 GateR-8 Shadow Run 只读列表响应。
 *
 * <p>分页字段仅描述本地查询窗口；items 不包含写侧 action、credential、private payload 或交易授权字段。
 */
@Schema(name = "ShadowRunListResponse", description = "GateR-8 read-only Shadow Run list response")
public record ShadowRunListResponse(
        List<ShadowRunListItemResponse> items,
        int limit,
        int offset,
        long total
) {
    public static ShadowRunListResponse from(ShadowRunListResult result) {
        return new ShadowRunListResponse(
                result.items().stream()
                        .map(ShadowRunListItemResponse::from)
                        .toList(),
                result.limit(),
                result.offset(),
                result.total()
        );
    }
}
