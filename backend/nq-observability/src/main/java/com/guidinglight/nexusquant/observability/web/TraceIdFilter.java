package com.guidinglight.nexusquant.observability.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TraceIdFilter 负责 HTTP 入口 traceId 透传与生成。
 * <p>
 * Why:
 * docs/CONTRACTS.md 强制要求网关/后端统一 `X-Trace-Id` 贯穿，
 * 骨架阶段先通过 Filter 固化入口行为，后续模块直接复用 MDC 值。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = resolveIncomingTraceId(request);
        String traceId = TraceIdContext.putOrCreate(incomingTraceId);
        request.setAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
        response.setHeader(TraceIdContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdContext.clear();
        }
    }

    private String resolveIncomingTraceId(HttpServletRequest request) {
        String standardHeader = request.getHeader(TraceIdContext.TRACE_ID_HEADER);
        if (standardHeader != null && !standardHeader.isBlank()) {
            return standardHeader.trim();
        }
        // 仅兼容历史 trace header；这是观测链路兼容，不属于业务 legacy account 语义。
        String legacyHeader = request.getHeader(TraceIdContext.LEGACY_TRACE_ID_HEADER);
        return legacyHeader == null || legacyHeader.isBlank() ? null : legacyHeader.trim();
    }
}
