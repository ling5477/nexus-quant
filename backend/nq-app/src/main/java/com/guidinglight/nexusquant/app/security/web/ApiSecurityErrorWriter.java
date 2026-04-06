package com.guidinglight.nexusquant.app.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * ApiSecurityErrorWriter 统一输出安全链中的 401/403 错误结构。
 * <p>
 * Why:
 * 该 writer 只服务于 `nq-app` 的安全装配，不应继续占用 `auth.api.web`
 * 命名空间，否则会和 `nq-api` / `nq-security` 一起形成 split package。
 */
public class ApiSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                resolveTraceId(request),
                List.of()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object requestTraceId = request.getAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE);
        if (requestTraceId instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        return TraceIdContext.get();
    }
}

