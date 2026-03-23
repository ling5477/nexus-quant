package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;

/**
 * OkxErrorClassifier 统一把 OKX 异常映射到 adapter-api 结果分类。
 */
final class OkxErrorClassifier {

    private OkxErrorClassifier() {
    }

    static AdapterError toAdapterError(OkxApiException exception) {
        AdapterResultCategory category = classify(exception);
        return new AdapterError(
                code(exception),
                exception.getMessage(),
                category,
                isRetryable(category)
        );
    }

    private static AdapterResultCategory classify(OkxApiException exception) {
        if (exception.errorKind() == OkxErrorCode.ORDER_NOT_FOUND) {
            return AdapterResultCategory.NOT_FOUND;
        }
        String errorCode = exception.errorCode();
        int httpStatus = exception.httpStatus();
        if ("HTTP_TIMEOUT".equals(errorCode)) {
            return AdapterResultCategory.RETRYABLE_FAILURE;
        }
        if ("50011".equals(errorCode) || httpStatus == 429) {
            return AdapterResultCategory.THROTTLED;
        }
        if ("50113".equals(errorCode) || "50110".equals(errorCode) || "50035".equals(errorCode)) {
            return AdapterResultCategory.AUTH_FAILURE;
        }
        if (httpStatus >= 500 || "HTTP_CLIENT_ERROR".equals(errorCode)) {
            return AdapterResultCategory.REMOTE_UNAVAILABLE;
        }
        return AdapterResultCategory.FATAL_FAILURE;
    }

    private static boolean isRetryable(AdapterResultCategory category) {
        return category == AdapterResultCategory.RETRYABLE_FAILURE
                || category == AdapterResultCategory.THROTTLED
                || category == AdapterResultCategory.REMOTE_UNAVAILABLE;
    }

    private static String code(OkxApiException exception) {
        return exception.errorCode() == null || exception.errorCode().isBlank()
                ? "OKX_API_ERROR"
                : exception.errorCode();
    }
}
