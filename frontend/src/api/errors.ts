import type {AxiosError} from 'axios';
import type {ApiFieldError, AppApiError} from '@/types/api';
import {resolveErrorCatalog} from '@/errors/catalog';
import {t} from '@/i18n';
import {hasGlobalErrorPresentation} from '@/utils/error-events';

const text = (value: unknown): string | undefined => typeof value === 'string' && value.length > 0 ? value : undefined;
const record = (value: unknown): Record<string, unknown> => value && typeof value === 'object' ? value as Record<string, unknown> : {};

/** 保留诊断数据，但绝不把不可信响应体作为本地化文案或错误身份。 */
export function normalizeApiError(error: AxiosError<unknown>): AppApiError {
    const body = record(error.response?.data);
    const normalized = new Error(text(body.message) ?? error.message ?? 'UNKNOWN_ERROR') as AppApiError;
    normalized.name = 'AppApiError';
    normalized.status = error.response?.status ?? 0;
    normalized.code = text(body.code) ?? (error.response ? 'UNKNOWN_ERROR' : 'NETWORK_ERROR');
    normalized.errorId = text(body.errorId);
    normalized.errorKey = text(body.errorKey);
    normalized.traceId = text(body.traceId) ?? text(error.response?.headers?.['x-trace-id']);
    normalized.path = text(body.path) ?? error.config?.url;
    normalized.fieldErrors = Array.isArray(body.fieldErrors) ? body.fieldErrors.map((value): ApiFieldError => {
        const field = record(value);
        return {field: text(field.field), message: text(field.message), reason: text(field.reason),
            rejectedValue: field.rejectedValue};
    }) : [];
    normalized.raw = error.response?.data ?? error.toJSON();
    return normalized;
}

export function describeApiError(error: Partial<AppApiError> | null | undefined) {
    const safeError = error ?? {};
    const catalog = resolveErrorCatalog(safeError);
    const fields = (Array.isArray(safeError.fieldErrors) ? safeError.fieldErrors : []).map((item) => ({
        field: item.field,
        // 无稳定 constraint code 时只展示通用校验说明，原始 message 仍留在 normalized error。
        message: t('errors:fieldInvalid', {
            field: item.field && /^[A-Za-z_$][\w.$[\]-]{0,127}$/.test(item.field)
                ? item.field : t('errors:requestField'),
        }),
    }));
    const diagnostics = [...new Set([safeError.errorId, safeError.errorKey, safeError.code]), safeError.traceId ? `traceId: ${safeError.traceId}` : undefined]
        .filter(Boolean).join(' · ');
    return {
        catalog, title: t(catalog.titleKey), message: t(catalog.messageKey), action: t(catalog.actionKey),
        fields, diagnostics, traceId: safeError.traceId,
    };
}

/** 所有页面和通知共享同一语义；字段错误与 traceId 同时保留。 */
export function formatApiError(error: Partial<AppApiError>): string {
    const view = describeApiError(error);
    return [view.message, view.action, ...view.fields.map((field) => field.message), view.diagnostics].filter(Boolean).join(' ');
}

/** 写操作的短提示统一入口；已由全局通知承接的错误不重复弹出。 */
export function showApiError(error: Partial<AppApiError>, sink: {
    error(content: string): unknown; warning(content: string): unknown;
}): void {
    if (hasGlobalErrorPresentation(error)) return;
    const view = describeApiError(error);
    sink[view.catalog.severity](formatApiError(error));
}
