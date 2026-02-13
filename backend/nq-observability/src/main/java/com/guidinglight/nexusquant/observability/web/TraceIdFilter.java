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
 *
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
        String incomingTraceId = request.getHeader(TraceIdContext.TRACE_ID_HEADER);
        String traceId = TraceIdContext.putOrCreate(incomingTraceId);
        response.setHeader(TraceIdContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdContext.clear();
        }
    }
}
