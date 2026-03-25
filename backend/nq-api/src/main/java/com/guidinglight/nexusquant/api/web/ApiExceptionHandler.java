package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * ApiExceptionHandler 统一处理 `nq-api` 的对外异常映射。
 * <p>
 * Why:
 * Step 2 要把参数错误、业务拒绝、状态冲突与系统错误统一输出成稳定结构，
 * 避免各个 Controller 自己拼装 `ResponseEntity` 或散落的异常格式。
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(violation -> new ApiFieldError(
                        extractLeafNode(violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString()),
                        safeRejectedValue(violation.getInvalidValue()),
                        violation.getMessage()
                ))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "request body is missing or malformed", request, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                ex.getParameterName() + " parameter is required",
                request,
                List.of(new ApiFieldError(ex.getParameterName(), null, "parameter is required"))
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "STATE_CONFLICT", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(build(status, mapCode(status), ex.getReason(), request, List.of()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleException(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal server error", request, List.of());
    }

    private ApiFieldError toFieldError(FieldError fieldError) {
        return new ApiFieldError(
                fieldError.getField(),
                safeRejectedValue(fieldError.getRejectedValue()),
                fieldError.getDefaultMessage()
        );
    }

    private ApiErrorResponse build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> fieldErrors
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message == null || message.isBlank() ? status.getReasonPhrase() : message,
                request.getRequestURI(),
                resolveTraceId(request),
                fieldErrors
        );
    }

    private String mapCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case CONFLICT -> "STATE_CONFLICT";
            case UNPROCESSABLE_ENTITY -> "BUSINESS_REJECTED";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "API_ERROR";
        };
    }

    private String extractLeafNode(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "request";
        }
        int dotIndex = propertyPath.lastIndexOf('.');
        int bracketIndex = propertyPath.lastIndexOf(']');
        int separatorIndex = Math.max(dotIndex, bracketIndex);
        if (separatorIndex < 0 || separatorIndex + 1 >= propertyPath.length()) {
            return propertyPath;
        }
        String candidate = propertyPath.substring(separatorIndex + 1);
        return candidate.startsWith(".") ? candidate.substring(1) : candidate;
    }

    private Object safeRejectedValue(Object rejectedValue) {
        if (rejectedValue == null) {
            return null;
        }
        if (rejectedValue instanceof Number || rejectedValue instanceof Boolean) {
            return rejectedValue;
        }
        String text = String.valueOf(rejectedValue);
        return text.length() <= 128 ? text : text.substring(0, 125) + "...";
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object requestTraceId = request.getAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE);
        if (requestTraceId instanceof String requestTrace && !requestTrace.isBlank()) {
            return requestTrace;
        }
        String traceId = TraceIdContext.get();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String mdcTraceId = MDC.get(TraceIdContext.TRACE_ID_KEY);
        return (mdcTraceId == null || mdcTraceId.isBlank()) ? null : mdcTraceId;
    }
}
