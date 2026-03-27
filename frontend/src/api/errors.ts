import type {AxiosError} from 'axios';

import type {ApiErrorResponse, AppApiError} from '@/types/api';

function isApiErrorResponse(payload: unknown): payload is ApiErrorResponse {
    if (!payload || typeof payload !== 'object') {
        return false;
    }

    return 'message' in payload
        || 'code' in payload
        || 'traceId' in payload
        || 'fieldErrors' in payload;
}

/**
 * 统一把 AxiosError 归一化为前端内部错误模型。
 * Why:
 * 拦截器接收到的错误响应体并不总是 `ApiErrorResponse`，例如网络失败、代理错误、
 * 或第三方中间层返回的非标准 payload；这里必须先做类型收窄，不能假定后端一定按正式模型返回。
 */
export function normalizeApiError(error: AxiosError<unknown>): AppApiError {
    const responsePayload = error.response?.data;
    const responseBody = isApiErrorResponse(responsePayload) ? responsePayload : undefined;
    const normalized = new Error(
        responseBody?.message || error.message || '请求失败，请稍后重试。',
    ) as AppApiError;

    normalized.name = 'AppApiError';
    normalized.status = error.response?.status ?? 0;
    normalized.code = responseBody?.code || 'UNKNOWN_ERROR';
    normalized.traceId = responseBody?.traceId;
    normalized.path = responseBody?.path ?? error.config?.url;
    normalized.fieldErrors = responseBody?.fieldErrors ?? [];
    normalized.raw = responseBody ?? error.toJSON();

    return normalized;
}

export function formatApiError(error: AppApiError): string {
    const details = error.fieldErrors
        .map((item) => `${item.field ?? 'field'}: ${item.message ?? 'invalid'}`)
        .join('；');

    if (details) {
        return `${error.message}（${details}）`;
    }

    if (error.traceId) {
        return `${error.message}（traceId: ${error.traceId}）`;
    }

    return error.message;
}
