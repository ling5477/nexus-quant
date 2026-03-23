package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;

/**
 * BinanceErrorClassifier 统一把 Binance REST 异常映射到 adapter-api 结果分类。
 */
final class BinanceErrorClassifier {

    private BinanceErrorClassifier() {
    }

    static AdapterError toAdapterError(BinanceApiException exception) {
        AdapterResultCategory category = classify(exception);
        return new AdapterError(
                code(exception),
                message(exception),
                category,
                isRetryable(category)
        );
    }

    private static AdapterResultCategory classify(BinanceApiException exception) {
        String errorCode = exception.errorCode();
        int httpStatus = exception.httpStatus();
        if ("BINANCE_CREDENTIALS_MISSING".equals(errorCode)
                || "-2015".equals(errorCode)
                || "-2014".equals(errorCode)
                || "-1022".equals(errorCode)) {
            return AdapterResultCategory.AUTH_FAILURE;
        }
        if ("-2013".equals(errorCode) || "-2011".equals(errorCode)) {
            return AdapterResultCategory.DEFERRED;
        }
        if ("-1003".equals(errorCode) || httpStatus == 429 || httpStatus == 418) {
            return AdapterResultCategory.THROTTLED;
        }
        if ("HTTP_TIMEOUT".equals(errorCode) || "-1021".equals(errorCode)) {
            return AdapterResultCategory.RETRYABLE_FAILURE;
        }
        if ("HTTP_CLIENT_ERROR".equals(errorCode) || httpStatus >= 500) {
            return AdapterResultCategory.REMOTE_UNAVAILABLE;
        }
        return AdapterResultCategory.FATAL_FAILURE;
    }

    private static boolean isRetryable(AdapterResultCategory category) {
        return category == AdapterResultCategory.RETRYABLE_FAILURE
                || category == AdapterResultCategory.THROTTLED
                || category == AdapterResultCategory.REMOTE_UNAVAILABLE
                || category == AdapterResultCategory.DEFERRED;
    }

    private static String code(BinanceApiException exception) {
        return exception.errorCode() == null || exception.errorCode().isBlank()
                ? "BINANCE_ADAPTER_ERROR"
                : exception.errorCode();
    }

    private static String message(BinanceApiException exception) {
        return exception.errorMessage() == null || exception.errorMessage().isBlank()
                ? exception.getMessage()
                : exception.errorMessage();
    }
}
