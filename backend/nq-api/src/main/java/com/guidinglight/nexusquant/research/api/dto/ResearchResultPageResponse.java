package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.ResearchResultPage;

import java.util.List;
import java.util.function.Function;

/** 只包装真实总数与窗口，结果对象仍使用既有正式DTO，金融字段不转换成浮点。 */
public record ResearchResultPageResponse<T>(List<T> items, long total, int limit, int offset) {
    public static <S, T> ResearchResultPageResponse<T> from(
            ResearchResultPage<S> page, Function<S, T> mapper) {
        return new ResearchResultPageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
